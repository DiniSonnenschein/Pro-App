package de.produktivitaet.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Die Farbpalette: 20 Farbtöne mit je 5 Helligkeitsstufen (von hell nach dunkel), gedeckt und
 * an Pilze und Herbst angelehnt. Eine Farbe wird über ihren Index (0–99) gespeichert –
 * die Reihenfolge darf sich deshalb nie ändern, neue Farben nur hinten anhängen.
 */
object Palette {
    private val rows: List<List<Long>> = listOf(
        // Grau: Weiß … Schwarz
        listOf(0xFFFFFFFF, 0xFFC9C5BF, 0xFF8E8983, 0xFF4A4743, 0xFF000000),
        // Fliegenpilz-Rot (mit dem Dunkelrot der App)
        listOf(0xFFEFC6BE, 0xFFD58C7E, 0xFFB0544A, 0xFF8E1B1B, 0xFF5C1414),
        // Rost / Ziegel
        listOf(0xFFF1D0BB, 0xFFDDA07A, 0xFFBA6C45, 0xFF8A4427, 0xFF572B18),
        // Kürbis
        listOf(0xFFF5DCBA, 0xFFE8B275, 0xFFCF8A3F, 0xFF9C6224, 0xFF643F17),
        // Pfifferling-Gold
        listOf(0xFFF3E4B9, 0xFFE2C67C, 0xFFC49E44, 0xFF8F722A, 0xFF5B481B),
        // Senf / Ocker
        listOf(0xFFE8E2B9, 0xFFCCC182, 0xFFA69A53, 0xFF767036, 0xFF4A4622),
        // Moos
        listOf(0xFFDCE3C4, 0xFFB0BF8C, 0xFF819460, 0xFF586B3F, 0xFF364224),
        // Waldgrün (mit dem Dunkelgrün der App)
        listOf(0xFFCDE3D3, 0xFF95C3A3, 0xFF5A9870, 0xFF1F6B3A, 0xFF13402A),
        // Salbei / Tanne
        listOf(0xFFD0E2DC, 0xFF9BC0B6, 0xFF659287, 0xFF3F675C, 0xFF243E37),
        // Petrol
        listOf(0xFFCDE2E4, 0xFF8FBDC4, 0xFF578C96, 0xFF326069, 0xFF1B3C42),
        // Himmelblau (mit dem Hellblau der App)
        listOf(0xFFDDEFF7, 0xFF8FD3F4, 0xFF63A9CC, 0xFF3B7C9B, 0xFF214D63),
        // Taubenblau
        listOf(0xFFD5DEE8, 0xFFA6B6C8, 0xFF768BA3, 0xFF506377, 0xFF313C4A),
        // Schiefer / Indigo
        listOf(0xFFD9D9EA, 0xFFADADCF, 0xFF8180AF, 0xFF585785, 0xFF34335A),
        // Veilchen
        listOf(0xFFE0D3EA, 0xFFBDA5D2, 0xFF9476B2, 0xFF6A5088, 0xFF412F57),
        // Pflaume
        listOf(0xFFE8D0DE, 0xFFC99AB6, 0xFFA0678B, 0xFF744363, 0xFF47283D),
        // Altrosa / Blut-Helmling
        listOf(0xFFF2D6D4, 0xFFDEAAA8, 0xFFBF7A79, 0xFF925356, 0xFF5D3235),
        // Lachs / Rosen-Seitling
        listOf(0xFFF5D8CC, 0xFFEAB09D, 0xFFD48470, 0xFFA85D4C, 0xFF6C392C),
        // Champignon-Beige
        listOf(0xFFF3EBDE, 0xFFE0D0B6, 0xFFC2AB89, 0xFF957F5F, 0xFF5E4F3B),
        // Holz
        listOf(0xFFE8D7C5, 0xFFC9AA8B, 0xFF9F7B58, 0xFF6F5338, 0xFF433222),
        // Morchel / Umbra
        listOf(0xFFE3DCCF, 0xFFB9A98D, 0xFF897453, 0xFF5B4B35, 0xFF352C20),
    )

    const val PER_ROW = 5

    val colors: List<Color> = rows.flatten().map { Color(it) }

    val size: Int get() = colors.size

    /** Von Anfang an freigeschaltet: Weiß, Schwarz und die Farben der App-Buttons. */
    val STARTERS: Set<Int> = setOf(0, 4, 1 * PER_ROW + 3, 7 * PER_ROW + 3, 10 * PER_ROW + 1)

    fun color(index: Int): Color = colors.getOrElse(index) { Color.Black }
}

/** Schrift-/Symbolfarbe, die auf [background] gut lesbar ist. */
fun onColor(background: Color): Color = if (background.luminance() > 0.42f) Black else White

/** Eine Einfärbung: Button, Karte oder Fensterhintergrund ([anchor]) bekommt Palettenfarbe [color]. */
data class Tint(val anchor: Anchor, val color: Int)

/** Chance auf eine neue Farbe nach einem erledigten Schritt (für den Nutzer unsichtbar). */
fun colorChance(minutes: Int, doneTodayBefore: Int): Double =
    (0.10 + 0.001 * minutes + 0.01 * doneTodayBefore).coerceAtMost(1.0)
