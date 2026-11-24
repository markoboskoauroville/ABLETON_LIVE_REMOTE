import AppKit
import CryptoKit
import Network

/// The phone's line: ONE port, 8966 (MANTRA_PORTS name `ableton-live-remote`), HTTP and a WebSocket on it, announced by
/// Bonjour as `_mantralive._tcp` so the phone finds the Mac on the same Wi-Fi by itself.
///   GET /hello            {"name","app","version"}
///   GET /ws               WebSocket: the helper pushes the set's state (JSON) whenever it changes (4 a second while
///                         Live plays); the phone sends orders as text, "c=play", "c=arm&t=3&v=1" (Live.order)
///   GET /state            the state once (for a test from the shell)
///   GET /do?c=...         one order (the same words as over the WebSocket)
///   GET /wave?t=N         track N's waveform on the timeline (Peaks.wave)
///   GET /window           the Ableton Live window's rectangle on the screen, in points and pixels (the SESSION tab)
/// Only the home network, Tailscale and this Mac itself (adb reverse) are answered.
let PORT: UInt16 = 8966

final class Peer {
    let c: NWConnection
    var buffer = Data()
    var ws = false
    let who: String
    init(_ c: NWConnection, who: String) { self.c = c; self.who = who }
}

final class Server {
    let live: Live
    let peaks = Peaks()
    var onSeen: (String) -> Void = { _ in }
    var onPhones: () -> Void = {}
    private var listener: NWListener?
    private var peers: [ObjectIdentifier: Peer] = [:]
    private(set) var status = "starting"
    let name = Host.current().localizedName ?? "Mac"

    init(live: Live) { self.live = live }

    /// The phones holding a WebSocket now.
    var phones: [String] { peers.values.filter { $0.ws }.map { $0.who } }

    func start() {
        guard listener == nil, let port = NWEndpoint.Port(rawValue: PORT),
              let l = try? NWListener(using: .tcp, on: port) else { status = "port \(PORT) is taken"; return }
        l.service = NWListener.Service(name: name, type: "_mantralive._tcp")
        l.newConnectionHandler = { [weak self] c in self?.serve(c) }
        l.stateUpdateHandler = { [weak self] st in
            switch st {
            case .ready:
                self?.status = "listening on \(PORT)"
                Ports.record("ableton-live-remote", PORT, udp: false, health: "/hello")
            case .failed(let e):
                self?.status = "failed: \(e)"
                self?.listener = nil
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) { self?.start() }
            default: break
            }
        }
        l.start(queue: .main)
        listener = l
    }

    /// The set changed: every phone gets it now.
    func broadcast() {
        guard !peers.isEmpty else { return }
        let body = json(live.json)
        for p in peers.values where p.ws { sendText(p, body) }
    }

    // MARK: who may ask

    private static func ip(_ c: NWConnection) -> String? {
        guard case let .hostPort(host, _) = c.endpoint else { return nil }
        return "\(host)".replacingOccurrences(of: "::ffff:", with: "").components(separatedBy: "%")[0]
    }

    private func allowed(_ ip: String) -> Bool {
        if ip == "127.0.0.1" || ip == "::1" { return true }
        let p = ip.split(separator: ".").compactMap { Int($0) }
        guard p.count == 4 else { return false }
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && (16...31).contains(p[1]))
            || (p[0] == 100 && (64...127).contains(p[1]))
    }

    // MARK: HTTP

    private func serve(_ c: NWConnection) {
        guard let ip = Server.ip(c), allowed(ip) else { c.cancel(); return }
        let p = Peer(c, who: ip == "127.0.0.1" || ip == "::1" ? "the cable (adb)" : ip)
        let id = ObjectIdentifier(p)
        peers[id] = p
        c.stateUpdateHandler = { [weak self] st in
            switch st {
            case .failed, .cancelled: self?.drop(id)
            default: break
            }
        }
        c.start(queue: .main)
        readRequest(p)
    }

    private func drop(_ id: ObjectIdentifier) {
        guard let p = peers.removeValue(forKey: id) else { return }
        if p.ws { live.watchers = phones.count; onPhones() }
        p.c.cancel()
    }

    private func readRequest(_ p: Peer) {
        p.c.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, done, err in
            guard let self = self else { return }
            if let d = data { p.buffer.append(d) }
            if let r = p.buffer.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(decoding: p.buffer[p.buffer.startIndex..<r.lowerBound], as: UTF8.self)
                let rest = Data(p.buffer[r.upperBound...])
                p.buffer = rest
                self.handle(p, head)
                return
            }
            if done || err != nil || p.buffer.count > 16384 { self.drop(ObjectIdentifier(p)); return }
            self.readRequest(p)
        }
    }

    private func handle(_ p: Peer, _ head: String) {
        let lines = head.components(separatedBy: "\r\n")
        let parts = (lines.first ?? "").split(separator: " ")
        guard parts.count >= 2, let u = URLComponents(string: String(parts[1])) else { drop(ObjectIdentifier(p)); return }
        var headers: [String: String] = [:]
        for l in lines.dropFirst() {
            guard let colon = l.firstIndex(of: ":") else { continue }
            headers[l[..<colon].trimmingCharacters(in: .whitespaces).lowercased()] =
                l[l.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        var q: [String: String] = [:]
        for i in u.queryItems ?? [] { q[i.name] = i.value ?? "" }
        onSeen(p.who)
        switch u.path {
        case "/ws":
            guard (headers["upgrade"] ?? "").lowercased() == "websocket", let key = headers["sec-websocket-key"] else {
                reply(p, Data("WebSocket only".utf8), status: "400 Bad Request", type: "text/plain"); return
            }
            let accept = Data(Insecure.SHA1.hash(data: Data((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").utf8)))
                .base64EncodedString()
            let resp = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: \(accept)\r\n\r\n"
            p.c.send(content: Data(resp.utf8), completion: .contentProcessed { _ in })
            p.ws = true
            live.watchers = phones.count
            onPhones()
            sendText(p, json(live.json))
            readFrames(p)
        case "/hello":
            reply(p, json(["name": name, "app": "ableton-live-remote", "version": VERSION]))
        case "/state":
            live.lookNow()
            reply(p, json(live.json))
        case "/do":
            let ok = live.order(q)
            reply(p, json(["ok": ok]))
        case "/wave":
            let t = Int(q["t"] ?? "") ?? -1
            let tempo = live.snap.tempo, end = max(live.snap.end, live.snap.loopStart + live.snap.loopLen)
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                guard let self = self else { return }
                let body: Data
                if t >= 0, let clips = Live.clips(t) {
                    body = self.json(self.peaks.wave(track: t, clips: clips, tempo: tempo, end: end))
                } else {
                    body = self.json(["t": t, "error": "Live did not answer"])
                }
                DispatchQueue.main.async { self.reply(p, body) }
            }
        case "/window":
            reply(p, json(LiveWindow.find()))
        default:
            reply(p, Data("no such thing".utf8), status: "404 Not Found", type: "text/plain")
        }
    }

    private func json(_ j: [String: Any]) -> Data { (try? JSONSerialization.data(withJSONObject: j)) ?? Data("{}".utf8) }

    private func reply(_ p: Peer, _ body: Data, status: String = "200 OK", type: String = "application/json") {
        var out = Data(("HTTP/1.1 \(status)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\n\r\n").utf8)
        out.append(body)
        let id = ObjectIdentifier(p)
        p.c.send(content: out, completion: .contentProcessed { [weak self] _ in self?.drop(id) })
    }

    // MARK: WebSocket (RFC 6455, the small part a phone needs: text, ping, close)

    private func readFrames(_ p: Peer) {
        parseFrames(p)
        guard peers[ObjectIdentifier(p)] != nil else { return }
        p.c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self = self else { return }
            if let d = data { p.buffer.append(d) }
            if done || err != nil { self.drop(ObjectIdentifier(p)); return }
            self.readFrames(p)
        }
    }

    private func parseFrames(_ p: Peer) {
        while true {
            let b = [UInt8](p.buffer)
            guard b.count >= 2 else { return }
            let op = b[0] & 0x0F
            let masked = (b[1] & 0x80) != 0
            var len = Int(b[1] & 0x7F)
            var at = 2
            if len == 126 {
                guard b.count >= 4 else { return }
                len = Int(b[2]) << 8 | Int(b[3]); at = 4
            } else if len == 127 {
                guard b.count >= 10 else { return }
                len = 0
                for i in 2..<10 { len = len << 8 | Int(b[i]) }
                at = 10
            }
            guard len >= 0, len < 1 << 20 else { drop(ObjectIdentifier(p)); return }
            var mask: [UInt8] = [0, 0, 0, 0]
            if masked {
                guard b.count >= at + 4 else { return }
                mask = Array(b[at..<at + 4]); at += 4
            }
            guard b.count >= at + len else { return }
            var payload = Array(b[at..<at + len])
            if masked { for i in 0..<len { payload[i] ^= mask[i & 3] } }
            p.buffer = Data(b[(at + len)...])
            switch op {
            case 0x1:
                let text = String(decoding: payload, as: UTF8.self)
                var q: [String: String] = [:]
                for i in URLComponents(string: "/?" + text)?.queryItems ?? [] { q[i.name] = i.value ?? "" }
                live.order(q)
            case 0x8:
                sendFrame(p, op: 0x8, payload: [])
                drop(ObjectIdentifier(p)); return
            case 0x9:
                sendFrame(p, op: 0xA, payload: payload)
            default:
                break
            }
        }
    }

    private func sendText(_ p: Peer, _ d: Data) { sendFrame(p, op: 0x1, payload: [UInt8](d)) }

    private func sendFrame(_ p: Peer, op: UInt8, payload: [UInt8]) {
        var f: [UInt8] = [0x80 | op]
        let n = payload.count
        if n < 126 { f.append(UInt8(n)) }
        else if n < 65536 { f += [126, UInt8(n >> 8), UInt8(n & 0xFF)] }
        else { f.append(127); for i in (0..<8).reversed() { f.append(UInt8((n >> (i * 8)) & 0xFF)) } }
        f += payload
        p.c.send(content: Data(f), completion: .contentProcessed { _ in })
    }
}

/// The Ableton Live window on the screen (CGWindowList: the bounds need no permission), for the SESSION tab's crop.
enum LiveWindow {
    static func find() -> [String: Any] {
        guard let list = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID)
                as? [[String: Any]] else { return ["found": false] }
        var best: CGRect = .zero
        for w in list {
            guard (w[kCGWindowOwnerName as String] as? String) == "Live",
                  (w[kCGWindowLayer as String] as? Int) == 0,
                  let bd = w[kCGWindowBounds as String] as? [String: Any],
                  let r = CGRect(dictionaryRepresentation: bd as CFDictionary) else { continue }
            if r.width * r.height > best.width * best.height { best = r }
        }
        guard best.width > 0 else { return ["found": false] }
        // CGWindow bounds are in points from the top left of the main screen; the VNC picture is in pixels
        let main = NSScreen.screens.first
        let scale = main?.backingScaleFactor ?? 1
        let total = NSScreen.screens.reduce(CGRect.zero) { $0.union($1.frame) }
        return ["found": true, "x": best.minX, "y": best.minY, "w": best.width, "h": best.height, "scale": scale,
                "screenW": main?.frame.width ?? 0, "screenH": main?.frame.height ?? 0,
                "desktopW": total.width, "desktopH": total.height]
    }
}

/// The cable, or wireless ADB, when the Wi-Fi is not shared: every 5 s each phone adb sees gets
/// `adb reverse tcp:8966 tcp:8966`, so on the phone the Mac is at 127.0.0.1:8966 (as Stream Deck Remote).
final class Cable {
    private let adb = NSHomeDirectory() + "/Library/Android/sdk/platform-tools/adb"
    private(set) var phones: [String] = []
    var onChange: () -> Void = {}

    func start() {
        Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.tick() }
        tick()
    }

    private func run(_ args: [String]) -> String {
        let p = Process(); p.executableURL = URL(fileURLWithPath: adb); p.arguments = args
        let out = Pipe(); p.standardOutput = out; p.standardError = Pipe()
        guard (try? p.run()) != nil else { return "" }
        let d = out.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        return String(data: d, encoding: .utf8) ?? ""
    }

    private func tick() {
        guard FileManager.default.isExecutableFile(atPath: adb) else { return }
        DispatchQueue.global().async {
            let serials = self.run(["devices"]).split(separator: "\n").dropFirst().compactMap { l -> String? in
                let f = l.split(separator: "\t"); return f.count == 2 && f[1] == "device" ? String(f[0]) : nil
            }
            var names: [String] = []
            for s in serials {
                if !self.run(["-s", s, "reverse", "--list"]).contains("tcp:\(PORT)") {
                    _ = self.run(["-s", s, "reverse", "tcp:\(PORT)", "tcp:\(PORT)"])
                }
                let model = self.run(["-s", s, "shell", "getprop", "ro.product.model"]).trimmingCharacters(in: .whitespacesAndNewlines)
                names.append(model.isEmpty ? s : model)
            }
            DispatchQueue.main.async {
                if names != self.phones { self.phones = names; self.onChange() }
            }
        }
    }
}
