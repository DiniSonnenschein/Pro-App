package de.produktivitaet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionTest {
    private val everywhere = Place.entries.toSet()

    @Test
    fun onlyTasksWithMatchingPlaceAndDurationAreEligible() {
        val tasks = listOf(
            Task(1, "Lesen", listOf(Step("", setOf(Place.HOME, Place.CAFE), 20))),
            Task(2, "Joggen", listOf(Step("", setOf(Place.CITY), 30))),
            Task(3, "Mails", listOf(Step("", everywhere, 10))),
            Task(4, "Aufräumen", listOf(Step("", setOf(Place.HOME), 60))),
        )

        assertEquals(listOf(1L, 3L), eligibleTasks(tasks, Place.HOME, 20).map { it.id })
        assertEquals(listOf(3L), eligibleTasks(tasks, Place.TRANSIT, 90).map { it.id })
        assertEquals(emptyList<Task>(), eligibleTasks(tasks, Place.CITY, 5))
    }

    @Test
    fun onlyTheCurrentStepCounts() {
        val task = Task(
            1, "Umzug",
            listOf(Step("Kartons kaufen", setOf(Place.CITY), 30), Step("Packen", setOf(Place.HOME), 120)),
        )

        assertEquals(1, eligibleTasks(listOf(task), Place.CITY, 30).size)
        assertEquals(0, eligibleTasks(listOf(task), Place.HOME, 200).size)

        val next = task.advanced()!!
        assertEquals("Packen", next.currentStep.title)
        assertEquals(1, next.completedSteps)
        assertEquals(2, next.totalSteps)
        assertEquals(1, eligibleTasks(listOf(next), Place.HOME, 200).size)
        assertNull(next.advanced())
    }

    @Test
    fun everywhereLabel() {
        assertEquals("Überall", everywhere.label())
        assertEquals("Zuhause, Café", setOf(Place.CAFE, Place.HOME).label())
    }
}
