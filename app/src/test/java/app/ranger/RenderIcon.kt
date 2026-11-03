package app.ranger

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The launcher icon as a 512 px squircle for the README (`-Prender --tests '*RenderIcon*'`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderIcon {
    @Test fun logo() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val n = 512
        val full = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        // the adaptive layers are 108 dp of which the middle 72 show; draw them oversized and crop
        val over = (n * 108f / 72f).toInt(); val off = (n - over) / 2
        for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            val d = ContextCompat.getDrawable(ctx, id)!!
            d.setBounds(off, off, off + over, off + over); d.draw(c)
        }
        val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        oc.drawPath(Path().apply { addRoundRect(0f, 0f, n.toFloat(), n.toFloat(), n * .23f, n * .23f, Path.Direction.CW) }, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        oc.drawBitmap(full, 0f, 0f, p)
        val dir = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        File(dir, "logo.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
