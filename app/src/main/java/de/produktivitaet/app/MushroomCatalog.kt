package de.produktivitaet.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

data class Species(val id: String, val name: String)

/**
 * Alle Pilzarten = alle Bilder im Ordner app/src/main/assets/pilze.
 * Der Dateiname ist der Name ("Grüner_Knollenblätterpilz.png" → "Grüner Knollenblätterpilz").
 */
class MushroomCatalog(private val context: Context) {
    val species: List<Species> = (context.assets.list(DIR) ?: emptyArray())
        .filter { it.substringAfterLast('.').lowercase() in IMAGE_TYPES }
        .map { Species(it, it.substringBeforeLast('.').replace('_', ' ')) }
        .sortedWith(compareBy(Collator.getInstance(Locale.GERMAN)) { it.name })

    private val byId = species.associateBy { it.id }

    // Größe in KB; begrenzt den Speicher für geladene Bilder.
    private val cache = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun find(id: String): Species? = byId[id]

    fun roll(random: Random = Random.Default): MushroomRoll? =
        if (species.isEmpty()) null else MushroomRoll(species.random(random).id, Rewards.rollSize(random))

    private val aspects = HashMap<String, Float>()

    /** Breite / Höhe des Bildes (liest nur den Dateikopf). */
    fun aspect(id: String): Float = aspects.getOrPut(id) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("$DIR/$id").use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 0.8f
        } catch (e: Exception) {
            0.8f
        }
    }

    fun cached(id: String, maxPx: Int): Bitmap? = cache.get("$id@$maxPx")

    /** Lädt das Bild so klein wie möglich, aber mindestens [maxPx] groß (falls das Original das hergibt). */
    fun load(id: String, maxPx: Int): Bitmap? {
        cached(id, maxPx)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("$DIR/$id").use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open("$DIR/$id").use { BitmapFactory.decodeStream(it, null, options) }
                ?.also { cache.put("$id@$maxPx", it) }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val DIR = "pilze"
        private val IMAGE_TYPES = setOf("png", "webp", "jpg", "jpeg")
    }
}
