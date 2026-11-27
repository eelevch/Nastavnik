package app.nastavnik.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nastavnik.R
import app.nastavnik.ai.AiException
import app.nastavnik.ai.ChatRequest
import app.nastavnik.ai.ChatTurn
import app.nastavnik.ai.Role
import app.nastavnik.ai.StreamUpdate
import app.nastavnik.app
import app.nastavnik.overlay.SpeechInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Компактный шар быстрого общения с наставником на главных экранах приложения
 * (Главная, Расписание, Оценки, Учёба): можно спросить текстом или голосом, не открывая отдельное окно.
 */
@Composable
fun BoxScope.GlobalMentorOverlay(
    bottomInset: Dp,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.app.settings
    val settings by remember { repo.flow() }.collectAsStateWithLifecycle(initialValue = repo.load())
    if (!settings.showAmbientMentor) return

    val scope = rememberCoroutineScope()
    val speaker = context.app.speaker
    val isSpeaking by speaker.isSpeaking.collectAsStateWithLifecycle()

    var open by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }
    var lastQuestion by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var needsSettings by remember { mutableStateOf(false) }
    var askJob by remember { mutableStateOf<Job?>(null) }

    var listening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableFloatStateOf(0f) }
    var partialVoice by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf<String?>(null) }

    fun send(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        val sNow = repo.load()
        val reason = repo.notReadyReason(sNow)
        if (reason != null) {
            errorText = "$reason. Открой настройки → «Нейросеть»."
            needsSettings = true
            return
        }
        askJob?.cancel()
        lastQuestion = clean
        replyText = ""
        errorText = null
        speechError = null
        needsSettings = false
        streaming = true
        askJob = scope.launch {
            try {
                val system = "Ты — дружелюбный и точный ИИ-наставник студента (${sNow.strictness.promptText}). " +
                    "Отвечай по-русски, кратко и понятно, формулы оформляй в LaTeX ($...$)."
                val buf = StringBuilder()
                val req = ChatRequest(
                    modelId = sNow.model,
                    system = system,
                    turns = listOf(ChatTurn(Role.USER, clean, hadImage = false)),
                    maxTokens = 1600,
                    fast = sNow.fastAnswers,
                )
                context.app.ai(sNow).stream(repo.apiKey(sNow.provider), req) { upd ->
                    when (upd) {
                        is StreamUpdate.Text -> {
                            buf.append(upd.delta)
                            replyText = buf.toString()
                        }
                        is StreamUpdate.Restart -> {
                            buf.setLength(0)
                            replyText = ""
                        }
                        else -> Unit
                    }
                }
            } catch (e: AiException) {
                errorText = e.userMessage
                needsSettings = e.needsSettings
            } catch (e: Exception) {
                errorText = e.message ?: "Не удалось получить ответ"
            } finally {
                streaming = false
            }
        }
    }

    val speech = remember {
        SpeechInput(context, object : SpeechInput.Callback {
            override fun onListening(active: Boolean) {
                listening = active
                if (!active) {
                    voiceRms = 0f
                    partialVoice = ""
                }
            }
            override fun onPartial(text: String) {
                partialVoice = text
            }
            override fun onResult(text: String) {
                listening = false
                voiceRms = 0f
                partialVoice = ""
                if (text.isNotBlank()) {
                    open = true
                    send(text)
                }
            }
            override fun onError(message: String, needsPermission: Boolean) {
                listening = false
                voiceRms = 0f
                partialVoice = ""
                speechError = message
            }
            override fun onRmsChanged(rmsdB: Float) {
                voiceRms = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
        })
    }
    DisposableEffect(speech) {
        onDispose {
            askJob?.cancel()
            speech.destroy()
        }
    }

    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 26.dp, bottom = bottomInset + 14.dp)
            .imePadding()
            .zIndex(4f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 480f), initialScale = 0.88f),
            exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.90f),
        ) {
            Column(
                Modifier
                    .widthIn(max = 368.dp)
                    .fillMaxWidth(0.92f)
                    .shadow(14.dp, RoundedCornerShape(26.dp))
                    .glass(RoundedCornerShape(26.dp), 12.dp, strong = true)
                    .border(0.8.dp, Brand.Line, RoundedCornerShape(26.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MascotIcon(
                        thinking = streaming,
                        speaking = isSpeaking,
                        listening = listening,
                        voiceLevel = voiceRms,
                        sizeDp = 26.dp,
                    )
                    Text(
                        if (streaming) "Думаю…" else "Быстрый наставник",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (replyText.isNotBlank() && !streaming) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { speaker.speak(replyText) }
                                .semantics { contentDescription = "Озвучить ответ" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (listening) speech.stop()
                                open = false
                            }
                            .semantics { contentDescription = "Свернуть" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = Brand.InkSoft, modifier = Modifier.size(16.dp))
                    }
                }

                (errorText ?: speechError)?.let { err ->
                    StatusLine(err, Tone.ERROR)
                    if (needsSettings) LinkText("Открыть настройки", onClick = onOpenSettings)
                }

                if (lastQuestion != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            lastQuestion!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = Brand.InkSoft,
                        )
                        if (replyText.isNotBlank()) {
                            RichText(replyText, sizePx = 14, streaming = streaming)
                        } else if (streaming) {
                            Text("Формулирую ответ…", style = MaterialTheme.typography.bodySmall, color = Brand.InkSoft)
                        }
                    }
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "Объясни тему" to "Кратко объясни ключевую идею по теме, которую я сейчас повторяю.",
                            "Формула" to "Напомни основные формулы и когда какую применять.",
                            "План подготовки" to "Составь короткий план подготовки к контрольной на 30 минут.",
                        ).forEach { (label, prompt) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Brand.Ink,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Brand.Ink.copy(alpha = 0.06f))
                                    .clickable(enabled = !streaming) { send(prompt) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                if (listening && partialVoice.isNotBlank()) {
                    Text(
                        "«$partialVoice»",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val micScale = if (listening) 1f + voiceRms * 0.2f else 1f
                    Box(
                        Modifier
                            .size(36.dp)
                            .graphicsLayer { scaleX = micScale; scaleY = micScale }
                            .clip(CircleShape)
                            .background(if (listening) Brand.Accent else Brand.Ink.copy(alpha = 0.06f))
                            .clickable {
                                if (isSpeaking) speaker.stop()
                                speech.toggle()
                            }
                            .semantics { contentDescription = "Спросить голосом" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_mic),
                            contentDescription = null,
                            tint = if (listening) Brand.OnAccent else Brand.Ink,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Row(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(Brand.Ink.copy(alpha = 0.05f))
                            .border(0.6.dp, Brand.Line, CircleShape)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Brand.Ink),
                            cursorBrush = SolidColor(Brand.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (promptText.isNotBlank()) {
                                    val q = promptText
                                    promptText = ""
                                    send(q)
                                }
                            }),
                            decorationBox = { inner ->
                                if (promptText.isEmpty()) {
                                    Text("Спросить наставника…", style = MaterialTheme.typography.bodySmall, color = Brand.InkSoft)
                                }
                                inner()
                            },
                        )
                        if (promptText.isNotBlank()) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Brand.Accent)
                                    .clickable {
                                        val q = promptText
                                        promptText = ""
                                        send(q)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_send), contentDescription = "Отправить", tint = Brand.OnAccent, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }

        if (!open) {
            Box(
                Modifier
                    .size(50.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brand.Glass)
                    .border(0.8.dp, Brand.Line, CircleShape)
                    .clickable { open = true }
                    .semantics { contentDescription = "Спросить наставника" },
                contentAlignment = Alignment.Center,
            ) {
                MascotIcon(thinking = streaming, speaking = isSpeaking, listening = listening, voiceLevel = voiceRms, sizeDp = 34.dp)
            }
        }
    }
}
