package de.produktivitaet.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

sealed interface Screen {
    data object Home : Screen
    /** [taskId] ist null, wenn keine Aufgabe gepasst hat. */
    data class Draw(val taskId: Long?) : Screen
    data object Add : Screen
    data object Editor : Screen
    data object Overview : Screen
}

enum class EditTarget { NEW, TASK, TEMPLATE }

class StepDraft(title: String = "", places: Set<Place> = emptySet(), minutes: String = "") {
    var title by mutableStateOf(title)
    var places by mutableStateOf(places)
    var minutes by mutableStateOf(minutes)

    val hasPlaces: Boolean get() = places.isNotEmpty()
    val hasMinutes: Boolean get() = (minutes.toIntOrNull() ?: 0) > 0
}

class EditorState(
    val target: EditTarget,
    val editingId: Long?,
    val completedSteps: Int,
    title: String,
    steps: List<StepDraft>,
) {
    var title by mutableStateOf(title)
    val steps = mutableStateListOf<StepDraft>().apply { addAll(steps) }
    var saveAsTemplate by mutableStateOf(false)

    val isValid: Boolean get() = missingHint() == null

    fun missingHint(): String? {
        val missing = buildList {
            if (title.isBlank()) add("Titel")
            if (steps.any { !it.hasPlaces }) add("Ort")
            if (steps.any { !it.hasMinutes }) add("Dauer")
        }
        return if (missing.isEmpty()) null else "Es fehlt noch: " + missing.joinToString(", ")
    }

    fun toSteps(): List<Step> = steps.map {
        // Bei nur einem Schritt gibt es kein eigenes Schritt-Feld; der Titel der Aufgabe reicht.
        Step(if (steps.size == 1) "" else it.title.trim(), it.places, it.minutes.toInt())
    }

    companion object {
        fun forNew(template: Task?) = EditorState(
            target = EditTarget.NEW,
            editingId = null,
            completedSteps = 0,
            title = template?.title ?: "",
            steps = template?.steps?.map(::draftOf) ?: listOf(StepDraft()),
        )

        fun forEdit(task: Task, target: EditTarget) = EditorState(
            target = target,
            editingId = task.id,
            completedSteps = task.completedSteps,
            title = task.title,
            steps = task.steps.map(::draftOf),
        )

        private fun draftOf(step: Step) = StepDraft(step.title, step.places, step.minutes.toString())
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = TaskStore(app)

    private val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    // Nur für die laufende Sitzung – beim nächsten Start der App ist alles wieder leer.
    var homePlace by mutableStateOf<Place?>(null)
    var homeMinutes by mutableStateOf("")
    var homeHint by mutableStateOf<String?>(null)

    var overviewTab by mutableIntStateOf(0)

    var editor by mutableStateOf<EditorState?>(null)
        private set

    fun open(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun backToHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun drawTask() {
        val place = homePlace
        val minutes = homeMinutes.toIntOrNull()
        if (place == null || minutes == null) {
            homeHint = when {
                place == null && minutes == null -> "Bitte gib an, wo du bist und wie viel Zeit du hast."
                place == null -> "Bitte gib an, wo du bist."
                else -> "Bitte gib an, wie viel Zeit du hast."
            }
            return
        }
        homeHint = null
        open(Screen.Draw(eligibleTasks(store.tasks, place, minutes).randomOrNull()?.id))
    }

    /** "Erledigt" und "Verwerfen": aktueller Schritt fliegt raus, der nächste rückt nach. */
    fun finishStep(taskId: Long) {
        store.advance(taskId)
        backToHome()
    }

    fun newTask(template: Task? = null) {
        editor = EditorState.forNew(template)
        open(Screen.Editor)
    }

    fun editTask(task: Task) {
        editor = EditorState.forEdit(task, EditTarget.TASK)
        open(Screen.Editor)
    }

    fun editTemplate(template: Task) {
        editor = EditorState.forEdit(template, EditTarget.TEMPLATE)
        open(Screen.Editor)
    }

    fun saveEditor() {
        val e = editor ?: return
        if (!e.isValid) return
        val title = e.title.trim()
        val steps = e.toSteps()
        when (e.target) {
            EditTarget.NEW -> {
                store.addTask(title, steps)
                if (e.saveAsTemplate) store.addTemplate(title, steps)
                backToHome()
            }
            EditTarget.TASK -> {
                store.updateTask(e.editingId!!, title, steps)
                back()
            }
            EditTarget.TEMPLATE -> {
                store.updateTemplate(e.editingId!!, title, steps)
                back()
            }
        }
    }
}
