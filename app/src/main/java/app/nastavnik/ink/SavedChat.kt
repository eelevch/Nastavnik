package app.nastavnik.ink

import app.nastavnik.ai.Drawing
import app.nastavnik.ai.DrawingKind
import app.nastavnik.ai.Mark
import app.nastavnik.ai.MarkKind
import app.nastavnik.ai.NBox
import app.nastavnik.session.AnswerUi
import app.nastavnik.session.AskKind
import app.nastavnik.ui.NotebookController.MentorChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val kind: String = "USER",
    val answer: SavedAnswerUi? = null,
    val timestamp: Long,
)

@Serializable
data class SavedAnswerUi(
    val seq: Long,
    val kind: String,
    val question: String,
    val marks: List<SavedMark> = emptyList(),
    val done: Boolean = false,
    val allGood: Boolean = false,
    val errorText: String? = null,
    val status: String? = null,
)

@Serializable
data class SavedMark(
    val kind: String,
    val box: SavedBox? = null,
    val text: String,
    val linesStart: Int? = null,
    val linesEnd: Int? = null,
    val after: Int? = null,
    val detail: String? = null,
    val drawingKind: String? = null,
    val coordMiss: Boolean = false,
)

@Serializable
data class SavedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

object SavedChatCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(messages: List<MentorChatMessage>): String {
        val saved = messages.map { msg ->
            SavedChatMessage(
                id = msg.id,
                isUser = msg.isUser,
                text = msg.text,
                kind = msg.kind.name,
                timestamp = msg.timestamp,
                answer = msg.answer?.let { ans ->
                    SavedAnswerUi(
                        seq = ans.seq,
                        kind = ans.kind.name,
                        question = ans.question,
                        marks = ans.marks.map { m ->
                            SavedMark(
                                kind = m.kind.name,
                                box = m.box?.let { SavedBox(it.left, it.top, it.right, it.bottom) },
                                text = m.text,
                                linesStart = m.lines?.first,
                                linesEnd = m.lines?.last,
                                after = m.after,
                                detail = m.detail,
                                drawingKind = m.drawing?.kind?.name,
                                coordMiss = m.coordMiss,
                            )
                        },
                        done = ans.done,
                        allGood = ans.allGood,
                        errorText = ans.errorText,
                        status = ans.status,
                    )
                },
            )
        }
        return json.encodeToString(saved)
    }

    fun decode(jsonStr: String): List<MentorChatMessage> {
        return runCatching {
            val list = json.decodeFromString<List<SavedChatMessage>>(jsonStr)
            list.map { s ->
                val kind = runCatching { AskKind.valueOf(s.kind) }.getOrDefault(AskKind.USER)
                val ans = s.answer?.let { a ->
                    AnswerUi(
                        seq = a.seq,
                        kind = runCatching { AskKind.valueOf(a.kind) }.getOrDefault(AskKind.USER),
                        question = a.question,
                        marks = a.marks.map { m ->
                            Mark(
                                kind = runCatching { MarkKind.valueOf(m.kind) }.getOrDefault(MarkKind.ERROR),
                                box = m.box?.let { NBox(it.left, it.top, it.right, it.bottom) },
                                text = m.text,
                                lines = if (m.linesStart != null && m.linesEnd != null) m.linesStart..m.linesEnd else null,
                                after = m.after,
                                detail = m.detail,
                                drawing = m.drawingKind?.let { dk -> runCatching { Drawing(DrawingKind.valueOf(dk)) }.getOrNull() },
                                coordMiss = m.coordMiss,
                            )
                        },
                        streaming = false,
                        status = a.status,
                        done = a.done,
                        allGood = a.allGood,
                        errorText = a.errorText,
                    )
                }
                MentorChatMessage(
                    id = s.id,
                    isUser = s.isUser,
                    text = s.text,
                    kind = kind,
                    answer = ans,
                    timestamp = s.timestamp,
                )
            }
        }.getOrDefault(emptyList())
    }
}
