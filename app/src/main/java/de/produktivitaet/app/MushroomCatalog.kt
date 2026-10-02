package de.produktivitaet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

enum class SpriteKind { MUSHROOM, BEING }

/**
 * Eine Sprite-Art. Pilze heißen "Name_Lateinischer Name.png", Pilz-Wesen nur "Name.png"
 * (also ohne Unterstrich).
 */
data class Species(val id: String, val name: String, val latin: String?, val kind: SpriteKind)

/** Weicher Schatten zu einem Sprite; [pad] = Rand um das Sprite herum, relativ zu seiner Breite/Höhe. */
class SpriteShadow(val bitmap: Bitmap, val pad: Float)

/** Alle Sprites = alle Bilder im Ordner app/src/main/assets/sprites. */
class MushroomCatalog(private val context: Context) {
    val species: List<Species> = (context.assets.list(DIR) ?: emptyArray())
        .filter { it.substringAfterLast('.').lowercase() in IMAGE_TYPES }
        .map { file ->
            val base = file.substringBeforeLast('.')
            val split = base.indexOf('_')
            if (split >= 0) {
                Species(file, base.substring(0, split).trim(), base.substring(split + 1).replace('_', ' ').trim(), SpriteKind.MUSHROOM)
            } else {
                Species(file, base.trim(), null, SpriteKind.BEING)
            }
        }
        .sortedWith(compareBy(Collator.getInstance(Locale.GERMAN)) { it.name })

    val mushrooms: List<Species> = species.filter { it.kind == SpriteKind.MUSHROOM }
    val beings: List<Species> = species.filter { it.kind == SpriteKind.BEING }

    private val byId = species.associateBy { it.id }

    // Größe in KB; begrenzt den Speicher für geladene Bilder.
    private val cache = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val shadows = HashMap<String, SpriteShadow>()
    private val aspects = HashMap<String, Float>()

    fun find(id: String): Species? = byId[id]

    fun isBeing(id: String): Boolean = byId[id]?.kind == SpriteKind.BEING

    fun rollMushroom(random: Random = Random.Default): MushroomRoll? =
        if (mushrooms.isEmpty()) null else MushroomRoll(mushrooms.random(random).id, Rewards.rollSize(random))

    /** Breite / Höhe des sichtbaren Inhalts (ohne durchsichtige Ränder). */
    fun aspect(id: String): Float = aspects.getOrPut(id) {
        val small = load(id, 128) ?: return@getOrPut 0.8f
        small.width.toFloat() / small.height
    }

    fun cached(id: String, maxPx: Int): Bitmap? = cache.get("$id@$maxPx")

    /**
     * Lädt das Bild mindestens [maxPx] groß (falls das Original das hergibt) und schneidet
     * durchsichtige Ränder ab – so sind alle Sprites unabhängig von ihrem Bildrand gleich groß.
     */
    fun load(id: String, maxPx: Int): Bitmap? {
        cached(id, maxPx)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("$DIR/$id").use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.assets.open("$DIR/$id").use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            trim(raw).also { cache.put("$id@$maxPx", it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Weicher, ringsum gleichmäßiger Schatten (einmal pro Sprite berechnet, klein und unscharf). */
    fun shadow(id: String): SpriteShadow? {
        shadows[id]?.let { return it }
        val source = load(id, 128) ?: return null
        val longest = maxOf(source.width, source.height)
        val radius = longest * 0.06f
        val padPx = (radius * 2).toInt()
        val alpha = source.extractAlpha()
        val out = Bitmap.createBitmap(source.width + 2 * padPx, source.height + 2 * padPx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(out).drawBitmap(alpha, padPx.toFloat(), padPx.toFloat(), paint)
        alpha.recycle()
        return SpriteShadow(out, padPx.toFloat() / longest).also { shadows[id] = it }
    }

    private fun trim(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((pixels[row + x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return bitmap
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    companion object {
        private const val DIR = "sprites"
        private val IMAGE_TYPES = setOf("png", "webp")
    }
}
