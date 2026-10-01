package de.produktivitaet.app

import kotlin.random.Random

/** Reihenfolge = alphabetische Anzeige-Reihenfolge. Gespeichert wird der Name, daher ist Umsortieren gefahrlos. */
enum class Place(val label: String) {
    CAFE("Café"),
    TRANSIT("Öffis"),
    OTHER("Sonstiges"),
    CITY("Stadt"),
    TRAIN("Zugfahrt"),
    HOME("Zuhause"),
}

/**
 * Orte eines Schritts. [everywhere] ist eine eigene Markierung, damit "Überall" auch
 * Orte einschließt, die später einmal dazukommen.
 */
data class Places(val set: Set<Place> = emptySet(), val everywhere: Boolean = false) {
    val isEmpty: Boolean get() = !everywhere && set.isEmpty()

    operator fun contains(place: Place): Boolean = everywhere || place in set

    val label: String
        get() = when {
            everywhere -> "Überall"
            else -> Place.entries.filter { it in set }.joinToString(", ") { it.label }
        }

    fun toggle(place: Place): Places = when {
        everywhere -> Places(Place.entries.toSet() - place)
        place in set -> Places(set - place)
        else -> of(set + place)
    }

    fun toggleEverywhere(): Places = if (everywhere) Places() else Places(everywhere = true)

    companion object {
        /** Sind alle Orte einzeln angehakt, ist das gleichbedeutend mit "Überall". */
        fun of(set: Set<Place>): Places =
            if (set.containsAll(Place.entries)) Places(everywhere = true) else Places(set)
    }
}

fun minutesText(minutes: Int): String = if (minutes == 1) "1 Minute" else "$minutes Minuten"

data class Step(
    val title: String,
    val places: Places,
    val minutes: Int,
)

/**
 * Eine Aufgabe (oder Vorlage). [steps] enthält nur die noch offenen Schritte;
 * der erste davon ist der aktuelle, der ausgelost werden kann.
 */
data class Task(
    val id: Long,
    val title: String,
    val steps: List<Step>,
    val completedSteps: Int = 0,
    /** Vorlage, aus der die Aufgabe entstanden ist – deren Pilze erscheinen als Kopien auf ihr. */
    val templateId: Long? = null,
    /** Pilz-Kopien der Vorlage, die auf dieser Aufgabe gelöscht wurden. */
    val hiddenCopies: Set<Long> = emptySet(),
) {
    val currentStep: Step get() = steps.first()
    val totalSteps: Int get() = completedSteps + steps.size

    fun fits(place: Place, availableMinutes: Int): Boolean =
        currentStep.minutes <= availableMinutes && place in currentStep.places

    /** Die Aufgabe nach "Erledigt": nächster Schritt, oder null, wenn keiner mehr übrig ist. */
    fun advanced(): Task? =
        if (steps.size > 1) copy(steps = steps.drop(1), completedSteps = completedSteps + 1) else null
}

fun eligibleTasks(tasks: List<Task>, place: Place, availableMinutes: Int): List<Task> =
    tasks.filter { it.fits(place, availableMinutes) }

/** Ein erspielter Pilz. [species] ist der Dateiname im Ordner assets/pilze, [size] 1 (winzig) bis 5 (riesig). */
data class Mushroom(val id: Long, val species: String, val size: Int)

/** Der vorab ausgeloste nächste Pilz – so kann seine Silhouette schon gezeigt werden. */
data class MushroomRoll(val species: String, val size: Int)

/** Ergebnis eines erledigten Schritts, für die Feier-Animation. */
data class Reward(
    val points: Int,
    val startTotal: Int,
    val endTotal: Int,
    val earned: List<Mushroom>,
    val next: MushroomRoll?,
)

object Rewards {
    const val FIRST_MUSHROOM = 10
    const val POINTS_PER_MUSHROOM = 30
    const val MIN_POINTS = 5
    const val MAX_POINTS = 60

    val SIZE_NAMES = listOf("Winzig", "Klein", "Mittel", "Groß", "Riesig")

    /** Mittlere Größen etwas häufiger, ganz kleine und ganz große etwas seltener (in Prozent). */
    private val SIZE_WEIGHTS = listOf(17, 21, 24, 21, 17)

    /** Ein Punkt pro Minute, mindestens 5, höchstens 60. */
    fun pointsFor(minutes: Int): Int = minutes.coerceIn(MIN_POINTS, MAX_POINTS)

    /** Gesamtpunktzahl, ab der man den [count]-ten Pilz hat: 10, 40, 70, … */
    fun threshold(count: Int): Int =
        if (count <= 0) 0 else FIRST_MUSHROOM + POINTS_PER_MUSHROOM * (count - 1)

    fun mushroomsFor(points: Int): Int =
        if (points < FIRST_MUSHROOM) 0 else 1 + (points - FIRST_MUSHROOM) / POINTS_PER_MUSHROOM

    fun rollSize(random: Random): Int {
        var r = random.nextInt(SIZE_WEIGHTS.sum())
        SIZE_WEIGHTS.forEachIndexed { i, weight ->
            if (r < weight) return i + 1
            r -= weight
        }
        return 3
    }
}

/** Woran ein platzierter Pilz hängt. Die Position ist immer relativ zur linken oberen Ecke davon. */
sealed interface Anchor {
    /** Auf dem Fenster selbst, hinter allen Buttons. */
    data class Window(val window: String) : Anchor

    /** Auf einem Button/Feld eines Fensters, darauf zugeschnitten. */
    data class Area(val window: String, val key: String) : Anchor

    /** Auf einer Aufgabenkarte – gehört dann zur Aufgabe. */
    data class OnTask(val taskId: Long) : Anchor

    /** Auf einer Vorlagenkarte – wird auf alle Aufgaben aus dieser Vorlage kopiert. */
    data class OnTemplate(val templateId: Long) : Anchor
}

/** Area-Schlüssel für Karten; alle anderen Schlüssel sind Buttons/Felder eines Fensters. */
fun taskKey(id: Long) = "task:$id"
fun templateKey(id: Long) = "template:$id"

fun anchorForArea(window: String, key: String): Anchor = when {
    key.startsWith("task:") -> Anchor.OnTask(key.removePrefix("task:").toLong())
    key.startsWith("template:") -> Anchor.OnTemplate(key.removePrefix("template:").toLong())
    else -> Anchor.Area(window, key)
}

/** Ein platzierter Pilz. [x]/[y] = Mittelpunkt in dp relativ zum [anchor]. */
data class Placement(
    val mushroomId: Long,
    val anchor: Anchor,
    val x: Float,
    val y: Float,
    val mirrored: Boolean = false,
)

/** Höhe eines Pilzes auf dem Bildschirm: winzig = so groß wie die runden Buttons, riesig = gut die halbe Bildschirmhöhe. */
fun mushroomHeightDp(size: Int, screenHeightDp: Float): Float {
    val smallest = 64f
    val largest = maxOf(screenHeightDp * 0.55f, smallest * 2)
    val ratio = Math.pow((largest / smallest).toDouble(), 1.0 / 4).toFloat()
    return smallest * Math.pow(ratio.toDouble(), (size.coerceIn(1, 5) - 1).toDouble()).toFloat()
}

/** Anteil der Fläche von [a], der in [b] liegt (Rechtecke als left, top, right, bottom). */
fun overlapFraction(a: FloatArray, b: FloatArray): Float {
    val w = minOf(a[2], b[2]) - maxOf(a[0], b[0])
    val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
    val area = (a[2] - a[0]) * (a[3] - a[1])
    return if (w <= 0f || h <= 0f || area <= 0f) 0f else w * h / area
}
