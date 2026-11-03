package app.ranger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ranger.R

/**
 * A ranger's field journal: parchment, iron-gall ink, a vermilion seal, forest green and river blue.
 * The outpost scene at the top is the only dark thing: a camp at night under the real sky.
 */
@Immutable
object C {
    val bg = Color(0xFFEFE3C8)
    val page = Color(0xFFF6EDD8)
    val deep = Color(0xFFE5D5B2)
    val line = Color(0xFFCDB88E)
    val ink = Color(0xFF2B2118)
    val soft = Color(0xFF5A4A38)
    val faint = Color(0xFF8C7A5D)
    val seal = Color(0xFF9E3B26)
    val sealSoft = Color(0xFFEBCDBB)
    val forest = Color(0xFF3D5E34)
    val forestSoft = Color(0xFFD6DDBF)
    val river = Color(0xFF2F5D7C)
    val riverSoft = Color(0xFFCFDCDD)
    val gold = Color(0xFFA8812A)
    val goldSoft = Color(0xFFEEDDA8)
    val night = Color(0xFF161B2B)
    val night2 = Color(0xFF232A40)
    val fire = Color(0xFFF2A53A)
    val ember = Color(0xFFD9542B)
    val onDark = Color(0xFFF1E6CC)
    val onDarkSoft = Color(0xFFB9AE95)
    /** Heart-rate zones 1–5. */
    val zones = listOf(Color(0xFF8FA6A3), Color(0xFF5F8A57), Color(0xFFC59B3A), Color(0xFFC46A31), Color(0xFF9E3B26))
}

/** Cormorant for titles: a scribe's hand set in type. */
val Scribe = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold),
    Font(R.font.cormorant_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    Font(R.font.alegreya_sans, FontWeight.Normal),
    Font(R.font.alegreya_sans_medium, FontWeight.Medium),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

object Sh {
    val Xl = RoundedCornerShape(24.dp)
    val Lg = RoundedCornerShape(18.dp)
    val Md = RoundedCornerShape(12.dp)
    val Sm = RoundedCornerShape(8.dp)
}

object T {
    val Display = TextStyle(fontFamily = Scribe, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, color = C.ink)
    val Title = TextStyle(fontFamily = Scribe, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, color = C.ink)
    val Name = TextStyle(fontFamily = Scribe, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, color = C.ink)
    val Lore = TextStyle(fontFamily = Scribe, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 23.sp, color = C.soft)
    val Eyebrow = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.14.em, color = C.faint)
    val Strong = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, color = C.ink)
    val Body = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp, color = C.ink)
    val Muted = TextStyle(fontFamily = Sans, fontSize = 14.5.sp, lineHeight = 19.sp, color = C.soft)
    val Small = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp, color = C.faint)
    val Button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
    val Number = TextStyle(fontFamily = Scribe, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp, color = C.ink, fontFeatureSettings = "lnum, tnum")
    val NumberSm = TextStyle(fontFamily = Scribe, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp, color = C.ink, fontFeatureSettings = "lnum, tnum")
}

@Composable
fun RangerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.seal, onPrimary = Color.White, background = C.bg, surface = C.page, onSurface = C.ink,
            onBackground = C.ink, surfaceContainer = C.page, surfaceContainerHigh = C.deep, outline = C.line, error = C.seal,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}
