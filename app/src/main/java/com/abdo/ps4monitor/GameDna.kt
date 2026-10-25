package com.abdo.ps4monitor

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Game identities in the running app: decodes the cover into pixels, runs [CoverAnalyzer] + [DnaGenerator] off the main thread, caches the
 * result per game keyed by the cover's fingerprint (a changed cover is re-analysed), and lets the user pin an imported DNA to a game.
 * Everything is local; no network is involved.
 */
object GameDna {
    private const val SAMPLE = 256
    private val cache = DnaCache(160)
    private val last = ConcurrentHashMap<String, DesignDna>()          // latest identity per game, for a flash-free first frame
    private val pins = ConcurrentHashMap<String, DesignDna>()
    /** Bumped when a pin changes so composables re-resolve. */
    var version by mutableIntStateOf(0); private set

    private const val PIN_PREFIX = "dna_pin_"

    fun peek(titleId: String): DesignDna? = pins[titleId] ?: last[titleId]

    /** The pinned DNA of a game (loaded lazily from preferences; malformed entries are ignored but never deleted). */
    fun pinned(titleId: String): DesignDna? {
        pins[titleId]?.let { return it }
        val raw = runCatching { Store.sp.getString(PIN_PREFIX + titleId, null) }.getOrNull() ?: return null
        val d = (DnaJson.parseDna(raw) as? Parsed.Ok)?.value ?: return null
        pins[titleId] = d; return d
    }
    fun pin(titleId: String, dna: DesignDna) {
        pins[titleId] = dna; Store.sp.edit().putString(PIN_PREFIX + titleId, DnaJson.toJson(dna)).apply(); version++
    }
    fun unpin(titleId: String) { pins.remove(titleId); Store.sp.edit().remove(PIN_PREFIX + titleId).apply(); version++ }

    /** A cheap fingerprint of the decoded picture: 16x16 reduction hashed. Only used as the cache validity key. */
    private fun quickFingerprint(b: Bitmap): String {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        val px = IntArray(256); s.getPixels(px, 0, 16, 0, 0, 16, 16)
        if (s !== b) s.recycle()
        var h = 0xcbf29ce484222325UL.toLong()
        for (p in px) { h = h xor (((p shr 20) and 0xF).toLong() or (((p shr 12) and 0xF).toLong() shl 4) or (((p shr 4) and 0xF).toLong() shl 8)); h *= 0x100000001b3L }
        return java.lang.Long.toHexString(h)
    }

    /** Pixels of [b] reduced by halving until its long side is at most [SAMPLE] (intermediate bitmaps are recycled; [b] never is). */
    private fun pixels(b: Bitmap): Triple<IntArray, Int, Int> {
        var cur = b
        while (max(cur.width, cur.height) >= SAMPLE * 2) {
            val n = Bitmap.createScaledBitmap(cur, max(1, cur.width / 2), max(1, cur.height / 2), true)
            if (cur !== b) cur.recycle()
            cur = n
        }
        val w = cur.width; val h = cur.height
        val px = IntArray(w * h); cur.getPixels(px, 0, w, 0, 0, w, h)
        if (cur !== b) cur.recycle()
        return Triple(px, w, h)
    }

    /** Blocking: call from a background dispatcher. Never throws; a missing or unreadable cover gives the neutral identity. */
    fun resolve(titleId: String, title: String, cover: Bitmap?): DesignDna {
        pinned(titleId)?.let { return it }
        if (cover == null || cover.isRecycled || cover.width <= 0 || cover.height <= 0) return DnaGenerator.fallback(titleId, title)
        return try {
            val fp = quickFingerprint(cover)
            cache.getOrCompute(titleId, fp) {
                val (px, w, h) = pixels(cover)
                DnaGenerator.generate(CoverAnalyzer.analyze(px, w, h), titleId, title)
            }.also { last[titleId] = it }
        } catch (e: Exception) { DnaGenerator.fallback(titleId, title) } catch (e: OutOfMemoryError) { DnaGenerator.fallback(titleId, title) }
    }

    fun forget(titleId: String) { cache.invalidate(titleId); last.remove(titleId) }
}

/**
 * The identity of a game for composition: null while the cover is not there yet (the page then uses the app look and switches when this
 * becomes available). Re-resolved when the cover changes or a pin is added / removed.
 */
@Composable fun rememberGameDna(titleId: String, title: String, cover: Bitmap?): State<DesignDna?> {
    val v = GameDna.version
    return produceState<DesignDna?>(GameDna.peek(titleId), titleId, cover, v) {
        if (cover == null && GameDna.pinned(titleId) == null) return@produceState
        value = withContext(Dispatchers.Default) { GameDna.resolve(titleId, title, cover) }
    }
}
