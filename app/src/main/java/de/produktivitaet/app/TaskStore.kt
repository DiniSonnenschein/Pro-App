package de.produktivitaet.app

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Hält Aufgaben, Vorlagen, Punkte, Pilze und deren Platzierungen und speichert alles als JSON-Datei. */
class TaskStore(context: Context, private val catalog: MushroomCatalog) {
    private val file = AtomicFile(File(context.filesDir, "daten.json"))

    val tasks = mutableStateListOf<Task>()
    val templates = mutableStateListOf<Task>()
    val mushrooms = mutableStateListOf<Mushroom>()
    val placements = mutableStateListOf<Placement>()
    var totalPoints by mutableIntStateOf(0)
        private set
    var nextMushroom by mutableStateOf<MushroomRoll?>(null)
        private set

    /** Fungarium-Schalter: beim Herausnehmen zuerst die großen (true) oder die kleinen Exemplare. */
    var bigFirst by mutableStateOf(true)
        private set

    init {
        if (file.baseFile.exists()) {
            try {
                replaceWith(parse(JSONObject(String(file.readFully(), Charsets.UTF_8))))
            } catch (e: Exception) {
                // Beschädigte Datei: mit leeren Listen weitermachen statt abzustürzen.
            }
        }
        if (repairMushrooms()) save()
    }

    fun findTask(id: Long): Task? = tasks.firstOrNull { it.id == id }
    fun findTemplate(id: Long): Task? = templates.firstOrNull { it.id == id }
    fun findMushroom(id: Long): Mushroom? = mushrooms.firstOrNull { it.id == id }

    fun addTask(title: String, steps: List<Step>, templateId: Long? = null) {
        tasks.add(Task(nextId(), title, steps, templateId = templateId))
        save()
    }

    fun addTemplate(title: String, steps: List<Step>) {
        templates.add(Task(nextId(), title, steps))
        save()
    }

    fun updateTask(id: Long, title: String, steps: List<Step>) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        tasks[i] = tasks[i].copy(title = title, steps = steps)
        save()
    }

    fun updateTemplate(id: Long, title: String, steps: List<Step>) {
        val i = templates.indexOfFirst { it.id == id }
        if (i < 0) return
        templates[i] = templates[i].copy(title = title, steps = steps)
        save()
    }

    /** Aufgabe löschen; ihre Pilze gehen zurück ins Fungarium. */
    fun deleteTask(id: Long) {
        tasks.removeAll { it.id == id }
        placements.removeAll { it.anchor == Anchor.OnTask(id) }
        save()
    }

    /** Vorlage löschen; ihre Pilze gehen zurück ins Fungarium, die Kopien verschwinden mit. */
    fun deleteTemplate(id: Long) {
        templates.removeAll { it.id == id }
        placements.removeAll { it.anchor == Anchor.OnTemplate(id) }
        save()
    }

    /**
     * Aktuellen Schritt erledigen: Punkte gutschreiben, ggf. Pilze vergeben,
     * nächster Schritt rückt nach (oder die Aufgabe verschwindet samt ihren Pilzen).
     */
    fun completeStep(id: Long): Reward? {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return null
        val task = tasks[i]

        val points = Rewards.pointsFor(task.currentStep.minutes)
        val start = totalPoints
        val end = start + points
        val earned = mutableListOf<Mushroom>()
        repeat(Rewards.mushroomsFor(end) - Rewards.mushroomsFor(start)) {
            val roll = nextMushroom ?: catalog.roll() ?: return@repeat
            val mushroom = Mushroom(nextMushroomId(), roll.species, roll.size)
            mushrooms.add(mushroom)
            earned.add(mushroom)
            nextMushroom = catalog.roll()
        }
        if (nextMushroom == null) nextMushroom = catalog.roll()
        totalPoints = end

        val next = task.advanced()
        if (next != null) {
            tasks[i] = next
        } else {
            tasks.removeAt(i)
            placements.removeAll { it.anchor == Anchor.OnTask(id) }
        }
        save()
        return Reward(points, start, end, earned, nextMushroom)
    }

    // ---- Fungarium und Platzierungen ----

    fun isPlaced(mushroomId: Long): Boolean = placements.any { it.mushroomId == mushroomId }

    /** Wie viele Exemplare dieser Art gerade im Fungarium liegen (erspielt minus platziert). */
    fun availableCount(species: String): Int {
        val placed = placements.mapTo(HashSet()) { it.mushroomId }
        return mushrooms.count { it.species == species && it.id !in placed }
    }

    /** Nimmt ein Exemplar aus dem Fungarium – je nach Schalter das größte oder das kleinste. */
    fun takeFromFungarium(species: String): Mushroom? {
        val placed = placements.mapTo(HashSet()) { it.mushroomId }
        val free = mushrooms.filter { it.species == species && it.id !in placed }
        return if (bigFirst) free.maxByOrNull { it.size } else free.minByOrNull { it.size }
    }

    fun toggleBigFirst() {
        bigFirst = !bigFirst
        save()
    }

    fun placementOf(mushroomId: Long): Placement? = placements.firstOrNull { it.mushroomId == mushroomId }

    /** Neu platzieren oder eine bestehende Platzierung ersetzen; die bewegte kommt nach oben. */
    fun place(placement: Placement) {
        placements.removeAll { it.mushroomId == placement.mushroomId }
        placements.add(placement)
        save()
    }

    /** Zurück ins Fungarium. */
    fun unplace(mushroomId: Long) {
        placements.removeAll { it.mushroomId == mushroomId }
        save()
    }

    /** Kopie eines Vorlagen-Pilzes nur auf dieser einen Aufgabe entfernen. */
    fun hideCopy(taskId: Long, mushroomId: Long) {
        val i = tasks.indexOfFirst { it.id == taskId }
        if (i < 0) return
        tasks[i] = tasks[i].copy(hiddenCopies = tasks[i].hiddenCopies + mushroomId)
        save()
    }

    /** Alle Pilze aus allen Fenstern, Aufgaben und Vorlagen zurück ins Fungarium. */
    fun resetPlacements() {
        placements.clear()
        for (i in tasks.indices) {
            if (tasks[i].hiddenCopies.isNotEmpty()) tasks[i] = tasks[i].copy(hiddenCopies = emptySet())
        }
        save()
    }

    // ---- Entwurf im Pilz-Modus ----

    /** Stand der Platzierungen (und gelöschten Kopien) beim Start des Pilz-Modus; null = kein Entwurf. */
    private var draft: Pair<List<Placement>, Map<Long, Set<Long>>>? = null

    /** Ab jetzt ist alles Entwurf: wird angezeigt, aber erst mit [commitDraft] gespeichert. */
    fun beginDraft() {
        draft = placements.toList() to tasks.associate { it.id to it.hiddenCopies }
    }

    /** Haken: Entwurf übernehmen. */
    fun commitDraft() {
        draft = null
        save()
    }

    /** Verwerfen: alles zurück auf den Stand vor dem Pilz-Modus. */
    fun revertDraft() {
        val (oldPlacements, oldHidden) = draft ?: return
        placements.clear()
        placements.addAll(oldPlacements)
        for (i in tasks.indices) {
            val hidden = oldHidden[tasks[i].id] ?: continue
            if (tasks[i].hiddenCopies != hidden) tasks[i] = tasks[i].copy(hiddenCopies = hidden)
        }
        draft = null
        save()
    }

    // ---- Sicherung ----

    /** Komplette Sicherung als Text (für "Daten sichern"). */
    fun exportJson(): String = toJson().toString(2)

    /** Ersetzt alle Daten durch die Sicherung. Bei ungültiger Datei bleibt alles, wie es ist. */
    fun importJson(text: String): Boolean {
        val data = try {
            parse(JSONObject(text))
        } catch (e: Exception) {
            return false
        }
        replaceWith(data)
        repairMushrooms()
        save()
        return true
    }

    private fun nextId(): Long =
        maxOf(tasks.maxOfOrNull { it.id } ?: 0L, templates.maxOfOrNull { it.id } ?: 0L) + 1

    private fun nextMushroomId(): Long = (mushrooms.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Wenn Pilzbilder ausgetauscht wurden (z. B. Platzhalter → echte Bilder), zeigen gespeicherte
     * Pilze ins Leere. Diese bekommen eine zufällige vorhandene Art, die Größe bleibt.
     */
    private fun repairMushrooms(): Boolean {
        if (catalog.species.isEmpty()) return false
        var changed = false
        for (i in mushrooms.indices) {
            val m = mushrooms[i]
            if (catalog.find(m.species) == null) {
                mushrooms[i] = m.copy(species = catalog.species.random().id)
                changed = true
            }
        }
        val next = nextMushroom
        if (next == null || catalog.find(next.species) == null) {
            nextMushroom = catalog.roll()
            changed = true
        }
        val ids = mushrooms.mapTo(HashSet()) { it.id }
        if (placements.removeAll { it.mushroomId !in ids }) changed = true
        return changed
    }

    private fun save() {
        if (draft != null) return
        val out = file.startWrite()
        try {
            out.write(toJson().toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private class Data(
        val tasks: List<Task>,
        val templates: List<Task>,
        val points: Int,
        val mushrooms: List<Mushroom>,
        val next: MushroomRoll?,
        val placements: List<Placement>,
        val bigFirst: Boolean,
    )

    private fun replaceWith(data: Data) {
        tasks.clear()
        tasks.addAll(data.tasks)
        templates.clear()
        templates.addAll(data.templates)
        mushrooms.clear()
        mushrooms.addAll(data.mushrooms)
        placements.clear()
        placements.addAll(data.placements)
        totalPoints = data.points
        nextMushroom = data.next
        bigFirst = data.bigFirst
    }

    private fun toJson(): JSONObject = JSONObject()
        .put("version", 3)
        .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        .put("templates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
        .put("points", totalPoints)
        .put("mushrooms", JSONArray().apply { mushrooms.forEach { put(it.toJson()) } })
        .put("nextMushroom", nextMushroom?.let { JSONObject().put("species", it.species).put("size", it.size) })
        .put("placements", JSONArray().apply { placements.forEach { put(it.toJson()) } })
        .put("bigFirst", bigFirst)

    private fun parse(root: JSONObject): Data {
        val next = root.optJSONObject("nextMushroom")
        return Data(
            tasks = root.optJSONArray("tasks").toTasks(),
            templates = root.optJSONArray("templates").toTasks(),
            points = root.optInt("points", 0),
            mushrooms = root.optJSONArray("mushrooms").mapObjects {
                Mushroom(it.getLong("id"), it.getString("species"), it.getInt("size").coerceIn(1, 5))
            },
            next = next?.let { MushroomRoll(it.getString("species"), it.getInt("size").coerceIn(1, 5)) },
            placements = root.optJSONArray("placements").mapObjects { it.toPlacement() }.filterNotNull(),
            bigFirst = root.optBoolean("bigFirst", true),
        )
    }
}

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { transform(getJSONObject(it)) }
}

private fun Task.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("completedSteps", completedSteps)
    .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
    .put("templateId", templateId)
    .put("hiddenCopies", JSONArray().apply { hiddenCopies.forEach { put(it) } })

private fun Step.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("minutes", minutes)
    .put("everywhere", places.everywhere)
    .put("places", JSONArray().apply { places.set.forEach { put(it.name) } })

private fun Mushroom.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("species", species)
    .put("size", size)

private fun Placement.toJson(): JSONObject {
    val a = when (val anchor = anchor) {
        is Anchor.Window -> JSONObject().put("type", "window").put("window", anchor.window)
        is Anchor.Area -> JSONObject().put("type", "area").put("window", anchor.window).put("key", anchor.key)
        is Anchor.OnTask -> JSONObject().put("type", "task").put("id", anchor.taskId)
        is Anchor.OnTemplate -> JSONObject().put("type", "template").put("id", anchor.templateId)
    }
    return JSONObject()
        .put("mushroomId", mushroomId)
        .put("anchor", a)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("mirrored", mirrored)
}

private fun JSONObject.toPlacement(): Placement? {
    val a = getJSONObject("anchor")
    val anchor = when (a.getString("type")) {
        "window" -> Anchor.Window(a.getString("window"))
        "area" -> Anchor.Area(a.getString("window"), a.getString("key"))
        "task" -> Anchor.OnTask(a.getLong("id"))
        "template" -> Anchor.OnTemplate(a.getLong("id"))
        else -> return null
    }
    return Placement(
        mushroomId = getLong("mushroomId"),
        anchor = anchor,
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        mirrored = optBoolean("mirrored", false),
    )
}

private fun JSONArray?.toTasks(): List<Task> = mapObjects { it.toTask() }.filter { it.steps.isNotEmpty() }

private fun JSONObject.toTask(): Task {
    val hidden = optJSONArray("hiddenCopies")
    return Task(
        id = getLong("id"),
        title = getString("title"),
        steps = optJSONArray("steps").mapObjects { it.toStep() },
        completedSteps = optInt("completedSteps", 0),
        templateId = if (has("templateId") && !isNull("templateId")) getLong("templateId") else null,
        hiddenCopies = if (hidden == null) emptySet() else (0 until hidden.length()).mapTo(HashSet()) { hidden.getLong(it) },
    )
}

/** Die vier Orte der ersten Version – wer die alle hatte, hatte "Überall" gewählt. */
private val ORIGINAL_PLACES = setOf(Place.HOME, Place.TRANSIT, Place.CAFE, Place.CITY)

private fun JSONObject.toStep(): Step {
    val names = getJSONArray("places")
    val set = (0 until names.length())
        .mapNotNull { i -> Place.entries.firstOrNull { it.name == names.getString(i) } }
        .toSet()
    val everywhere = if (has("everywhere")) getBoolean("everywhere") else set.containsAll(ORIGINAL_PLACES)
    return Step(
        title = optString("title", ""),
        places = if (everywhere) Places(everywhere = true) else Places(set),
        minutes = getInt("minutes"),
    )
}
