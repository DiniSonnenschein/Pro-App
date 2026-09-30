package de.produktivitaet.app

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Hält Aufgaben und Vorlagen und speichert sie als JSON-Datei auf dem Gerät. */
class TaskStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "daten.json"))

    val tasks = mutableStateListOf<Task>()
    val templates = mutableStateListOf<Task>()

    init {
        load()
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

    /** Aktuellen Schritt abschließen: nächster Schritt rückt nach, oder die Aufgabe verschwindet. */
    fun advance(id: Long) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        val next = tasks[i].advanced()
        if (next != null) tasks[i] = next else tasks.removeAt(i)
        save()
    }

    private fun nextId(): Long =
        maxOf(tasks.maxOfOrNull { it.id } ?: 0L, templates.maxOfOrNull { it.id } ?: 0L) + 1

    private fun load() {
        if (!file.baseFile.exists()) return
        try {
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            tasks.addAll(root.optJSONArray("tasks").toTasks())
            templates.addAll(root.optJSONArray("templates").toTasks())
        } catch (e: Exception) {
            // Beschädigte Datei: mit leeren Listen weitermachen statt abzustürzen.
        }
    }

    private fun save() {
        val root = JSONObject()
            .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
            .put("templates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}

private fun Task.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("completedSteps", completedSteps)
    .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })

private fun Step.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("minutes", minutes)
    .put("places", JSONArray().apply { places.forEach { put(it.name) } })

private fun JSONArray?.toTasks(): List<Task> {
    if (this == null) return emptyList()
    return (0 until length()).map { getJSONObject(it).toTask() }.filter { it.steps.isNotEmpty() }
}

private fun JSONObject.toTask(): Task {
    val steps = getJSONArray("steps")
    return Task(
        id = getLong("id"),
        title = getString("title"),
        steps = (0 until steps.length()).map { steps.getJSONObject(it).toStep() },
        completedSteps = optInt("completedSteps", 0),
    )
}

private fun JSONObject.toStep(): Step {
    val places = getJSONArray("places")
    return Step(
        title = optString("title", ""),
        places = (0 until places.length())
            .mapNotNull { i -> Place.entries.firstOrNull { it.name == places.getString(i) } }
            .toSet(),
        minutes = getInt("minutes"),
    )
}
