import Foundation

enum BitrateCalculator {
    static let minVideoBitrateBps = 220_000
    static let maxVideoBitrateBps = 35_000_000

    /// Align dimension to multiple of 8 (preserves exact 2160/1080/720/480 while remaining codec-friendly).
    static func alignDimension(_ value: Int) -> Int {
        let clamped = max(64, value)
        return max(64, (clamped / 8) * 8)
    }

    static func buildPlan(info: LocalVideoInfo, config: ConversionConfig) -> EncodingPlan {
        let trimStart = max(0.0, config.trimStartSeconds)
        let trimEnd = config.trimEndSeconds > trimStart
            ? min(info.durationSeconds, config.trimEndSeconds)
            : info.durationSeconds
        let effectiveDuration = max(0.5, trimEnd - trimStart)

        let srcW = max(64, info.width)
        let srcH = max(64, info.height)
        let isPortrait = srcH > srcW

        let targetShortSide: Int = {
            let originalShort = min(srcW, srcH)
            if config.resolutionCap == .original { return originalShort }
            return min(originalShort, config.resolutionCap.rawValue)
        }()

        func dimsForShortSide(_ shortSide: Int) -> (Int, Int) {
            if let ratio = config.aspectRatio.ratioValue {
                if ratio >= 1.0 {
                    let h = alignDimension(shortSide)
                    let w = alignDimension(Int((Double(h) * ratio).rounded()))
                    return (w, h)
                } else {
                    let w = alignDimension(shortSide)
                    let h = alignDimension(Int((Double(w) / ratio).rounded()))
                    return (w, h)
                }
            } else {
                let srcShort = Double(min(srcW, srcH))
                let scale = min(1.0, Double(shortSide) / max(1.0, srcShort))
                let w = alignDimension(Int((Double(srcW) * scale).rounded()))
                let h = alignDimension(Int((Double(srcH) * scale).rounded()))
                return (w, h)
            }
        }

        var (outW, outH) = dimsForShortSide(targetShortSide)

        let srcFps = info.frameRate > 1.0 ? info.frameRate : 30.0
        let outFps: Int = {
            if config.frameRateCap == .original {
                return Int(srcFps.rounded()).clamped(to: 15...120)
            }
            return min(Int(srcFps.rounded()), config.frameRateCap.rawValue).clamped(to: 15...60)
        }()

        let audioBps: Int = (!info.hasAudio || config.removeAudio) ? 0 : {
            if config.compressionMode == .targetSize && config.targetSizeMb <= 10 {
                return 96_000
            }
            return 128_000
        }()

        let codecEfficiency = config.videoCodec == .hevc ? 0.72 : 1.0
        var notice: EncodingNotice? = nil
        var videoBps: Int

        switch config.compressionMode {
        case .quality:
            let pixelsPerSec = Double(outW * outH) * Double(outFps)
            let rawBps = pixelsPerSec * config.qualityTier.bitsPerPixelFactor * codecEfficiency
            videoBps = Int(rawBps.rounded()).clamped(to: minVideoBitrateBps...maxVideoBitrateBps)

            // Avoid unnecessary bloat if source bitrate is known and already modest
            if info.bitrateBps > 0 {
                let sourceVideoCap = Int(Double(info.bitrateBps) * 1.15)
                if sourceVideoCap > minVideoBitrateBps {
                    videoBps = min(videoBps, sourceVideoCap)
                }
            }

        case .targetSize:
            // Reserve ~4% for MP4 container overhead
            let budgetBytes = Double(config.targetSizeMb) * 1024.0 * 1024.0 * 0.96
            let totalTargetBps = (budgetBytes * 8.0) / effectiveDuration
            var computedVideoBps = Int(totalTargetBps - Double(audioBps))

            // Smart no-bloat cap: if original clipped video is already smaller than target MB
            let durationRatio = info.durationSeconds > 0 ? min(1.0, effectiveDuration / info.durationSeconds) : 1.0
            let estimatedSourceClipBytes = Double(info.fileSizeBytes) * durationRatio
            if estimatedSourceClipBytes > 0 && estimatedSourceClipBytes <= budgetBytes {
                let naturalBps = Int(Double(outW * outH * outFps) * QualityTier.high.bitsPerPixelFactor * codecEfficiency)
                let srcBpsCap = info.bitrateBps > 0 ? Int(Double(info.bitrateBps) * 1.05) : naturalBps
                let ceiling = max(minVideoBitrateBps, min(naturalBps, srcBpsCap))
                if computedVideoBps > ceiling {
                    computedVideoBps = ceiling
                    notice = .alreadyUnderTarget(targetMb: config.targetSizeMb)
                }
            }

            videoBps = computedVideoBps.clamped(to: minVideoBitrateBps...maxVideoBitrateBps)

            // Smart auto-downscaling to keep bits-per-pixel sharp when target MB is tight
            if config.autoDownscaleForTargetSize && notice == nil {
                let steps = [1080, 720, 480, 360].filter { $0 < min(outW, outH) }
                let minComfortableBpp = 0.042 * codecEfficiency
                var currentBpp = Double(videoBps) / max(1.0, Double(outW * outH * outFps))
                var downscaledTo: Int? = nil

                for candidate in steps {
                    if currentBpp >= minComfortableBpp { break }
                    let (candW, candH) = dimsForShortSide(candidate)
                    outW = candW
                    outH = candH
                    downscaledTo = isPortrait ? candW : candH
                    currentBpp = Double(videoBps) / max(1.0, Double(outW * outH * outFps))
                }
                if let scaledH = downscaledTo {
                    notice = .autoDownscaled(height: scaledH, targetMb: config.targetSizeMb)
                }
            }
        }

        let estimatedBytes = Int64(((Double(videoBps + audioBps) / 8.0) * effectiveDuration * 1.03).rounded())
        return EncodingPlan(
            outputWidth: outW,
            outputHeight: outH,
            outputFps: outFps,
            videoBitrateBps: videoBps,
            audioBitrateBps: audioBps,
            effectiveDurationSeconds: effectiveDuration,
            estimatedBytes: max(1024, estimatedBytes),
            notice: notice
        )
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
