package com.vidfit.converter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidfit.converter.model.ConversionResult
import com.vidfit.converter.model.DownloadResult
import com.vidfit.converter.ui.MainViewModel
import com.vidfit.converter.ui.VidFitScreen
import com.vidfit.converter.ui.theme.VidFitTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickVideoFromGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    private val pickVideoFromFiles = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onVideoSelected(uri)
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.startConversion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            VidFitTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                VidFitScreen(
                    uiState = uiState,
                    onSelectTab = viewModel::selectTab,
                    onUrlChange = viewModel::updateUrlInput,
                    onParseUrl = viewModel::parseCurrentUrl,
                    onClearLink = viewModel::clearParsedLink,
                    onDownloadFormatModeChange = viewModel::updateDownloadFormatMode,
                    onSelectedMaxHeightChange = viewModel::updateSelectedMaxHeight,
                    onEmbedSubtitlesChange = viewModel::updateEmbedSubtitles,
                    onAutoConvertChange = viewModel::updateAutoConvertAfterDownload,
                    onStartDownload = viewModel::startDownload,
                    onCancelDownload = viewModel::cancelDownload,
                    onDismissDownloadDialog = viewModel::clearDownloadStatus,
                    onToggleNightlyChannel = viewModel::updateNightlyChannel,
                    onUpdateYtDlpCore = viewModel::updateYtDlpCore,
                    onSaveSiteCookies = viewModel::captureSiteCookies,
                    onClearSiteCookies = viewModel::clearSiteCookies,
                    onClearAllCookies = viewModel::clearAllCookies,
                    onConvertDownloaded = viewModel::openDownloadedInConverter,
                    onShareDownloaded = ::shareDownloadedMedia,
                    onPlayDownloaded = ::playDownloadedMedia,
                    onRemoveDownloadItem = viewModel::removeDownloadHistoryItem,
                    onClearDownloadHistory = viewModel::clearDownloadHistory,
                    onPickFromGallery = {
                        pickVideoFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickFromFiles = {
                        pickVideoFromFiles.launch(arrayOf("video/*"))
                    },
                    onSelectPreset = viewModel::applyPreset,
                    onUpdateCodec = viewModel::updateVideoCodec,
                    onUpdateToneMapHdr = viewModel::updateToneMapHdr,
                    onUpdateResolution = viewModel::updateResolutionCap,
                    onUpdateFps = viewModel::updateFrameRateCap,
                    onUpdateAspectRatio = viewModel::updateAspectRatio,
                    onUpdateAspectFitMode = viewModel::updateAspectFitMode,
                    onUpdateCompressionMode = viewModel::updateCompressionMode,
                    onUpdateQualityTier = viewModel::updateQualityTier,
                    onUpdateTargetSizeMb = viewModel::updateTargetSizeMb,
                    onUpdateAutoDownscale = viewModel::updateAutoDownscale,
                    onUpdateRemoveAudio = viewModel::updateRemoveAudio,
                    onUpdateStripLocation = viewModel::updateStripLocation,
                    onUpdateTrimRange = viewModel::updateTrimRange,
                    onStartConversion = ::startConversionWithNotificationCheck,
                    onCancelConversion = viewModel::cancelConversion,
                    onClearResult = viewModel::clearResult,
                    onClearVideo = viewModel::clearSelectedVideo,
                    onShareResult = ::shareVideo,
                    onPlayResult = ::playVideo
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (type.startsWith("text/")) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.onSharedTextReceived(sharedText)
            }
            return
        }
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    private fun startConversionWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startConversion()
    }

    private fun shareVideo(result: ConversionResult) {
        shareMediaUri(result.outputUri, "video/mp4", getString(R.string.share_chooser_video))
    }

    private fun playVideo(result: ConversionResult) {
        playMediaUri(result.outputUri, "video/mp4")
    }

    private fun shareDownloadedMedia(result: DownloadResult) {
        shareMediaUri(result.uri, result.mimeType, getString(R.string.share_chooser_media))
    }

    private fun playDownloadedMedia(result: DownloadResult) {
        playMediaUri(result.uri, result.mimeType)
    }

    private fun shareMediaUri(uri: Uri, mimeType: String, chooserTitle: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    private fun playMediaUri(uri: Uri, mimeType: String) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(viewIntent) }
    }
}
