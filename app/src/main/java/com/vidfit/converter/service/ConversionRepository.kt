package com.vidfit.converter.service

import com.vidfit.converter.model.ConversionConfig
import com.vidfit.converter.model.ConversionState
import com.vidfit.converter.model.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingConversionJob(
    val info: VideoInfo,
    val config: ConversionConfig
)

/**
 * In-process singleton state bridge between [ConversionService] and the UI ViewModel.
 */
object ConversionRepository {

    private val _state = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    @Volatile
    var pendingJob: PendingConversionJob? = null

    fun updateState(newState: ConversionState) {
        _state.value = newState
    }

    fun resetToIdle() {
        _state.value = ConversionState.Idle
    }
}
