package de.produktivitaet.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FungariumScreen(vm: MainViewModel) {
    val store = vm.store
    val earned = store.mushrooms.groupingBy { it.species }.eachCount()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val showBeings = tab == 1
    val list = if (showBeings) vm.catalog.beings else vm.catalog.mushrooms
    val found = list.count { (earned[it.id] ?: 0) > 0 }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Fungarium", vm::back) {
            if (!showBeings) {
                // Schalter: welche Größe beim Herausnehmen zuerst kommt.
                Box(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, White, CircleShape)
                        .clickable { store.toggleBigFirst() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(if (store.bigFirst) "Große zuerst" else "Kleine zuerst", fontSize = 13.sp)
                }
            }
            if (!vm.decorMode) {
                IconButton(onClick = vm::enterDecorMode) { Icon(Icons.Filled.Edit, "Pilz-Modus", tint = White) }
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Black, contentColor = White) {
            Tab(
                selected = !showBeings,
                onClick = { tab = 0 },
                text = { Text("Pilze", fontSize = 16.sp) },
                selectedContentColor = White,
                unselectedContentColor = Muted,
            )
            Tab(
                selected = showBeings,
                onClick = { tab = 1 },
                text = { Text("Pilz-Wesen", fontSize = 16.sp) },
                selectedContentColor = White,
                unselectedContentColor = Muted,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GoalsHeader(vm)
            }
            if (vm.decorMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Pilz-Modus: Tippe ein Sprite an, um es ins Fenster zu setzen. Mit dem Haken im Fenster übernimmst du alles.",
                        fontSize = 14.sp,
                        color = White,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (showBeings) "$found von ${list.size} Pilz-Wesen gefunden" else "$found von ${list.size} Pilzarten gefunden",
                    color = Muted,
                    fontSize = 15.sp,
                )
            }
            items(list, key = { it.id }) { species ->
                val total = earned[species.id] ?: 0
                val available = store.availableCount(species.id)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(CardShape)
                        .clickable(enabled = available > 0) { vm.pickFromFungarium(species.id) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .border(1.dp, if (available > 0) Muted else Dim, CardShape)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MushroomImage(
                            vm.catalog,
                            species.id,
                            Modifier.fillMaxSize().alpha(if (total > 0 && available == 0) 0.35f else 1f),
                            silhouette = total == 0,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (total > 0) species.name else "???",
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (total > 0) White else Dim,
                        maxLines = 2,
                    )
                    if (total > 0 && species.latin != null) {
                        Text(
                            species.latin,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = Muted,
                            maxLines = 2,
                        )
                    }
                    if (total > 0) {
                        Text(
                            when {
                                !showBeings -> "× $available"
                                available > 0 -> "im Fungarium"
                                else -> "platziert"
                            },
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
            }
            if (store.placements.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlineButton(
                        "Alles zurücksetzen",
                        { confirmReset = true },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Alles zurücksetzen?",
            text = "Alle Pilze und Pilz-Wesen werden aus allen Fenstern, Aufgaben und Vorlagen entfernt und kommen zurück ins Fungarium.",
            confirmText = "Zurücksetzen",
            onConfirm = {
                vm.resetAllDecor()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** Kopf des Fungariums: Fortschritt zum Pilz des Tages und zum Pilz-Wesen der Woche. */
@Composable
private fun GoalsHeader(vm: MainViewModel) {
    val store = vm.store
    val (day, week) = store.currentProgress()
    Column(
        Modifier.fillMaxWidth().border(1.dp, White, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GoalRow(
            vm,
            title = "Pilz des Tages",
            species = store.nextMushroom?.species,
            goal = store.dailyGoal,
            progress = day,
            done = store.rewardedToday(),
            doneText = "Heute schon erspielt",
        )
        GoalRow(
            vm,
            title = "Pilz-Wesen der Woche",
            species = store.nextBeing,
            goal = store.weeklyGoal,
            progress = week,
            done = store.rewardedThisWeek(),
            doneText = "Diese Woche schon erspielt",
        )
    }
}

@Composable
private fun GoalRow(
    vm: MainViewModel,
    title: String,
    species: String?,
    goal: Goal,
    progress: Int,
    done: Boolean,
    doneText: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp)) {
            if (species != null) MushroomImage(vm.catalog, species, Modifier.fillMaxSize(), silhouette = true)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ProgressBar(if (done) 1f else progress.toFloat() / goal.value)
            Spacer(Modifier.height(4.dp))
            Text(if (done) "$doneText ✓" else goal.format(progress), color = Muted, fontSize = 13.sp)
        }
    }
}

private val SporeColors = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF),
    Color(0xFFC77DFF), Color(0xFFFF9F45), Color(0xFF5EEAD4), Color(0xFFF9A8D4),
)

private class Spore(
    val angle: Float,
    val speed: Float,
    val radius: Float,
    val color: Color,
    val wobble: Float,
    val phase: Float,
    val life: Float,
)

private class Burst(val startNanos: Long, val origin: Offset, val spores: List<Spore>)

private fun burst(startNanos: Long, origin: Offset, density: Float, count: Int, power: Float = 1f): Burst {
    val r = Random(startNanos + origin.hashCode())
    return Burst(startNanos, origin, List(count) {
        Spore(
            angle = (r.nextFloat() * 2 * PI).toFloat(),
            speed = (180 + r.nextFloat() * 520) * density * power,
            radius = (2f + r.nextFloat() * 4.5f) * density,
            color = SporeColors[r.nextInt(SporeColors.size)],
            wobble = (6 + r.nextFloat() * 18) * density,
            phase = r.nextFloat() * 6f,
            life = 2.2f + r.nextFloat() * 1.6f,
        )
    })
}

/**
 * Nach jedem erledigten Schritt: Sporen, darunter Pilz und Pilz-Wesen als Silhouette mit ihren
 * Balken. Beide Balken wachsen um den neuen Fortschritt; ist einer voll, füllt sich die Silhouette
 * mit dem erspielten Sprite und die Sporen fliegen weiter, bis der Bildschirm berührt wird.
 */
@Composable
fun CelebrationScreen(vm: MainViewModel, reward: Reward) {
    val density = LocalDensity.current.density
    val dayAnim = remember { Animatable(barFraction(reward.day, before = true)) }
    val weekAnim = remember { Animatable(barFraction(reward.week, before = true)) }
    var mushroomShown by remember { mutableStateOf(false) }
    var beingShown by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(0L) }
    val bursts = remember { mutableStateListOf<Burst>() }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    var mushroomCenter by remember { mutableStateOf(Offset.Zero) }
    var beingCenter by remember { mutableStateOf(Offset.Zero) }

    // Uhr für die Sporen; alte Wolken werden aufgeräumt.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now = it }
            bursts.removeAll { (now - it.startNanos) > 4_500_000_000L }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos {
            bursts.add(burst(it, mushroomCenter - containerOffset, density, 50))
            bursts.add(burst(it, beingCenter - containerOffset, density, 50))
        }
        delay(300)
        launch {
            dayAnim.animateTo(barFraction(reward.day, before = false), tween(1200, easing = FastOutSlowInEasing))
            if (reward.mushroom != null) {
                mushroomShown = true
                withFrameNanos { bursts.add(burst(it, mushroomCenter - containerOffset, density, 110)) }
            }
        }
        launch {
            weekAnim.animateTo(barFraction(reward.week, before = false), tween(1200, easing = FastOutSlowInEasing))
            if (reward.being != null) {
                beingShown = true
                withFrameNanos { bursts.add(burst(it, beingCenter - containerOffset, density, 110)) }
            }
        }
        delay(1600)
        finished = true
    }

    // Solange etwas Neues gezeigt wird, sporen die Sprites weiter – bis der Bildschirm berührt wird.
    LaunchedEffect(mushroomShown, beingShown) {
        if (!mushroomShown && !beingShown) return@LaunchedEffect
        while (true) {
            delay(700)
            withFrameNanos {
                if (mushroomShown) bursts.add(burst(it, mushroomCenter - containerOffset, density, 28, power = 0.6f))
                if (beingShown) bursts.add(burst(it, beingCenter - containerOffset, density, 28, power = 0.6f))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerOffset = it.positionInRoot() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.back() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("Geschafft!", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(0.6f))
            RewardRow(
                vm = vm,
                title = "Pilz des Tages",
                progress = reward.day,
                fraction = dayAnim.value,
                earned = reward.mushroom,
                shown = mushroomShown,
                silhouette = reward.mushroom?.species ?: reward.nextMushroom,
                emptyText = null,
                onCenter = { mushroomCenter = it },
            )
            Spacer(Modifier.height(28.dp))
            RewardRow(
                vm = vm,
                title = "Pilz-Wesen der Woche",
                progress = reward.week,
                fraction = weekAnim.value,
                earned = reward.being,
                shown = beingShown,
                silhouette = reward.being?.species ?: reward.nextBeing,
                emptyText = "Alle Pilz-Wesen gesammelt!",
                onCenter = { beingCenter = it },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Tippen zum Schließen",
                color = Dim,
                fontSize = 14.sp,
                modifier = Modifier.alpha(if (finished) 1f else 0f),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            bursts.forEach { b ->
                val t = (now - b.startNanos) / 1_000_000_000f
                if (t < 0f) return@forEach
                b.spores.forEach { s ->
                    if (t > s.life) return@forEach
                    // Schnell heraus, dann abbremsen und langsam sinken – wie Sporenstaub.
                    val travel = s.speed * (1 - exp(-2.2f * t)) / 2.2f
                    val x = b.origin.x + cos(s.angle) * travel + sin(t * 3f + s.phase) * s.wobble
                    val y = b.origin.y + sin(s.angle) * travel + 40f * density * t * t
                    val alpha = (1f - t / s.life).coerceIn(0f, 1f)
                    drawCircle(s.color.copy(alpha = alpha * 0.25f), s.radius * 2.4f, Offset(x, y))
                    drawCircle(s.color.copy(alpha = alpha), s.radius, Offset(x, y))
                }
            }
        }
    }
}

/** Balkenstand: voll, wenn die Belohnung für diesen Zeitraum schon vorher erspielt war. */
private fun barFraction(p: GoalProgress, before: Boolean): Float {
    if (p.alreadyRewarded) return 1f
    val value = if (before) p.before else p.after
    return (value.toFloat() / p.goal.value).coerceIn(0f, 1f)
}

@Composable
private fun RewardRow(
    vm: MainViewModel,
    title: String,
    progress: GoalProgress,
    fraction: Float,
    earned: Mushroom?,
    shown: Boolean,
    silhouette: String?,
    emptyText: String?,
    onCenter: (Offset) -> Unit,
    imageSize: Dp = 150.dp,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(shown) {
        if (shown) {
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(imageSize)
                .onGloballyPositioned { onCenter(it.boundsInRoot().center) }
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            contentAlignment = Alignment.Center,
        ) {
            if (silhouette != null) {
                Crossfade(targetState = shown, label = "sprite") { revealed ->
                    MushroomImage(vm.catalog, silhouette, Modifier.fillMaxSize(), silhouette = !revealed, maxPx = 500)
                }
            } else if (emptyText != null) {
                Text(emptyText, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (shown && earned != null) "Neu: ${vm.catalog.find(earned.species)?.name ?: "?"}" else title,
            fontSize = 17.sp,
            fontWeight = if (shown) FontWeight.Bold else FontWeight.Normal,
            color = if (shown) White else Muted,
            textAlign = TextAlign.Center,
        )
        if (shown && earned != null && !vm.catalog.isBeing(earned.species)) {
            Text(Rewards.SIZE_NAMES[earned.size - 1], fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(fraction)
        Spacer(Modifier.height(6.dp))
        Text(
            if (progress.alreadyRewarded) "${progress.goal.format(progress.after)} ✓" else progress.goal.format(progress.after),
            color = Muted,
            fontSize = 14.sp,
        )
    }
}
