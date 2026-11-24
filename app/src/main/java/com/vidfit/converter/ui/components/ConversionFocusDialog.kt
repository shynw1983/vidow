package com.vidfit.converter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidfit.converter.R
import com.vidfit.converter.model.ConversionResult
import com.vidfit.converter.model.ConversionState
import com.vidfit.converter.ui.theme.VidFitPalette

@Composable
fun ConversionFocusDialog(
    state: ConversionState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onShare: (ConversionResult) -> Unit,
    onPlay: (ConversionResult) -> Unit
) {
    if (state is ConversionState.Idle) return

    val colors = VidFitPalette.colors
    val isConverting = state is ConversionState.Converting

    Dialog(
        onDismissRequest = { if (!isConverting) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isConverting,
            dismissOnClickOutside = !isConverting,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.cardBackground,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is ConversionState.Converting -> {
                        ConvertingDialogBody(state = state, onCancel = onCancel)
                    }
                    is ConversionState.Success -> {
                        SuccessDialogBody(
                            result = state.result,
                            onShare = { onShare(state.result) },
                            onPlay = { onPlay(state.result) },
                            onDone = onDismiss
                        )
                    }
                    is ConversionState.Failed -> {
                        ErrorDialogBody(
                            title = stringResource(R.string.conv_failed_title),
                            message = state.message,
                            showRetry = true,
                            onRetry = onRetry,
                            onDismiss = onDismiss
                        )
                    }
                    is ConversionState.Cancelled -> {
                        ErrorDialogBody(
                            title = stringResource(R.string.conv_cancelled_title),
                            message = stringResource(R.string.conv_cancelled_desc),
                            showRetry = false,
                            onRetry = onRetry,
                            onDismiss = onDismiss
                        )
                    }
                    ConversionState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun ConvertingDialogBody(
    state: ConversionState.Converting,
    onCancel: () -> Unit
) {
    val colors = VidFitPalette.colors
    val animatedProgress by animateFloatAsState(
        targetValue = (state.progressPercent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 220),
        label = "conversionProgress"
    )

    Box(
        modifier = Modifier.size(136.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(136.dp)) {
            val strokeWidth = 11.dp.toPx()
            drawArc(
                color = colors.progressTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val sweep = (animatedProgress * 360f).coerceAtLeast(10f)
            drawArc(
                color = colors.actionButtonBg,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.progressPercent}%",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primaryText
            )
            Text(
                text = formatDuration(state.elapsedMs),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        text = state.statusText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.primaryText,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.conv_bg_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryText,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(22.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = stringResource(R.string.conv_cancel_btn),
            color = colors.primaryText,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SuccessDialogBody(
    result: ConversionResult,
    onShare: () -> Unit,
    onPlay: () -> Unit,
    onDone: () -> Unit
) {
    val colors = VidFitPalette.colors

    Box(
        modifier = Modifier
            .size(64.dp)
            .background(colors.successBg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = colors.successAccent,
            modifier = Modifier.size(34.dp)
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.conv_done_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = colors.primaryText
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.conv_done_meta, formatDuration(result.elapsedMs)),
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryText
    )

    Spacer(Modifier.height(18.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.subtleSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(stringResource(R.string.conv_before_label), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
            Text(
                text = formatFileSize(result.originalSizeBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.accentText,
            modifier = Modifier.size(20.dp)
        )
        Column(horizontalAlignment = Alignment.Start) {
            Text(stringResource(R.string.conv_after_label), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
            Text(
                text = formatFileSize(result.outputSizeBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.successAccent
            )
        }
        val deltaText = when {
            result.sizeReductionPercent > 0 -> "-${result.sizeReductionPercent}%"
            result.sizeReductionPercent < 0 -> "+${-result.sizeReductionPercent}%"
            else -> "0%"
        }
        Box(
            modifier = Modifier
                .background(colors.successBg, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = deltaText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.successText
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    val sdrTag = if (result.convertedHdrToSdr) stringResource(R.string.conv_converted_sdr) else ""
    Text(
        text = "${result.outputWidth}×${result.outputHeight} · ${result.codecLabel}$sdrTag",
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryText
    )

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onShare,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.actionButtonBg,
            contentColor = colors.actionButtonText
        )
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.conv_share_now), fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.conv_play_check), color = colors.primaryText)
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.conv_done_back), color = colors.primaryText)
        }
    }
}

@Composable
private fun ErrorDialogBody(
    title: String,
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = VidFitPalette.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(30.dp)
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.primaryText
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = colors.secondaryText,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))
    if (showRetry) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.actionButtonBg,
                contentColor = colors.actionButtonText
            )
        ) {
            Text(stringResource(R.string.conv_retry_btn), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.conv_back_adjust), color = colors.secondaryText)
        }
    } else {
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.actionButtonBg,
                contentColor = colors.actionButtonText
            )
        ) {
            Text(stringResource(R.string.action_back), fontWeight = FontWeight.Bold)
        }
    }
}
