package org.schabi.newpipe.videoprime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType

class QueueStoreTest {
    private fun queue(id: Long, name: String, index: Int, position: Long) = SavedQueue(
        id = id,
        name = name,
        items = listOf(
            SavedItem(
                0,
                "https://www.youtube.com/watch?v=aaaaaaaaaaa",
                "Uno",
                "Canal",
                60,
                StreamType.VIDEO_STREAM,
                "https://i.ytimg.com/a.jpg"
            ),
            SavedItem(
                7,
                "content://downloads/b.m4a",
                "Dos",
                "",
                0,
                StreamType.AUDIO_STREAM,
                null
            )
        ),
        index = index,
        positionMillis = position,
        updated = 1234
    )

    @Test
    fun savesAndLoadsQueues() {
        val file = File(Files.createTempDirectory("queues").toFile(), "q.json")
        val store = QueueStore(file)
        assertEquals(QueueStore.Content(emptyList(), null), store.load())

        val content = QueueStore.Content(listOf(queue(1, "Música", 1, 42_000), queue(2, "Pods", 0, 0)), 1)
        store.save(content)
        assertEquals(content, QueueStore(file).load())

        store.save(content.copy(activeId = null))
        assertNull(store.load().activeId)
    }

    @Test
    fun corruptFileLoadsEmpty() {
        val file = File(Files.createTempDirectory("queues").toFile(), "q.json")
        file.writeText("{not json")
        assertEquals(QueueStore.Content(emptyList(), null), QueueStore(file).load())
    }

    @Test
    fun playQueueResumesAtItemAndPosition() {
        val playQueue = queue(1, "Música", 1, 42_000).toPlayQueue()
        assertEquals(2, playQueue.size())
        assertEquals(1, playQueue.index)
        assertEquals("Dos", playQueue.item!!.title)
        assertEquals(7, playQueue.item!!.serviceId)
        assertEquals(42_000L, playQueue.item!!.recoveryPosition)
    }
}
