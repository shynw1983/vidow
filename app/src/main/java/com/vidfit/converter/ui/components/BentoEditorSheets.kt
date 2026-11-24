package com.vidfit.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidfit.converter.R
import com.vidfit.converter.domain.EncodingNotice
import com.vidfit.converter.domain.EncodingPlan
import com.vidfit.converter.model.AspectFitMode
import com.vidfit.converter.model.AspectRatioOption
import com.vidfit.converter.model.CompressionMode
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.FrameRateCap
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.QualityTier
import com.vidfit.converter.model.ResolutionCap
import com.vidfit.converter.model.VideoInfo
import com.vidfit.converter.ui.theme.VidFitPalette
import java.util.Locale
import kotlin.math.roundToInt

enum class BentoSheetType {
    DIAGNOSIS,
    SIZE,
    RESOLUTION,
    COMPAT,
    TRIM_PRIVACY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BentoSettingsBottomSheet(
    sheetType: BentoSheetType,
    info: VideoInfo,
    config: ConversionConfig,
    plan: EncodingPlan,
    onDismiss: () -> Unit,
    onUpdateCodec: (OutputVideoCodec) -> Unit,
    onUpdateToneMapHdr: (Boolean) -> Unit,
    onUpdateResolution: (ResolutionCap) -> Unit,
    onUpdateFps: (FrameRateCap) -> Unit,
    onUpdateAspectRatio: (AspectRatioOption) -> Unit,
    onUpdateAspectFitMode: (AspectFitMode) -> Unit,
    onUpdateCompressionMode: (CompressionMode) -> Unit,
    onUpdateQualityTier: (QualityTier) -> Unit,
    onUpdateTargetSizeMb: (Float) -> Unit,
    onUpdateAutoDownscale: (Boolean) -> Unit,
    onUpdateRemoveAudio: (Boolean) -> Unit,
    onUpdateStripLocation: (Boolean) -> Unit,
    onUpdateTrimRange: (Long, Long) -> Unit
) {
    val colors = VidFitPalette.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.actionButtonBg,
        selectedLabelColor = colors.actionButtonText,
        containerColor = colors.subtleSurface,
        labelColor = colors.primaryText
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        contentColor = colors.primaryText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (sheetType) {
                BentoSheetType.DIAGNOSIS -> {
                    SheetTitle(stringResource(R.string.sheet_diagnosis_title))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecChip("${info.displayWidth}×${info.displayHeight}")
                        SpecChip(info.shortCodecName)
                        SpecChip(if (info.hasAudio) info.shortAudioCodecName else stringResource(R.string.audio_none))
                        SpecChip(String.format(Locale.US, "%.0f fps", info.frameRate))
                        SpecChip(info.colorTransferLabel)
                        SpecChip(formatFileSize(info.fileSizeBytes))
                        SpecChip(formatDuration(info.durationMs))
                        if (info.hasLocationMetadata) SpecChip(stringResource(R.string.sheet_has_gps))
                    }

                    val issues = info.compatibilityIssues
                    if (issues.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.successBg, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.sheet_compat_ok_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.successText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.sheet_compat_ok_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.successText
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            issues.forEach { issue ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.warningBg, RoundedCornerShape(14.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(issue.titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.warningText
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = stringResource(issue.detailRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.warningText
                                    )
                                }
                            }
                        }
                    }
                }

                BentoSheetType.SIZE -> {
                    SheetTitle(stringResource(R.string.sheet_size_title))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompressionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.compressionMode == mode,
                                onClick = { onUpdateCompressionMode(mode) },
                                colors = chipColors,
                                label = { Text(stringResource(mode.labelRes), fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    when (config.compressionMode) {
                        CompressionMode.QUALITY_LEVEL -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QualityTier.entries.forEach { tier ->
                                    FilterChip(
                                        selected = config.qualityTier == tier,
                                        onClick = { onUpdateQualityTier(tier) },
                                        colors = chipColors,
                                        label = { Text(stringResource(tier.labelRes), fontWeight = FontWeight.SemiBold) }
                                    )
                                }
                            }
                            Text(
                                text = stringResource(config.qualityTier.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }

                        CompressionMode.TARGET_SIZE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = stringResource(R.string.sheet_target_cap),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText
                                )
                                Text(
                                    text = "${config.targetSizeMb.roundToInt()} MB",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentText
                                )
                            }
                            Slider(
                                value = config.targetSizeMb.coerceIn(5f, 200f),
                                onValueChange = { onUpdateTargetSizeMb(it.roundToInt().toFloat()) },
                                valueRange = 5f..200f
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    8f to "8 MB",
                                    10f to stringResource(R.string.chip_10mb_discord),
                                    25f to stringResource(R.string.chip_25mb_email),
                                    50f to "50 MB",
                                    100f to "100 MB"
                                ).forEach { (mb, label) ->
                                    FilterChip(
                                        selected = config.targetSizeMb.roundToInt() == mb.roundToInt(),
                                        onClick = { onUpdateTargetSizeMb(mb) },
                                        colors = chipColors,
                                        label = { Text(label) }
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.cardBorder)
                            SheetToggleRow(
                                title = stringResource(R.string.toggle_auto_downscale_title),
                                subtitle = stringResource(R.string.toggle_auto_downscale_sub),
                                checked = config.autoDownscaleForTargetSize,
                                onCheckedChange = onUpdateAutoDownscale
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.subtleSurface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_orig_size, formatFileSize(info.fileSizeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Text(
                            text = stringResource(R.string.sheet_est_output, plan.estimatedSizeMb),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.successAccent
                        )
                    }

                    val noticeText = when (val n = plan.notice) {
                        is EncodingNotice.AlreadyUnderTarget ->
                            stringResource(R.string.notice_already_under_target, n.targetMb)
                        is EncodingNotice.AutoDownscaled ->
                            stringResource(R.string.notice_auto_downscaled, n.newShortSide, n.targetMb)
                        null -> null
                    }
                    if (noticeText != null) {
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentText
                        )
                    }
                }

                BentoSheetType.RESOLUTION -> {
                    SheetTitle(stringResource(R.string.sheet_res_title))
                    SheetSectionLabel(stringResource(R.string.sheet_max_res))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResolutionCap.entries.forEach { cap ->
                            val label = cap.fixedLabel ?: stringResource(cap.labelRes!!)
                            FilterChip(
                                selected = config.resolutionCap == cap,
                                onClick = { onUpdateResolution(cap) },
                                colors = chipColors,
                                label = { Text(label, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    SheetSectionLabel(stringResource(R.string.sheet_max_fps))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameRateCap.entries.forEach { fps ->
                            val label = fps.fixedLabel ?: stringResource(fps.labelRes!!)
                            FilterChip(
                                selected = config.frameRateCap == fps,
                                onClick = { onUpdateFps(fps) },
                                colors = chipColors,
                                label = { Text(label, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    SheetSectionLabel(stringResource(R.string.sheet_aspect_ratio))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectRatioOption.entries.forEach { ratio ->
                            FilterChip(
                                selected = config.aspectRatio == ratio,
                                onClick = { onUpdateAspectRatio(ratio) },
                                colors = chipColors,
                                label = { Text(stringResource(ratio.labelRes), fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (config.aspectRatio != AspectRatioOption.ORIGINAL) {
                        SheetSectionLabel(stringResource(R.string.sheet_fit_mode))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AspectFitMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = config.aspectFitMode == mode,
                                    onClick = { onUpdateAspectFitMode(mode) },
                                    colors = chipColors,
                                    label = { Text(stringResource(mode.labelRes), fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.sheet_final_dims, plan.outputWidth, plan.outputHeight) +
                            (plan.targetFps?.let { " · ${it.toInt()} fps" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentText
                    )
                }

                BentoSheetType.COMPAT -> {
                    SheetTitle(stringResource(R.string.sheet_compat_title))
                    SheetSectionLabel(stringResource(R.string.sheet_video_codec))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputVideoCodec.entries.forEach { codec ->
                            FilterChip(
                                selected = config.videoCodec == codec,
                                onClick = { onUpdateCodec(codec) },
                                colors = chipColors,
                                label = { Text(stringResource(codec.labelRes), fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(config.videoCodec.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )

                    HorizontalDivider(color = colors.cardBorder)

                    SheetToggleRow(
                        title = stringResource(R.string.toggle_hdr_sdr_title),
                        subtitle = stringResource(R.string.toggle_hdr_sdr_sub),
                        checked = config.toneMapHdrToSdr,
                        onCheckedChange = onUpdateToneMapHdr
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.subtleSurface, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_auto_compat_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.sheet_auto_compat_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }

                BentoSheetType.TRIM_PRIVACY -> {
                    SheetTitle(stringResource(R.string.sheet_trim_title))
                    TrimRangeSelector(
                        durationMs = info.durationMs,
                        startMs = config.trimStartMs,
                        endMs = config.trimEndMs ?: info.durationMs,
                        onRangeChange = onUpdateTrimRange
                    )
                    HorizontalDivider(color = colors.cardBorder)
                    SheetToggleRow(
                        title = stringResource(R.string.toggle_strip_gps_title),
                        subtitle = stringResource(R.string.toggle_strip_gps_sub),
                        checked = config.stripLocationMetadata,
                        onCheckedChange = onUpdateStripLocation
                    )
                    if (info.hasAudio) {
                        SheetToggleRow(
                            title = stringResource(R.string.toggle_mute_title),
                            subtitle = stringResource(R.string.toggle_mute_sub),
                            checked = config.removeAudio,
                            onCheckedChange = onUpdateRemoveAudio
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionButtonBg,
                    contentColor = colors.actionButtonText
                )
            ) {
                Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = VidFitPalette.colors.primaryText
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = VidFitPalette.colors.secondaryText
    )
}

@Composable
private fun SpecChip(text: String) {
    val colors = VidFitPalette.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.primaryText,
        modifier = Modifier
            .background(colors.subtleSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun SheetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = VidFitPalette.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
