package com.vidfit.converter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidfit.converter.R
import com.vidfit.converter.VidFitApp
import com.vidfit.converter.data.CookieSite
import com.vidfit.converter.data.HistoryStore
import com.vidfit.converter.data.MediaStoreSaver
import com.vidfit.converter.data.SavedDownloadPrefs
import com.vidfit.converter.data.SiteCookieStore
import com.vidfit.converter.data.VideoInspector
import com.vidfit.converter.domain.BitrateCalculator
import com.vidfit.converter.domain.EncodingPlan
import com.vidfit.converter.engine.YtDlpEngine
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
import com.vidfit.converter.model.DownloadState
import com.vidfit.converter.model.FrameRateCap
import com.vidfit.converter.model.OutputVideoCodec
import com.vidfit.converter.model.ParsedLinkInfo
import com.vidfit.converter.model.QualityTier
import com.vidfit.converter.model.ResolutionCap
import com.vidfit.converter.model.SubtitlePreset
import com.vidfit.converter.model.VideoInfo
import com.vidfit.converter.model.YtDlpUpdateState
import com.vidfit.converter.service.ConversionRepository
import com.vidfit.converter.service.ConversionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val urlInput: String = "",
    val parsedLink: ParsedLinkInfo? = null,
    val formatMode: DownloadFormatMode = DownloadFormatMode.VIDEO_MP4,
    /** null means "最高画质" (default). */
    val selectedMaxHeight: Int? = null,
    val embedSubtitles: Boolean = false,
    val subtitlePreset: SubtitlePreset = SubtitlePreset.MULTI_4,
    val includeAutoSubtitles: Boolean = false,
    val selectedSubtitleLangs: Set<String> = emptySet(),
    val autoConvertAfterDownload: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val recentDownloads: List<DownloadResult> = emptyList(),
    val ytDlpVersion: String? = null,
    val useNightlyChannel: Boolean = false,
    val updateState: YtDlpUpdateState = YtDlpUpdateState.Idle,
    val loggedInSiteIds: Set<String> = emptySet(),
    val cookieNotice: String? = null
)

data class VidFitUiState(
    val activeTab: AppTab = AppTab.DOWNLOAD,
    val isInspecting: Boolean = false,
    val inspectError: String? = null,
    val videoInfo: VideoInfo? = null,
    val config: ConversionConfig = ConversionPreset.UNIVERSAL_COMPAT.toConfig(),
    val encodingPlan: EncodingPlan? = null,
    val conversionState: ConversionState = ConversionState.Idle,
    val recentResults: List<ConversionResult> = emptyList(),
    val download: DownloadUiState = DownloadUiState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VidFitApp
    private val inspector = VideoInspector(application.applicationContext)
    private val mediaSaver = MediaStoreSaver(application.applicationContext)
    private val cookieStore = SiteCookieStore(application.applicationContext)
    private val historyStore = HistoryStore(application.applicationContext)
    private val ytDlp: YtDlpEngine get() = app.ytDlpEngine

    private val initialPrefs = historyStore.loadPrefs()

    private val _activeTab = MutableStateFlow(AppTab.DOWNLOAD)
    private val _isInspecting = MutableStateFlow(false)
    private val _inspectError = MutableStateFlow<String?>(null)
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    private val _config = MutableStateFlow(ConversionPreset.UNIVERSAL_COMPAT.toConfig())
    private val _recentResults = MutableStateFlow(historyStore.loadConversions())
    private val _download = MutableStateFlow(
        DownloadUiState(
            formatMode = initialPrefs.formatMode,
            embedSubtitles = initialPrefs.embedSubtitles,
            subtitlePreset = initialPrefs.subtitlePreset,
            includeAutoSubtitles = initialPrefs.includeAutoSubtitles,
            autoConvertAfterDownload = initialPrefs.autoConvertAfterDownload,
            useNightlyChannel = initialPrefs.useNightlyChannel,
            recentDownloads = historyStore.loadDownloads(),
            loggedInSiteIds = cookieStore.getLoggedInSiteIds()
        )
    )

    private var activeDownloadJob: Job? = null

    init {
        viewModelScope.launch {
            ConversionRepository.state.collect { state ->
                if (state is ConversionState.Success) {
                    var updatedList: List<ConversionResult>? = null
                    _recentResults.update { list ->
                        if (list.any { it.outputUri == state.result.outputUri }) {
                            list
                        } else {
                            (listOf(state.result) + list).take(50).also { updatedList = it }
                        }
                    }
                    updatedList?.let { toSave ->
                        viewModelScope.launch(Dispatchers.IO) {
                            historyStore.saveConversions(toSave)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            val ver = ytDlp.getCoreVersion()
            _download.update { it.copy(ytDlpVersion = ver) }
        }
    }

    val uiState: StateFlow<VidFitUiState> = combine(
        _activeTab,
        _isInspecting,
        _inspectError,
        _videoInfo,
        _config,
        ConversionRepository.state,
        _recentResults,
        _download
    ) { values ->
        val tab = values[0] as AppTab
        val inspecting = values[1] as Boolean
        val inspectErr = values[2] as String?
        val info = values[3] as VideoInfo?
        val cfg = values[4] as ConversionConfig
        val convState = values[5] as ConversionState
        @Suppress("UNCHECKED_CAST")
        val recents = values[6] as List<ConversionResult>
        val dl = values[7] as DownloadUiState

        val plan = info?.let { BitrateCalculator.buildPlan(it, cfg) }
        VidFitUiState(
            activeTab = tab,
            isInspecting = inspecting,
            inspectError = inspectErr,
            videoInfo = info,
            config = cfg,
            encodingPlan = plan,
            conversionState = convState,
            recentResults = recents,
            download = dl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = VidFitUiState()
    )

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    // ==================== Cookie & Channel Actions ====================

    fun captureSiteCookies(site: CookieSite) {
        viewModelScope.launch {
            val count = cookieStore.captureFromWebView(site)
            val updatedIds = cookieStore.getLoggedInSiteIds()
            val siteName = app.getString(site.displayNameRes)
            val notice = if (count > 0) {
                app.getString(R.string.cookie_notice_saved, siteName, count)
            } else {
                app.getString(R.string.cookie_notice_empty, siteName)
            }
            _download.update {
                it.copy(loggedInSiteIds = updatedIds, cookieNotice = notice)
            }
        }
    }

    fun clearSiteCookies(site: CookieSite) {
        viewModelScope.launch {
            cookieStore.clearSite(site)
            val siteName = app.getString(site.displayNameRes)
            _download.update {
                it.copy(
                    loggedInSiteIds = cookieStore.getLoggedInSiteIds(),
                    cookieNotice = app.getString(R.string.cookie_notice_cleared, siteName)
                )
            }
        }
    }

    fun clearAllCookies() {
        viewModelScope.launch {
            cookieStore.clearAll()
            _download.update {
                it.copy(
                    loggedInSiteIds = emptySet(),
                    cookieNotice = app.getString(R.string.cookie_notice_cleared_all)
                )
            }
        }
    }

    fun updateNightlyChannel(useNightly: Boolean) {
        _download.update { it.copy(useNightlyChannel = useNightly) }
        persistDownloadPrefs()
    }

    // ==================== Downloader Actions ====================

    fun updateUrlInput(text: String) {
        _download.update { it.copy(urlInput = text) }
    }

    fun onSharedTextReceived(rawText: String) {
        val extracted = YtDlpEngine.extractFirstUrl(rawText) ?: rawText.trim()
        if (extracted.isBlank()) return
        _activeTab.value = AppTab.DOWNLOAD
        _download.update {
            it.copy(
                urlInput = extracted,
                parsedLink = null,
                selectedMaxHeight = null,
                selectedSubtitleLangs = emptySet(),
                downloadState = DownloadState.Idle
            )
        }
        parseCurrentUrl()
    }

    fun parseCurrentUrl() {
        val raw = _download.value.urlInput.trim()
        val url = YtDlpEngine.extractFirstUrl(raw) ?: raw
        if (url.isBlank()) {
            _download.update {
                it.copy(downloadState = DownloadState.Failed(app.getString(R.string.dl_err_empty_url)))
            }
            return
        }
        _download.update {
            it.copy(
                urlInput = url,
                parsedLink = null,
                selectedMaxHeight = null,
                selectedSubtitleLangs = emptySet(),
                downloadState = DownloadState.Parsing
            )
        }
        viewModelScope.launch {
            val result = ytDlp.parseUrl(url, cookiesFile = cookieStore.combinedCookiesFile())
            result.onSuccess { info ->
                _download.update { cur ->
                    val nextPreset = if (cur.subtitlePreset == SubtitlePreset.CUSTOM && !info.hasAnySubtitles) {
                        SubtitlePreset.MULTI_4
                    } else {
                        cur.subtitlePreset
                    }
                    cur.copy(
                        parsedLink = info,
                        selectedMaxHeight = null,
                        selectedSubtitleLangs = emptySet(),
                        subtitlePreset = nextPreset,
                        downloadState = DownloadState.Idle
                    )
                }
            }.onFailure { err ->
                _download.update {
                    it.copy(
                        downloadState = DownloadState.Failed(
                            app.getString(R.string.dl_err_parse, err.localizedMessage ?: "Unknown")
                        )
                    )
                }
            }
        }
    }

    fun updateDownloadFormatMode(mode: DownloadFormatMode) {
        _download.update { it.copy(formatMode = mode) }
        persistDownloadPrefs()
    }

    fun updateSelectedMaxHeight(height: Int?) {
        _download.update { it.copy(selectedMaxHeight = height) }
    }

    fun updateEmbedSubtitles(enabled: Boolean) {
        _download.update { it.copy(embedSubtitles = enabled) }
        persistDownloadPrefs()
    }

    fun updateSubtitlePreset(preset: SubtitlePreset) {
        _download.update { cur ->
            val nextSelected = if (preset == SubtitlePreset.CUSTOM && cur.selectedSubtitleLangs.isEmpty()) {
                val parsed = cur.parsedLink
                val candidates = parsed?.officialSubtitles?.takeIf { it.isNotEmpty() }
                    ?: parsed?.autoSubtitles.orEmpty()
                val preferred = candidates.filter { track ->
                    val base = track.langCode.lowercase().substringBefore('-').substringBefore('_')
                    base in setOf("zh", "en", "ja", "ko")
                }.take(2).map { it.langCode }.toSet()
                preferred.ifEmpty { candidates.take(1).map { it.langCode }.toSet() }
            } else {
                cur.selectedSubtitleLangs
            }
            cur.copy(subtitlePreset = preset, selectedSubtitleLangs = nextSelected)
        }
        if (preset != SubtitlePreset.CUSTOM) {
            persistDownloadPrefs()
        }
    }

    fun updateIncludeAutoSubtitles(enabled: Boolean) {
        _download.update { it.copy(includeAutoSubtitles = enabled) }
        persistDownloadPrefs()
    }

    fun toggleSubtitleLang(langCode: String) {
        _download.update { cur ->
            val updated = cur.selectedSubtitleLangs.toMutableSet().apply {
                if (!add(langCode)) remove(langCode)
            }
            cur.copy(
                subtitlePreset = SubtitlePreset.CUSTOM,
                selectedSubtitleLangs = updated
            )
        }
    }

    fun updateAutoConvertAfterDownload(enabled: Boolean) {
        _download.update { it.copy(autoConvertAfterDownload = enabled) }
        persistDownloadPrefs()
    }

    fun startDownload() {
        val dl = _download.value
        val url = dl.parsedLink?.url ?: YtDlpEngine.extractFirstUrl(dl.urlInput) ?: dl.urlInput.trim()
        if (url.isBlank()) {
            _download.update { it.copy(downloadState = DownloadState.Failed(app.getString(R.string.dl_err_empty_url))) }
            return
        }

        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            _download.update {
                it.copy(downloadState = DownloadState.Downloading(0, 0L, app.getString(R.string.dl_connecting)))
            }
            val parsed = dl.parsedLink
            val selectedIncludesAuto = parsed?.autoSubtitles?.any { it.langCode in dl.selectedSubtitleLangs } ?: false
            val result = ytDlp.download(
                url = url,
                mode = dl.formatMode,
                selectedMaxHeight = dl.selectedMaxHeight,
                embedSubtitles = dl.embedSubtitles,
                subtitlePreset = dl.subtitlePreset,
                selectedSubtitleLangs = dl.selectedSubtitleLangs,
                includeAutoSubtitles = dl.includeAutoSubtitles,
                hasOfficialSubtitles = parsed?.officialSubtitles?.isNotEmpty() ?: true,
                selectedIncludesAutoTrack = selectedIncludesAuto,
                cookiesFile = cookieStore.combinedCookiesFile(),
                onProgress = { pct, eta, line ->
                    val cleanLine = line.trim().take(80).ifBlank { app.getString(R.string.dl_downloading_title) }
                    _download.update { state ->
                        if (state.downloadState is DownloadState.Downloading) {
                            state.copy(downloadState = DownloadState.Downloading(pct, eta, cleanLine))
                        } else {
                            state
                        }
                    }
                }
            )

            result.onSuccess { downloadedFile ->
                try {
                    _download.update { it.copy(downloadState = DownloadState.Saving) }
                    val isAudio = dl.formatMode == DownloadFormatMode.AUDIO_M4A
                    val title = dl.parsedLink?.title ?: downloadedFile.nameWithoutExtension
                    val saved = mediaSaver.saveDownloadedMedia(
                        tempFile = downloadedFile,
                        preferredTitle = title,
                        isAudio = isAudio
                    )
                    runCatching { downloadedFile.parentFile?.deleteRecursively() }

                    val dlResult = DownloadResult(
                        uri = saved.uri,
                        fileName = saved.fileName,
                        title = title,
                        mimeType = saved.mimeType,
                        sizeBytes = saved.sizeBytes,
                        isAudioOnly = isAudio,
                        thumbnailUrl = dl.parsedLink?.thumbnailUrl
                    )

                    var updatedDownloads: List<DownloadResult> = emptyList()
                    _download.update { state ->
                        val nextList = (listOf(dlResult) + state.recentDownloads.filterNot { it.uri == dlResult.uri })
                            .take(50)
                        updatedDownloads = nextList
                        state.copy(
                            downloadState = DownloadState.Success(dlResult),
                            recentDownloads = nextList
                        )
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        historyStore.saveDownloads(updatedDownloads)
                    }

                    if (!isAudio && dl.autoConvertAfterDownload) {
                        openDownloadedInConverter(dlResult)
                    }
                } catch (e: Exception) {
                    runCatching { downloadedFile.parentFile?.deleteRecursively() }
                    _download.update {
                        it.copy(
                            downloadState = DownloadState.Failed(
                                app.getString(R.string.conv_err_save, e.localizedMessage ?: "Unknown")
                            )
                        )
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException || _download.value.downloadState is DownloadState.Cancelled) {
                    _download.update { it.copy(downloadState = DownloadState.Cancelled) }
                } else {
                    _download.update {
                        it.copy(
                            downloadState = DownloadState.Failed(
                                app.getString(R.string.dl_err_download, err.localizedMessage ?: "Unknown")
                            )
                        )
                    }
                }
            }
        }
    }

    fun removeDownloadHistoryItem(item: DownloadResult) {
        var nextList: List<DownloadResult> = emptyList()
        _download.update { state ->
            nextList = state.recentDownloads.filterNot { it.uri == item.uri }
            state.copy(recentDownloads = nextList)
        }
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.saveDownloads(nextList)
        }
    }

    fun clearDownloadHistory() {
        _download.update { it.copy(recentDownloads = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.saveDownloads(emptyList())
        }
    }

    fun removeConversionHistoryItem(item: ConversionResult) {
        val nextList = _recentResults.value.filterNot { it.outputUri == item.outputUri }
        _recentResults.value = nextList
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.saveConversions(nextList)
        }
    }

    fun clearConversionHistory() {
        _recentResults.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.saveConversions(emptyList())
        }
    }

    fun cancelDownload() {
        _download.update { it.copy(downloadState = DownloadState.Cancelled) }
        ytDlp.cancelActiveDownload()
        activeDownloadJob?.cancel()
        activeDownloadJob = null
    }

    fun clearDownloadStatus() {
        _download.update { it.copy(downloadState = DownloadState.Idle) }
    }

    fun clearParsedLink() {
        _download.update {
            it.copy(
                urlInput = "",
                parsedLink = null,
                selectedMaxHeight = null,
                selectedSubtitleLangs = emptySet(),
                downloadState = DownloadState.Idle
            )
        }
    }

    fun updateYtDlpCore() {
        val current = _download.value
        if (current.updateState is YtDlpUpdateState.Updating) return
        _download.update { it.copy(updateState = YtDlpUpdateState.Updating) }
        viewModelScope.launch {
            val status = ytDlp.updateCore(useNightly = current.useNightlyChannel)
            val latestVer = ytDlp.getCoreVersion()
            _download.update {
                it.copy(
                    ytDlpVersion = latestVer ?: it.ytDlpVersion,
                    updateState = status
                )
            }
        }
    }

    fun openDownloadedInConverter(result: DownloadResult) {
        if (result.isAudioOnly) return
        _download.update { it.copy(downloadState = DownloadState.Idle) }
        _activeTab.value = AppTab.CONVERT
        onVideoSelected(result.uri)
    }

    private fun persistDownloadPrefs() {
        val dl = _download.value
        val prefs = SavedDownloadPrefs(
            formatMode = dl.formatMode,
            embedSubtitles = dl.embedSubtitles,
            subtitlePreset = if (dl.subtitlePreset == SubtitlePreset.CUSTOM) {
                SubtitlePreset.MULTI_4
            } else {
                dl.subtitlePreset
            },
            includeAutoSubtitles = dl.includeAutoSubtitles,
            autoConvertAfterDownload = dl.autoConvertAfterDownload,
            useNightlyChannel = dl.useNightlyChannel
        )
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.savePrefs(prefs)
        }
    }

    // ==================== Converter Actions ====================

    fun onVideoSelected(uri: Uri) {
        _activeTab.value = AppTab.CONVERT
        viewModelScope.launch {
            _isInspecting.value = true
            _inspectError.value = null
            ConversionRepository.resetToIdle()
            val result = inspector.inspect(uri)
            result.onSuccess { info ->
                _videoInfo.value = info
                _config.value = ConversionPreset.UNIVERSAL_COMPAT.toConfig(
                    trimStartMs = 0L,
                    trimEndMs = info.durationMs
                )
            }.onFailure { err ->
                _inspectError.value = err.localizedMessage ?: app.getString(R.string.inspect_error_default)
            }
            _isInspecting.value = false
        }
    }

    fun applyPreset(preset: ConversionPreset) {
        val current = _config.value
        _config.value = preset.toConfig(
            trimStartMs = current.trimStartMs,
            trimEndMs = current.trimEndMs
        )
    }

    fun updateVideoCodec(codec: OutputVideoCodec) = updateCustomConfig {
        it.copy(videoCodec = codec)
    }

    fun updateToneMapHdr(enabled: Boolean) = updateCustomConfig {
        it.copy(toneMapHdrToSdr = enabled)
    }

    fun updateResolutionCap(cap: ResolutionCap) = updateCustomConfig {
        it.copy(resolutionCap = cap)
    }

    fun updateFrameRateCap(cap: FrameRateCap) = updateCustomConfig {
        it.copy(frameRateCap = cap)
    }

    fun updateAspectRatio(option: AspectRatioOption) = updateCustomConfig {
        it.copy(aspectRatio = option)
    }

    fun updateAspectFitMode(mode: AspectFitMode) = updateCustomConfig {
        it.copy(aspectFitMode = mode)
    }

    fun updateCompressionMode(mode: CompressionMode) = updateCustomConfig {
        it.copy(compressionMode = mode)
    }

    fun updateQualityTier(tier: QualityTier) = updateCustomConfig {
        it.copy(qualityTier = tier)
    }

    fun updateTargetSizeMb(sizeMb: Float) = updateCustomConfig {
        it.copy(targetSizeMb = sizeMb.coerceIn(2f, 500f))
    }

    fun updateAutoDownscale(enabled: Boolean) = updateCustomConfig {
        it.copy(autoDownscaleForTargetSize = enabled)
    }

    fun updateRemoveAudio(remove: Boolean) = updateCustomConfig {
        it.copy(removeAudio = remove)
    }

    fun updateStripLocation(strip: Boolean) = updateCustomConfig {
        it.copy(stripLocationMetadata = strip)
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _config.update { it.copy(trimStartMs = startMs, trimEndMs = endMs) }
    }

    fun startConversion() {
        val info = _videoInfo.value ?: return
        val config = _config.value
        ConversionService.startConversion(getApplication(), info, config)
    }

    fun cancelConversion() {
        ConversionService.cancelConversion(getApplication())
    }

    fun clearResult() {
        ConversionRepository.resetToIdle()
    }

    fun clearSelectedVideo() {
        ConversionRepository.resetToIdle()
        _videoInfo.value = null
        _inspectError.value = null
    }

    private fun updateCustomConfig(transform: (ConversionConfig) -> ConversionConfig) {
        _config.update { transform(it).copy(presetId = "custom") }
    }
}
