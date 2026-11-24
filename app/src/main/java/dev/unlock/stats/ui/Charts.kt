package dev.unlock.stats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Säulendiagramm mit Pillen-Balken, die federnd aus dem Boden wachsen.
 * Leere Tage zeigen einen kleinen Punkt, damit der Rhythmus sichtbar bleibt.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PillBarChart(
    values: List<Int>,
    labels: List<String?>,
    highlightIndex: Int?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 148.dp,
    description: String,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val colors = MaterialTheme.colorScheme
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    // Bei jedem Wechsel des Zeitraums neu einwachsen lassen.
    val growth = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) { growth.animateTo(1f, spec) }
    val gap = when {
        values.size > 20 -> 3.dp
        values.size > 10 -> 6.dp
        else -> 10.dp
    }

    Column(modifier.semantics { contentDescription = description }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        ) {
            values.forEachIndexed { index, value ->
                val target = value.toFloat() / max
                val fraction by animateFloatAsState(target, spec, label = "bar")
                val highlighted = index == highlightIndex
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val w = size.width
                    if (value == 0) {
                        val d = minOf(w, 6.dp.toPx())
                        drawCircle(colors.surfaceContainerHighest, d / 2, Offset(w / 2, size.height - d / 2))
                    } else {
                        val h = (size.height * fraction * growth.value).coerceAtLeast(w.coerceAtMost(size.height))
                        drawRoundRect(
                            color = if (highlighted) colors.tertiary else colors.primary,
                            topLeft = Offset(0f, size.height - h),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(w / 2, w / 2),
                        )
                    }
                }
            }
        }
        if (labels.any { it != null }) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (label != null) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == highlightIndex) colors.tertiary else colors.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 24-Stunden-Zifferblatt: jede Stunde ein runder Strahl, die stärkste Stunde
 * leuchtet in Tertiary. Mitte bleibt frei für eine Beschriftung.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HourDial(
    hourly: List<Int>,
    peakHour: Int?,
    modifier: Modifier = Modifier,
    description: String,
    center: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) { growth.animateTo(1f, spec) }
    val labelStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(modifier.aspectRatio(1f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        val side = maxWidth
        Canvas(Modifier.size(side)) {
            val c = Offset(size.width / 2, size.height / 2)
            val outer = size.minDimension / 2 - 22.dp.toPx()
            val inner = outer * 0.48f
            val stroke = (2 * PI.toFloat() * outer / 24f) * 0.52f
            drawCircle(colors.surfaceContainerHighest, inner - stroke * 0.9f, c)
            for (hour in 0 until 24) {
                // 0 Uhr oben, im Uhrzeigersinn.
                val angle = (hour / 24f) * 2 * PI.toFloat() - PI.toFloat() / 2
                val dir = Offset(cos(angle), sin(angle))
                val value = hourly[hour].toFloat() / max
                val length = (outer - inner) * value * growth.value
                drawLine(
                    color = colors.surfaceContainerHighest,
                    start = c + dir * inner,
                    end = c + dir * outer,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                if (hourly[hour] > 0) {
                    drawLine(
                        color = if (hour == peakHour) colors.tertiary else lerp(colors.primaryContainer, colors.primary, 0.35f + 0.65f * value),
                        start = c + dir * inner,
                        end = c + dir * (inner + length),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Stundenmarken 0 · 6 · 12 · 18 außen.
        listOf(0, 6, 12, 18).forEach { hour ->
            val angle = (hour / 24f) * 2 * PI - PI / 2
            val r = side / 2 - 9.dp
            Text(
                hour.toString(),
                style = labelStyle,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(r * cos(angle).toFloat(), r * sin(angle).toFloat()),
            )
        }
        Box(Modifier.fillMaxWidth(0.42f), contentAlignment = Alignment.Center) { center() }
    }
}

/**
 * Kalender-Heatmap (Spalten = Wochen, Zeilen = Mo–So) mit weichen Quadraten.
 * Fünf Intensitätsstufen zwischen Surface und Primary.
 */
@Composable
fun CalendarHeatmap(
    daily: Map<Long, Int>,
    today: LocalDate,
    weeks: Int,
    modifier: Modifier = Modifier,
    description: String,
) {
    val colors = MaterialTheme.colorScheme
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val firstMonday = lastMonday.minusWeeks((weeks - 1).toLong())
    val counts = remember(today, weeks, daily) {
        List(weeks * 7) { i -> firstMonday.plusDays(i.toLong()).let { if (it.isAfter(today)) -1 else daily[it.toEpochDay()] ?: 0 } }
    }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val gap = 4.dp
        val cell = (maxWidth - gap * (weeks - 1)) / weeks
        Canvas(Modifier.fillMaxWidth().height(cell * 7 + gap * 6)) {
            val c = cell.toPx()
            val g = gap.toPx()
            val radius = CornerRadius(c * 0.32f, c * 0.32f)
            for (week in 0 until weeks) {
                for (dow in 0 until 7) {
                    val count = counts[week * 7 + dow]
                    if (count < 0) continue
                    val color = heatColor(count, max, colors.surfaceContainerHighest, colors.primaryContainer, colors.primary)
                    val isToday = week == weeks - 1 && dow == today.dayOfWeek.value - 1
                    drawRoundRect(color, Offset(week * (c + g), dow * (c + g)), Size(c, c), radius)
                    if (isToday) {
                        drawRoundRect(
                            colors.tertiary,
                            Offset(week * (c + g), dow * (c + g)),
                            Size(c, c),
                            radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

fun heatColor(count: Int, max: Int, empty: Color, low: Color, high: Color): Color = when {
    count <= 0 -> empty
    else -> {
        val level = ((count.toFloat() / max) * 4).toInt().coerceIn(0, 3) // 0..3
        lerp(low, high, level / 3f)
    }
}

/** Legende „Weniger □□□□□ Mehr“ für die Heatmap. */
@Composable
fun HeatLegend(less: String, more: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(less, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(2.dp))
        listOf(0, 1, 2, 3, 4).forEach { level ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(heatColor(level, 4, colors.surfaceContainerHighest, colors.primaryContainer, colors.primary)),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(more, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

data class Segment(val label: String, val value: Int, val color: Color)

/** Horizontale, segmentierte Pille mit kleinen Lücken – wie eine Connected Button Group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val visible = segments.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1)
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) { growth.animateTo(1f, spec) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val gap = 4.dp.toPx()
            val usable = (size.width - gap * (visible.size - 1).coerceAtLeast(0)) * growth.value
            val r = size.height / 2
            var x = 0f
            visible.forEachIndexed { index, seg ->
                val w = (usable * seg.value / total).coerceAtLeast(size.height * 0.5f)
                // Außen voll rund, innen nur leicht gerundet.
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = x, top = 0f, right = x + w, bottom = size.height,
                            topLeftCornerRadius = CornerRadius(if (index == 0) r else 6f),
                            bottomLeftCornerRadius = CornerRadius(if (index == 0) r else 6f),
                            topRightCornerRadius = CornerRadius(if (index == visible.lastIndex) r else 6f),
                            bottomRightCornerRadius = CornerRadius(if (index == visible.lastIndex) r else 6f),
                        ),
                    )
                }
                drawPath(path, seg.color)
                x += w + gap
            }
        }
        Spacer(Modifier.height(12.dp))
        visible.forEach { seg ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(seg.color))
                Spacer(Modifier.width(10.dp))
                Text(seg.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${seg.value * 100 / total} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Rangliste mit Balken, die proportional zum Spitzenreiter wachsen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RankedBars(items: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) { growth.animateTo(1f, spec) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, (name, count) ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(if (index == 0) 20.dp else 12.dp))
                    .background(colors.surfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((count.toFloat() / max * growth.value).coerceIn(0.02f, 1f))
                        .background(if (index == 0) colors.primaryContainer else colors.secondaryContainer),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(count.toString(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
