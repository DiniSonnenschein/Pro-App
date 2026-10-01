package de.produktivitaet.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

val LocalAppViewModel = staticCompositionLocalOf<MainViewModel?> { null }
val LocalDecorWindow = staticCompositionLocalOf<DecorWindowState?> { null }

class AreaInfo(val rectInRoot: Rect, val shape: Shape)

/** Alles, was ein dekorierbares Fenster über seine Buttons/Karten weiß. */
class DecorWindowState(val window: String) {
    /** Buttons, Felder und Karten dieses Fensters (Rechteck in Root-Koordinaten). */
    val areas = mutableStateMapOf<String, AreaInfo>()
    var origin by mutableStateOf(Offset.Zero)

    /** Liste/Spalte, die im Pilz-Modus per Wischen auf freier Fläche gescrollt wird. */
    var scrollable: ScrollableState? = null

    fun areaRect(key: String): Rect? = areas[key]?.rectInRoot?.translate(-origin)

    /** Button/Karte mit der größten Überlappung mit [r] (Fensterkoordinaten), falls es eine gibt. */
    fun bestArea(r: Rect): Pair<String, Rect>? {
        var best: Pair<String, Rect>? = null
        var bestFraction = 0f
        for ((key, info) in areas) {
            val rect = info.rectInRoot.translate(-origin)
            val f = overlapFraction(r.toArray(), rect.toArray())
            if (f > bestFraction) {
                bestFraction = f
                best = key to rect
            }
        }
        return best
    }
}

fun Rect.toArray() = floatArrayOf(left, top, right, bottom)

/** Ein im Fenster sichtbarer Pilz mit seinem Rechteck in Fensterkoordinaten. */
class VisibleDecor(
    val id: DecorItemId,
    val mushroom: Mushroom,
    /** null bei Vorlagen-Kopien auf Aufgaben. */
    val placement: Placement?,
    val rect: Rect,
    val mirrored: Boolean,
    val onArea: Boolean,
)

private fun mushroomSizePx(vm: MainViewModel, m: Mushroom, density: Float, screenHeightDp: Float): Pair<Float, Float> {
    val h = mushroomHeightDp(m.size, screenHeightDp) * density
    return h * vm.catalog.aspect(m.species) to h
}

fun DrawScope.drawMushroom(
    vm: MainViewModel,
    m: Mushroom,
    center: Offset,
    mirrored: Boolean,
    screenHeightDp: Float,
    alpha: Float = 1f,
) {
    val (w, h) = mushroomSizePx(vm, m, density, screenHeightDp)
    val image = vm.mushroomBitmap(m.species, h) ?: return
    val topLeft = IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt())
    val size = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
    scale(if (mirrored) -1f else 1f, 1f, pivot = center) {
        drawImage(image, dstOffset = topLeft, dstSize = size, alpha = alpha, filterQuality = FilterQuality.Medium)
    }
}

/** Pilze, die auf einem bestimmten Button/einer Karte liegen (inklusive Vorlagen-Kopien auf Aufgaben). */
private fun areaDecor(vm: MainViewModel, window: String, key: String): List<Triple<DecorItemId, Mushroom, Placement>> {
    val store = vm.store
    val result = mutableListOf<Triple<DecorItemId, Mushroom, Placement>>()
    val anchor = anchorForArea(window, key)
    if (anchor is Anchor.OnTask) {
        val task = store.findTask(anchor.taskId)
        val templateId = task?.templateId
        if (task != null && templateId != null) {
            for (p in store.placements) {
                if (p.anchor != Anchor.OnTemplate(templateId) || p.mushroomId in task.hiddenCopies) continue
                val m = store.findMushroom(p.mushroomId) ?: continue
                result += Triple(DecorItemId.Copy(task.id, m.id), m, p)
            }
        }
    }
    for (p in store.placements) {
        if (p.anchor != anchor) continue
        val m = store.findMushroom(p.mushroomId) ?: continue
        result += Triple(DecorItemId.Placed(m.id), m, p)
    }
    return result
}

/**
 * Macht ein Element zu einem Button/einer Karte im Sinne der Pilz-Ebenen: Seine Lage wird gemerkt,
 * und Pilze, die darauf liegen, werden über dem Inhalt gezeichnet und auf seine Form zugeschnitten.
 * Die Funktion des Buttons bleibt unberührt – Pilze fangen keine Berührungen ab.
 */
fun Modifier.decoArea(key: String, shape: Shape): Modifier = composed {
    val win = LocalDecorWindow.current
    val vm = LocalAppViewModel.current
    if (win == null || vm == null) return@composed Modifier
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    DisposableEffect(win, key) {
        onDispose { win.areas.remove(key) }
    }
    Modifier
        .onGloballyPositioned {
            val rect = Rect(it.positionInRoot(), it.size.toSize())
            if (win.areas[key]?.rectInRoot != rect) win.areas[key] = AreaInfo(rect, shape)
        }
        .drawWithContent {
            drawContent()
            val items = areaDecor(vm, win.window, key)
            if (items.isEmpty()) return@drawWithContent
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
            clipPath(path) {
                for ((id, m, p) in items) {
                    if (vm.dragging == id) continue
                    drawMushroom(vm, m, Offset(p.x * density, p.y * density), p.mirrored, screenHeightDp)
                }
            }
        }
}

/** Meldet die scrollbare Liste/Spalte des Fensters für das Scrollen im Pilz-Modus an. */
@Composable
fun RegisterDecorScroll(state: ScrollableState) {
    val win = LocalDecorWindow.current
    SideEffect { win?.scrollable = state }
}

/**
 * Rahmen für ein dekorierbares Fenster: zeichnet die Pilze der Fenster-Ebene hinter den Inhalt
 * und legt im Pilz-Modus die Bearbeitungsebene darüber.
 */
@Composable
fun DecoratedWindow(vm: MainViewModel, window: String, content: @Composable () -> Unit) {
    val state = remember(window) { DecorWindowState(window) }
    val density = LocalDensity.current.density
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    CompositionLocalProvider(LocalDecorWindow provides state) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val origin = it.positionInRoot()
                    if (state.origin != origin) state.origin = origin
                    vm.windowSizesDp[window] = Offset(it.size.width / density, it.size.height / density)
                }
                .drawBehind {
                    for (p in vm.store.placements) {
                        val a = p.anchor
                        if (a !is Anchor.Window || a.window != window) continue
                        if (vm.dragging == DecorItemId.Placed(p.mushroomId)) continue
                        val m = vm.store.findMushroom(p.mushroomId) ?: continue
                        drawMushroom(vm, m, Offset(p.x * density, p.y * density), p.mirrored, screenHeightDp)
                    }
                },
        ) {
            content()
            if (vm.decorMode) DecorOverlay(vm, state)
        }
    }
}

/** Alle Pilze, die gerade in diesem Fenster zu sehen sind – Fenster-Ebene zuerst, Buttons/Karten darüber. */
private fun visibleDecor(vm: MainViewModel, win: DecorWindowState, density: Float, screenHeightDp: Float): List<VisibleDecor> {
    val behind = mutableListOf<VisibleDecor>()
    val onTop = mutableListOf<VisibleDecor>()
    fun add(id: DecorItemId, m: Mushroom, p: Placement?, center: Offset, mirrored: Boolean, onArea: Boolean) {
        val (w, h) = mushroomSizePx(vm, m, density, screenHeightDp)
        val item = VisibleDecor(id, m, p, Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2), mirrored, onArea)
        if (onArea) onTop += item else behind += item
    }
    for (p in vm.store.placements) {
        val m = vm.store.findMushroom(p.mushroomId) ?: continue
        val origin: Offset = when (val a = p.anchor) {
            is Anchor.Window -> if (a.window == win.window) Offset.Zero else continue
            is Anchor.Area -> if (a.window == win.window) win.areaRect(a.key)?.topLeft ?: continue else continue
            is Anchor.OnTask -> win.areaRect(taskKey(a.taskId))?.topLeft ?: continue
            is Anchor.OnTemplate -> win.areaRect(templateKey(a.templateId))?.topLeft ?: continue
        }
        add(DecorItemId.Placed(m.id), m, p, origin + Offset(p.x * density, p.y * density), p.mirrored, p.anchor !is Anchor.Window)
    }
    // Kopien von Vorlagen-Pilzen auf Aufgabenkarten.
    for (key in win.areas.keys) {
        if (!key.startsWith("task:")) continue
        val task = vm.store.findTask(key.removePrefix("task:").toLong()) ?: continue
        val templateId = task.templateId ?: continue
        val origin = win.areaRect(key)?.topLeft ?: continue
        for (p in vm.store.placements) {
            if (p.anchor != Anchor.OnTemplate(templateId) || p.mushroomId in task.hiddenCopies) continue
            val m = vm.store.findMushroom(p.mushroomId) ?: continue
            add(DecorItemId.Copy(task.id, m.id), m, null, origin + Offset(p.x * density, p.y * density), p.mirrored, true)
        }
    }
    return behind + onTop
}

/** Bearbeitungsebene im Pilz-Modus: fängt alle Berührungen ab, damit keine anderen Buttons reagieren. */
@Composable
private fun DecorOverlay(vm: MainViewModel, win: DecorWindowState) {
    val density = LocalDensity.current.density
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val items = visibleDecor(vm, win, density, screenHeightDp)
    val currentItems by rememberUpdatedState(items)
    var dragItem by remember { mutableStateOf<VisibleDecor?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // Kein State: wird beim Platzieren der Leiste gesetzt und nur von der Gestenerkennung gelesen.
    val toolbarRect = remember { arrayOf(Rect.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(win) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val start = down.position
                    if (toolbarRect[0].contains(start)) return@awaitEachGesture
                    down.consume()
                    val hit = currentItems.lastOrNull { it.rect.contains(start) }
                    var dragging = false
                    var scrolling = false
                    var last = start
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val pos = change.position
                        if (!dragging && !scrolling && (pos - start).getDistance() > viewConfiguration.touchSlop) {
                            if (hit != null && hit.placement != null) {
                                dragging = true
                                dragItem = hit
                                vm.dragging = hit.id
                                vm.selected = hit.id
                            } else {
                                scrolling = true
                            }
                        }
                        if (dragging) dragOffset = pos - start
                        if (scrolling) win.scrollable?.dispatchRawDelta(last.y - pos.y)
                        last = pos
                        change.consume()
                    }
                    val item = dragItem
                    if (dragging && item != null) {
                        vm.commitMove(item, item.rect.center + dragOffset, win, density)
                        dragItem = null
                        dragOffset = Offset.Zero
                    } else if (!scrolling) {
                        val mushroomButton = win.areaRect("pilz")
                        when {
                            hit != null -> vm.selected = hit.id
                            mushroomButton != null && mushroomButton.contains(start) -> vm.open(Screen.Fungarium)
                            else -> vm.selected = null
                        }
                    }
                    // Falls die Geste abgebrochen wurde.
                    if (vm.dragging != null && dragItem == null) vm.dragging = null
                }
            },
    ) {
        val selected = items.firstOrNull { it.id == vm.selected }
        Canvas(Modifier.fillMaxSize()) {
            dragItem?.let {
                drawMushroom(vm, it.mushroom, it.rect.center + dragOffset, it.mirrored, screenHeightDp)
            }
            if (selected != null && dragItem == null) {
                val pad = 4.dp.toPx()
                drawRoundRect(
                    color = White,
                    topLeft = selected.rect.topLeft - Offset(pad, pad),
                    size = selected.rect.inflate(pad).size,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                )
            }
        }
        if (selected != null && dragItem == null) {
            DecorToolbar(vm, win, selected, density) { toolbarRect[0] = it }
        } else {
            toolbarRect[0] = Rect.Zero
        }
    }
}

/** Spiegeln / Ebene / Löschen über (oder unter) dem ausgewählten Pilz. */
@Composable
private fun DecorToolbar(
    vm: MainViewModel,
    win: DecorWindowState,
    item: VisibleDecor,
    density: Float,
    onPlaced: (Rect) -> Unit,
) {
    val anchor = item.placement?.anchor
    val canFlip = item.placement != null
    val canChangeLayer = anchor != null && anchor !is Anchor.OnTemplate &&
        (anchor !is Anchor.Window || win.bestArea(item.rect) != null)
    val gap = 10.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints())
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (item.rect.center.x - p.width / 2).roundToInt()
                    .coerceIn(0, maxOf(0, constraints.maxWidth - p.width))
                val above = item.rect.top - gap.toPx() - p.height
                val y = (if (above >= 0) above else item.rect.bottom + gap.toPx()).roundToInt()
                    .coerceIn(0, maxOf(0, constraints.maxHeight - p.height))
                onPlaced(Rect(x.toFloat(), y.toFloat(), (x + p.width).toFloat(), (y + p.height).toFloat()))
                p.place(x, y)
            }
        },
    ) {
        if (canFlip) ToolButton(painterRes = R.drawable.ic_spiegeln, description = "Spiegeln") { vm.mirror(item.id) }
        if (canChangeLayer) {
            ToolButton(painterRes = R.drawable.ic_ebene, description = "Ebene wechseln") { vm.toggleLayer(item, win, density) }
        }
        ToolButton(vector = Icons.Filled.Delete, description = "Löschen") { vm.deleteDecor(item.id) }
    }
}

@Composable
private fun ToolButton(
    @DrawableRes painterRes: Int? = null,
    vector: ImageVector? = null,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Black)
            .border(1.dp, White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (painterRes != null) {
            Icon(painterResource(painterRes), description, tint = White, modifier = Modifier.size(22.dp))
        } else if (vector != null) {
            Icon(vector, description, tint = White, modifier = Modifier.size(22.dp))
        }
    }
}
