package app.ranger.ui.art

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import app.ranger.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The painted artwork in assets (portraits, medals, the outpost, banners, icons, crests), decoded at about the size it's
 * shown and kept in a small cache, so screens full of paintings don't hold every one at full size.
 */
object Paintings {
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    /** Each file's own width, read once. */
    private val widths = HashMap<String, Int>()
    private val missing = HashSet<String>()

    private fun width(ctx: Context, path: String): Int? {
        synchronized(widths) { widths[path]?.let { return it }; if (path in missing) return null }
        val w = runCatching {
            ctx.assets.open(path).use { BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeStream(it, null, this) }.outWidth }
        }.getOrNull()?.takeIf { it > 0 }
        synchronized(widths) { if (w != null) widths[path] = w else missing += path }
        return w
    }

    private fun sample(src: Int, px: Int): Int { var s = 1; while (src / (s * 2) >= px) s *= 2; return s }

    /** Whether the painting is there at all, so callers can fall back to drawing. */
    fun has(ctx: Context, path: String) = width(ctx, path) != null

    fun cached(path: String, px: Int): ImageBitmap? {
        val w = synchronized(widths) { widths[path] } ?: return null
        return cache.get("$path@${sample(w, px)}")
    }

    fun load(ctx: Context, path: String, px: Int): ImageBitmap? {
        val w = width(ctx, path) ?: return null
        val s = sample(w, px)
        cache.get("$path@$s")?.let { return it }
        val b = runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } }.getOrNull() ?: return null
        return b.asImageBitmap().also { cache.put("$path@$s", it) }
    }
}

/** A painting from assets at about [px] wide; null while it loads or if it isn't there. */
@Composable
fun rememberPainting(path: String, px: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState(Paintings.cached(path, px), path, px) {
        if (value == null) value = withContext(Dispatchers.IO) { Paintings.load(ctx, path, px) }
    }
    return img
}

/** Whether a painting ships with the app; checked once per path. */
@Composable
fun hasPainting(path: String): Boolean {
    val ctx = LocalContext.current
    return remember(path) { Paintings.has(ctx, path) }
}

/** Warm sepia: what a painting looks like while it's only pencilled into the journal. */
val Sepia = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    .30f, .52f, .12f, 0f, 18f,
    .26f, .46f, .10f, 0f, 10f,
    .20f, .36f, .08f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)))

/** A painting filling [modifier], cropped to fit; paper colour while it loads. */
@Composable
fun Painted(path: String, modifier: Modifier, alignment: Alignment = Alignment.Center, alpha: Float = 1f) {
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx().roundToInt().coerceAtLeast(1) }
        val img = rememberPainting(path, px)
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = alignment, alpha = alpha)
    }
}

/**
 * A painted banner across the top of a screen or sheet: the picture fades into the page at its foot, like a vignette
 * pasted into the journal.
 */
@Composable
fun Banner(key: String, modifier: Modifier, fade: Color? = C.bg) {
    val path = "banners/$key.webp"
    if (!hasPainting(path)) return
    Box(modifier.drawWithContent {
        drawContent()
        if (fade != null) drawRect(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to fade))
    }) { Painted(path, Modifier.fillMaxSize()) }
}

/** A banner at the head of a sheet: a framed picture with rounded corners. */
@Composable
fun SheetBanner(key: String) = Banner(key, Modifier.fillMaxWidth().aspectRatio(2.2f).clip(app.ranger.ui.theme.Sh.Lg)
    .border(1.dp, C.line, app.ranger.ui.theme.Sh.Lg), fade = null)

/** An icon inked by hand: an alpha mask from assets, tinted [color]; [fallback] draws it in code if it's missing. */
@Composable
fun InkIcon(name: String, color: Color, modifier: Modifier, fallback: @Composable BoxScope.() -> Unit) {
    val path = "icons/$name.png"
    if (!hasPainting(path)) { Box(modifier) { fallback() }; return }
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val img = rememberPainting(path, px)
        if (img != null) Image(img, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(color))
    }
}
