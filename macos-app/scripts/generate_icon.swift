import AppKit
import CoreGraphics
import Foundation

// Renders Vidow Scheme C ("V·W 折叠波翼") macOS squircle icon at 1024x1024 PNG.
let outPath = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "/tmp/vidow_icon_1024.png"
let size = 1024
let cs = CGColorSpaceCreateDeviceRGB()
guard let ctx = CGContext(
    data: nil,
    width: size,
    height: size,
    bitsPerComponent: 8,
    bytesPerRow: 0,
    space: cs,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else {
    fatalError("Failed to create CGContext")
}

// Flip coordinate system to top-left origin (matching SVG / Android VectorDrawable)
ctx.translateBy(x: 0, y: CGFloat(size))
ctx.scaleBy(x: 1, y: -1)

// macOS Big Sur+ icon inset (~824x824 rounded squircle centered in 1024x1024 canvas with subtle shadow)
let inset: CGFloat = 100
let rect = CGRect(x: inset, y: inset, width: CGFloat(size) - inset * 2, height: CGFloat(size) - inset * 2)
let radius: CGFloat = 185
let bgPath = CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)

ctx.saveGState()
ctx.setShadow(offset: CGSize(width: 0, height: -18), blur: 36, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.28))
ctx.addPath(bgPath)
ctx.setFillColor(CGColor(red: 0.145, green: 0.388, blue: 0.922, alpha: 1))
ctx.fillPath()
ctx.restoreGState()

// Clip to rounded icon squircle and fill electric blue-cyan gradient
ctx.saveGState()
ctx.addPath(bgPath)
ctx.clip()

let colors = [
    CGColor(red: 0.145, green: 0.388, blue: 0.922, alpha: 1.0), // #2563EB
    CGColor(red: 0.055, green: 0.647, blue: 0.914, alpha: 1.0), // #0EA5E9
    CGColor(red: 0.024, green: 0.714, blue: 0.831, alpha: 1.0)  // #06B6D4
] as CFArray
let grad = CGGradient(colorsSpace: cs, colors: colors, locations: [0.0, 0.52, 1.0])!
ctx.drawLinearGradient(
    grad,
    start: CGPoint(x: rect.minX, y: rect.minY),
    end: CGPoint(x: rect.maxX, y: rect.maxY),
    options: []
)

// Subtle radial highlight top-left
let glowColors = [
    CGColor(red: 1, green: 1, blue: 1, alpha: 0.24),
    CGColor(red: 1, green: 1, blue: 1, alpha: 0.0)
] as CFArray
let glow = CGGradient(colorsSpace: cs, colors: glowColors, locations: [0.0, 1.0])!
ctx.drawRadialGradient(
    glow,
    startCenter: CGPoint(x: rect.minX + 180, y: rect.minY + 140),
    startRadius: 10,
    endCenter: CGPoint(x: rect.minX + 180, y: rect.minY + 140),
    endRadius: 520,
    options: []
)

// Transform coordinate space from 108x108 vector viewport into the 824x824 icon rect (scaled up cleanly!)
let scale = (rect.width / 108.0) * 1.18
ctx.translateBy(x: rect.midX, y: rect.midY)
ctx.scaleBy(x: scale, y: scale)
ctx.translateBy(x: -54, y: -54)

// Translucent rear wing (completing the W silhouette)
let backWing = CGMutablePath()
backWing.move(to: CGPoint(x: 47, y: 69))
backWing.addLine(to: CGPoint(x: 61, y: 42))
backWing.addLine(to: CGPoint(x: 75, y: 69))
backWing.closeSubpath()
ctx.addPath(backWing)
ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.42))
ctx.fillPath()

// Crisp bold white V ribbon stroke
let vPath = CGMutablePath()
vPath.move(to: CGPoint(x: 27, y: 34))
vPath.addLine(to: CGPoint(x: 47, y: 74))
vPath.addLine(to: CGPoint(x: 73, y: 35))

ctx.saveGState()
ctx.setShadow(offset: CGSize(width: 0, height: -4), blur: 10, color: CGColor(red: 0.03, green: 0.18, blue: 0.48, alpha: 0.28))
ctx.addPath(vPath)
ctx.setStrokeColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1.0))
ctx.setLineWidth(11.5)
ctx.setLineCap(.round)
ctx.setLineJoin(.round)
ctx.strokePath()
ctx.restoreGState()

ctx.restoreGState()

// Subtle inner top-edge glass stroke
ctx.addPath(bgPath)
ctx.setStrokeColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.22))
ctx.setLineWidth(3)
ctx.strokePath()

guard let cgImage = ctx.makeImage() else { fatalError("Failed to create image") }
let rep = NSBitmapImageRep(cgImage: cgImage)
guard let pngData = rep.representation(using: .png, properties: [:]) else { fatalError("Failed to encode PNG") }
try pngData.write(to: URL(fileURLWithPath: outPath))
print("Rendered \(outPath)")
