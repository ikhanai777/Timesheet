package com.ikhanai.timesheet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ikhanai.timesheet.domain.ChartBucket
import com.ikhanai.timesheet.ui.Fmt
import com.ikhanai.timesheet.ui.theme.LocalSeriesColors

data class Segment(val label: String, val minutes: Int, val color: Color)

/** Horizontal stacked bar of a day against its available hours (FR-1.5, FR-3 day chart). */
@Composable
fun HoursBar(segments: List<Segment>, capacityMinutes: Int, modifier: Modifier = Modifier) {
    val track = LocalSeriesColors.current.track
    val marker = MaterialTheme.colorScheme.onSurface
    val total = segments.sumOf { it.minutes }
    val scale = maxOf(total, capacityMinutes, 1)
    val description = segments.filter { it.minutes > 0 }
        .joinToString(", ") { "${it.label} ${Fmt.hoursWithUnit(it.minutes)}" } +
        " of ${Fmt.hoursWithUnit(capacityMinutes)} available"
    Canvas(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = description },
    ) {
        drawRect(track)
        var x = 0f
        for (segment in segments) {
            if (segment.minutes <= 0) continue
            val w = size.width * segment.minutes / scale
            drawRect(segment.color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
        if (total > capacityMinutes && capacityMinutes > 0) {
            val cx = size.width * capacityMinutes / scale
            drawLine(marker, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 3f)
        }
    }
}

/** Stacked columns coloured NTIME / P6 / Overhead (week and month charts). */
@Composable
fun StackedColumnChart(
    buckets: List<ChartBucket>,
    referenceMinutes: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSeriesColors.current
    val maxValue = maxOf(buckets.maxOfOrNull { it.totalMinutes } ?: 0, referenceMinutes ?: 0, 60)
    val description = buckets.joinToString("; ") {
        "${it.label}: NTIME ${Fmt.hours(it.ntimeMinutes)}, P6 ${Fmt.hours(it.p6Minutes)}, " +
            "overhead ${Fmt.hours(it.overheadMinutes)}"
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val slot = size.width / buckets.size.coerceAtLeast(1)
            val barWidth = (slot * 0.6f).coerceAtMost(48.dp.toPx())
            if (referenceMinutes != null && referenceMinutes > 0) {
                val y = size.height - size.height * referenceMinutes / maxValue
                drawLine(
                    gridColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            buckets.forEachIndexed { index, bucket ->
                val left = slot * index + (slot - barWidth) / 2
                var bottom = size.height
                for ((minutes, color) in listOf(
                    bucket.ntimeMinutes to colors.ntime,
                    bucket.p6Minutes to colors.p6,
                    bucket.overheadMinutes to colors.overhead,
                )) {
                    if (minutes <= 0) continue
                    val h = size.height * minutes / maxValue
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, bottom - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                    bottom -= h
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            buckets.forEach {
                Text(
                    it.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Project share donut (month view). */
@Composable
fun DonutChart(slices: List<Segment>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.minutes }
    val track = LocalSeriesColors.current.track
    val description = slices.joinToString(", ") { "${it.label} ${Fmt.hoursWithUnit(it.minutes)}" }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .size(120.dp)
                .semantics { contentDescription = "Share of hours: $description" },
        ) {
            val stroke = 22.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (total == 0) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            for (slice in slices) {
                val sweep = 360f * slice.minutes / total
                drawArc(slice.color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                val pct = if (total == 0) 0 else Math.round(100f * slice.minutes / total)
                LegendItem(slice.color, "${slice.label} · $pct%")
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemLegend(showOvertime: Boolean = false) {
    val colors = LocalSeriesColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(colors.ntime, "NTIME")
        LegendItem(colors.p6, "P6")
        LegendItem(colors.overhead, "Overhead")
        if (showOvertime) LegendItem(colors.overtime, "Overtime")
    }
}
