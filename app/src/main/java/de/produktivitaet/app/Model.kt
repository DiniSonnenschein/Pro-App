package de.produktivitaet.app

enum class Place(val label: String) {
    HOME("Zuhause"),
    TRANSIT("Öffis"),
    CAFE("Café"),
    CITY("Stadt"),
}

/** "Überall", wenn alle Orte gewählt sind, sonst die Orte als Liste. */
fun Set<Place>.label(): String = when {
    isEmpty() -> ""
    containsAll(Place.entries) -> "Überall"
    else -> Place.entries.filter { it in this }.joinToString(", ") { it.label }
}

fun minutesText(minutes: Int): String = if (minutes == 1) "1 Minute" else "$minutes Minuten"

data class Step(
    val title: String,
    val places: Set<Place>,
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
) {
    val currentStep: Step get() = steps.first()
    val totalSteps: Int get() = completedSteps + steps.size

    fun fits(place: Place, availableMinutes: Int): Boolean =
        currentStep.minutes <= availableMinutes && place in currentStep.places

    /** Die Aufgabe nach "Erledigt"/"Verwerfen": nächster Schritt, oder null, wenn keiner mehr übrig ist. */
    fun advanced(): Task? =
        if (steps.size > 1) copy(steps = steps.drop(1), completedSteps = completedSteps + 1) else null
}

fun eligibleTasks(tasks: List<Task>, place: Place, availableMinutes: Int): List<Task> =
    tasks.filter { it.fits(place, availableMinutes) }
