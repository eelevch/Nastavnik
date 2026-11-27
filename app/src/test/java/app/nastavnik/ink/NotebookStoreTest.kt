package app.nastavnik.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class NotebookStoreTest {
    @Test
    fun createWriteReloadReorderAndDelete() {
        val root = Files.createTempDirectory("nbs").toFile()
        val store = NotebookStore(root)
        val (m, first) = store.create("Матан", Paper.GRID, now = 1_000)
        first.strokes += Stroke(Tool.PEN, 1, 3f, floatArrayOf(1f, 2f, 0.5f, 3f, 4f, 0.6f))
        store.savePage(m.id, first)

        val second = InkPage(store.newId(2_000), Paper.LINED)
        val m2 = store.addPage(m, second, afterIndex = 0, now = 2_000)
        assertEquals(second.id, m2.lastEditedPageId)
        val third = InkPage(store.newId(3_000), Paper.DOTS)
        val m3 = store.addPage(m2, third, afterIndex = 0, now = 3_000) // между первой и второй
        assertEquals(third.id, m3.lastEditedPageId)

        val reloaded = store.meta(m.id)!!
        assertEquals(listOf(first.id, third.id, second.id), reloaded.pages)
        assertEquals(third.id, reloaded.lastEditedPageId)
        val pages = store.loadPages(reloaded)
        assertEquals(1, pages[0].strokes.size)
        assertEquals(Paper.DOTS, pages[1].paper)

        // Последнюю страницу удалить нельзя, остальные — можно; при удалении активной lastEditedPageId обновляется.
        val m4 = store.removePage(m3, third.id, now = 4_000)
        assertEquals(listOf(first.id, second.id), m4.pages)
        assertEquals(second.id, m4.lastEditedPageId)

        val other = store.createWith("Фото", listOf(InkPage(store.newId(), Paper.BLANK, "bg.jpg", 800f)), now = 5_000)
        assertEquals(listOf(other.id, m.id), store.list().map { it.id }) // свежие сверху
        assertEquals("Линал", store.rename(other, "  Линал ").title)

        store.delete(m.id)
        assertNull(store.meta(m.id))
        assertTrue(store.list().size == 1)
        root.deleteRecursively()
    }

    @Test
    fun idsAreUniqueEvenInTheSameMillisecond() {
        val store = NotebookStore(Files.createTempDirectory("ids").toFile())
        val ids = (1..100).map { store.newId(42) }
        assertEquals(100, ids.toSet().size)
    }

    @Test
    fun variantTasksSurviveReload() {
        val store = NotebookStore(Files.createTempDirectory("nbv").toFile())
        val (m, _) = store.create("Вариант: пределы", Paper.GRID, now = 1_000)
        val tasks = listOf(app.nastavnik.study.PracticeTask("Найти предел", "1", 2))
        store.saveMeta(m.copy(tasks = tasks))
        assertEquals(tasks, store.meta(m.id)!!.tasks)
        // Старые тетради без поля tasks читаются как без варианта.
        assertTrue(store.meta(store.create("Обычная", Paper.LINED, now = 2_000).first.id)!!.tasks.isEmpty())
    }

    @Test
    fun chatSaveLoadAndDelete() {
        val root = Files.createTempDirectory("nbs_chat").toFile()
        val store = NotebookStore(root)
        val (m, _) = store.create("Физика", Paper.GRID)
        assertNull(store.loadChat(m.id))

        val messages = listOf(
            app.nastavnik.ui.NotebookController.MentorChatMessage(
                id = 100L,
                isUser = true,
                text = "Как найти производную?",
                kind = app.nastavnik.session.AskKind.USER,
                timestamp = 1000L,
            ),
            app.nastavnik.ui.NotebookController.MentorChatMessage(
                id = 101L,
                isUser = false,
                text = "",
                kind = app.nastavnik.session.AskKind.USER,
                answer = app.nastavnik.session.AnswerUi(
                    seq = 101L,
                    kind = app.nastavnik.session.AskKind.USER,
                    question = "Как найти производную?",
                    marks = listOf(
                        app.nastavnik.ai.Mark(
                            kind = app.nastavnik.ai.MarkKind.HINT,
                            box = app.nastavnik.ai.NBox(0.1f, 0.2f, 0.3f, 0.4f),
                            text = "Используй правило дифференцирования произведения",
                        )
                    ),
                    actions = listOf(
                        app.nastavnik.ai.MentorPageAction(
                            id = "act_0",
                            kind = app.nastavnik.ai.MentorActionKind.WRITE_TEXT,
                            text = "(uv)' = u'v + uv'",
                            after = 2,
                        )
                    ),
                    done = true,
                ),
                timestamp = 1005L,
            ),
        )

        val encoded = SavedChatCodec.encode(messages, mapOf(101L to "APPROVED"))
        store.saveChat(m.id, encoded)

        val loadedRaw = store.loadChat(m.id)!!
        val (decoded, decodedStates) = SavedChatCodec.decodeWithStates(loadedRaw)
        assertEquals(2, decoded.size)
        assertEquals("Как найти производную?", decoded[0].text)
        assertTrue(decoded[0].isUser)
        assertFalse(decoded[1].isUser)
        assertEquals(1, decoded[1].answer!!.marks.size)
        assertEquals("Используй правило дифференцирования произведения", decoded[1].answer!!.marks[0].text)
        assertEquals(1, decoded[1].answer!!.actions.size)
        assertEquals(app.nastavnik.ai.MentorActionKind.WRITE_TEXT, decoded[1].answer!!.actions[0].kind)
        assertEquals("(uv)' = u'v + uv'", decoded[1].answer!!.actions[0].text)
        assertEquals("APPROVED", decodedStates[101L])
        assertTrue(decoded[1].answer!!.done)

        store.deleteChat(m.id)
        assertNull(store.loadChat(m.id))
        root.deleteRecursively()
    }
}
