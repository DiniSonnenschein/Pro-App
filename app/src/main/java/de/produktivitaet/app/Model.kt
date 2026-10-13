package de.produktivitaet.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Ein Ort, den man auf der Startseite wählen und Schritten zuordnen kann. Orte lassen sich in den
 * Einstellungen anlegen und löschen; gespeichert wird die [id] (bei den mitgelieferten Orten der alte Enum-Name).
 */
data class Place(val id: String, val label: String) {
    companion object {
        val DEFAULTS = listOf(
            Place("CAFE", "Café"),
            Place("TRANSIT", "Öffis"),
            Place("OTHER", "Sonstiges"),
            Place("CITY", "Stadt"),
            Place("TRAIN", "Zugfahrt"),
            Place("HOME", "Zuhause"),
        )

        private val collator = java.text.Collator.getInstance(java.util.Locale.GERMAN)

        /** Anzeige-Reihenfolge: alphabetisch. */
        fun sorted(places: Collection<Place>): List<Place> = places.sortedWith(compareBy(collator) { it.label })
    }
}

/**
 * Orte eines Schritts (als Ort-IDs). [everywhere] ist eine eigene Markierung, damit "Überall" auch
 * Orte einschließt, die später einmal dazukommen.
 */
data class Places(val ids: Set<String> = emptySet(), val everywhere: Boolean = false) {
    val isEmpty: Boolean get() = !everywhere && ids.isEmpty()

    operator fun contains(place: Place): Boolean = everywhere || place.id in ids

    /** Text für die Anzeige; [all] = alle vorhandenen Orte in Anzeige-Reihenfolge. */
    fun label(all: List<Place>): String = when {
        everywhere -> "Überall"
        else -> all.filter { it.id in ids }.joinToString(", ") { it.label }
    }

    fun toggle(place: Place, all: List<Place>): Places = when {
        everywhere -> Places(all.mapTo(HashSet()) { it.id } - place.id)
        place.id in ids -> Places(ids - place.id)
        else -> of(ids + place.id, all)
    }

    fun toggleEverywhere(): Places = if (everywhere) Places() else Places(everywhere = true)

    /** Ort gelöscht: Wäre danach kein Ort mehr übrig, gilt der Schritt überall. */
    fun without(placeId: String): Places = when {
        everywhere || placeId !in ids -> this
        ids.size == 1 -> Places(everywhere = true)
        else -> Places(ids - placeId)
    }

    companion object {
        /** Sind alle Orte einzeln angehakt, ist das gleichbedeutend mit "Überall". */
        fun of(ids: Set<String>, all: List<Place>): Places =
            if (all.isNotEmpty() && ids.containsAll(all.map { it.id })) Places(everywhere = true) else Places(ids)
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
    /** Pool (Ordner), in dem die Karte liegt; null = normale Liste. */
    val poolId: Long? = null,
    /** Position in ihrer Liste: kleiner = weiter oben. */
    val sortKey: Long = 0,
    /** Nur Vorlagen: automatisch als Aufgabe hinzufügen (Termin und Wiederholung). */
    val auto: AutoAdd? = null,
) {
    /**
     * Suche: Jedes Wort muss im Titel oder in einer Schritt-Beschreibung vorkommen;
     * eine Zahl passt auch, wenn ein Schritt genau so viele Minuten dauert.
     */
    fun matches(query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.all { w ->
            val minutes = w.toIntOrNull()
            (minutes != null && steps.any { it.minutes == minutes }) ||
                title.lowercase().contains(w) ||
                steps.any { it.title.lowercase().contains(w) }
        }
    }

    val currentStep: Step get() = steps.first()
    val totalSteps: Int get() = completedSteps + steps.size

    /** Passt der aktuelle Schritt? null = keine Einschränkung bei Ort bzw. Zeit. */
    fun fits(place: Place?, availableMinutes: Int?): Boolean =
        (availableMinutes == null || currentStep.minutes <= availableMinutes) &&
            (place == null || place in currentStep.places)

    /** Die Aufgabe nach "Erledigt": nächster Schritt, oder null, wenn keiner mehr übrig ist. */
    fun advanced(): Task? =
        if (steps.size > 1) copy(steps = steps.drop(1), completedSteps = completedSteps + 1) else null
}

/** [poolId] != null: nur Aufgaben aus diesem Pool. */
fun eligibleTasks(tasks: List<Task>, place: Place?, availableMinutes: Int?, poolId: Long? = null): List<Task> =
    tasks.filter { (poolId == null || it.poolId == poolId) && it.fits(place, availableMinutes) }

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
    /** Neu gewonnene Palettenfarbe (Index) oder null. */
    val color: Int? = null,
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

    /** Auf einer Pool-Karte – gehört nur zur Pool-Karte, nicht zu den Aufgaben darin. */
    data class OnPool(val poolId: Long) : Anchor
}

/** Ordner für Aufgaben oder Vorlagen (getrennt je Reiter). Unterordner gibt es nicht. */
data class Pool(
    val id: Long,
    val name: String,
    val forTemplates: Boolean,
    val sortKey: Long = 0,
    /** Aufgaben-Pool, der einen Vorlagen-Pool spiegelt: Id des Vorlagen-Pools. */
    val sourcePoolId: Long? = null,
    /** Pilz-Kopien der Vorlagen-Pool-Karte, die auf diesem Spiegel-Pool gelöscht wurden. */
    val hiddenCopies: Set<Long> = emptySet(),
)

/** Wiederholung für "Automatisch hinzufügen". */
sealed interface Repeat {
    data object Once : Repeat
    data class EveryDays(val days: Int) : Repeat
    data class MonthDay(val day: Int) : Repeat
}

/** Nächster Termin (Epoch-Tag), an dem aus der Vorlage eine Aufgabe entsteht, und die Wiederholung. */
data class AutoAdd(val nextDue: Long, val repeat: Repeat) {
    /** Termin nach [today]; null, wenn es keinen weiteren gibt. Verpasste Termine werden übersprungen. */
    fun advancedAfter(today: Long): AutoAdd? = when (val r = repeat) {
        Repeat.Once -> null
        is Repeat.EveryDays -> {
            var d = nextDue
            val step = r.days.coerceAtLeast(1)
            while (d <= today) d += step
            copy(nextDue = d)
        }
        is Repeat.MonthDay -> {
            var month = YearMonth.from(LocalDate.ofEpochDay(nextDue))
            var date = dayInMonth(month, r.day)
            while (date.toEpochDay() <= today) {
                month = month.plusMonths(1)
                date = dayInMonth(month, r.day)
            }
            copy(nextDue = date.toEpochDay())
        }
    }

    fun describe(): String {
        val date = LocalDate.ofEpochDay(nextDue).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        return when (val r = repeat) {
            Repeat.Once -> "Automatisch am $date"
            is Repeat.EveryDays -> if (r.days == 1) "Automatisch täglich, nächstes Mal $date" else "Automatisch alle ${r.days} Tage, nächstes Mal $date"
            is Repeat.MonthDay -> "Automatisch am ${r.day}. jedes Monats, nächstes Mal $date"
        }
    }
}

/** Der [day]. Tag im Monat – in kürzeren Monaten der letzte Tag. */
fun dayInMonth(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

/** Ein Eintrag in der Übersicht: eine Karte (Aufgabe/Vorlage) oder ein Pool. */
sealed interface ListEntry {
    val key: String
    val sortKey: Long

    data class Card(val task: Task) : ListEntry {
        override val key get() = "t${task.id}"
        override val sortKey get() = task.sortKey
    }

    data class Folder(val pool: Pool) : ListEntry {
        override val key get() = "p${pool.id}"
        override val sortKey get() = pool.sortKey
    }
}

/** Fenster eines Pools (dekorierbar wie jedes andere Fenster). */
fun poolWindow(poolId: Long) = "pool-$poolId"

/** Area-Schlüssel für Karten; alle anderen Schlüssel sind Buttons/Felder eines Fensters. */
fun taskKey(id: Long) = "task:$id"
fun templateKey(id: Long) = "template:$id"
fun poolKey(id: Long) = "pool:$id"

fun anchorForArea(window: String, key: String): Anchor = when {
    key.startsWith("task:") -> Anchor.OnTask(key.removePrefix("task:").toLong())
    key.startsWith("template:") -> Anchor.OnTemplate(key.removePrefix("template:").toLong())
    key.startsWith("pool:") -> Anchor.OnPool(key.removePrefix("pool:").toLong())
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
