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

/** Hält Aufgaben, Vorlagen, Punkte und Pilze und speichert alles als JSON-Datei auf dem Gerät. */
class TaskStore(context: Context, private val catalog: MushroomCatalog) {
    private val file = AtomicFile(File(context.filesDir, "daten.json"))

    val tasks = mutableStateListOf<Task>()
    val templates = mutableStateListOf<Task>()
    val mushrooms = mutableStateListOf<Mushroom>()
    var totalPoints by mutableIntStateOf(0)
        private set
    var nextMushroom by mutableStateOf<MushroomRoll?>(null)
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

    fun addTask(title: String, steps: List<Step>) {
        tasks.add(Task(nextId(), title, steps))
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

    fun deleteTask(id: Long) {
        tasks.removeAll { it.id == id }
        save()
    }

    fun deleteTemplate(id: Long) {
        templates.removeAll { it.id == id }
        save()
    }

    /**
     * Aktuellen Schritt erledigen: Punkte gutschreiben, ggf. Pilze vergeben,
     * nächster Schritt rückt nach (oder die Aufgabe verschwindet).
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
        if (next != null) tasks[i] = next else tasks.removeAt(i)
        save()
        return Reward(points, start, end, earned, nextMushroom)
    }

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
        return changed
    }

    private fun save() {
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
    )

    private fun replaceWith(data: Data) {
        tasks.clear()
        tasks.addAll(data.tasks)
        templates.clear()
        templates.addAll(data.templates)
        mushrooms.clear()
        mushrooms.addAll(data.mushrooms)
        totalPoints = data.points
        nextMushroom = data.next
    }

    private fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        .put("templates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
        .put("points", totalPoints)
        .put("mushrooms", JSONArray().apply { mushrooms.forEach { put(it.toJson()) } })
        .put("nextMushroom", nextMushroom?.let { JSONObject().put("species", it.species).put("size", it.size) })

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

private fun Step.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("minutes", minutes)
    .put("everywhere", places.everywhere)
    .put("places", JSONArray().apply { places.set.forEach { put(it.name) } })

private fun Mushroom.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("species", species)
    .put("size", size)

private fun JSONArray?.toTasks(): List<Task> = mapObjects { it.toTask() }.filter { it.steps.isNotEmpty() }

private fun JSONObject.toTask(): Task = Task(
    id = getLong("id"),
    title = getString("title"),
    steps = optJSONArray("steps").mapObjects { it.toStep() },
    completedSteps = optInt("completedSteps", 0),
)

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
