package de.produktivitaet.app

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Hält Aufgaben, Vorlagen, Ziele, Sprites und deren Platzierungen und speichert alles als JSON-Datei. */
class TaskStore(context: Context, private val catalog: MushroomCatalog) {
    private val file = AtomicFile(File(context.filesDir, "daten.json"))

    val tasks = mutableStateListOf<Task>()
    val templates = mutableStateListOf<Task>()
    val mushrooms = mutableStateListOf<Mushroom>()
    val placements = mutableStateListOf<Placement>()
    /** Jeder erledigte Schritt mit Tag und Minuten – für Ziele und Statistik. */
    val completions = mutableStateListOf<Completion>()
    var dailyGoal by mutableStateOf(Rewards.DEFAULT_DAILY)
        private set
    var weeklyGoal by mutableStateOf(Rewards.DEFAULT_WEEKLY)
        private set
    /** Tag bzw. Wochenbeginn (Epoch-Tag), für den es die Belohnung schon gab. */
    private var rewardedDay: Long? = null
    private var rewardedWeek: Long? = null
    var nextMushroom by mutableStateOf<MushroomRoll?>(null)
        private set
    var nextBeing by mutableStateOf<String?>(null)
        private set

    /** Auswählbare Orte in Anzeige-Reihenfolge (alphabetisch). "Überall" ist kein Ort, sondern eine Markierung. */
    val places = mutableStateListOf<Place>().apply { addAll(Place.sorted(Place.DEFAULTS)) }

    /** Pools (Ordner) der Übersicht. */
    val pools = mutableStateListOf<Pool>()

    /** Freigeschaltete Palettenfarben (Indizes). */
    val unlockedColors = mutableStateListOf<Int>().apply { addAll(Palette.STARTERS.sorted()) }

    /** Eingefärbte Buttons, Karten und Fensterhintergründe. */
    val tints = mutableStateListOf<Tint>()

    /** Stand der Platzierungen (und gelöschten Kopien) beim Start des Pilz-Modus; null = kein Entwurf. */
    private var draft: Pair<List<Placement>, Map<Long, Set<Long>>>? = null
    private var draftTints: List<Tint> = emptyList()
    private var draftPools: List<Pool> = emptyList()

    // Achtung: Alle Eigenschaften müssen VOR diesem init-Block stehen, sonst sind sie beim Laden noch null.
    init {
        if (file.baseFile.exists()) {
            try {
                replaceWith(parse(JSONObject(String(file.readFully(), Charsets.UTF_8))))
            } catch (e: Exception) {
                // Nicht lesbar: Datei vorher beiseitelegen, damit beim nächsten Speichern nichts verloren geht,
                // dann mit leeren Listen weitermachen statt abzustürzen.
                try {
                    file.baseFile.copyTo(File(file.baseFile.parentFile, "daten-nicht-lesbar-${System.currentTimeMillis()}.json"))
                } catch (ignored: Exception) {
                }
            }
        }
        val repaired = repairMushrooms()
        syncTemplatePools()
        syncTemplateTasks()
        if (repaired) save()
        runAutoAdd()
        save()
    }

    fun findTask(id: Long): Task? = tasks.firstOrNull { it.id == id }
    fun findTemplate(id: Long): Task? = templates.firstOrNull { it.id == id }
    fun findMushroom(id: Long): Mushroom? = mushrooms.firstOrNull { it.id == id }

    /** Neue Aufgabe – landet ganz oben in ihrer Liste (bzw. ihrem Pool). */
    fun addTask(title: String, steps: List<Step>, templateId: Long? = null, poolId: Long? = null) {
        tasks.add(Task(nextId(), title, steps, templateId = templateId, poolId = poolId, sortKey = topKey(false, poolId)))
        syncTemplateTasks()
        save()
    }

    /** Neue Vorlage – landet ganz oben in ihrer Liste (bzw. ihrem Pool). */
    fun addTemplate(title: String, steps: List<Step>, poolId: Long? = null, auto: AutoAdd? = null) {
        templates.add(Task(nextId(), title, steps, poolId = poolId, sortKey = topKey(true, poolId), auto = auto))
        save()
    }

    // ---- Übersicht: Reihenfolge und Pools ----

    fun findPool(id: Long): Pool? = pools.firstOrNull { it.id == id }

    private fun cards(forTemplates: Boolean) = if (forTemplates) templates else tasks

    /** Karten und Pools einer Liste in Anzeige-Reihenfolge. Pools gibt es nur in der Hauptliste. */
    fun entries(forTemplates: Boolean, poolId: Long?): List<ListEntry> {
        val cards = cards(forTemplates).filter { it.poolId == poolId }.map { ListEntry.Card(it) }
        val folders = if (poolId == null) pools.filter { it.forTemplates == forTemplates }.map { ListEntry.Folder(it) } else emptyList()
        return (cards + folders).sortedBy { it.sortKey }
    }

    fun poolSize(pool: Pool): Int = cards(pool.forTemplates).count { it.poolId == pool.id }

    /** Sortierschlüssel für "ganz oben" in einer Liste. */
    private fun topKey(forTemplates: Boolean, poolId: Long?): Long =
        (entries(forTemplates, poolId).minOfOrNull { it.sortKey } ?: 0L) - 1

    /** Aufgaben-Pool, der den Vorlagen-Pool [templatePoolId] spiegelt. */
    fun mirrorOf(templatePoolId: Long): Pool? = pools.firstOrNull { it.sourcePoolId == templatePoolId }

    /** Aufgabe aus einer noch vorhandenen Vorlage: bleibt im Pool ihrer Vorlage und lässt sich nicht umsortieren. */
    fun isPinned(task: Task): Boolean = task.templateId?.let { findTemplate(it) } != null

    /** Pool, in den Aufgaben aus [template] gehören (Spiegel ihres Vorlagen-Pools) oder null. */
    fun taskPoolFor(template: Task): Long? = template.poolId?.let { mirrorOf(it)?.id }

    fun addPool(name: String, forTemplates: Boolean) {
        val id = (pools.maxOfOrNull { it.id } ?: 0L) + 1
        pools.add(Pool(id, name, forTemplates, topKey(forTemplates, null)))
        syncTemplatePools()
        save()
    }

    fun renamePool(id: Long, name: String) {
        val i = pools.indexOfFirst { it.id == id }
        if (i < 0) return
        pools[i] = pools[i].copy(name = name)
        syncTemplatePools()
        save()
    }

    /** Pool samt Inhalt löschen; alle zugehörigen Pilze gehen zurück ins Fungarium. */
    fun deletePool(id: Long) {
        val pool = findPool(id) ?: return
        val contained = cards(pool.forTemplates).filter { it.poolId == id }.map { it.id }.toSet()
        cards(pool.forTemplates).removeAll { it.id in contained }
        removeDecorOf(pool, contained)
        pools.removeAll { it.id == id }
        if (pool.forTemplates) {
            // Spiegel-Pool: Aufgaben aus den gelöschten Vorlagen gehen mit, eigene Aufgaben zurück in die Liste.
            mirrorOf(id)?.let { mirror ->
                val inMirror = tasks.filter { it.poolId == mirror.id }
                val fromTemplates = inMirror.filter { it.templateId in contained }.map { it.id }.toSet()
                tasks.removeAll { it.id in fromTemplates }
                removeDecorOf(mirror, fromTemplates)
                pools.removeAll { it.id == mirror.id }
                for (t in inMirror.filter { it.id !in fromTemplates }) {
                    val i = tasks.indexOfFirst { it.id == t.id }
                    if (i >= 0) tasks[i] = tasks[i].copy(poolId = null, sortKey = topKey(false, null))
                }
            }
        }
        syncTemplateTasks()
        save()
    }

    /** Pilze und Farben eines Pools (Karte, Fenster) und seiner gelöschten Karten [cardIds] entfernen. */
    private fun removeDecorOf(pool: Pool, cardIds: Set<Long>) {
        val window = poolWindow(pool.id)
        fun belongs(a: Anchor) = when (a) {
            is Anchor.OnTask -> !pool.forTemplates && a.taskId in cardIds
            is Anchor.OnTemplate -> pool.forTemplates && a.templateId in cardIds
            is Anchor.OnPool -> a.poolId == pool.id
            is Anchor.Window -> a.window == window
            is Anchor.Area -> a.window == window
        }
        tints.removeAll { belongs(it.anchor) }
        placements.removeAll { belongs(it.anchor) }
    }

    /** Jeder Vorlagen-Pool hat einen gleichnamigen Spiegel-Pool bei den Aufgaben; verwaiste Spiegel verschwinden. */
    private fun syncTemplatePools() {
        for (source in pools.filter { it.forTemplates }) {
            val i = pools.indexOfFirst { it.sourcePoolId == source.id }
            if (i < 0) {
                val id = (pools.maxOfOrNull { it.id } ?: 0L) + 1
                pools.add(Pool(id, source.name, forTemplates = false, sortKey = topKey(false, null), sourcePoolId = source.id))
            } else if (pools[i].name != source.name) {
                pools[i] = pools[i].copy(name = source.name)
            }
        }
        val sources = pools.filter { it.forTemplates }.mapTo(HashSet()) { it.id }
        for (orphan in pools.filter { it.sourcePoolId != null && it.sourcePoolId !in sources }) {
            for (i in tasks.indices) {
                if (tasks[i].poolId == orphan.id) tasks[i] = tasks[i].copy(poolId = null)
            }
            removeDecorOf(orphan, emptySet())
            pools.removeAll { it.id == orphan.id }
        }
    }

    /** Aufgaben aus Vorlagen liegen immer im (Spiegel-)Pool ihrer Vorlage bzw. in der normalen Liste. */
    private fun syncTemplateTasks() {
        for (i in tasks.indices) {
            val t = tasks[i]
            val template = t.templateId?.let { findTemplate(it) } ?: continue
            val target = taskPoolFor(template)
            if (t.poolId != target) tasks[i] = t.copy(poolId = target, sortKey = topKey(false, target))
        }
    }

    /** Kopie der Vorlagen-Pool-Dekoration nur auf diesem Spiegel-Pool entfernen. */
    fun hidePoolCopy(poolId: Long, mushroomId: Long) {
        val i = pools.indexOfFirst { it.id == poolId }
        if (i < 0) return
        pools[i] = pools[i].copy(hiddenCopies = pools[i].hiddenCopies + mushroomId)
        save()
    }

    /** Neue Reihenfolge einer Liste nach dem Verschieben. */
    fun reorder(forTemplates: Boolean, ordered: List<ListEntry>) {
        ordered.forEachIndexed { index, entry ->
            val key = index.toLong()
            when (entry) {
                is ListEntry.Folder -> {
                    val i = pools.indexOfFirst { it.id == entry.pool.id }
                    if (i >= 0 && pools[i].sortKey != key) pools[i] = pools[i].copy(sortKey = key)
                }
                is ListEntry.Card -> {
                    val list = cards(forTemplates)
                    val i = list.indexOfFirst { it.id == entry.task.id }
                    if (i >= 0 && list[i].sortKey != key) list[i] = list[i].copy(sortKey = key)
                }
            }
        }
        save()
    }

    /** Karte in einen Pool legen ([poolId] = null: zurück in die normale Liste) – jeweils ganz oben. */
    fun moveToPool(forTemplates: Boolean, cardId: Long, poolId: Long?) {
        val list = cards(forTemplates)
        val i = list.indexOfFirst { it.id == cardId }
        if (i < 0) return
        list[i] = list[i].copy(poolId = poolId, sortKey = topKey(forTemplates, poolId))
        // Wandert eine Vorlage, folgen ihr alle daraus entstandenen Aufgaben.
        if (forTemplates) syncTemplateTasks()
        save()
    }

    // ---- Automatisch hinzufügen ----

    /**
     * Fällige Vorlagen als Aufgabe hinzufügen (beim Start und beim Zurückkehren in die App).
     * Verpasste Termine ergeben höchstens eine Aufgabe. Ist aus der Vorlage noch eine unveränderte,
     * unbegonnene Aufgabe offen, setzt der Termin einmal aus.
     */
    fun runAutoAdd(today: Long = LocalDate.now().toEpochDay()) {
        var changed = false
        for (i in templates.indices) {
            val template = templates[i]
            val auto = template.auto ?: continue
            if (auto.nextDue > today) continue
            val alreadyOpen = tasks.any {
                it.templateId == template.id && it.completedSteps == 0 && it.title == template.title && it.steps == template.steps
            }
            if (!alreadyOpen) {
                val target = taskPoolFor(template)
                tasks.add(
                    Task(nextId(), template.title, template.steps, templateId = template.id, poolId = target, sortKey = topKey(false, target)),
                )
            }
            templates[i] = template.copy(auto = auto.advancedAfter(today))
            changed = true
        }
        if (changed) save()
    }

    fun updateTask(id: Long, title: String, steps: List<Step>) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        tasks[i] = tasks[i].copy(title = title, steps = steps)
        save()
    }

    fun updateTemplate(id: Long, title: String, steps: List<Step>, auto: AutoAdd?) {
        val i = templates.indexOfFirst { it.id == id }
        if (i < 0) return
        templates[i] = templates[i].copy(title = title, steps = steps, auto = auto)
        save()
    }

    /** Aufgabe löschen; ihre Pilze gehen zurück ins Fungarium. */
    fun deleteTask(id: Long) {
        tasks.removeAll { it.id == id }
        placements.removeAll { it.anchor == Anchor.OnTask(id) }
        tints.removeAll { it.anchor == Anchor.OnTask(id) }
        save()
    }

    /** Vorlage löschen; ihre Pilze gehen zurück ins Fungarium, die Kopien verschwinden mit. */
    fun deleteTemplate(id: Long) {
        templates.removeAll { it.id == id }
        placements.removeAll { it.anchor == Anchor.OnTemplate(id) }
        tints.removeAll { it.anchor == Anchor.OnTemplate(id) }
        save()
    }

    /**
     * Aktuellen Schritt erledigen: zählt für Tages- und Wochenziel, vergibt beim Erreichen den Pilz
     * des Tages bzw. das Pilz-Wesen der Woche. Der nächste Schritt rückt nach (oder die Aufgabe
     * verschwindet samt ihren Pilzen).
     */
    fun completeStep(id: Long): Reward? {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return null
        val task = tasks[i]
        val today = LocalDate.now().toEpochDay()
        val (dayBefore, weekBefore) = currentProgress(today)
        val doneToday = completions.count { it.epochDay == today }
        completions.add(Completion(today, task.currentStep.minutes))
        val color = rollColor(task.currentStep.minutes, doneToday)

        val next = task.advanced()
        if (next != null) {
            tasks[i] = next
        } else {
            tasks.removeAt(i)
            placements.removeAll { it.anchor == Anchor.OnTask(id) }
            tints.removeAll { it.anchor == Anchor.OnTask(id) }
        }
        val reward = evaluate(today, dayBefore, weekBefore).copy(color = color)
        save()
        return reward
    }

    /** Ziele ändern. Ist ein Ziel damit schon erreicht, gibt es die Belohnung sofort (dann nicht null). */
    fun setGoals(daily: Goal, weekly: Goal): Reward? {
        val today = LocalDate.now().toEpochDay()
        dailyGoal = daily
        weeklyGoal = weekly
        val (day, week) = currentProgress(today)
        val reward = evaluate(today, day, week)
        save()
        return if (reward.mushroom != null || reward.being != null) reward else null
    }

    /** Fortschritt heute und in dieser Woche, jeweils in der Einheit des Ziels. */
    fun currentProgress(today: Long = LocalDate.now().toEpochDay()): Pair<Int, Int> {
        val week = weekStart(today)
        return progressOf(completions, dailyGoal.unit, today, today) to
            progressOf(completions, weeklyGoal.unit, week, week + 6)
    }

    fun rewardedToday(today: Long = LocalDate.now().toEpochDay()) = rewardedDay == today
    fun rewardedThisWeek(today: Long = LocalDate.now().toEpochDay()) = rewardedWeek == weekStart(today)

    private fun evaluate(today: Long, dayBefore: Int, weekBefore: Int): Reward {
        val week = weekStart(today)
        val (dayAfter, weekAfter) = currentProgress(today)
        val dayDone = rewardedDay == today
        val weekDone = rewardedWeek == week

        var mushroom: Mushroom? = null
        if (!dayDone && dayAfter >= dailyGoal.value) {
            val roll = nextMushroom ?: catalog.rollMushroom()
            if (roll != null) {
                mushroom = Mushroom(nextMushroomId(), roll.species, roll.size).also { mushrooms.add(it) }
            }
            rewardedDay = today
            nextMushroom = catalog.rollMushroom()
        }
        var being: Mushroom? = null
        if (!weekDone && weekAfter >= weeklyGoal.value) {
            val species = nextBeing ?: rollBeing()
            if (species != null) {
                being = Mushroom(nextMushroomId(), species, Rewards.BEING_SIZE).also { mushrooms.add(it) }
            }
            rewardedWeek = week
            nextBeing = rollBeing()
        }
        return Reward(
            day = GoalProgress(dailyGoal, dayBefore, dayAfter, dayDone),
            week = GoalProgress(weeklyGoal, weekBefore, weekAfter, weekDone),
            mushroom = mushroom,
            being = being,
            nextMushroom = nextMushroom?.species,
            nextBeing = nextBeing,
        )
    }

    /** Zufälliges Pilz-Wesen, das noch nicht gesammelt wurde (jedes gibt es nur einmal). */
    private fun rollBeing(): String? {
        val owned = mushrooms.mapTo(HashSet()) { it.species }
        return catalog.beings.filter { it.id !in owned }.randomOrNull()?.id
    }

    // ---- Fungarium und Platzierungen ----

    fun isPlaced(mushroomId: Long): Boolean = placements.any { it.mushroomId == mushroomId }

    /** Wie viele Exemplare dieser Art gerade im Fungarium liegen (erspielt minus platziert). */
    fun availableCount(species: String): Int {
        val placed = placements.mapTo(HashSet()) { it.mushroomId }
        return mushrooms.count { it.species == species && it.id !in placed }
    }

    /** Wie viele Exemplare je Größe gerade im Fungarium liegen (nur Größen mit mindestens einem). */
    fun availableBySize(species: String): Map<Int, Int> {
        val placed = placements.mapTo(HashSet()) { it.mushroomId }
        return mushrooms.filter { it.species == species && it.id !in placed }
            .groupingBy { it.size }.eachCount().toSortedMap()
    }

    /** Nimmt ein Exemplar aus dem Fungarium – das größte oder eines der gewünschten Größe. */
    fun takeFromFungarium(species: String, size: Int? = null): Mushroom? {
        val placed = placements.mapTo(HashSet()) { it.mushroomId }
        val free = mushrooms.filter { it.species == species && it.id !in placed }
        return if (size == null) free.maxByOrNull { it.size } else free.firstOrNull { it.size == size }
    }

    // ---- Orte ----

    /** Neuen Ort anlegen; false, wenn der Name leer ist oder es ihn schon gibt. */
    fun addPlace(name: String): Boolean {
        val label = name.trim()
        if (label.isEmpty() || label.equals("Überall", true) || places.any { it.label.equals(label, true) }) return false
        val place = Place("p${System.currentTimeMillis()}", label)
        val sorted = Place.sorted(places + place)
        places.clear()
        places.addAll(sorted)
        save()
        return true
    }

    /** Ort löschen; Schritte, die nur dort möglich waren, gelten danach überall. */
    fun deletePlace(id: String) {
        places.removeAll { it.id == id }
        fun Task.cleaned() = copy(steps = steps.map { it.copy(places = it.places.without(id)) })
        for (i in tasks.indices) tasks[i] = tasks[i].cleaned()
        for (i in templates.indices) templates[i] = templates[i].cleaned()
        save()
    }

    /** Wie viele Schritte (Aufgaben und Vorlagen) nur an diesem einen Ort möglich sind. */
    fun stepsOnlyAt(id: String): Int =
        (tasks + templates).sumOf { t -> t.steps.count { !it.places.everywhere && it.places.ids == setOf(id) } }

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

    // ---- Farben ----

    /** Mit etwas Glück eine noch nicht freigeschaltete Farbe gewinnen (höchstens eine pro Schritt). */
    private fun rollColor(minutes: Int, doneTodayBefore: Int): Int? {
        val locked = (0 until Palette.size).filter { it !in unlockedColors }
        if (locked.isEmpty() || Random.nextDouble() >= colorChance(minutes, doneTodayBefore)) return null
        return locked.random().also { unlockedColors.add(it) }
    }

    /** Farbe einer Einfärbung; Aufgabenkarten erben sonst die Farbe ihrer Vorlage. */
    fun tintOf(anchor: Anchor): Int? {
        tints.firstOrNull { it.anchor == anchor }?.let { return it.color }
        if (anchor is Anchor.OnTask) {
            val templateId = findTask(anchor.taskId)?.templateId ?: return null
            return tints.firstOrNull { it.anchor == Anchor.OnTemplate(templateId) }?.color
        }
        if (anchor is Anchor.OnPool) {
            val source = findPool(anchor.poolId)?.sourcePoolId ?: return null
            return tints.firstOrNull { it.anchor == Anchor.OnPool(source) }?.color
        }
        return null
    }

    /** Pinsel: einfärben; dieselbe Farbe nochmal macht es rückgängig, eine andere färbt um. */
    fun toggleTint(anchor: Anchor, color: Int) {
        val i = tints.indexOfFirst { it.anchor == anchor }
        if (i >= 0 && tints[i].color == color) {
            // Nochmal dieselbe Farbe: zurück zur Farbe vom letzten Speichern (bzw. ganz ohne Farbe).
            val saved = draftTints.firstOrNull { it.anchor == anchor }
            if (saved != null) tints[i] = saved else tints.removeAt(i)
        } else if (i >= 0) {
            tints[i] = Tint(anchor, color)
        } else {
            tints.add(Tint(anchor, color))
        }
        save()
    }

    fun resetTints() {
        tints.clear()
        save()
    }

    // ---- Entwurf im Pilz-Modus ----

    /** Ab jetzt ist alles Entwurf: wird angezeigt, aber erst mit [commitDraft] gespeichert. */
    fun beginDraft() {
        draft = placements.toList() to tasks.associate { it.id to it.hiddenCopies }
        draftTints = tints.toList()
        draftPools = pools.toList()
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
        tints.clear()
        tints.addAll(draftTints)
        pools.clear()
        pools.addAll(draftPools)
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
        var changed = false
        for (i in mushrooms.indices) {
            val m = mushrooms[i]
            if (catalog.find(m.species) != null) continue
            // Gleicher Name, nur andere Dateiendung (z. B. .png → .webp)? Dann dieselbe Art behalten.
            val base = m.species.substringBeforeLast('.').let { RENAMED[it] ?: it }
            val same = catalog.species.firstOrNull { it.id.substringBeforeLast('.') == base }
            val replacement = when {
                same != null -> same.id
                // Ohne Unterstrich war es ein Pilz-Wesen: ein noch nicht gesammeltes nehmen.
                '_' !in base -> rollBeing()
                else -> catalog.mushrooms.randomOrNull()?.id
            } ?: continue
            mushrooms[i] = m.copy(species = replacement)
            changed = true
        }
        val next = nextMushroom
        if (next == null || catalog.find(next.species)?.kind != SpriteKind.MUSHROOM) {
            nextMushroom = catalog.rollMushroom()
            changed = true
        }
        val owned = mushrooms.mapTo(HashSet()) { it.species }
        val being = nextBeing
        if (being == null || catalog.find(being) == null || being in owned) {
            val rolled = rollBeing()
            if (rolled != being) {
                nextBeing = rolled
                changed = true
            }
        }
        val ids = mushrooms.mapTo(HashSet()) { it.id }
        if (placements.removeAll { it.mushroomId !in ids }) changed = true
        // Karten in nicht (mehr) vorhandenen Pools zurück in die normale Liste.
        val poolIds = pools.mapTo(HashSet()) { it.id }
        for (list in listOf(tasks, templates)) {
            for (i in list.indices) {
                val pid = list[i].poolId
                if (pid != null && pid !in poolIds) {
                    list[i] = list[i].copy(poolId = null)
                    changed = true
                }
            }
        }
        // Das Fenster "Hinzufügen" gibt es nicht mehr: dort platzierte Sprites zurück ins Fungarium.
        val removedWindow = "add"
        if (placements.removeAll { p ->
                val a = p.anchor
                (a is Anchor.Window && a.window == removedWindow) || (a is Anchor.Area && a.window == removedWindow)
            }
        ) {
            changed = true
        }
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
        val mushrooms: List<Mushroom>,
        val next: MushroomRoll?,
        val nextBeing: String?,
        val completions: List<Completion>,
        val dailyGoal: Goal,
        val weeklyGoal: Goal,
        val rewardedDay: Long?,
        val rewardedWeek: Long?,
        val placements: List<Placement>,
        val places: List<Place>,
        val pools: List<Pool>,
        val unlockedColors: List<Int>,
        val tints: List<Tint>,
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
        nextMushroom = data.next
        nextBeing = data.nextBeing
        completions.clear()
        completions.addAll(data.completions)
        dailyGoal = data.dailyGoal
        weeklyGoal = data.weeklyGoal
        rewardedDay = data.rewardedDay
        rewardedWeek = data.rewardedWeek
        places.clear()
        places.addAll(data.places)
        pools.clear()
        pools.addAll(data.pools)
        unlockedColors.clear()
        unlockedColors.addAll(data.unlockedColors)
        tints.clear()
        tints.addAll(data.tints)
    }

    private fun toJson(): JSONObject = JSONObject()
        .put("version", 7)
        .put("colors", JSONArray().apply { unlockedColors.forEach { put(it) } })
        .put("tints", JSONArray().apply { tints.forEach { put(JSONObject().put("anchor", it.anchor.toJson()).put("color", it.color)) } })
        .put("pools", JSONArray().apply {
            pools.forEach {
                put(
                    JSONObject().put("id", it.id).put("name", it.name).put("forTemplates", it.forTemplates)
                        .put("sortKey", it.sortKey).put("sourcePoolId", it.sourcePoolId)
                        .put("hiddenCopies", JSONArray().apply { it.hiddenCopies.forEach { m -> put(m) } }),
                )
            }
        })
        .put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        .put("templates", JSONArray().apply { templates.forEach { put(it.toJson()) } })
        .put("completions", JSONArray().apply { completions.forEach { put(JSONArray().put(it.epochDay).put(it.minutes)) } })
        .put("dailyGoal", dailyGoal.toJson())
        .put("weeklyGoal", weeklyGoal.toJson())
        .put("rewardedDay", rewardedDay)
        .put("rewardedWeek", rewardedWeek)
        .put("nextBeing", nextBeing)
        .put("mushrooms", JSONArray().apply { mushrooms.forEach { put(it.toJson()) } })
        .put("nextMushroom", nextMushroom?.let { JSONObject().put("species", it.species).put("size", it.size) })
        .put("placements", JSONArray().apply { placements.forEach { put(it.toJson()) } })
        .put("places", JSONArray().apply { places.forEach { put(JSONObject().put("id", it.id).put("label", it.label)) } })

    private fun parse(root: JSONObject): Data {
        val next = root.optJSONObject("nextMushroom")
        return Data(
            tasks = root.optJSONArray("tasks").toTasks(),
            templates = root.optJSONArray("templates").toTasks(),
            mushrooms = root.optJSONArray("mushrooms").mapObjects {
                Mushroom(it.getLong("id"), it.getString("species"), it.getInt("size").coerceIn(1, 5))
            },
            next = next?.let { MushroomRoll(it.getString("species"), it.getInt("size").coerceIn(1, 5)) },
            placements = root.optJSONArray("placements").mapObjects { it.toPlacement() }.filterNotNull(),
            places = root.optJSONArray("places").let { arr ->
                if (arr == null) Place.sorted(Place.DEFAULTS)
                else Place.sorted((0 until arr.length()).map { i -> arr.getJSONObject(i).let { Place(it.getString("id"), it.getString("label")) } })
            },
            unlockedColors = root.optJSONArray("colors").let { arr ->
                val saved = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getInt(it) }
                (Palette.STARTERS + saved).filter { it in 0 until Palette.size }.distinct()
            },
            tints = root.optJSONArray("tints").mapObjects { o ->
                o.getJSONObject("anchor").toAnchor()?.let { Tint(it, o.getInt("color")) }
            }.filterNotNull().filter { it.color in 0 until Palette.size },
            pools = root.optJSONArray("pools").mapObjects {
                val hidden = it.optJSONArray("hiddenCopies")
                Pool(
                    it.getLong("id"),
                    it.getString("name"),
                    it.optBoolean("forTemplates"),
                    it.optLong("sortKey", 0),
                    sourcePoolId = if (it.has("sourcePoolId") && !it.isNull("sourcePoolId")) it.getLong("sourcePoolId") else null,
                    hiddenCopies = if (hidden == null) emptySet() else (0 until hidden.length()).mapTo(HashSet()) { k -> hidden.getLong(k) },
                )
            },
            nextBeing = if (root.isNull("nextBeing")) null else root.optString("nextBeing"),
            completions = root.optJSONArray("completions").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map {
                    val c = arr.getJSONArray(it)
                    Completion(c.getLong(0), c.getInt(1))
                }
            },
            dailyGoal = root.optJSONObject("dailyGoal")?.toGoal() ?: Rewards.DEFAULT_DAILY,
            weeklyGoal = root.optJSONObject("weeklyGoal")?.toGoal() ?: Rewards.DEFAULT_WEEKLY,
            rewardedDay = if (root.isNull("rewardedDay")) null else root.optLong("rewardedDay"),
            rewardedWeek = if (root.isNull("rewardedWeek")) null else root.optLong("rewardedWeek"),
        )
    }
}

private fun Goal.toJson(): JSONObject = JSONObject().put("value", value).put("unit", unit.name)

private fun JSONObject.toGoal(): Goal? {
    val unit = GoalUnit.entries.firstOrNull { it.name == optString("unit") } ?: return null
    return Goal(optInt("value", 1).coerceAtLeast(1), unit)
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
    .put("poolId", poolId)
    .put("sortKey", sortKey)
    .put("auto", auto?.let { a ->
        val r = a.repeat
        JSONObject().put("nextDue", a.nextDue).put(
            "repeat",
            when (r) {
                Repeat.Once -> "once"
                is Repeat.EveryDays -> "days:${r.days}"
                is Repeat.MonthDay -> "month:${r.day}"
            },
        )
    })

private fun Step.toJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("minutes", minutes)
    .put("everywhere", places.everywhere)
    .put("places", JSONArray().apply { places.ids.forEach { put(it) } })

private fun Mushroom.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("species", species)
    .put("size", size)

private fun Anchor.toJson(): JSONObject = when (val anchor = this) {
    is Anchor.Window -> JSONObject().put("type", "window").put("window", anchor.window)
    is Anchor.Area -> JSONObject().put("type", "area").put("window", anchor.window).put("key", anchor.key)
    is Anchor.OnTask -> JSONObject().put("type", "task").put("id", anchor.taskId)
    is Anchor.OnTemplate -> JSONObject().put("type", "template").put("id", anchor.templateId)
    is Anchor.OnPool -> JSONObject().put("type", "pool").put("id", anchor.poolId)
}

private fun JSONObject.toAnchor(): Anchor? = when (getString("type")) {
    "window" -> Anchor.Window(getString("window"))
    "area" -> Anchor.Area(getString("window"), getString("key"))
    "task" -> Anchor.OnTask(getLong("id"))
    "template" -> Anchor.OnTemplate(getLong("id"))
    "pool" -> Anchor.OnPool(getLong("id"))
    else -> null
}

private fun Placement.toJson(): JSONObject {
    return JSONObject()
        .put("mushroomId", mushroomId)
        .put("anchor", anchor.toJson())
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("mirrored", mirrored)
        .put("scale", scale.toDouble())
}

private fun JSONObject.toPlacement(): Placement? {
    val anchor = getJSONObject("anchor").toAnchor() ?: return null
    return Placement(
        mushroomId = getLong("mushroomId"),
        anchor = anchor,
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        mirrored = optBoolean("mirrored", false),
        scale = optDouble("scale", 1.0).toFloat(),
    )
}

/** Ältere Daten ohne Sortierschlüssel behalten ihre bisherige Reihenfolge. */
private fun JSONArray?.toTasks(): List<Task> = mapObjects { it.toTask() }
    .mapIndexed { i, t -> if (t.sortKey == Long.MIN_VALUE) t.copy(sortKey = i.toLong()) else t }
    .filter { it.steps.isNotEmpty() }

private fun JSONObject.toTask(): Task {
    val hidden = optJSONArray("hiddenCopies")
    return Task(
        id = getLong("id"),
        title = getString("title"),
        steps = optJSONArray("steps").mapObjects { it.toStep() },
        completedSteps = optInt("completedSteps", 0),
        templateId = if (has("templateId") && !isNull("templateId")) getLong("templateId") else null,
        hiddenCopies = if (hidden == null) emptySet() else (0 until hidden.length()).mapTo(HashSet()) { hidden.getLong(it) },
        poolId = if (has("poolId") && !isNull("poolId")) getLong("poolId") else null,
        sortKey = if (has("sortKey")) getLong("sortKey") else Long.MIN_VALUE,
        auto = optJSONObject("auto")?.let { a ->
            val r = a.optString("repeat", "once")
            val repeat = when {
                r.startsWith("days:") -> Repeat.EveryDays(r.removePrefix("days:").toIntOrNull()?.coerceAtLeast(1) ?: 1)
                r.startsWith("month:") -> Repeat.MonthDay(r.removePrefix("month:").toIntOrNull()?.coerceIn(1, 31) ?: 1)
                else -> Repeat.Once
            }
            AutoAdd(a.getLong("nextDue"), repeat)
        },
    )
}

/** Umbenannte Sprites (alter Name → neuer Name, ohne Endung), damit erspielte Pilze ihre Art behalten. */
private val RENAMED = mapOf(
    "Wiesen-Champignon_Agaricus campestris" to "Champignon_Agaricus bisporus",
    "Glimmer-Tintling_Coprinellus micaceus" to "Faserling_Psathyrella",
    "Parasol_Macrolepiota procera" to "Gemeiner Riesenschirmling_Macrolepiota procera",
    "Rosablättriger Helmling_Mycena galericulata" to "Gesäter Tintling_Coprinellus disseminatus",
    "Pfifferling_Cantharellus cibarius" to "Pfifferling_Cantharellus",
    "Speisemorchel_Morchella esculenta" to "Morchel_Morchella",
)

/** Die vier Orte der ersten Version – wer die alle hatte, hatte "Überall" gewählt. */
private val ORIGINAL_PLACES = setOf("HOME", "TRANSIT", "CAFE", "CITY")

private fun JSONObject.toStep(): Step {
    val names = getJSONArray("places")
    val set = (0 until names.length())
        .mapTo(HashSet()) { i -> names.getString(i) }
    val everywhere = if (has("everywhere")) getBoolean("everywhere") else set.containsAll(ORIGINAL_PLACES)
    return Step(
        title = optString("title", ""),
        places = if (everywhere) Places(everywhere = true) else Places(set),
        minutes = getInt("minutes"),
    )
}
