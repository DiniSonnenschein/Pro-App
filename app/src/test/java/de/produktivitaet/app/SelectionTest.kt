package de.produktivitaet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    private val everywhere = Places(everywhere = true)

    @Test
    fun onlyTasksWithMatchingPlaceAndDurationAreEligible() {
        val tasks = listOf(
            Task(1, "Lesen", listOf(Step("", Places(setOf(Place.HOME, Place.CAFE)), 20))),
            Task(2, "Joggen", listOf(Step("", Places(setOf(Place.CITY)), 30))),
            Task(3, "Mails", listOf(Step("", everywhere, 10))),
            Task(4, "Aufräumen", listOf(Step("", Places(setOf(Place.HOME)), 60))),
        )

        assertEquals(listOf(1L, 3L), eligibleTasks(tasks, Place.HOME, 20).map { it.id })
        assertEquals(listOf(3L), eligibleTasks(tasks, Place.TRAIN, 90).map { it.id })
        assertEquals(emptyList<Task>(), eligibleTasks(tasks, Place.CITY, 5))
    }

    @Test
    fun onlyTheCurrentStepCounts() {
        val task = Task(
            1, "Umzug",
            listOf(
                Step("Kartons kaufen", Places(setOf(Place.CITY)), 30),
                Step("Packen", Places(setOf(Place.HOME)), 120),
            ),
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
    fun placesLabelsAndToggling() {
        assertEquals("Überall", everywhere.label)
        assertEquals("Café, Zuhause", Places(setOf(Place.HOME, Place.CAFE)).label)

        // Alle einzeln angehakt = Überall.
        var p = Places()
        Place.entries.forEach { p = p.toggle(it) }
        assertTrue(p.everywhere)

        // Einen Ort aus "Überall" abwählen → alle anderen bleiben angehakt.
        val withoutTrain = everywhere.toggle(Place.TRAIN)
        assertFalse(withoutTrain.everywhere)
        assertFalse(Place.TRAIN in withoutTrain)
        assertTrue(Place.OTHER in withoutTrain)

        assertTrue(Places().toggleEverywhere().everywhere)
        assertTrue(everywhere.toggleEverywhere().isEmpty)
    }
}
