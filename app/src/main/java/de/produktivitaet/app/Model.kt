package de.produktivitaet.app

import java.time.DayOfWeek
import java.time.LocalDate
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

/**
 * Ein erspieltes Sprite (Pilz oder Pilz-Wesen). [species] ist der Dateiname im Ordner assets/sprites,
 * [size] 1 (winzig) bis 5 (riesig); Pilz-Wesen haben immer 3 und werden über [Placement.scale] verstellt.
 */
data class Mushroom(val id: Long, val species: String, val size: Int)

/** Der vorab ausgeloste nächste Pilz – so kann seine Silhouette schon gezeigt werden. */
data class MushroomRoll(val species: String, val size: Int)

enum class GoalUnit(val label: String) {
    TASKS("Aufgaben"),
    MINUTES("Minuten"),
}

/** Tages- oder Wochenziel: [value] Aufgaben bzw. Minuten. */
data class Goal(val value: Int, val unit: GoalUnit) {
    fun format(progress: Int) = "${progress.coerceAtMost(value)} / $value ${unit.label}"
}

/** Ein erledigter Schritt (für Ziele und Statistik). */
data class Completion(val epochDay: Long, val minutes: Int)

/** Montag der Woche (Montag–Sonntag), in der [epochDay] liegt. */
fun weekStart(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay).with(DayOfWeek.MONDAY).toEpochDay()

/** Aufgaben bzw. Minuten im Zeitraum [from]..[to] (Epoch-Tage, beide inklusive). */
fun progressOf(completions: List<Completion>, unit: GoalUnit, from: Long, to: Long): Int =
    completions.filter { it.epochDay in from..to }.sumOf { if (unit == GoalUnit.TASKS) 1 else it.minutes }

/** Fortschritt zu einem Ziel vor und nach einem erledigten Schritt. */
data class GoalProgress(
    val goal: Goal,
    val before: Int,
    val after: Int,
    /** Belohnung für diesen Tag/diese Woche gab es schon vorher. */
    val alreadyRewarded: Boolean,
)

/** Ergebnis eines erledigten Schritts, für die Feier. */
data class Reward(
    val day: GoalProgress,
    val week: GoalProgress,
    /** Heute neu erspielter Pilz (Tagesziel erreicht) oder null. */
    val mushroom: Mushroom?,
    /** Neu erspieltes Pilz-Wesen (Wochenziel erreicht) oder null. */
    val being: Mushroom?,
    /** Silhouetten, falls nichts Neues erspielt wurde. null = keins mehr übrig. */
    val nextMushroom: String?,
    val nextBeing: String?,
)

object Rewards {
    val SIZE_NAMES = listOf("Winzig", "Klein", "Mittel", "Groß", "Riesig")

    const val BEING_SIZE = 3

    val DEFAULT_DAILY = Goal(3, GoalUnit.TASKS)
    val DEFAULT_WEEKLY = Goal(20, GoalUnit.TASKS)

    /** Mittlere Größen etwas häufiger, ganz kleine und ganz große etwas seltener (in Prozent). */
    private val SIZE_WEIGHTS = listOf(17, 21, 24, 21, 17)

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
    /** Nur Pilz-Wesen: Größenfaktor, im Pilz-Modus mit − / + verstellbar. */
    val scale: Float = 1f,
)

private const val SMALLEST_SPRITE_DP = 64f
private fun largestSpriteDp(screenHeightDp: Float) = maxOf(screenHeightDp * 0.55f, SMALLEST_SPRITE_DP * 2)

/**
 * Längste Seite eines Sprites auf dem Bildschirm: winzig = so groß wie die runden Buttons,
 * riesig = gut die halbe Bildschirmhöhe. Mit [scale] (Pilz-Wesen) bleibt es in derselben Spanne.
 */
fun spriteSizeDp(size: Int, screenHeightDp: Float, scale: Float = 1f): Float =
    (stepSizeDp(size, screenHeightDp) * scale).coerceIn(SMALLEST_SPRITE_DP, largestSpriteDp(screenHeightDp))

private fun stepSizeDp(size: Int, screenHeightDp: Float): Float {
    val smallest = SMALLEST_SPRITE_DP
    val largest = largestSpriteDp(screenHeightDp)
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
