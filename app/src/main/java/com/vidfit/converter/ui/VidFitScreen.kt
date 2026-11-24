package com.vidfit.converter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidfit.converter.data.CookieSite
import com.vidfit.converter.domain.EncodingPlan
import com.vidfit.converter.model.AppTab
import com.vidfit.converter.model.AspectFitMode
import com.vidfit.converter.model.AspectRatioOption
import com.vidfit.converter.model.CompressionMode
import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.ConversionPreset
import com.vidfit.converter.model.ConversionResult
import com.vidfit.converter.model.ConversionState
import com.vidfit.converter.model.DownloadFormatMode
import com.vidfit.converter.model.DownloadResult
import com.vidfit.converter.model.FrameRateCap
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.QualityTier
import com.vidfit.converter.model.ResolutionCap
import com.vidfit.converter.model.VideoInfo
import com.vidfit.converter.ui.components.BentoDownloadTab
import com.vidfit.converter.ui.components.BentoSettingsBottomSheet
import com.vidfit.converter.ui.components.BentoSheetType
import com.vidfit.converter.ui.components.ConversionFocusDialog
import com.vidfit.converter.ui.components.MediaThumbnailBox
import com.vidfit.converter.ui.components.VideoPreviewPlayer
import com.vidfit.converter.ui.components.formatDuration
import com.vidfit.converter.ui.components.formatFileSize
import com.vidfit.converter.ui.theme.VidFitPalette
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VidFitScreen(
    uiState: VidFitUiState,
    onSelectTab: (AppTab) -> Unit,
    // Downloader callbacks
    onUrlChange: (String) -> Unit,
    onParseUrl: () -> Unit,
    onClearLink: () -> Unit,
    onDownloadFormatModeChange: (DownloadFormatMode) -> Unit,
    onSelectedMaxHeightChange: (Int?) -> Unit,
    onEmbedSubtitlesChange: (Boolean) -> Unit,
    onAutoConvertChange: (Boolean) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadDialog: () -> Unit,
    onToggleNightlyChannel: (Boolean) -> Unit,
    onUpdateYtDlpCore: () -> Unit,
    onSaveSiteCookies: (CookieSite) -> Unit,
    onClearSiteCookies: (CookieSite) -> Unit,
    onClearAllCookies: () -> Unit,
    onConvertDownloaded: (DownloadResult) -> Unit,
    onShareDownloaded: (DownloadResult) -> Unit,
    onPlayDownloaded: (DownloadResult) -> Unit,
    onRemoveDownloadItem: (DownloadResult) -> Unit,
    onClearDownloadHistory: () -> Unit,
    // Converter callbacks
    onPickFromGallery: () -> Unit,
    onPickFromFiles: () -> Unit,
    onSelectPreset: (ConversionPreset) -> Unit,
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
    onUpdateTrimRange: (Long, Long) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onClearResult: () -> Unit,
    onClearVideo: () -> Unit,
    onShareResult: (ConversionResult) -> Unit,
    onPlayResult: (ConversionResult) -> Unit
) {
    val colors = VidFitPalette.colors
    var activeSheet by remember { mutableStateOf<BentoSheetType?>(null) }

    // Centered focus modal dialog for conversion progress & completion result
    ConversionFocusDialog(
        state = uiState.conversionState,
        onCancel = onCancelConversion,
        onDismiss = onClearResult,
        onRetry = onStartConversion,
        onShare = onShareResult,
        onPlay = onPlayResult
    )

    val currentInfo = uiState.videoInfo
    val currentPlan = uiState.encodingPlan
    if (activeSheet != null && currentInfo != null && currentPlan != null) {
        BentoSettingsBottomSheet(
            sheetType = activeSheet!!,
            info = currentInfo,
            config = uiState.config,
            plan = currentPlan,
            onDismiss = { activeSheet = null },
            onUpdateCodec = onUpdateCodec,
            onUpdateToneMapHdr = onUpdateToneMapHdr,
            onUpdateResolution = onUpdateResolution,
            onUpdateFps = onUpdateFps,
            onUpdateAspectRatio = onUpdateAspectRatio,
            onUpdateAspectFitMode = onUpdateAspectFitMode,
            onUpdateCompressionMode = onUpdateCompressionMode,
            onUpdateQualityTier = onUpdateQualityTier,
            onUpdateTargetSizeMb = onUpdateTargetSizeMb,
            onUpdateAutoDownscale = onUpdateAutoDownscale,
            onUpdateRemoveAudio = onUpdateRemoveAudio,
            onUpdateStripLocation = onUpdateStripLocation,
            onUpdateTrimRange = onUpdateTrimRange
        )
    }

    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            BentoTopBar(
                activeTab = uiState.activeTab,
                hasVideo = currentInfo != null,
                isConverting = uiState.conversionState is ConversionState.Converting,
                onSelectTab = onSelectTab,
                onSwitchVideo = onClearVideo
            )
        },
        bottomBar = {
            if (uiState.activeTab == AppTab.CONVERT && currentInfo != null && currentPlan != null) {
                BentoBottomActionDock(
                    originalBytes = currentInfo.fileSizeBytes,
                    plan = currentPlan,
                    enabled = uiState.conversionState !is ConversionState.Converting,
                    onStartConversion = onStartConversion
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                AppTab.DOWNLOAD -> {
                    BentoDownloadTab(
                        state = uiState.download,
                        onUrlChange = onUrlChange,
                        onParseUrl = onParseUrl,
                        onClearLink = onClearLink,
                        onFormatModeChange = onDownloadFormatModeChange,
                        onSelectedMaxHeightChange = onSelectedMaxHeightChange,
                        onEmbedSubtitlesChange = onEmbedSubtitlesChange,
                        onAutoConvertChange = onAutoConvertChange,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDismissDownloadDialog = onDismissDownloadDialog,
                        onToggleNightlyChannel = onToggleNightlyChannel,
                        onUpdateCore = onUpdateYtDlpCore,
                        onSaveSiteCookies = onSaveSiteCookies,
                        onClearSiteCookies = onClearSiteCookies,
                        onClearAllCookies = onClearAllCookies,
                        onConvertDownloaded = onConvertDownloaded,
                        onShareDownloaded = onShareDownloaded,
                        onPlayDownloaded = onPlayDownloaded,
                        onRemoveDownloadItem = onRemoveDownloadItem,
                        onClearDownloadHistory = onClearDownloadHistory
                    )
                }

                AppTab.CONVERT -> {
                    when {
                        uiState.isInspecting -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = colors.actionButtonBg)
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "正在体检视频编码与色彩...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.primaryText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        currentInfo == null -> {
                            BentoHomeScreen(
                                errorMessage = uiState.inspectError,
                                recentResults = uiState.recentResults,
                                onPickFromGallery = onPickFromGallery,
                                onPickFromFiles = onPickFromFiles,
                                onShareResult = onShareResult,
                                onPlayResult = onPlayResult
                            )
                        }

                        currentPlan != null -> {
                            BentoEditorDashboard(
                                info = currentInfo,
                                config = uiState.config,
                                plan = currentPlan,
                                onSelectPreset = onSelectPreset,
                                onOpenSheet = { activeSheet = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoTopBar(
    activeTab: AppTab,
    hasVideo: Boolean,
    isConverting: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onSwitchVideo: () -> Unit
) {
    val colors = VidFitPalette.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val isDarkSurface = colors.pageBackground.red < 0.2f
                val owColor = if (isDarkSurface) Color(0xFF7DD3FC) else Color(0xFF0284C7)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.subtleSurface,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.primaryText, fontWeight = FontWeight.ExtraBold)) {
                                append("Vid")
                            }
                            withStyle(SpanStyle(color = owColor, fontWeight = FontWeight.ExtraBold)) {
                                append("ow")
                            }
                        },
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = if (activeTab == AppTab.DOWNLOAD) "网络视频下载 · yt-dlp 核心" else "全平台兼容 · 上传体积精准控制",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            }
            if (activeTab == AppTab.CONVERT && hasVideo && !isConverting) {
                Surface(
                    onClick = onSwitchVideo,
                    shape = RoundedCornerShape(999.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "换视频",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentText
                        )
                    }
                }
            }
        }

        // Sleek 2-tab Bento Segmented Switcher (Download | Convert)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBackground, RoundedCornerShape(16.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Surface(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) colors.actionButtonBg else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tab == AppTab.DOWNLOAD) Icons.Rounded.Download else Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = if (selected) colors.actionButtonText else colors.secondaryText,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.actionButtonText else colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoHomeScreen(
    errorMessage: String?,
    recentResults: List<ConversionResult>,
    onPickFromGallery: () -> Unit,
    onPickFromFiles: () -> Unit,
    onShareResult: (ConversionResult) -> Unit,
    onPlayResult: (ConversionResult) -> Unit
) {
    val colors = VidFitPalette.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .weight(1f)
                    .height(156.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.actionButtonBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "从相册选择",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "MP4 · MOV · HDR",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Surface(
                onClick = onPickFromFiles,
                modifier = Modifier
                    .weight(1f)
                    .height(156.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.cardBackground,
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.subtleSurface, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "从文件导入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "MKV · 下载目录视频",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "专为兼容性与上传打造",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
                CapabilityRow(
                    icon = Icons.Rounded.HighQuality,
                    title = "一键全平台兼容",
                    subtitle = "自动转 H.264 + AAC，HDR 转 SDR 防发灰，竖屏旋转固化"
                )
                CapabilityRow(
                    icon = Icons.Rounded.Compress,
                    title = "指定 MB 精准压大小",
                    subtitle = "适配邮件 25MB / Discord 10MB 限制，低码率自动降分辨率防糊"
                )
                CapabilityRow(
                    icon = Icons.Rounded.Shield,
                    title = "隐私保护与相册直存",
                    subtitle = "上传前自动剥离 GPS 位置，硬件加速不发烫"
                )
            }
        }

        if (recentResults.isNotEmpty()) {
            Text(
                text = "最近转换",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )
            recentResults.forEach { res ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaThumbnailBox(
                            localVideoUri = res.outputUri,
                            showPlayBadge = true,
                            modifier = Modifier
                                .size(width = 66.dp, height = 46.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.outputFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${formatFileSize(res.originalSizeBytes)} → ${formatFileSize(res.outputSizeBytes)} · ${res.outputWidth}×${res.outputHeight}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.successAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { onPlayResult(res) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = colors.primaryText)
                        }
                        IconButton(onClick = { onShareResult(res) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "分享", tint = colors.accentText)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val colors = VidFitPalette.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.subtleSurface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
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
    }
}

@Composable
private fun BentoEditorDashboard(
    info: VideoInfo,
    config: ConversionConfig,
    plan: EncodingPlan,
    onSelectPreset: (ConversionPreset) -> Unit,
    onOpenSheet: (BentoSheetType) -> Unit
) {
    val colors = VidFitPalette.colors
    var showPreviewPlayer by rememberSaveable(info.uriString) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Compact Video Strip + Expandable ExoPlayer Preview
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MediaThumbnailBox(
                        localVideoUri = info.uri,
                        showPlayBadge = true,
                        modifier = Modifier
                            .size(width = 68.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPreviewPlayer = !showPreviewPlayer }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${info.displayWidth}×${info.displayHeight} · ${info.shortCodecName} · ${info.frameRate.roundToInt()}fps · ${formatFileSize(info.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = { showPreviewPlayer = !showPreviewPlayer },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.subtleSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showPreviewPlayer) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = colors.accentText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (showPreviewPlayer) "收起" else "预览",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accentText
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showPreviewPlayer) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        VideoPreviewPlayer(
                            uri = info.uri,
                            seekPreviewMs = config.trimStartMs.takeIf { it > 0L }
                        )
                    }
                }
            }
        }

        // 2. High-Contrast Compatibility Status Banner
        val issues = info.compatibilityIssues
        if (issues.isNotEmpty()) {
            Surface(
                onClick = { onOpenSheet(BentoSheetType.DIAGNOSIS) },
                shape = RoundedCornerShape(16.dp),
                color = colors.warningBg,
                border = BorderStroke(1.dp, colors.warningBorder.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = colors.warningText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "检测到 ${issues.size} 项兼容/上传问题（${issues.take(2).joinToString("、") { it.title }}）",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.warningText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "详情 ›",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.warningText
                    )
                }
            }
        } else {
            Surface(
                onClick = { onOpenSheet(BentoSheetType.DIAGNOSIS) },
                shape = RoundedCornerShape(16.dp),
                color = colors.successBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.successAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "原视频兼容性良好，可直接按需压缩上传体积",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.successText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "参数 ›",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.successText
                    )
                }
            }
        }

        // 3. Quick One-Tap Presets Strip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(ConversionPreset.entries) { preset ->
                val selected = config.presetId == preset.id
                Surface(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) colors.actionButtonBg else colors.cardBackground,
                    border = if (selected) null else BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) colors.actionButtonText else colors.primaryText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

        // 4. Core 2x2 Bento Dashboard Grid
        val sizeHeadline = when (config.compressionMode) {
            CompressionMode.TARGET_SIZE -> "≤ ${config.targetSizeMb.roundToInt()} MB"
            CompressionMode.QUALITY_LEVEL -> config.qualityTier.label
        }
        val estReduction = if (info.fileSizeBytes > 0L) {
            (((info.fileSizeBytes - plan.estimatedOutputBytes).toDouble() / info.fileSizeBytes) * 100).roundToInt()
        } else 0
        val sizeSub = when {
            plan.autoDownscaledNotice?.contains("不膨胀") == true ->
                String.format(Locale.US, "预估 %.1f MB · 不膨胀", plan.estimatedSizeMb)
            plan.autoDownscaledNotice != null -> "已自动降分辨率防糊"
            estReduction > 5 -> String.format(Locale.US, "预估 %.1f MB (-%d%%)", plan.estimatedSizeMb, estReduction)
            else -> String.format(Locale.US, "预估 %.1f MB", plan.estimatedSizeMb)
        }

        val resHeadline = "${minOf(plan.outputWidth, plan.outputHeight)}p · ${(plan.targetFps ?: info.frameRate).roundToInt()}fps"
        val resSub = if (config.aspectRatio == AspectRatioOption.ORIGINAL) {
            "原比例 · 8px 对齐"
        } else {
            "${config.aspectRatio.label} · ${config.aspectFitMode.label}"
        }

        val compatHeadline = "${if (config.videoCodec == OutputVideoCodec.H264) "H.264" else "H.265"} + AAC"
        val compatSub = if (config.toneMapHdrToSdr) "HDR → SDR 防发灰已开" else "保留原始色彩空间"

        val effDur = config.effectiveDurationMs(info.durationMs)
        val isTrimmed = effDur < info.durationMs - 500L
        val trimHeadline = if (isTrimmed) "已裁 ${formatDuration(effDur)}" else "${formatDuration(info.durationMs)} 完整"
        val trimSub = buildList {
            if (config.stripLocationMetadata) add("去 GPS")
            if (config.removeAudio) add("已静音") else add("保留声音")
        }.joinToString(" · ")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoGridTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Compress,
                label = "上传体积 / 画质",
                headline = sizeHeadline,
                headlineColor = colors.accentText,
                subtitle = sizeSub,
                subtitleColor = if (estReduction > 5) colors.successAccent else colors.secondaryText,
                onClick = { onOpenSheet(BentoSheetType.SIZE) }
            )
            BentoGridTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AspectRatio,
                label = "画面与帧率",
                headline = resHeadline,
                headlineColor = colors.primaryText,
                subtitle = resSub,
                subtitleColor = colors.secondaryText,
                onClick = { onOpenSheet(BentoSheetType.RESOLUTION) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoGridTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.HighQuality,
                label = "兼容性与色彩",
                headline = compatHeadline,
                headlineColor = colors.primaryText,
                subtitle = compatSub,
                subtitleColor = if (config.toneMapHdrToSdr) colors.accentText else colors.secondaryText,
                onClick = { onOpenSheet(BentoSheetType.COMPAT) }
            )
            BentoGridTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCut,
                label = "剪辑与隐私",
                headline = trimHeadline,
                headlineColor = colors.primaryText,
                subtitle = trimSub,
                subtitleColor = colors.secondaryText,
                onClick = { onOpenSheet(BentoSheetType.TRIM_PRIVACY) }
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BentoGridTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    headline: String,
    headlineColor: Color,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    val colors = VidFitPalette.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryText,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = headline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headlineColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BentoBottomActionDock(
    originalBytes: Long,
    plan: EncodingPlan,
    enabled: Boolean,
    onStartConversion: () -> Unit
) {
    val colors = VidFitPalette.colors
    Surface(
        color = colors.cardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = colors.cardBorder)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onStartConversion,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.actionButtonBg,
                    contentColor = colors.actionButtonText
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "开始转换并存入相册",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val est = String.format(Locale.US, "~%.1f MB", plan.estimatedSizeMb)
                        Text(
                            text = "${formatFileSize(originalBytes)} → $est",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
