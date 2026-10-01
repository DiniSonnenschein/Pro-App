package de.produktivitaet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RewardsTest {
    @Test
    fun pointsAreMinutesBetween5And60() {
        assertEquals(5, Rewards.pointsFor(1))
        assertEquals(25, Rewards.pointsFor(25))
        assertEquals(60, Rewards.pointsFor(240))
    }

    @Test
    fun firstMushroomAt10ThenEvery30() {
        assertEquals(0, Rewards.mushroomsFor(9))
        assertEquals(1, Rewards.mushroomsFor(10))
        assertEquals(1, Rewards.mushroomsFor(39))
        assertEquals(2, Rewards.mushroomsFor(40))
        assertEquals(4, Rewards.mushroomsFor(100))
        assertEquals(listOf(0, 10, 40, 70), (0..3).map { Rewards.threshold(it) })
        for (points in 0..500) {
            val n = Rewards.mushroomsFor(points)
            assertTrue(points >= Rewards.threshold(n) && points < Rewards.threshold(n + 1))
        }
    }

    @Test
    fun middleSizesAreSlightlyMoreLikely() {
        val random = Random(42)
        val counts = IntArray(6)
        repeat(100_000) { counts[Rewards.rollSize(random)]++ }
        assertEquals(0, counts[0])
        assertTrue(counts[3] > counts[2] && counts[2] > counts[1])
        assertTrue(counts[3] > counts[4] && counts[4] > counts[5])
    }
}
