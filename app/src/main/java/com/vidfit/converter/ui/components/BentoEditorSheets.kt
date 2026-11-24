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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    SheetTitle("视频体检与兼容诊断")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecChip("${info.displayWidth}×${info.displayHeight}")
                        SpecChip(info.shortCodecName)
                        SpecChip(info.shortAudioCodecName)
                        SpecChip(String.format(Locale.US, "%.0f fps", info.frameRate))
                        SpecChip(info.colorTransferLabel)
                        SpecChip(formatFileSize(info.fileSizeBytes))
                        SpecChip(formatDuration(info.durationMs))
                        if (info.hasLocationMetadata) SpecChip("含 GPS 坐标")
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
                                text = "兼容性良好",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.successText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "该视频已是常见兼容格式，你可以直接按需要调整上传目标体积或比例。",
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
                                        text = issue.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.warningText
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = issue.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.warningText
                                    )
                                }
                            }
                        }
                    }
                }

                BentoSheetType.SIZE -> {
                    SheetTitle("上传体积与压缩模式")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompressionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.compressionMode == mode,
                                onClick = { onUpdateCompressionMode(mode) },
                                label = { Text(mode.label) }
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
                                        label = { Text(tier.label) }
                                    )
                                }
                            }
                            Text(
                                text = config.qualityTier.description,
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
                                Text("目标体积上限", style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
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
                                    10f to "10 MB (Discord)",
                                    25f to "25 MB (邮件)",
                                    50f to "50 MB",
                                    100f to "100 MB"
                                ).forEach { (mb, label) ->
                                    FilterChip(
                                        selected = config.targetSizeMb.roundToInt() == mb.roundToInt(),
                                        onClick = { onUpdateTargetSizeMb(mb) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.cardBorder)
                            SheetToggleRow(
                                title = "码率偏低时自动降分辨率防模糊",
                                subtitle = "防止极低码率硬压高分辨率导致严重马赛克",
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
                            text = "原体积 ${formatFileSize(info.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                        Text(
                            text = String.format(Locale.US, "预估输出 %.1f MB", plan.estimatedSizeMb),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.successAccent
                        )
                    }

                    if (plan.autoDownscaledNotice != null) {
                        Text(
                            text = plan.autoDownscaledNotice,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentText
                        )
                    }
                }

                BentoSheetType.RESOLUTION -> {
                    SheetTitle("画面分辨率、帧率与比例")
                    SheetSectionLabel("最大分辨率（自动 8 像素对齐）")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResolutionCap.entries.forEach { cap ->
                            FilterChip(
                                selected = config.resolutionCap == cap,
                                onClick = { onUpdateResolution(cap) },
                                label = { Text(cap.label) }
                            )
                        }
                    }

                    SheetSectionLabel("帧率上限")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameRateCap.entries.forEach { fps ->
                            FilterChip(
                                selected = config.frameRateCap == fps,
                                onClick = { onUpdateFps(fps) },
                                label = { Text(fps.label) }
                            )
                        }
                    }

                    SheetSectionLabel("目标画面比例")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectRatioOption.entries.forEach { ratio ->
                            FilterChip(
                                selected = config.aspectRatio == ratio,
                                onClick = { onUpdateAspectRatio(ratio) },
                                label = { Text(ratio.label) }
                            )
                        }
                    }

                    if (config.aspectRatio != AspectRatioOption.ORIGINAL) {
                        SheetSectionLabel("画面填充方式")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AspectFitMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = config.aspectFitMode == mode,
                                    onClick = { onUpdateAspectFitMode(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                    }

                    Text(
                        text = "最终输出尺寸：${plan.outputWidth} × ${plan.outputHeight}" +
                            (plan.targetFps?.let { " · ${it.toInt()} fps" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentText
                    )
                }

                BentoSheetType.COMPAT -> {
                    SheetTitle("兼容性编码与色彩")
                    SheetSectionLabel("输出视频编码")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputVideoCodec.entries.forEach { codec ->
                            FilterChip(
                                selected = config.videoCodec == codec,
                                onClick = { onUpdateCodec(codec) },
                                label = { Text(codec.label) }
                            )
                        }
                    }
                    Text(
                        text = config.videoCodec.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )

                    HorizontalDivider(color = colors.cardBorder)

                    SheetToggleRow(
                        title = "HDR 自动转 SDR (Rec.709)",
                        subtitle = "避免 HDR 视频发到电脑、微信或网页后画面发灰、过曝",
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
                            text = "已自动启用的底层兼容优化",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "• 统一 MP4 封装 + 通用 AAC 音频\n• 自动固化竖屏旋转元数据，防止老播放器横置",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }

                BentoSheetType.TRIM_PRIVACY -> {
                    SheetTitle("时长裁切与隐私")
                    TrimRangeSelector(
                        durationMs = info.durationMs,
                        startMs = config.trimStartMs,
                        endMs = config.trimEndMs ?: info.durationMs,
                        onRangeChange = onUpdateTrimRange
                    )
                    HorizontalDivider(color = colors.cardBorder)
                    SheetToggleRow(
                        title = "清除 GPS 地理位置元数据",
                        subtitle = "上传前移除拍摄定位坐标，保护隐私",
                        checked = config.stripLocationMetadata,
                        onCheckedChange = onUpdateStripLocation
                    )
                    if (info.hasAudio) {
                        SheetToggleRow(
                            title = "移除音轨（静音导出）",
                            subtitle = "适合纯画面演示、动图场景，进一步省体积",
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
                Text("完成", fontWeight = FontWeight.Bold)
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
