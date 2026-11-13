package com.animewave.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeQueueTest {
    @Test fun nextAndPreviousStayWithinQueueBounds() {
        assertEquals(1, EpisodeQueue.nextIndex(3, 0))
        assertEquals(2, EpisodeQueue.nextIndex(3, 1))
        assertNull(EpisodeQueue.nextIndex(3, 2))
        assertEquals(1, EpisodeQueue.previousIndex(3, 2))
        assertEquals(0, EpisodeQueue.previousIndex(3, 1))
        assertNull(EpisodeQueue.previousIndex(3, 0))
    }

    @Test fun emptyAndInvalidIndicesDoNotProduceQueueTargets() {
        assertNull(EpisodeQueue.nextIndex(0, 0))
        assertNull(EpisodeQueue.previousIndex(0, 0))
        assertNull(EpisodeQueue.nextIndex(2, 2))
        assertNull(EpisodeQueue.previousIndex(2, -1))
    }
}
