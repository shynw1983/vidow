package com.vidfit.converter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vidfit.converter.ui.theme.VidFitPalette

@Composable
fun TrimRangeSelector(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VidFitPalette.colors
    val safeDuration = durationMs.coerceAtLeast(1000L)
    val safeStart = startMs.coerceIn(0L, safeDuration - 500L)
    val safeEnd = endMs.coerceIn(safeStart + 500L, safeDuration)
    val isTrimmed = safeStart > 0L || safeEnd < safeDuration

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "时长裁切：${formatDuration(safeStart)} - ${formatDuration(safeEnd)}（共 ${formatDuration(safeEnd - safeStart)}）",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            if (isTrimmed) {
                TextButton(onClick = { onRangeChange(0L, safeDuration) }) {
                    Text("重置", color = colors.accentText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        RangeSlider(
            value = safeStart.toFloat()..safeEnd.toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong().coerceIn(0L, safeDuration - 500L)
                val newEnd = range.endInclusive.toLong().coerceIn(newStart + 500L, safeDuration)
                onRangeChange(newStart, newEnd)
            },
            valueRange = 0f..safeDuration.toFloat()
        )
    }
}
