package app.ranger.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ranger.ui.LocalRu
import app.ranger.ui.art.leaf
import app.ranger.ui.art.flourish
import app.ranger.ui.art.parchment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import app.ranger.ui.theme.C
import app.ranger.ui.theme.Sh
import app.ranger.ui.theme.T
import kotlin.math.roundToInt

/** Russian or English, by the phone's (or the app's) language. */
@Composable
fun t(ru: String, en: String): String = if (LocalRu.current) ru else en

@Composable
fun Card(modifier: Modifier = Modifier, fill: Color = C.page, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().leaf(fill).clip(Sh.Md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

enum class Look { SEAL, INK, SOFT, GHOST }

@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: Look = Look.SEAL, enabled: Boolean = true, small: Boolean = false) {
    val (fill, ink) = when (kind) {
        Look.SEAL -> C.seal to Color.White
        Look.INK -> C.ink to C.page
        Look.SOFT -> C.deep to C.ink
        Look.GHOST -> Color.Transparent to C.ink
    }
    val a = if (enabled) 1f else .4f
    val shape = Sh.Md
    Box(
        modifier.clip(shape)
            .then(if (kind == Look.SEAL || kind == Look.INK) Modifier.background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(fill, Color.White, .12f), fill, androidx.compose.ui.graphics.lerp(fill, Color.Black, .18f))), alpha = a)
                .border(1.dp, androidx.compose.ui.graphics.lerp(fill, Color.Black, .35f).copy(alpha = a), shape)
                .drawBehind { val m = 3 * density; drawRoundRect(Color.White.copy(alpha = .22f * a), androidx.compose.ui.geometry.Offset(m, m), androidx.compose.ui.geometry.Size(size.width - 2 * m, size.height - 2 * m), androidx.compose.ui.geometry.CornerRadius(8 * density), style = androidx.compose.ui.graphics.drawscope.Stroke(.8f * density)) }
            else Modifier.background(fill.copy(alpha = fill.alpha * a)))
            .then(if (kind == Look.GHOST) Modifier.border(1.dp, C.ink.copy(alpha = .35f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = if (small) 36.dp else 48.dp)
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 7.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = (if (small) T.Button.copy(fontSize = T.Button.fontSize * .9f) else T.Button).copy(color = ink.copy(alpha = a)), textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    val fill = if (on) color ?: C.ink else C.deep
    val ink = when { on && color != null -> Color.White; on -> C.page; else -> C.soft }
    Text(text, style = T.Small.copy(color = ink, fontSize = T.Small.fontSize * 1.1f), maxLines = 1,
        modifier = modifier.clip(Sh.Sm).background(fill).border(1.dp, if (on) Color.Transparent else C.ink.copy(alpha = .18f), Sh.Sm).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}

/** A genre label in its colour. */
@Composable
fun Tag(text: String, color: Color = C.ink, modifier: Modifier = Modifier) {
    Text(text, style = T.Small.copy(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), modifier = modifier.clip(Sh.Sm).background(color.copy(alpha = .09f))
        .border(1.dp, color.copy(alpha = .55f), Sh.Sm).padding(horizontal = 9.dp, vertical = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Flow(modifier: Modifier = Modifier, gap: Dp = 6.dp, content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap), content = content)

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, style: TextStyle = T.Body, single: Boolean = true, number: Boolean = false, onDone: (() -> Unit)? = null) {
    BasicTextField(value, onChange, modifier.fillMaxWidth().leaf().clip(Sh.Md), textStyle = style, singleLine = single, cursorBrush = SolidColor(C.seal),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (onDone != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { if (value.isEmpty()) Text(hint, style = style.copy(color = C.faint)); inner() } })
}

/** A sheet from the bottom over a dim layer; tap outside, swipe down or back closes it. */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val close = t("Закрыть", "Close")
    Box(Modifier.fillMaxSize().background(Color(0x99201810)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).imePadding(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 780.dp)
                .clip(Sh.Xl).parchment(edge = .06f)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .pointerInput(Unit) { var dy = 0f; detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > 120) onDismiss() }) { _, d -> dy += d } }
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Box(Modifier.align(Alignment.TopCenter).width(40.dp).height(4.dp).clip(CircleShape).background(C.line))
                Box(Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(40.dp).clip(CircleShape).clickable(onClick = onDismiss).semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(14.dp)) {
                        val w = 2.dp.toPx()
                        drawLine(C.soft, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
                        drawLine(C.soft, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                    }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** A full-screen page over the tabs with a back arrow. */
@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().parchment().clickable(remember { MutableInteractionSource() }, null) {}.imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(18.dp)) {
                    val w = 2.2.dp.toPx(); val m = size.height / 2
                    drawLine(C.ink, Offset(size.width * .65f, 0f), Offset(size.width * .2f, m), w, StrokeCap.Round)
                    drawLine(C.ink, Offset(size.width * .2f, m), Offset(size.width * .65f, size.height), w, StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(title, style = T.Title, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))

/** A section header: small ticket capitals, an optional note on the right. */
@Composable
fun Section(text: String, note: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = T.Eyebrow.copy(color = C.seal))
        Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 10.dp)) { flourish(0f, size.width, size.height / 2, C.seal, toRight = true) }
        if (note != null) Text(note, style = T.Small)
    }
}

/** A thin progress bar. */
@Composable
fun Bar(p: Float, modifier: Modifier = Modifier, color: Color = C.seal) {
    Box(modifier.height(6.dp).clip(CircleShape).background(C.deep)) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}
