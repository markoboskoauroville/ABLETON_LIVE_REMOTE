// make_icon.swift: the icon, drawn on the GitHub runner by build.sh (nothing compiles on the Mac). Six letters, ABL over
// REM, over one picture, a waveform with a playhead (Marko 4.11.2026: "6 letters in this icon and one symbolic background").
//   swift make_icon.swift <dir.iconset>
import AppKit
let set = CommandLine.arguments[1]
try? FileManager.default.createDirectory(atPath: set, withIntermediateDirectories: true)
func draw(_ px: Int) -> NSBitmapImageRep {
    let s = CGFloat(px)
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: px, pixelsHigh: px, bitsPerSample: 8, samplesPerPixel: 4,
                               hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let m = s * 100 / 1024, box = NSRect(x: m, y: m, width: s - 2 * m, height: s - 2 * m)
    NSColor(srgbRed: 0.07, green: 0.07, blue: 0.08, alpha: 1).setFill()
    NSBezierPath(roundedRect: box, xRadius: s * 185 / 1024, yRadius: s * 185 / 1024).fill()
    // the picture: a waveform across the middle and a playhead
    NSColor(srgbRed: 1.0, green: 0.62, blue: 0.04, alpha: 0.55).setFill()
    let n = 28
    for i in 0..<n {
        let x = box.minX + box.width * (CGFloat(i) + 0.5) / CGFloat(n)
        let h = box.height * (0.08 + 0.30 * abs(sin(CGFloat(i) * 0.9) * cos(CGFloat(i) * 0.31)))
        NSBezierPath(roundedRect: NSRect(x: x - s * 0.008, y: box.midY - h / 2, width: s * 0.016, height: h),
                     xRadius: s * 0.008, yRadius: s * 0.008).fill()
    }
    NSColor(srgbRed: 0.2, green: 0.82, blue: 0.48, alpha: 0.9).setFill()
    NSRect(x: box.minX + box.width * 0.62, y: box.minY + box.height * 0.1, width: s * 0.012, height: box.height * 0.8).fill()
    let font = NSFont.systemFont(ofSize: s * 0.2, weight: .black)
    let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: NSColor.white, .kern: s * 0.01]
    for (t, y) in [("ABL", 0.62), ("REM", 0.18)] {
        let a = NSAttributedString(string: t, attributes: attrs)
        let sz = a.size()
        a.draw(at: NSPoint(x: box.midX - sz.width / 2, y: box.minY + box.height * CGFloat(y)))
    }
    NSGraphicsContext.restoreGraphicsState()
    return rep
}
for (n, p) in [("16x16", 16), ("16x16@2x", 32), ("32x32", 32), ("32x32@2x", 64), ("128x128", 128), ("128x128@2x", 256),
               ("256x256", 256), ("256x256@2x", 512), ("512x512", 512), ("512x512@2x", 1024)] {
    try! draw(p).representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: "\(set)/icon_\(n).png"))
}
