package de.produktivitaet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val ALL = Place.sorted(Place.DEFAULTS)
private fun place(id: String) = Place.DEFAULTS.first { it.id == id }
private val HOME = place("HOME")
private val CAFE = place("CAFE")
private val CITY = place("CITY")
private val TRAIN = place("TRAIN")
private val OTHER = place("OTHER")
private fun at(vararg p: Place) = Places(p.mapTo(HashSet()) { it.id })

class SelectionTest {
    private val everywhere = Places(everywhere = true)

    @Test
    fun onlyTasksWithMatchingPlaceAndDurationAreEligible() {
        val tasks = listOf(
            Task(1, "Lesen", listOf(Step("", at(HOME, CAFE), 20))),
            Task(2, "Joggen", listOf(Step("", at(CITY), 30))),
            Task(3, "Mails", listOf(Step("", everywhere, 10))),
            Task(4, "Aufräumen", listOf(Step("", at(HOME), 60))),
        )

        assertEquals(listOf(1L, 3L), eligibleTasks(tasks, HOME, 20).map { it.id })
        assertEquals(listOf(3L), eligibleTasks(tasks, TRAIN, 90).map { it.id })
        assertEquals(emptyList<Task>(), eligibleTasks(tasks, CITY, 5))
    }

    @Test
    fun onlyTheCurrentStepCounts() {
        val task = Task(
            1, "Umzug",
            listOf(
                Step("Kartons kaufen", at(CITY), 30),
                Step("Packen", at(HOME), 120),
            ),
        )

        assertEquals(1, eligibleTasks(listOf(task), CITY, 30).size)
        assertEquals(0, eligibleTasks(listOf(task), HOME, 200).size)

        val next = task.advanced()!!
        assertEquals("Packen", next.currentStep.title)
        assertEquals(1, next.completedSteps)
        assertEquals(2, next.totalSteps)
        assertEquals(1, eligibleTasks(listOf(next), HOME, 200).size)
        assertNull(next.advanced())
    }

    @Test
    fun placesLabelsAndToggling() {
        assertEquals("Überall", everywhere.label(ALL))
        assertEquals("Café, Zuhause", at(HOME, CAFE).label(ALL))

        // Alle einzeln angehakt = Überall.
        var p = Places()
        ALL.forEach { p = p.toggle(it, ALL) }
        assertTrue(p.everywhere)

        // Einen Ort aus "Überall" abwählen → alle anderen bleiben angehakt.
        val withoutTrain = everywhere.toggle(TRAIN, ALL)
        assertFalse(withoutTrain.everywhere)
        assertFalse(TRAIN in withoutTrain)
        assertTrue(OTHER in withoutTrain)

        assertTrue(Places().toggleEverywhere().everywhere)
        assertTrue(everywhere.toggleEverywhere().isEmpty)
    }

    @Test
    fun deletingAPlace() {
        // Einziger Ort gelöscht → überall.
        assertTrue(at(TRAIN).without("TRAIN").everywhere)
        assertEquals(at(HOME), at(HOME, TRAIN).without("TRAIN"))
        assertEquals(everywhere, everywhere.without("TRAIN"))
        // Neue Orte sind in "Überall" enthalten.
        assertTrue(Place("p1", "Garten") in everywhere)
        assertEquals(listOf("Café", "Öffis", "Sonstiges"), Place.sorted(listOf(OTHER, place("TRANSIT"), CAFE)).map { it.label })
    }

    @Test
    fun drawingFromAPool() {
        val tasks = listOf(
            Task(1, "A", listOf(Step("", everywhere, 10)), poolId = 7),
            Task(2, "B", listOf(Step("", everywhere, 10))),
        )
        assertEquals(listOf(1L), eligibleTasks(tasks, null, null, 7).map { it.id })
        assertEquals(2, eligibleTasks(tasks, null, null, null).size)
    }
}

class SearchTest {
    private val task = Task(
        1, "Wohnung putzen",
        listOf(Step("Bad wischen", Places(everywhere = true), 15), Step("Fenster", Places(everywhere = true), 30)),
    )

    @Test
    fun findsWordsInTitleAndSteps() {
        assertTrue(task.matches("putzen"))
        assertTrue(task.matches("BAD"))
        assertTrue(task.matches("wohnung fenster"))
        assertFalse(task.matches("garten"))
    }

    @Test
    fun numbersMatchStepDurations() {
        assertTrue(task.matches("15"))
        assertTrue(task.matches("30"))
        assertFalse(task.matches("20"))
    }
}

class OpenDrawTest {
    @Test
    fun withoutPlaceOrTimeEverythingFits() {
        val tasks = listOf(
            Task(1, "Lang", listOf(Step("", at(CITY), 240))),
            Task(2, "Kurz", listOf(Step("", at(HOME), 5))),
        )
        assertEquals(2, eligibleTasks(tasks, null, null).size)
        assertEquals(listOf(2L), eligibleTasks(tasks, HOME, null).map { it.id })
        assertEquals(listOf(2L), eligibleTasks(tasks, null, 30).map { it.id })
    }
}
