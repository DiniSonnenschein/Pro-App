package de.produktivitaet.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FungariumScreen(vm: MainViewModel) {
    val store = vm.store
    val counts = store.mushrooms.groupingBy { it.species }.eachCount()
    val found = vm.catalog.species.count { (counts[it.id] ?: 0) > 0 }

    Column(Modifier.fillMaxSize()) {
        TopBar("Fungarium", vm::back)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                NextMushroomHeader(vm)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "$found von ${vm.catalog.species.size} Pilzarten gefunden",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(vm.catalog.species, key = { it.id }) { species ->
                val count = counts[species.id] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .border(1.dp, if (count > 0) Muted else Dim, CardShape)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MushroomImage(vm.catalog, species.id, Modifier.fillMaxSize(), silhouette = count == 0)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (count > 0) species.name else "???",
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (count > 0) White else Dim,
                        maxLines = 2,
                    )
                    if (count > 0) Text("× $count", fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}

/** Kopf des Fungariums: nächster Pilz als Silhouette und Fortschritt dorthin. */
@Composable
private fun NextMushroomHeader(vm: MainViewModel) {
    val total = vm.store.totalPoints
    val count = Rewards.mushroomsFor(total)
    val segStart = Rewards.threshold(count)
    val segEnd = Rewards.threshold(count + 1)
    Row(
        Modifier.fillMaxWidth().border(1.dp, White, CardShape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp)) {
            vm.store.nextMushroom?.let { MushroomImage(vm.catalog, it.species, Modifier.fillMaxSize(), silhouette = true) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Nächster Pilz", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ProgressBar((total - segStart).toFloat() / (segEnd - segStart))
            Spacer(Modifier.height(6.dp))
            Text("${total - segStart} / ${segEnd - segStart} Punkte · insgesamt $total", color = Muted, fontSize = 13.sp)
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

private fun burst(startNanos: Long, origin: Offset, density: Float, count: Int): Burst {
    val r = Random(startNanos)
    return Burst(startNanos, origin, List(count) {
        Spore(
            angle = (r.nextFloat() * 2 * PI).toFloat(),
            speed = (180 + r.nextFloat() * 520) * density,
            radius = (2f + r.nextFloat() * 4.5f) * density,
            color = SporeColors[r.nextInt(SporeColors.size)],
            wobble = (6 + r.nextFloat() * 18) * density,
            phase = r.nextFloat() * 6f,
            life = 2.2f + r.nextFloat() * 1.6f,
        )
    })
}

/** Nach dem Erledigen: bunte Sporen, nächster Pilz als Silhouette, animierter Fortschrittsbalken. */
@Composable
fun CelebrationScreen(vm: MainViewModel, reward: Reward) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val startCount = remember { Rewards.mushroomsFor(reward.startTotal) }
    val progress = remember { Animatable(reward.startTotal.toFloat()) }
    val popScale = remember { Animatable(1f) }
    var revealed by remember { mutableIntStateOf(0) }
    var revealing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(0L) }
    val bursts = remember { mutableStateListOf<Burst>() }
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    var mushroomCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    fun origin() = mushroomCenterInRoot - containerOffset

    // Uhr für die Sporen.
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now = it }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { bursts.add(burst(it, origin(), density, 70)) }
        delay(500)
        reward.earned.forEachIndexed { i, _ ->
            val target = Rewards.threshold(startCount + i + 1).toFloat()
            progress.animateTo(target, tween(durationFor(target - progress.value), easing = FastOutSlowInEasing))
            revealing = true
            withFrameNanos { bursts.add(burst(it, origin(), density, 110)) }
            popScale.snapTo(0.55f)
            popScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
            delay(1600)
            revealed = i + 1
            revealing = false
        }
        val end = reward.endTotal.toFloat()
        progress.animateTo(end, tween(durationFor(end - progress.value), easing = FastOutSlowInEasing))
    }

    val count = startCount + revealed
    val segStart = Rewards.threshold(count)
    val segEnd = Rewards.threshold(count + 1)
    val fraction = (progress.value - segStart) / (segEnd - segStart)
    val shown: Mushroom? = if (revealing) reward.earned[revealed] else null
    val silhouetteSpecies = reward.earned.getOrNull(revealed)?.species ?: reward.next?.species

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerOffset = it.positionInRoot() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text("+${reward.points} Punkte", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(220.dp)
                    .onGloballyPositioned { mushroomCenterInRoot = it.boundsInRoot().center }
                    .graphicsLayer {
                        scaleX = popScale.value
                        scaleY = popScale.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = shown?.species to (shown == null), label = "pilz") { (species, silhouette) ->
                    val id = species ?: silhouetteSpecies
                    if (id != null) {
                        MushroomImage(vm.catalog, id, Modifier.fillMaxSize(), silhouette = silhouette, maxPx = 600)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (shown != null) {
                    "Neuer Pilz: ${vm.catalog.find(shown.species)?.name ?: "?"}"
                } else {
                    "Nächster Pilz"
                },
                fontSize = 20.sp,
                fontWeight = if (shown != null) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = if (shown != null) White else Muted,
            )
            if (shown != null) {
                Text(Rewards.SIZE_NAMES[shown.size - 1], fontSize = 15.sp, color = Muted)
            }
            Spacer(Modifier.height(24.dp))
            ProgressBar(fraction)
            Spacer(Modifier.height(8.dp))
            Text(
                "${(progress.value - segStart).toInt().coerceIn(0, segEnd - segStart)} / ${segEnd - segStart} Punkte",
                color = Muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            OutlineButton("Weiter", vm::back, Modifier.fillMaxWidth())
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

private fun durationFor(points: Float): Int = (points * 35).toInt().coerceIn(500, 1600)
