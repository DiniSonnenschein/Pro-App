package de.produktivitaet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class RewardsTest {
    @Test
    fun weeksRunFromMondayToSunday() {
        val thursday = LocalDate.of(2026, 10, 1).toEpochDay()
        val monday = LocalDate.of(2026, 9, 28).toEpochDay()
        val sunday = LocalDate.of(2026, 10, 4).toEpochDay()
        assertEquals(monday, weekStart(thursday))
        assertEquals(monday, weekStart(monday))
        assertEquals(monday, weekStart(sunday))
        assertEquals(sunday + 1, weekStart(sunday + 1))
    }

    @Test
    fun progressCountsStepsOrMinutesInRange() {
        val log = listOf(Completion(10, 25), Completion(10, 5), Completion(11, 60), Completion(14, 30))
        assertEquals(2, progressOf(log, GoalUnit.TASKS, 10, 10))
        assertEquals(30, progressOf(log, GoalUnit.MINUTES, 10, 10))
        assertEquals(3, progressOf(log, GoalUnit.TASKS, 10, 13))
        assertEquals(120, progressOf(log, GoalUnit.MINUTES, 0, 20))
    }

    @Test
    fun goalFormatStopsAtGoal() {
        assertEquals("2 / 3 Aufgaben", Goal(3, GoalUnit.TASKS).format(2))
        assertEquals("60 / 60 Minuten", Goal(60, GoalUnit.MINUTES).format(75))
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

class DecorRulesTest {
    @Test
    fun overlapIsShareOfFirstRect() {
        val mushroom = floatArrayOf(0f, 0f, 10f, 10f)
        assertEquals(1f, overlapFraction(mushroom, floatArrayOf(-5f, -5f, 20f, 20f)), 0.001f)
        assertEquals(0.5f, overlapFraction(mushroom, floatArrayOf(5f, 0f, 30f, 10f)), 0.001f)
        assertEquals(0f, overlapFraction(mushroom, floatArrayOf(20f, 20f, 30f, 30f)), 0.001f)
    }

    @Test
    fun sizesGrowFromButtonSizeToHalfTheScreen() {
        assertEquals(64f, spriteSizeDp(1, 800f), 0.01f)
        assertEquals(440f, spriteSizeDp(5, 800f), 0.01f)
        assertTrue((1..4).all { spriteSizeDp(it, 800f) < spriteSizeDp(it + 1, 800f) })
        // Pilz-Wesen bleiben beim Verstellen in derselben Spanne.
        assertEquals(440f, spriteSizeDp(3, 800f, scale = 10f), 0.01f)
        assertEquals(64f, spriteSizeDp(3, 800f, scale = 0.01f), 0.01f)
    }

    @Test
    fun cardKeysBecomeTaskAndTemplateAnchors() {
        assertEquals(Anchor.OnTask(7), anchorForArea("overview", taskKey(7)))
        assertEquals(Anchor.OnTemplate(3), anchorForArea("add", templateKey(3)))
        assertEquals(Anchor.Area("home", "plus"), anchorForArea("home", "plus"))
    }
}
