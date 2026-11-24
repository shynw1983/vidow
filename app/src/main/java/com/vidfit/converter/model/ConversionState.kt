package com.vidfit.converter.model

import android.net.Uri

data class ConversionResult(
    val outputUri: Uri,
    val outputFileName: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val outputWidth: Int,
    val outputHeight: Int,
    val elapsedMs: Long,
    val codecLabel: String,
    val convertedHdrToSdr: Boolean
) {
    val sizeReductionPercent: Int
        get() {
            if (originalSizeBytes <= 0L) return 0
            val diff = originalSizeBytes - outputSizeBytes
            return ((diff.toDouble() / originalSizeBytes.toDouble()) * 100.0).toInt()
        }
}

sealed interface ConversionState {
    data object Idle : ConversionState
    data class Converting(
        val progressPercent: Int,
        val statusText: String,
        val elapsedMs: Long
    ) : ConversionState
    data class Success(val result: ConversionResult) : ConversionState
    data class Failed(val message: String) : ConversionState
    data object Cancelled : ConversionState
}
