package com.vidfit.converter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidfit.converter.data.CookieSite
import com.vidfit.converter.model.DownloadFormatMode
import com.vidfit.converter.model.DownloadResult
import com.vidfit.converter.model.DownloadState
import com.vidfit.converter.model.YtDlpUpdateState
import com.vidfit.converter.ui.DownloadUiState
import com.vidfit.converter.ui.theme.VidFitPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoDownloadTab(
    state: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onParseUrl: () -> Unit,
    onClearLink: () -> Unit,
    onFormatModeChange: (DownloadFormatMode) -> Unit,
    onSelectedMaxHeightChange: (Int?) -> Unit,
    onEmbedSubtitlesChange: (Boolean) -> Unit,
    onAutoConvertChange: (Boolean) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadDialog: () -> Unit,
    onToggleNightlyChannel: (Boolean) -> Unit,
    onUpdateCore: () -> Unit,
    onSaveSiteCookies: (CookieSite) -> Unit,
    onClearSiteCookies: (CookieSite) -> Unit,
    onClearAllCookies: () -> Unit,
    onConvertDownloaded: (DownloadResult) -> Unit,
    onShareDownloaded: (DownloadResult) -> Unit,
    onPlayDownloaded: (DownloadResult) -> Unit,
    onRemoveDownloadItem: (DownloadResult) -> Unit,
    onClearDownloadHistory: () -> Unit
) {
    val colors = VidFitPalette.colors
    val clipboard = LocalClipboardManager.current
    var showCookieSheet by remember { mutableStateOf(false) }
    var loginDialogSite by remember { mutableStateOf<CookieSite?>(null) }

    if (showCookieSheet) {
        CookieManagerBottomSheet(
            loggedInSiteIds = state.loggedInSiteIds,
            onDismiss = { showCookieSheet = false },
            onLoginSite = { site ->
                showCookieSheet = false
                loginDialogSite = site
            },
            onClearSite = onClearSiteCookies,
            onClearAll = onClearAllCookies
        )
    }

    val activeLoginSite = loginDialogSite
    if (activeLoginSite != null) {
        SiteCookieLoginDialog(
            site = activeLoginSite,
            onDismiss = { loginDialogSite = null },
            onSaveCookies = { site ->
                loginDialogSite = null
                onSaveSiteCookies(site)
            }
        )
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.actionButtonBg,
        selectedLabelColor = colors.actionButtonText,
        containerColor = colors.subtleSurface,
        labelColor = colors.primaryText
    )

    // Centered progress / result dialog for active downloads
    DownloadFocusDialog(
        state = state.downloadState,
        thumbnailUrl = state.parsedLink?.thumbnailUrl,
        title = state.parsedLink?.title,
        onCancel = onCancelDownload,
        onDismiss = onDismissDownloadDialog,
        onRetry = onStartDownload,
        onConvert = onConvertDownloaded,
        onShare = onShareDownloaded,
        onPlay = onPlayDownloaded
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Link Input Bento Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "粘贴视频链接（或从 App 直接分享到 Vidow）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText
                    )
                }

                OutlinedTextField(
                    value = state.urlInput,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text(
                            "支持 YouTube、B站、X、TikTok、Instagram 等...",
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    trailingIcon = {
                        if (state.urlInput.isNotEmpty()) {
                            IconButton(onClick = onClearLink) {
                                Icon(Icons.Rounded.Clear, contentDescription = "清空", tint = colors.secondaryText)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.primaryText,
                        unfocusedTextColor = colors.primaryText,
                        focusedBorderColor = colors.actionButtonBg,
                        unfocusedBorderColor = colors.cardBorder
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                onUrlChange(clipText)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            tint = colors.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("粘贴剪贴板", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onParseUrl,
                        enabled = state.urlInput.isNotBlank() && state.downloadState !is DownloadState.Parsing,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionButtonBg,
                            contentColor = colors.actionButtonText
                        )
                    ) {
                        if (state.downloadState is DownloadState.Parsing) {
                            CircularProgressIndicator(
                                color = colors.actionButtonText,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("解析中...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("解析视频", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Parsed Link Configuration Bento Card (with Thumbnail Preview + Dynamic Real Qualities!)
        val parsed = state.parsedLink
        if (parsed != null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.cardBackground,
                border = BorderStroke(1.dp, colors.actionButtonBg.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail preview with site & duration badges overlaid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        MediaThumbnailBox(
                            thumbnailUrl = parsed.thumbnailUrl,
                            showPlayBadge = false,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Top-left source badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = parsed.extractor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        // Bottom-right duration badge
                        if (parsed.durationSeconds > 0L) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formatDuration(parsed.durationSeconds * 1000L),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Text(
                        text = parsed.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "作者：${parsed.uploader}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )

                    // Live-updating video/audio format & codec info pills based on current selection
                    val activeQual = parsed.activeQuality(state.formatMode, state.selectedMaxHeight)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.formatMode == DownloadFormatMode.AUDIO_M4A) {
                            MediaSpecPill(text = "M4A 音频")
                            MediaSpecPill(text = parsed.bestAudioCodec)
                            if (parsed.bestAudioBitrateKbps > 0) {
                                MediaSpecPill(text = "${parsed.bestAudioBitrateKbps} kbps")
                            }
                            parsed.bestAudioSizeBytes?.let {
                                MediaSpecPill(text = "~${formatFileSize(it)}")
                            }
                        } else if (activeQual != null) {
                            if (activeQual.width > 0 && activeQual.height > 0) {
                                MediaSpecPill(text = "${activeQual.width}×${activeQual.height}")
                            }
                            MediaSpecPill(text = activeQual.ext)
                            if (activeQual.videoCodec != "未知") {
                                MediaSpecPill(text = activeQual.videoCodec)
                            }
                            activeQual.audioCodec?.let { MediaSpecPill(text = it) }
                            if (activeQual.fps > 0) {
                                MediaSpecPill(text = "${activeQual.fps} fps")
                            }
                            if (activeQual.bitrateKbps > 0) {
                                val brLabel = if (activeQual.bitrateKbps >= 1000) {
                                    String.format(java.util.Locale.US, "%.1f Mbps", activeQual.bitrateKbps / 1000.0)
                                } else {
                                    "${activeQual.bitrateKbps} kbps"
                                }
                                MediaSpecPill(text = brLabel)
                            }
                            activeQual.approxSizeBytes?.let {
                                MediaSpecPill(text = "~${formatFileSize(it)}")
                            }
                            val nonUniversal = activeQual.videoCodec in setOf("AV1", "VP9", "H.265") ||
                                activeQual.ext in setOf("WEBM", "MKV")
                            if (nonUniversal) {
                                Box(
                                    modifier = Modifier
                                        .background(colors.warningBg, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "⚠ 建议下完后转 H.264 兼容",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.warningText
                                    )
                                }
                            }
                        } else {
                            MediaSpecPill(text = parsed.defaultExt)
                            if (parsed.defaultVideoCodec != "未知") {
                                MediaSpecPill(text = parsed.defaultVideoCodec)
                            }
                            MediaSpecPill(text = parsed.bestAudioCodec)
                        }
                    }

                    HorizontalDivider(color = colors.cardBorder)

                    // Download Format Mode
                    Text(
                        text = "下载类型",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DownloadFormatMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.formatMode == mode,
                                onClick = { onFormatModeChange(mode) },
                                colors = chipColors,
                                label = { Text(mode.label, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                    Text(
                        text = state.formatMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )

                    // Dynamic Quality Chips matching the video's ACTUAL streams (Default: 最高画质)
                    if (state.formatMode != DownloadFormatMode.AUDIO_M4A) {
                        val modeQualities = parsed.qualitiesFor(state.formatMode)
                        Text(
                            text = "选择画质（默认最高画质）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = state.selectedMaxHeight == null,
                                onClick = { onSelectedMaxHeightChange(null) },
                                colors = chipColors,
                                label = {
                                    val bestTag = modeQualities.firstOrNull()?.let { " (${it.label})" } ?: ""
                                    Text("最高画质$bestTag", fontWeight = FontWeight.SemiBold)
                                }
                            )
                            modeQualities.forEach { q ->
                                val sizeHint = q.approxSizeBytes?.let { " · ${formatFileSize(it)}" } ?: ""
                                FilterChip(
                                    selected = state.selectedMaxHeight == q.ytDlpHeight,
                                    onClick = { onSelectedMaxHeightChange(q.ytDlpHeight) },
                                    colors = chipColors,
                                    label = { Text("${q.label}$sizeHint") }
                                )
                            }
                        }

                        HorizontalDivider(color = colors.cardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "下载完自动进入格式转换 / 压大小",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "自动切换到 Bento 转换工作台进行兼容检测或控体积",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Switch(
                                checked = state.autoConvertAfterDownload,
                                onCheckedChange = onAutoConvertChange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "尝试内嵌中/英字幕",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = "源站有官方字幕时自动封装进视频",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                            Switch(
                                checked = state.embedSubtitles,
                                onCheckedChange = onEmbedSubtitlesChange
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.actionButtonBg,
                            contentColor = colors.actionButtonText
                        )
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.formatMode == DownloadFormatMode.AUDIO_M4A) "立即下载音频" else "立即下载视频",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Site Login / Cookie Management Bento Card (Bilibili HD, Xiaohongshu, Douyin, Weibo)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cookie,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "站点登录（Cookie 增强）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                            val loggedNames = CookieSite.entries
                                .filter { state.loggedInSiteIds.contains(it.id) }
                                .joinToString("、") { it.displayName }
                            Text(
                                text = if (loggedNames.isNotEmpty()) {
                                    "已保存：$loggedNames"
                                } else {
                                    "登录 B站/小红书/抖音/微博 解锁高清与防拦截"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (loggedNames.isNotEmpty()) colors.successAccent else colors.secondaryText
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showCookieSheet = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("管理登录", color = colors.accentText, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!state.cookieNotice.isNullOrBlank()) {
                    Text(
                        text = state.cookieNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentText
                    )
                }
            }
        }

        // 4. yt-dlp Core Engine Stable / Nightly Hot-Update Bento Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "yt-dlp 解析引擎",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Text(
                            text = "当前版本：${state.ytDlpVersion ?: "初始化中..."}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onUpdateCore,
                        enabled = state.updateState !is YtDlpUpdateState.Updating,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.updateState is YtDlpUpdateState.Updating) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("更新核心", color = colors.accentText, fontWeight = FontWeight.SemiBold)
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.useNightlyChannel,
                        onClick = { onToggleNightlyChannel(false) },
                        colors = chipColors,
                        label = { Text("Stable 稳定版", fontWeight = FontWeight.SemiBold) }
                    )
                    FilterChip(
                        selected = state.useNightlyChannel,
                        onClick = { onToggleNightlyChannel(true) },
                        colors = chipColors,
                        label = { Text("Nightly 每日版（国内站点推荐）", fontWeight = FontWeight.SemiBold) }
                    )
                }

                when (val upd = state.updateState) {
                    is YtDlpUpdateState.Updated -> {
                        Text(
                            text = "✓ 已热更新至最新版本：${upd.version}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.successAccent
                        )
                    }
                    is YtDlpUpdateState.UpToDate -> {
                        Text(
                            text = "✓ 当前已是最新（${upd.version}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.successAccent
                        )
                    }
                    is YtDlpUpdateState.Failed -> {
                        Text(
                            text = upd.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
            }
        }

        // 5. Recent Downloads List WITH real video thumbnails (persisted across restarts!)
        if (state.recentDownloads.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已下载记录（${state.recentDownloads.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
                TextButton(onClick = onClearDownloadHistory) {
                    Text("清空记录", color = colors.secondaryText, style = MaterialTheme.typography.labelMedium)
                }
            }
            state.recentDownloads.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MediaThumbnailBox(
                                thumbnailUrl = item.thumbnailUrl,
                                localVideoUri = item.uri,
                                isAudioOnly = item.isAudioOnly,
                                showPlayBadge = !item.isAudioOnly,
                                modifier = Modifier
                                    .size(width = 84.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "${formatFileSize(item.sizeBytes)} · ${if (item.isAudioOnly) "Music/Vidow" else "Movies/Vidow"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRemoveDownloadItem(item) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "移除记录",
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!item.isAudioOnly) {
                                Button(
                                    onClick = { onConvertDownloaded(item) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.actionButtonBg,
                                        contentColor = colors.actionButtonText
                                    )
                                ) {
                                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("转换/压大小", fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { onPlayDownloaded(item) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = colors.primaryText, modifier = Modifier.size(18.dp))
                            }
                            OutlinedButton(
                                onClick = { onShareDownloaded(item) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "分享", tint = colors.accentText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DownloadFocusDialog(
    state: DownloadState,
    thumbnailUrl: String?,
    title: String?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onConvert: (DownloadResult) -> Unit,
    onShare: (DownloadResult) -> Unit,
    onPlay: (DownloadResult) -> Unit
) {
    if (state is DownloadState.Idle || state is DownloadState.Parsing) return

    val colors = VidFitPalette.colors
    val isBusy = state is DownloadState.Downloading || state is DownloadState.Saving

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isBusy,
            dismissOnClickOutside = !isBusy,
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
                    is DownloadState.Downloading -> {
                        if (!thumbnailUrl.isNullOrBlank()) {
                            MediaThumbnailBox(
                                thumbnailUrl = thumbnailUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        val animated by animateFloatAsState(
                            targetValue = state.progressPercent.coerceIn(0, 100) / 100f,
                            animationSpec = tween(200),
                            label = "downloadProgress"
                        )
                        Box(
                            modifier = Modifier.size(118.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(118.dp)) {
                                val stroke = 10.dp.toPx()
                                drawArc(
                                    color = colors.progressTrack,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = colors.actionButtonBg,
                                    startAngle = -90f,
                                    sweepAngle = (animated * 360f).coerceAtLeast(10f),
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${state.progressPercent}%",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.primaryText
                                )
                                if (state.etaSeconds > 0L) {
                                    Text(
                                        text = "约剩 ${state.etaSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = title?.take(36) ?: "正在下载媒体...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("取消下载", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    DownloadState.Saving -> {
                        CircularProgressIndicator(color = colors.actionButtonBg)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "正在合并并保存到系统相册...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                    }

                    is DownloadState.Success -> {
                        val res = state.result
                        if (!res.thumbnailUrl.isNullOrBlank() || !res.isAudioOnly) {
                            MediaThumbnailBox(
                                thumbnailUrl = res.thumbnailUrl,
                                localVideoUri = res.uri,
                                isAudioOnly = res.isAudioOnly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(124.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(Modifier.height(14.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(colors.successBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.successAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = if (res.isAudioOnly) "音频已保存到音乐目录" else "视频已下载到相册",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${res.title} · ${formatFileSize(res.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        if (!res.isAudioOnly) {
                            Button(
                                onClick = { onConvert(res) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.actionButtonBg,
                                    contentColor = colors.actionButtonText
                                )
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("去转换兼容格式 / 压大小", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onShare(res) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("直接分享", color = colors.primaryText)
                            }
                            OutlinedButton(
                                onClick = { onPlay(res) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("播放", color = colors.primaryText)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onDismiss) {
                            Text("完成返回", color = colors.secondaryText)
                        }
                    }

                    is DownloadState.Failed -> {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "下载未完成",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
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
                            Text("重试", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = colors.secondaryText)
                        }
                    }

                    DownloadState.Cancelled -> {
                        Text(
                            text = "已取消下载",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText
                        )
                        Spacer(Modifier.height(16.dp))
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
                            Text("返回", fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun MediaSpecPill(text: String) {
    val colors = VidFitPalette.colors
    Box(
        modifier = Modifier
            .background(colors.subtleSurface, RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText
        )
    }
}
