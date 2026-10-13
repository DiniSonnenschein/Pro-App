package de.produktivitaet.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Liste der Übersicht (Hauptliste eines Reiters oder Inhalt eines Pools):
 * oben + (neue Karte), Lupe (Suche) und – nur in der Hauptliste – Ordner-Plus (neuer Pool).
 * Karten und Pools lassen sich per langem Drücken verschieben; eine Karte, die auf einem Pool
 * losgelassen wird, wandert hinein. Im Pool: lange drücken und loslassen = "Aus dem Pool entfernen".
 */
@Composable
fun CardList(vm: MainViewModel, forTemplates: Boolean, poolId: Long?, modifier: Modifier = Modifier) {
    val store = vm.store
    val listState = rememberLazyListState()
    RegisterDecorScroll(listState)

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = searchOpen && query.isNotBlank()

    // Dialoge
    var toComplete by remember { mutableStateOf<Task?>(null) }
    var toDelete by remember { mutableStateOf<Task?>(null) }
    var toRemoveFromPool by remember { mutableStateOf<Task?>(null) }
    var poolToDelete by remember { mutableStateOf<Pool?>(null) }
    var poolToRename by remember { mutableStateOf<Pool?>(null) }
    var newPool by remember { mutableStateOf(false) }

    // Ziehen
    val working = remember { mutableStateListOf<ListEntry>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    // Sichtbare Oberkante der gezogenen Karte (Koordinaten der Liste) und Griffpunkt darin.
    var visualTop by remember { mutableFloatStateOf(0f) }
    var grabY by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    // Zähler je Pool: springt hoch, wenn eine Karte hineingelegt wurde (löst die Animation aus).
    val poolPulses = remember { mutableStateMapOf<Long, Int>() }
    val density = LocalDensity.current
    var hoverPool by remember { mutableStateOf<Long?>(null) }
    var moved by remember { mutableStateOf(false) }
    // Zeitpunkt des letzten Loslassens: verhindert, dass ein Pool nach langem Drücken aufgeht.
    val lastDragEnd = remember { longArrayOf(0L) }

    val entries = store.entries(forTemplates, poolId)
    val shown: List<ListEntry> = when {
        searching -> searchResults(store, forTemplates, poolId, query)
        draggingKey != null -> working
        else -> entries
    }

    /** Aufgabe aus einer Vorlage: bleibt im Pool ihrer Vorlage (nur Umsortieren erlaubt). */
    fun isPinnedKey(key: String): Boolean =
        !forTemplates && key.startsWith("t") && store.findTask(key.drop(1).toLong())?.let { store.isPinned(it) } == true

    /**
     * Tauscht die gezogene Karte mit ihrem Nachbarn, sobald ihre Mitte näher an dem Platz liegt,
     * den sie nach dem Tausch hätte – so klappt es auch mit unterschiedlich großen Karten.
     * Über einem Pool: in der Mitte hineinlegen, getauscht wird erst auf der anderen Seite.
     */
    fun onDragMove() {
        val key = draggingKey ?: return
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo
        val cur = info.firstOrNull { it.key == key } ?: return
        val from = working.indexOfFirst { it.key == key }
        // Erst weitermachen, wenn die Liste den letzten Tausch umgesetzt hat (Index 0 = Kopfzeile).
        if (from < 0 || cur.index != from + 1) return
        val c = visualTop + cur.size / 2f
        val oldCenter = cur.offset + cur.size / 2f
        val down = c > oldCenter
        val nIdx = if (down) from + 1 else from - 1
        val neighbor = working.getOrNull(nIdx)
        val n = neighbor?.let { nb -> info.firstOrNull { it.key == nb.key } }
        if (neighbor == null || n == null) {
            hoverPool = null
            return
        }
        val newCenter = if (down) n.offset + n.size - cur.size / 2f else n.offset + cur.size / 2f
        val mid = (oldCenter + newCenter) / 2f
        var passed = if (down) c > mid else c < mid
        if (key.startsWith("t") && neighbor is ListEntry.Folder && !isPinnedKey(key)) {
            val rel = (c - n.offset) / n.size
            hoverPool = if (rel in 0.2f..0.8f) neighbor.pool.id else null
            passed = passed && (if (down) rel > 0.8f else rel < 0.2f)
        } else {
            hoverPool = null
        }
        // Der neue Platz muss im Bild liegen: Rutscht er aus der Liste heraus, verwirft die Liste
        // die Karte und das Ziehen bricht ab. Dann lieber warten, bis das Mitscrollen ihn hereinholt.
        val newTop = if (down) n.offset + n.size - cur.size else n.offset
        val margin = 8f
        passed = passed && newTop < layout.viewportEndOffset - margin && newTop + cur.size > layout.viewportStartOffset + margin
        if (passed) {
            hoverPool = null
            // Die Liste hält sonst ihre oberste sichtbare Karte fest – ist das die gezogene oder ihr Nachbar,
            // springt sie beim Tausch mit und die Karte rutscht aus dem Bild. Also die Position beibehalten.
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            working.add(nIdx, working.removeAt(from))
        }
    }

    fun finishDrag() {
        val key = draggingKey
        val pool = hoverPool
        if (key != null) {
            val id = key.drop(1).toLong()
            when {
                !moved && poolId != null && key.startsWith("t") && !isPinnedKey(key) ->
                    toRemoveFromPool = (if (forTemplates) store.templates else store.tasks).firstOrNull { it.id == id }
                pool != null -> {
                    store.moveToPool(forTemplates, id, pool)
                    poolPulses[pool] = (poolPulses[pool] ?: 0) + 1
                }
                moved -> store.reorder(forTemplates, working.toList())
            }
        }
        draggingKey = null
        hoverPool = null
        lastDragEnd[0] = System.currentTimeMillis()
    }

    // Beim Ziehen am oberen oder unteren Rand automatisch mitscrollen – auch wenn der Finger stillhält.
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        val edge = with(density) { 80.dp.toPx() }
        val maxSpeed = with(density) { 16.dp.toPx() }
        while (true) {
            withFrameNanos { }
            if (draggingKey == null) break
            val layout = listState.layoutInfo
            val finger = visualTop + grabY
            val start = layout.viewportStartOffset + edge
            val end = layout.viewportEndOffset - edge
            var speed = when {
                finger > end -> maxSpeed * ((finger - end) / edge).coerceIn(0.15f, 1f)
                finger < start -> -maxSpeed * ((start - finger) / edge).coerceIn(0.15f, 1f)
                else -> 0f
            }
            // Nie so weit scrollen, dass der Platz der gezogenen Karte ganz aus dem Bild rutscht.
            val cur = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey }
            val margin = 8f
            speed = when {
                cur == null -> 0f
                speed > 0f -> minOf(speed, cur.offset + cur.size - layout.viewportStartOffset - margin).coerceAtLeast(0f)
                else -> maxOf(speed, cur.offset - layout.viewportEndOffset + margin).coerceAtMost(0f)
            }
            if (speed != 0f) listState.dispatchRawDelta(speed)
            onDragMove()
        }
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "kopf") {
                ListHeader(
                    forTemplates = forTemplates,
                    inPool = poolId != null,
                    searchOpen = searchOpen,
                    query = query,
                    onQuery = { query = it },
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                    onAdd = { if (forTemplates) vm.newTemplate(poolId) else vm.newTask(poolId = poolId) },
                    onAddPool = { newPool = true },
                )
            }
            if (shown.isEmpty()) {
                item(key = "leer") {
                    Text(
                        when {
                            searching -> "Nichts gefunden."
                            forTemplates -> "Noch keine Vorlagen."
                            else -> "Noch keine Aufgaben."
                        },
                        color = Muted,
                        fontSize = 15.sp,
                    )
                }
            }
            items(shown, key = { it.key }) { entry ->
                val isDragged = entry.key == draggingKey
                val itemModifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            val off = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }?.offset
                            translationY = if (off != null) visualTop - off else 0f
                        }
                        // Über einem Pool wird die Karte kleiner – sie "fällt" gleich hinein.
                        val s = if (!isDragged) 1f else if (hoverPool != null) 0.9f else 1.03f
                        scaleX = s
                        scaleY = s
                        alpha = if (isDragged && hoverPool != null) 0.85f else 1f
                    }
                    .then(if (isDragged) Modifier else Modifier.animateItem())
                    .pointerInput(entry.key, searching) {
                        if (searching) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                working.clear()
                                working.addAll(store.entries(forTemplates, poolId))
                                val layout = listState.layoutInfo
                                visualTop = layout.visibleItemsInfo.firstOrNull { it.key == entry.key }?.offset?.toFloat() ?: 0f
                                grabY = pos.y
                                dragDistance = 0f
                                draggingKey = entry.key
                                moved = false
                                hoverPool = null
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val layout = listState.layoutInfo
                                // Finger bleibt im sichtbaren Bereich der Liste.
                                visualTop = (visualTop + amount.y).coerceIn(
                                    layout.viewportStartOffset - grabY,
                                    layout.viewportEndOffset - grabY,
                                )
                                dragDistance += abs(amount.y)
                                if (dragDistance > viewConfiguration.touchSlop) moved = true
                                onDragMove()
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                        )
                    }
                Box(itemModifier) {
                    when (entry) {
                        is ListEntry.Folder -> PoolCard(
                            pool = entry.pool,
                            count = store.poolSize(entry.pool),
                            highlighted = hoverPool == entry.pool.id,
                            pulse = poolPulses[entry.pool.id] ?: 0,
                            onOpen = {
                                if (draggingKey == null && System.currentTimeMillis() - lastDragEnd[0] > 400) {
                                    vm.open(Screen.PoolView(entry.pool.id))
                                }
                            },
                            onEdit = { poolToRename = entry.pool },
                            onDelete = { poolToDelete = entry.pool },
                            mirrored = entry.pool.sourcePoolId != null,
                        )
                        is ListEntry.Card -> Column {
                            val task = entry.task
                            val inPool = task.poolId
                            if (searching && poolId == null && inPool != null) {
                                Text(
                                    "Pool: ${store.findPool(inPool)?.name ?: ""}",
                                    color = Muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                )
                            }
                            TaskCard(task, dimPending = !forTemplates, isTemplate = forTemplates, actionSlots = 3) {
                                if (forTemplates) {
                                    IconButton(onClick = { vm.newTask(task) }) {
                                        Icon(Icons.Filled.Add, "Aufgabe aus dieser Vorlage erstellen", tint = cardAccent())
                                    }
                                } else {
                                    IconButton(onClick = { toComplete = task }) {
                                        Icon(Icons.Filled.Check, "Erledigt", tint = cardAccent())
                                    }
                                }
                                IconButton(onClick = { if (forTemplates) vm.editTemplate(task) else vm.editTask(task) }) {
                                    Icon(Icons.Filled.Edit, "Bearbeiten", tint = LocalContentColor.current)
                                }
                                IconButton(onClick = { toDelete = task }) {
                                    Icon(Icons.Filled.Delete, "Löschen", tint = LocalContentColor.current)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toComplete?.let { task ->
        val stepText = if (task.totalSteps > 1) {
            "Schritt ${task.completedSteps + 1} von ${task.totalSteps} von „${task.title}“"
        } else {
            "„${task.title}“"
        }
        ConfirmDialog(
            title = "Erledigt?",
            text = "$stepText als erledigt markieren?",
            confirmText = "Erledigt",
            confirmColor = LightBlue,
            confirmTextColor = Black,
            onConfirm = {
                toComplete = null
                vm.completeStep(task.id, fromOverview = true)
            },
            onDismiss = { toComplete = null },
        )
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = if (forTemplates) "Vorlage löschen?" else "Aufgabe löschen?",
            text = "„${item.title}“ wird endgültig gelöscht." +
                if (forTemplates) " Ihre Pilze kommen zurück ins Fungarium." else "",
            confirmText = "Löschen",
            onConfirm = {
                if (forTemplates) store.deleteTemplate(item.id) else store.deleteTask(item.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }

    toRemoveFromPool?.let { item ->
        ConfirmDialog(
            title = "Aus dem Pool entfernen?",
            text = "„${item.title}“ kommt zurück in die normale Liste.",
            confirmText = "Entfernen",
            confirmColor = White,
            confirmTextColor = Black,
            onConfirm = {
                store.moveToPool(forTemplates, item.id, null)
                toRemoveFromPool = null
            },
            onDismiss = { toRemoveFromPool = null },
        )
    }

    poolToDelete?.let { pool ->
        val count = store.poolSize(pool)
        val what = if (forTemplates) "Vorlagen" else "Aufgaben"
        ConfirmDialog(
            title = "Pool löschen?",
            text = "Der Pool „${pool.name}“ wird mit allen $count enthaltenen $what endgültig gelöscht. " +
                (if (forTemplates) "Auch sein Pool bei den Aufgaben verschwindet: Aufgaben aus diesen Vorlagen werden gelöscht, eigene Aufgaben kommen zurück in die Liste. " else "") +
                "Zugehörige Pilze kommen zurück ins Fungarium.",
            confirmText = "Löschen",
            onConfirm = {
                store.deletePool(pool.id)
                poolToDelete = null
            },
            onDismiss = { poolToDelete = null },
        )
    }

    if (newPool) {
        PoolNameDialog(
            title = "Neuer Pool",
            initial = "",
            confirmText = "Anlegen",
            onConfirm = {
                store.addPool(it, forTemplates)
                newPool = false
            },
            onDismiss = { newPool = false },
        )
    }

    poolToRename?.let { pool ->
        PoolNameDialog(
            title = "Pool umbenennen",
            initial = pool.name,
            confirmText = "Speichern",
            onConfirm = {
                store.renamePool(pool.id, it)
                poolToRename = null
            },
            onDismiss = { poolToRename = null },
        )
    }
}

/** Suchtreffer: passende Karten (in der Hauptliste auch aus Pools) und Pools mit passendem Namen. */
private fun searchResults(store: TaskStore, forTemplates: Boolean, poolId: Long?, query: String): List<ListEntry> {
    val cards = (if (forTemplates) store.templates else store.tasks)
        .filter { (poolId == null || it.poolId == poolId) && it.matches(query) }
        .sortedWith(compareBy({ it.poolId != null }, { it.sortKey }))
        .map { ListEntry.Card(it) }
    val q = query.trim().lowercase()
    val pools = if (poolId == null) {
        store.pools.filter { it.forTemplates == forTemplates && it.name.lowercase().contains(q) }
            .sortedBy { it.sortKey }
            .map { ListEntry.Folder(it) }
    } else {
        emptyList()
    }
    return pools + cards
}

/** Kopfzeile: platzsparendes +, Lupe und (in der Hauptliste) Ordner-Plus; darunter ggf. das Suchfeld. */
@Composable
private fun ListHeader(
    forTemplates: Boolean,
    inPool: Boolean,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    onAddPool: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircleIconButton(
                Icons.Filled.Add,
                if (forTemplates) "Vorlage hinzufügen" else "Aufgabe hinzufügen",
                onAdd,
                size = 44.dp,
                decoKey = "liste-plus",
            )
            CircleIconButton(
                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                if (searchOpen) "Suche schließen" else "Suchen",
                onToggleSearch,
                size = 44.dp,
                decoKey = "liste-suche",
            )
            if (!inPool) {
                PainterCircleButton(R.drawable.ic_ordner_plus, "Neuer Pool", onAddPool, decoKey = "liste-pool")
            }
        }
        if (searchOpen) {
            TextInput(
                value = query,
                onValueChange = onQuery,
                label = "Suchen (Wort oder Minuten)",
                modifier = Modifier.fillMaxWidth(),
                decoKey = "liste-suchfeld",
            )
        }
    }
}

@Composable
private fun PainterCircleButton(res: Int, description: String, onClick: () -> Unit, decoKey: String) {
    Box(
        Modifier
            .size(44.dp)
            .decoArea(decoKey, CircleShape, Black)
            .clip(CircleShape)
            .border(1.dp, onColor(areaColor(decoKey, Black)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(res), description, tint = onColor(areaColor(decoKey, Black)), modifier = Modifier.size(22.dp))
    }
}

/** Pool-Karte: Ordner-Symbol, Name, Anzahl, Stift und Mülltonne. Dekorierbar wie andere Karten. */
@Composable
private fun PoolCard(
    pool: Pool,
    count: Int,
    highlighted: Boolean,
    pulse: Int,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    mirrored: Boolean,
) {
    val fg = onColor(areaColor(poolKey(pool.id), Black))
    // Karte schwebt darüber: Pool wird etwas größer und leuchtet.
    val hoverScale by animateFloatAsState(if (highlighted) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "poolHover")
    val hoverGlow by animateFloatAsState(if (highlighted) 1f else 0f, tween(150), label = "poolGlow")
    // Karte hineingelegt: kurzes Aufploppen mit Nachleuchten.
    val pulseScale = remember { Animatable(1f) }
    val pulseGlow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        launch {
            pulseGlow.snapTo(1f)
            pulseGlow.animateTo(0f, tween(700))
        }
        pulseScale.animateTo(1.12f, tween(110))
        pulseScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f))
    }
    val glow = maxOf(hoverGlow, pulseGlow.value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = hoverScale * pulseScale.value
                scaleX = s
                scaleY = s
            }
            .decoArea(poolKey(pool.id), CardShape, Black)
            .clip(CardShape)
            .drawWithContent {
                drawContent()
                if (glow > 0f) drawRect(LightBlue.copy(alpha = 0.22f * glow))
            }
            .border(1.dp + 2.dp * glow, lerp(fg, LightBlue, glow), CardShape)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_ordner), "Pool", tint = fg, modifier = Modifier.size(26.dp))
        if (mirrored) {
            // Kennzeichen: Pool stammt aus den Vorlagen.
            Icon(
                painterResource(R.drawable.ic_vorlage),
                "Aus den Vorlagen",
                tint = fg.copy(alpha = 0.65f),
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(pool.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .clip(CircleShape)
                .border(1.dp, fg.copy(alpha = 0.65f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text("$count", fontSize = 14.sp, color = fg.copy(alpha = 0.65f))
        }
        if (mirrored) {
            // Umbenennen und Löschen nur im Vorlagen-Reiter.
            Spacer(Modifier.width(96.dp))
        } else {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Umbenennen", tint = fg) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Löschen", tint = fg) }
        }
    }
}

@Composable
private fun PoolNameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AppDialog(title, onDismiss) {
        TextInput(name, { name = it }, "Name", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Abbrechen", onDismiss, Modifier.weight(1f))
            OutlineButton(confirmText, { onConfirm(name.trim()) }, Modifier.weight(1f), enabled = name.isNotBlank())
        }
    }
}

/** Inhalt eines Pools als eigenes Fenster. */
@Composable
fun PoolScreen(vm: MainViewModel, poolId: Long) {
    val pool = vm.store.findPool(poolId)
    if (pool == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(pool.name, vm::back)
        CardList(vm, pool.forTemplates, poolId, Modifier.weight(1f))
        MushroomBar(vm)
    }
}
