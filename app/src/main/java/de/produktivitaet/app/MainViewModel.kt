package de.produktivitaet.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    /** [taskId] ist null, wenn keine Aufgabe gepasst hat. */
    data class Draw(val taskId: Long?) : Screen
    data object Add : Screen
    data object Editor : Screen
    data object Overview : Screen
    data object Fungarium : Screen
    data class Celebration(val reward: Reward) : Screen
    data object Settings : Screen
    data object Statistics : Screen
}

enum class EditTarget { NEW, TASK, TEMPLATE }

/** Dekorierbares Fenster eines Bildschirms (null = nicht dekorierbar). */
fun windowId(screen: Screen): String? = when (screen) {
    Screen.Home -> "home"
    Screen.Overview -> "overview"
    Screen.Add -> "add"
    Screen.Editor -> "editor"
    is Screen.Draw -> if (screen.taskId != null) "draw" else "relax"
    Screen.Fungarium, is Screen.Celebration, Screen.Settings, Screen.Statistics -> null
}

/** Ein Pilz, den man im Pilz-Modus auswählen kann: ein platzierter oder eine Vorlagen-Kopie auf einer Aufgabe. */
sealed interface DecorItemId {
    data class Placed(val mushroomId: Long) : DecorItemId
    data class Copy(val taskId: Long, val mushroomId: Long) : DecorItemId
}

class StepDraft(title: String = "", places: Places = Places(), minutes: String = "") {
    var title by mutableStateOf(title)
    var places by mutableStateOf(places)
    var minutes by mutableStateOf(minutes)

    val hasPlaces: Boolean get() = !places.isEmpty
    val hasMinutes: Boolean get() = (minutes.toIntOrNull() ?: 0) > 0
}

class EditorState(
    val target: EditTarget,
    val editingId: Long?,
    val completedSteps: Int,
    val templateId: Long?,
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
            templateId = template?.id,
            title = template?.title ?: "",
            steps = template?.steps?.map(::draftOf) ?: listOf(StepDraft()),
        )

        fun forEdit(task: Task, target: EditTarget) = EditorState(
            target = target,
            editingId = task.id,
            completedSteps = task.completedSteps,
            templateId = null,
            title = task.title,
            steps = task.steps.map(::draftOf),
        )

        private fun draftOf(step: Step) = StepDraft(step.title, step.places, step.minutes.toString())
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val catalog = MushroomCatalog(app)
    val store = TaskStore(app, catalog)

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

    /**
     * Aktuellen Schritt erledigen und die Feier zeigen. Aus dem Aufgabenfenster geht es danach
     * zur Startseite, aus der Übersicht zurück in die Übersicht.
     */
    fun completeStep(taskId: Long, fromOverview: Boolean) {
        val reward = store.completeStep(taskId)
        if (!fromOverview) backToHome()
        if (reward != null) open(Screen.Celebration(reward))
    }

    /** "Verwerfen": die ganze Aufgabe fliegt raus, auch bei mehreren Schritten. */
    fun discardTask(taskId: Long) {
        store.deleteTask(taskId)
        backToHome()
    }

    fun exportTo(uri: Uri): Boolean = try {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(store.exportJson().toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) {
        false
    }

    fun importFrom(uri: Uri): Boolean = try {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        store.importJson(text)
    } catch (e: Exception) {
        false
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
                store.addTask(title, steps, e.templateId)
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

    // ---- Pilz-Modus ----

    var decorMode by mutableStateOf(false)
        private set
    var selected by mutableStateOf<DecorItemId?>(null)
    /** Der Pilz, der gerade gezogen wird – er wird an seinem alten Platz ausgeblendet. */
    var dragging by mutableStateOf<DecorItemId?>(null)

    /** Gerade aus dem Fungarium gesetzter Pilz: bekommt seine Ebene, sobald das Fenster vermessen ist. */
    var autoLayerFor by mutableStateOf<Long?>(null)

    /** Zuletzt gemessene Fenstergrößen in dp, um neue Pilze mittig abzusetzen. */
    val windowSizesDp = HashMap<String, Offset>()

    val canHandleBack: Boolean get() = canGoBack || decorMode

    fun onBackPressed() {
        if (decorMode && screen != Screen.Fungarium) confirmDiscard = true else back()
    }

    /** Abfrage "Änderungen verwerfen?" (Zurück-Taste im Pilz-Modus). */
    var confirmDiscard by mutableStateOf(false)

    /** Pilz-Modus im aktuellen Fenster starten (langes Drücken auf den Pilz-Button). */
    fun startDecorMode() {
        if (decorMode || windowId(screen) == null) return
        store.beginDraft()
        decorMode = true
        selected = null
    }

    /** Stift im Fungarium: Pilz-Modus an, zurück ins Fenster. */
    fun enterDecorMode() {
        back()
        startDecorMode()
    }

    /** Haken: alle Platzierungen und Zuordnungen übernehmen, Modus beenden. */
    fun confirmDecor() {
        store.commitDraft()
        leaveDecorMode()
    }

    /** Ohne Haken raus: alles seit Beginn des Modus verwerfen. */
    fun discardDecor() {
        confirmDiscard = false
        store.revertDraft()
        leaveDecorMode()
    }

    private fun leaveDecorMode() {
        decorMode = false
        selected = null
        dragging = null
        autoLayerFor = null
    }

    /** Pilz im Fungarium angetippt: ein Exemplar herausnehmen und mittig ins vorherige Fenster setzen. */
    fun pickFromFungarium(species: String) {
        val mushroom = store.takeFromFungarium(species) ?: return
        back()
        val window = windowId(screen) ?: return
        val size = windowSizesDp[window] ?: Offset(360f, 640f)
        startDecorMode()
        store.place(Placement(mushroom.id, Anchor.Window(window), size.x / 2, size.y / 2))
        selected = DecorItemId.Placed(mushroom.id)
        autoLayerFor = mushroom.id
    }

    /** Einstellungen speichern; ist ein Ziel damit schon erreicht, gibt es gleich die Feier. */
    fun saveGoals(daily: Goal, weekly: Goal) {
        val reward = store.setGoals(daily, weekly)
        back()
        if (reward != null) open(Screen.Celebration(reward))
    }

    fun isBeing(id: DecorItemId): Boolean =
        id is DecorItemId.Placed && store.findMushroom(id.mushroomId)?.let { catalog.isBeing(it.species) } == true

    /** Pilz-Wesen im Pilz-Modus vergrößern ([factor] > 1) oder verkleinern. */
    fun resizeBeing(id: DecorItemId, factor: Float) {
        if (id !is DecorItemId.Placed || !isBeing(id)) return
        val p = store.placementOf(id.mushroomId) ?: return
        store.place(p.copy(scale = (p.scale * factor).coerceIn(0.3f, 3f)))
    }

    fun mirror(id: DecorItemId) {
        if (id !is DecorItemId.Placed) return
        val p = store.placementOf(id.mushroomId) ?: return
        store.place(p.copy(mirrored = !p.mirrored))
    }

    fun deleteDecor(id: DecorItemId) {
        when (id) {
            is DecorItemId.Placed -> store.unplace(id.mushroomId)
            is DecorItemId.Copy -> store.hideCopy(id.taskId, id.mushroomId)
        }
        selected = null
    }

    /** Ebenen-Button: zwischen Fenster (dahinter) und dem berührten Button/der Karte (darauf) wechseln. */
    fun toggleLayer(item: VisibleDecor, win: DecorWindowState, density: Float) {
        val p = item.placement ?: return
        val anchor = p.anchor
        val center = item.rect.center
        val newPlacement = if (anchor is Anchor.Window) {
            val (key, rect) = win.bestArea(item.rect) ?: return
            p.copy(anchor = anchorForArea(win.window, key), x = (center.x - rect.left) / density, y = (center.y - rect.top) / density)
        } else {
            p.copy(anchor = Anchor.Window(win.window), x = center.x / density, y = center.y / density)
        }
        store.place(newPlacement)
    }

    /** Nach dem Ziehen: liegt der Pilz zum größten Teil auf einem Button/einer Karte, kommt er darauf, sonst dahinter. */
    fun commitMove(item: VisibleDecor, newCenter: Offset, win: DecorWindowState, density: Float) {
        dragging = null
        val p = item.placement ?: return
        val moved = item.rect.translate(newCenter - item.rect.center)
        val best = win.bestArea(moved)
        val newPlacement = if (best != null && overlapFraction(moved.toArray(), best.second.toArray()) > 0.5f) {
            val (key, rect) = best
            p.copy(anchor = anchorForArea(win.window, key), x = (newCenter.x - rect.left) / density, y = (newCenter.y - rect.top) / density)
        } else {
            p.copy(anchor = Anchor.Window(win.window), x = newCenter.x / density, y = newCenter.y / density)
        }
        store.place(newPlacement)
    }

    fun resetAllDecor() {
        store.resetPlacements()
        selected = null
    }

    // ---- Pilzbilder in Anzeigegröße ----

    private val bitmaps = mutableStateMapOf<String, ImageBitmap>()
    private val loading = HashSet<String>()

    private val shadowImages = HashMap<String, Pair<ImageBitmap, Float>?>()

    /** Weicher Schatten eines Sprites (Bild + Randanteil); einmal berechnet, danach aus dem Speicher. */
    fun shadowFor(species: String): Pair<ImageBitmap, Float>? = shadowImages.getOrPut(species) {
        catalog.shadow(species)?.let { it.bitmap.asImageBitmap() to it.pad }
    }

    /** Liefert das Bild in passender Auflösung; lädt es bei Bedarf im Hintergrund nach. */
    fun mushroomBitmap(species: String, heightPx: Float): ImageBitmap? {
        var bucket = 128
        while (bucket < heightPx && bucket < 2048) bucket *= 2
        val key = "$species@$bucket"
        bitmaps[key]?.let { return it }
        if (loading.add(key)) {
            viewModelScope.launch {
                val image = withContext(Dispatchers.IO) { catalog.load(species, bucket)?.asImageBitmap() }
                if (image != null) bitmaps[key] = image
            }
        }
        return bitmaps.entries.firstOrNull { it.key.startsWith("$species@") }?.value
    }
}
