// Ableton Live Remote (Mantra): the Mac's half of the Android remote for Ableton Live (Marko, 24.11.2026: "We are
// building Ableton Live remote controller Android app ... control the playback of Ableton Live"). It is the only one
// that talks to AbletonOSC for this product, mirrors the set to the phone over one port (8966: HTTP + WebSocket),
// computes the waveforms, and says where the Live window is. A small status window; its X quits the app.
import AppKit
import SwiftUI

let VERSION = 1
let APP_NAME = "Ableton Live Remote (Mantra)"

final class Model: ObservableObject {
    @Published var liveLine = "asking Ableton Live…"
    @Published var phones: [String] = []
    @Published var lastPhone = "no phone yet"
    @Published var serverLine = ""
    @Published var addresses: [String] = []
    @Published var cable: [String] = []

    let live = Live()
    lazy var server = Server(live: live)
    let cableLink = Cable()

    func start() {
        live.onChange = { [weak self] in self?.server.broadcast(); self?.refresh() }
        server.onPhones = { [weak self] in self?.refresh() }
        server.onSeen = { [weak self] who in self?.lastPhone = who }
        cableLink.onChange = { [weak self] in self?.refresh() }
        live.start()
        server.start()
        cableLink.start()
        Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in self?.refresh() }
        refresh()
    }

    func refresh() {
        let s = live.snap
        if live.watchers == 0 {
            liveLine = "Ableton Live is asked only while a phone is connected"
        } else if !live.answering {
            liveLine = "Ableton Live does not answer (is it open, with AbletonOSC as a Control Surface?)"
        } else {
            liveLine = "Ableton Live answers · \(s.tracks.count) tracks · \(s.playing ? "playing" : "stopped") · " +
                String(format: "%.1f BPM", s.tempo) + (s.metro ? " · metronome on" : "")
        }
        phones = server.phones
        serverLine = server.status
        addresses = Model.ipv4()
        cable = cableLink.phones
    }

    /// This Mac's addresses a phone can type (Wi-Fi, cable, Tailscale), without the loopback.
    static func ipv4() -> [String] {
        var out: [String] = []
        var ifa: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifa) == 0, let first = ifa else { return out }
        defer { freeifaddrs(ifa) }
        for p in sequence(first: first, next: { $0.pointee.ifa_next }) {
            guard let sa = p.pointee.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: p.pointee.ifa_name)
            guard name != "lo0" else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                let ip = String(cString: host)
                if !ip.hasPrefix("169.254.") && !ip.hasPrefix("198.18.") { out.append("\(ip)  (\(name))") }
            }
        }
        return out
    }
}

struct Panel: View {
    @ObservedObject var m: Model
    private let green = Color(red: 0x33 / 255.0, green: 0xD1 / 255.0, blue: 0x7A / 255.0)
    private let grey = Color(white: 0.6)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Circle().fill(m.phones.isEmpty ? Color.orange : green).frame(width: 10, height: 10)
                    Text(m.phones.isEmpty ? "No phone connected" :
                            "\(m.phones.count) phone\(m.phones.count == 1 ? "" : "s") connected")
                        .font(.system(size: 15, weight: .semibold))
                    Spacer()
                    Text("v\(VERSION)").font(.system(size: 12)).foregroundColor(grey)
                }
                section("ABLETON LIVE") { line(m.liveLine) }
                section("THE PHONE") {
                    line("Ableton Live Remote on the phone finds this Mac by itself on the same Wi-Fi, over the cable, or over Tailscale.")
                    if !m.phones.isEmpty { line("Connected: " + m.phones.joined(separator: ", ")) }
                    line("Last asked by: \(m.lastPhone)")
                    line("Server: \(m.serverLine)")
                }
                section("THIS MAC'S ADDRESSES (port \(PORT))") {
                    ForEach(m.addresses, id: \.self) { a in line(a).textSelection(.enabled) }
                }
                section("CABLE / ADB") {
                    line(m.cable.isEmpty ? "no phone on adb (not needed on Wi-Fi)" : m.cable.joined(separator: ", ") + " · adb reverse on")
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(minWidth: 360, minHeight: 260)
        .background(Color(red: 0x0B / 255.0, green: 0x0B / 255.0, blue: 0x0C / 255.0))
    }

    @ViewBuilder private func section<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 11, weight: .bold)).foregroundColor(grey)
            c()
        }
    }

    private func line(_ t: String) -> some View {
        Text(t).font(.system(size: 12)).fixedSize(horizontal: false, vertical: true)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    let model = Model()
    var window: NSWindow!

    func applicationDidFinishLaunching(_ n: Notification) {
        model.start()
        // a plain standard window (MANTRA_MANIFEST standard-mac-windows): its title once, its X quits the app
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 440, height: 420),
                          styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        window.title = APP_NAME
        window.isReleasedWhenClosed = false
        window.contentViewController = NSHostingController(rootView: Panel(m: model))
        window.minSize = NSSize(width: 360, height: 260)
        window.appearance = NSAppearance(named: .darkAqua)
        if !window.setFrameUsingName("AbletonLiveRemote") { window.setContentSize(NSSize(width: 440, height: 420)); window.center() }
        window.setFrameAutosaveName("AbletonLiveRemote")
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        Updater.checkAtStart()
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ s: NSApplication) -> Bool { true }

    func applicationShouldHandleReopen(_ s: NSApplication, hasVisibleWindows: Bool) -> Bool {
        window.makeKeyAndOrderFront(nil)
        return true
    }
}

@main
struct Main {
    static func main() {
        let app = NSApplication.shared
        let d = AppDelegate()
        app.delegate = d
        app.setActivationPolicy(.regular)
        let menu = NSMenu(), item = NSMenuItem(), sub = NSMenu()
        sub.addItem(withTitle: "Quit \(APP_NAME)", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        item.submenu = sub
        menu.addItem(item)
        app.mainMenu = menu
        app.run()
    }
}
