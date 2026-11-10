import SwiftUI
#if canImport(AppKit)
import AppKit

extension Color {
    func toHex() -> String? {
        let nsColor = NSColor(self)
        guard let rgbColor = nsColor.usingColorSpace(.sRGB) else { return nil }
        let r = Int(round(rgbColor.redComponent * 255))
        let g = Int(round(rgbColor.greenComponent * 255))
        let b = Int(round(rgbColor.blueComponent * 255))
        return String(format: "#%02X%02X%02X", max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))
    }

    init(hex: String) {
        let clean = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: clean).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch clean.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 128, 128, 128)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
#endif

#if canImport(CoreImage)
import CoreImage

func generateQRCodeImage(from string: String) -> NSImage? {
    let context = CIContext()
    guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
    filter.setValue(Data(string.utf8), forKey: "inputMessage")
    filter.setValue("H", forKey: "inputCorrectionLevel")
    if let outputImage = filter.outputImage {
        let transform = CGAffineTransform(scaleX: 8, y: 8)
        let scaledImage = outputImage.transformed(by: transform)
        if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
            return NSImage(cgImage: cgImage, size: NSSize(width: 170, height: 170))
        }
    }
    return nil
}
#endif

public enum MacSidebarSection: String, CaseIterable, Identifiable {
    case system = "system"
    case hardware = "hardware"
    case timetable = "timetable"
    case spotify = "spotify"
    case alarms = "alarms"
    case tools = "tools"
    case settings = "settings"

    public var id: String { rawValue }

    public func title(isVi: Bool) -> String {
        switch self {
        case .system: return isVi ? "Giám sát hệ thống & Đo từ xa" : "System & Telemetry"
        case .hardware: return isVi ? "Phần cứng & Trạm ESP32" : "Hardware & ESP32 Station"
        case .timetable: return isVi ? "Thời khóa biểu (TKB)" : "Timetable (TKB)"
        case .spotify: return isVi ? "Spotify & Âm nhạc" : "Spotify & Media"
        case .alarms: return isVi ? "Báo thức & Còi báo" : "Alarms & Buzzer"
        case .tools: return isVi ? "Công cụ & Groq AI" : "Tools & Groq AI"
        case .settings: return isVi ? "Cài đặt & Giao diện" : "Settings & Appearance"
        }
    }

    public var systemIcon: String {
        switch self {
        case .system: return "gauge.with.dots.needle.50percent"
        case .hardware: return "display.2"
        case .timetable: return "calendar.badge.clock"
        case .spotify: return "music.note"
        case .alarms: return "bell.badge.fill"
        case .tools: return "wand.and.stars"
        case .settings: return "gearshape.fill"
        }
    }

    public var accentColor: Color {
        switch self {
        case .system: return Color(hex: "#10b981")
        case .hardware: return Color(hex: "#38bdf8")
        case .timetable: return Color(hex: "#06b6d4")
        case .spotify: return Color(hex: "#1db954")
        case .alarms: return Color(hex: "#f59e0b")
        case .tools: return Color(hex: "#a855f7")
        case .settings: return Color(hex: "#94a3b8")
        }
    }
}

// MARK: - Liquid Glass Design System

public struct LiquidGlassAtmosphere: View {
    public init() {}
    public var body: some View {
        ZStack {
            Color(hex: "#0b0c16")
                .ignoresSafeArea()

            Circle()
                .fill(Color(hex: "#6366f1").opacity(0.18))
                .frame(width: 520, height: 520)
                .blur(radius: 95)
                .offset(x: 240, y: -220)

            Circle()
                .fill(Color(hex: "#06b6d4").opacity(0.14))
                .frame(width: 440, height: 440)
                .blur(radius: 85)
                .offset(x: -260, y: 240)

            Circle()
                .fill(Color(hex: "#a855f7").opacity(0.12))
                .frame(width: 380, height: 380)
                .blur(radius: 85)
                .offset(x: 120, y: 320)
        }
    }
}

public struct LiquidGlassCard<Content: View>: View {
    let title: String?
    let icon: String?
    let iconColor: Color?
    let content: Content

    public init(
        _ title: String? = nil,
        icon: String? = nil,
        iconColor: Color? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.icon = icon
        self.iconColor = iconColor
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let title = title, !title.isEmpty {
                HStack(spacing: 10) {
                    if let icon = icon {
                        ZStack {
                            Circle()
                                .fill((iconColor ?? .cyan).opacity(0.18))
                                .frame(width: 28, height: 28)
                            Image(systemName: icon)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(iconColor ?? .cyan)
                        }
                    }
                    Text(title)
                        .font(.system(.headline, design: .rounded).weight(.semibold))
                        .foregroundColor(.primary)
                    Spacer()
                }
            }
            content
        }
        .padding(18)
        .background(
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(.ultraThinMaterial)
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.05),
                                Color.white.opacity(0.01)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            }
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.24),
                            Color.white.opacity(0.04)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
        )
        .shadow(color: Color.black.opacity(0.28), radius: 14, x: 0, y: 7)
    }
}

public struct LiquidStatBadge: View {
    let title: String
    let value: String
    let icon: String
    let tint: Color
    var subtext: String? = nil

    public init(title: String, value: String, icon: String, tint: Color, subtext: String? = nil) {
        self.title = title
        self.value = value
        self.icon = icon
        self.tint = tint
        self.subtext = subtext
    }

    public var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(tint.opacity(0.18))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(tint)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
                Text(value)
                    .font(.system(.subheadline, design: .rounded).weight(.bold))
                    .foregroundColor(.primary)
                if let sub = subtext {
                    Text(sub)
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.white.opacity(0.04))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }
}

public struct LiquidGlassToast: View {
    let message: String
    let isError: Bool
    let onDismiss: () -> Void

    public init(message: String, isError: Bool, onDismiss: @escaping () -> Void) {
        self.message = message
        self.isError = isError
        self.onDismiss = onDismiss
    }

    public var body: some View {
        HStack(spacing: 12) {
            Image(systemName: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(isError ? .red : .green)
            Text(message)
                .font(.system(.subheadline, design: .rounded).weight(.medium))
                .foregroundColor(.primary)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(
                    LinearGradient(
                        colors: [Color.white.opacity(0.25), Color.white.opacity(0.06)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
        )
        .shadow(color: Color.black.opacity(0.35), radius: 16, x: 0, y: 8)
        .padding(.horizontal, 24)
        .padding(.top, 12)
    }
}

public struct MainSplitView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var selectedSection: MacSidebarSection? = .system
    @State private var notePendingDelete: NoteItem?

    public init(client: DeskFlowApiClient) {
        self.client = client
    }

    public var body: some View {
        if !client.isPaired {
            MacPairingGateView(client: client)
        } else {
            NavigationSplitView {
            List(MacSidebarSection.allCases, selection: $selectedSection) { section in
                NavigationLink(value: section) {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(section.accentColor.opacity(0.18))
                                .frame(width: 26, height: 26)
                            Image(systemName: section.systemIcon)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(section.accentColor)
                        }
                        Text(section.title(isVi: client.appLanguage == "vi"))
                            .font(.system(.body, design: .rounded).weight(.medium))
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Deskflow")
            .listStyle(.sidebar)
        } detail: {
            ZStack(alignment: .top) {
                LiquidGlassAtmosphere()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        switch selectedSection ?? .system {
                        case .system:
                            DashboardMacView(client: client)
                        case .hardware:
                            StationMacView(client: client)
                        case .timetable:
                            TimetableMacView(client: client)
                        case .spotify:
                            SpotifyMacView(client: client)
                        case .alarms:
                            AlarmsMacView(client: client)
                        case .tools:
                            ToolsMacView(client: client, notePendingDelete: $notePendingDelete)
                        case .settings:
                            SettingsMacView(client: client)
                        }
                    }
                    .padding(26)
                }

                if let err = client.errorBannerMessage {
                    LiquidGlassToast(message: err, isError: true) { client.errorBannerMessage = nil }
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                withAnimation { client.errorBannerMessage = nil }
                            }
                        }
                } else if let msg = client.statusBannerMessage {
                    LiquidGlassToast(message: msg, isError: false) { client.statusBannerMessage = nil }
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                withAnimation { client.statusBannerMessage = nil }
                            }
                        }
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button {
                        Task { await client.refreshAll() }
                    } label: {
                        Label(tr("Refresh"), systemImage: "arrow.clockwise")
                    }
                    .keyboardShortcut("r", modifiers: [.command])
                }
            }
            .alert(
                "Delete Sticky Note?",
                isPresented: Binding(
                    get: { notePendingDelete != nil },
                    set: { if !$0 { notePendingDelete = nil } }
                ),
                presenting: notePendingDelete
            ) { note in
                Button(tr("Delete"), role: .destructive) {
                    Task { await client.deleteNote(noteId: note.id) }
                }
                Button(tr("Cancel"), role: .cancel) {}
            } message: { note in
                Text(L10n.lang == "vi" ? "Bạn có chắc muốn xóa \"\(note.displayText)\"? Ghi chú cũng sẽ được bỏ ghim khỏi trạm ESP32." : "Are you sure you want to delete \"\(note.displayText)\"? This will also unpin it from the ESP32 Station.")
            }
        }
    }
}
}

public struct MacPairingGateView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var pairCode: String = ""
    @State private var isPairing: Bool = false
    @State private var errorMessage: String? = nil
    @State private var qrPayload: String = ""
    @State private var sessionId: String? = nil
    @State private var isLoadingQR: Bool = false
    @State private var pollingTask: Task<Void, Never>? = nil

    public init(client: DeskFlowApiClient) {
        self.client = client
    }

    private func startPairingSession() {
        pollingTask?.cancel()
        isLoadingQR = true
        errorMessage = nil
        pollingTask = Task { @MainActor in
            let fallbackPayload = "deskflow://pair?server=\(client.apiUrl)&name=\(client.deviceName)"
            if qrPayload.isEmpty {
                qrPayload = fallbackPayload
            }
            if let res = await client.createPairingSession(),
               let sid = res.sessionId,
               let payload = res.qrPayload {
                self.sessionId = sid
                self.qrPayload = payload
                self.isLoadingQR = false

                while !Task.isCancelled && !client.isPaired {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    if Task.isCancelled || client.isPaired { break }

                    if let pollRes = await client.pollPairingSession(sessionId: sid) {
                        if pollRes.status == "approved" {
                            break
                        } else if pollRes.status == "rejected" {
                            errorMessage = client.appLanguage == "vi" ? "Yêu cầu ghép nối bị từ chối." : "Pairing request rejected."
                            break
                        }
                    }
                }
            } else {
                isLoadingQR = false
            }
        }
    }

    public var body: some View {
        ZStack {
            LiquidGlassAtmosphere()

            ScrollView {
                VStack(spacing: 28) {
                    Spacer(minLength: 40)

                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(LinearGradient(colors: [Color(hex: "#6366f1"), Color(hex: "#a855f7")], startPoint: .topLeading, endPoint: .bottomTrailing))
                                .frame(width: 80, height: 80)
                                .shadow(color: Color(hex: "#6366f1").opacity(0.4), radius: 16, x: 0, y: 8)
                            Image(systemName: "desktopcomputer.and.arrow.down")
                                .font(.system(size: 34, weight: .semibold))
                                .foregroundColor(.white)
                        }

                        Text(client.appLanguage == "vi" ? "Yêu cầu Ghép nối Thiết bị" : "Device Pairing Required")
                            .font(.system(.largeTitle, design: .rounded).bold())

                        Text(client.appLanguage == "vi" ? "Để sử dụng Trung tâm điều khiển DeskFlow, bạn phải ghép nối máy Mac này với tài khoản hệ sinh thái DeskFlow." : "To access the DeskFlow Control Center, you must pair this Mac with your DeskFlow ecosystem.")
                            .font(.system(.body, design: .rounded))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: 520)
                    }

                    LiquidGlassCard(
                        client.appLanguage == "vi" ? "Ghép nối thiết bị" : "Pair Device",
                        icon: "qrcode.viewfinder",
                        iconColor: .indigo
                    ) {
                        VStack(spacing: 24) {
                            HStack(alignment: .top, spacing: 32) {
                                VStack(spacing: 12) {
                                    let activePayload = !qrPayload.isEmpty ? qrPayload : "deskflow://pair?server=\(client.apiUrl)&name=\(client.deviceName)"
                                    #if canImport(CoreImage)
                                    if isLoadingQR && qrPayload.isEmpty {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.white)
                                            .frame(width: 160, height: 160)
                                            .overlay(ProgressView())
                                            .shadow(color: Color.black.opacity(0.1), radius: 8)
                                    } else if let qrImg = generateQRCodeImage(from: activePayload) {
                                        Image(nsImage: qrImg)
                                            .interpolation(.none)
                                            .resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(width: 160, height: 160)
                                            .padding(10)
                                            .background(Color.white)
                                            .cornerRadius(12)
                                            .shadow(color: Color.black.opacity(0.1), radius: 8)
                                    } else {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.secondary.opacity(0.15))
                                            .frame(width: 160, height: 160)
                                            .overlay(Image(systemName: "qrcode").font(.system(size: 48)).foregroundColor(.secondary))
                                    }
                                    #else
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color.secondary.opacity(0.15))
                                        .frame(width: 160, height: 160)
                                        .overlay(Image(systemName: "qrcode").font(.system(size: 48)).foregroundColor(.secondary))
                                    #endif

                                    Text(client.appLanguage == "vi" ? "Quét bằng app DeskFlow trên điện thoại" : "Scan with DeskFlow mobile app")
                                        .font(.system(size: 11, design: .rounded).weight(.medium))
                                        .foregroundColor(.secondary)
                                        .multilineTextAlignment(.center)
                                        .frame(maxWidth: 170)

                                    HStack(spacing: 6) {
                                        Circle()
                                            .fill(Color.green)
                                            .frame(width: 6, height: 6)
                                        Text(client.appLanguage == "vi" ? "Tự động nhận diện khi quét" : "Auto-detecting approval")
                                            .font(.system(size: 10, design: .rounded))
                                            .foregroundColor(.secondary)
                                    }

                                    Button {
                                        startPairingSession()
                                    } label: {
                                        HStack(spacing: 4) {
                                            Image(systemName: "arrow.clockwise")
                                            Text(client.appLanguage == "vi" ? "Tạo lại mã QR" : "Regenerate QR")
                                        }
                                        .font(.system(size: 10, weight: .medium))
                                    }
                                    .buttonStyle(.plain)
                                    .foregroundColor(.indigo)
                                }

                                Divider()
                                    .frame(height: 220)

                                VStack(alignment: .leading, spacing: 14) {
                                    Text(client.appLanguage == "vi" ? "Hoặc nhập mã ghép nối từ Discord:" : "Or enter pairing code from Discord:")
                                        .font(.system(.subheadline, design: .rounded).weight(.semibold))

                                    HStack(spacing: 6) {
                                        Text(client.appLanguage == "vi" ? "Gõ lệnh" : "Type")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                        Text("/app pair")
                                            .font(.system(.caption, design: .monospaced).bold())
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.indigo.opacity(0.15))
                                            .cornerRadius(4)
                                        Text(client.appLanguage == "vi" ? "trên Discord để nhận mã 8 ký tự." : "in Discord to get your 8-char code.")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(client.appLanguage == "vi" ? "Mã ghép nối (8 ký tự)" : "Pairing Code (8 characters)")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.secondary)

                                        HStack {
                                            TextField("VD: A1B2C3D4", text: $pairCode)
                                                .textFieldStyle(.plain)
                                                .font(.system(.title3, design: .monospaced).bold())
                                                .frame(height: 38)
                                                .padding(.horizontal, 10)
                                                .background(RoundedRectangle(cornerRadius: 8).fill(Color.secondary.opacity(0.1)))

                                            Button {
                                                Task {
                                                    isPairing = true
                                                    errorMessage = nil
                                                    let ok = await client.pairDevice(code: pairCode.trimmingCharacters(in: .whitespaces))
                                                    isPairing = false
                                                    if !ok {
                                                        errorMessage = client.errorBannerMessage ?? (client.appLanguage == "vi" ? "Mã ghép nối không hợp lệ hoặc đã hết hạn." : "Invalid or expired pairing code.")
                                                    }
                                                }
                                            } label: {
                                                HStack(spacing: 6) {
                                                    if isPairing {
                                                        ProgressView().scaleEffect(0.7)
                                                    } else {
                                                        Image(systemName: "link.badge.plus")
                                                    }
                                                    Text(client.appLanguage == "vi" ? "Ghép nối" : "Pair")
                                                }
                                                .frame(height: 38)
                                                .padding(.horizontal, 16)
                                            }
                                            .buttonStyle(.borderedProminent)
                                            .tint(.indigo)
                                            .disabled(pairCode.trimmingCharacters(in: .whitespaces).count < 6 || isPairing)
                                        }
                                    }

                                    if let err = errorMessage {
                                        Text(err)
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundColor(.red)
                                    }

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(client.appLanguage == "vi" ? "Máy chủ: \(client.apiUrl)" : "Server: \(client.apiUrl)")
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 8)
                    }
                    .frame(maxWidth: 680)

                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 24)
            }
        }
        .onAppear {
            startPairingSession()
        }
        .onDisappear {
            pollingTask?.cancel()
            pollingTask = nil
        }
    }
}

// MARK: - Subviews

private struct DashboardMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var pairCode: String = ""
    @State private var quoteTopic: String = "học tập và kiên trì"
    @State private var generatedQuote: String = ""
    @State private var quoteAuthor: String = ""
    @State private var isGeneratingQuote: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(client.appLanguage == "vi" ? "Trung tâm điều khiển DeskFlow macOS" : "Deskflow macOS Control Center")
                .font(.system(.largeTitle, design: .rounded).bold())

            // 0. Pairing & Authentication
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Xác thực & Liên kết thiết bị" : "Device Authentication & Pairing",
                icon: "link.badge.plus",
                iconColor: .indigo
            ) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(client.isPaired ? Color.green : Color.orange)
                            .frame(width: 8, height: 8)
                        Text(client.isPaired ? (client.appLanguage == "vi" ? "Trạng thái: Đã liên kết (\(client.deviceName))" : "Status: Paired (\(client.deviceName))") : (client.appLanguage == "vi" ? "Trạng thái: Chưa liên kết (Chế độ giám sát)" : "Status: Unpaired (Inspection Mode)"))
                            .font(.system(.headline, design: .rounded).weight(.semibold))
                    }
                    HStack(spacing: 12) {
                        TextField(tr("6-Character Pairing Code"), text: $pairCode)
                            .textFieldStyle(.plain)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                            .frame(maxWidth: 280)
                        Button(client.appLanguage == "vi" ? "Liên kết thiết bị" : "Pair Device") {
                            Task { await client.pairDevice(code: pairCode) }
                        }
                        .buttonStyle(.borderedProminent)
                        if client.isPaired {
                            Button(client.appLanguage == "vi" ? "Hủy liên kết" : "Unpair", role: .destructive) {
                                Task { await client.unpairDevice() }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
            }

            // Spotify Now Playing Mini Player
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Đang phát nhạc (Spotify)" : "Now Playing (Spotify)",
                icon: "music.note",
                iconColor: Color(hex: "#1db954")
            ) {
                HStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: "#1db954").opacity(0.18))
                            .frame(width: 48, height: 48)
                        Image(systemName: "music.note")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Color(hex: "#1db954"))
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(client.spotifyState.track ?? (client.appLanguage == "vi" ? "Chưa phát nhạc" : "Not Playing"))
                            .font(.system(.headline, design: .rounded).bold())
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text(client.spotifyState.artist ?? "Spotify Desktop")
                            .font(.system(.subheadline, design: .rounded))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    HStack(spacing: 10) {
                        Button {
                            Task { await client.controlSpotify(command: "previous") }
                        } label: {
                            Image(systemName: "backward.fill")
                                .font(.system(size: 13))
                        }
                        .buttonStyle(.bordered)

                        Button {
                            Task {
                                let cmd = client.spotifyState.isPlaying ? "pause" : "play"
                                await client.controlSpotify(command: cmd)
                            }
                        } label: {
                            Image(systemName: client.spotifyState.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.black)
                                .frame(width: 36, height: 36)
                                .background(Circle().fill(Color(hex: "#1db954")))
                        }
                        .buttonStyle(.plain)

                        Button {
                            Task { await client.controlSpotify(command: "next") }
                        } label: {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 13))
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            // 1. ESP32 Station Metrics
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Thông số ESP32 Station" : "ESP32 Station Metrics",
                icon: "display.2",
                iconColor: .cyan
            ) {
                HStack(spacing: 16) {
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Pin ESP32" : "Battery",
                        value: "\(client.esp32BatteryPct)%",
                        icon: "battery.100",
                        tint: client.esp32BatteryPct > 20 ? .green : .red,
                        subtext: "\(String(format: "%.2f", client.esp32Voltage))V"
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Sóng Wi-Fi" : "Wi-Fi Signal",
                        value: "\(client.esp32WifiRssi) dBm",
                        icon: "wifi",
                        tint: .blue,
                        subtext: client.esp32WifiRssi > -65 ? "Mạnh" : "Trung bình"
                    )
                    Spacer()
                    LiquidStatBadge(
                        title: "Màn hình OLED",
                        value: client.esp32CurrentView.uppercased(),
                        icon: "macbook.and.iphone",
                        tint: .cyan,
                        subtext: "SSD1306 128×64"
                    )
                }
            }

            // 2. Server Companion Metrics
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Thông số Server Companion" : "Companion Server Metrics",
                icon: "server.rack",
                iconColor: .purple
            ) {
                HStack(spacing: 16) {
                    LiquidStatBadge(
                        title: "CPU Server",
                        value: "\(String(format: "%.1f", client.serverCpu))%",
                        icon: "cpu",
                        tint: .orange
                    )
                    LiquidStatBadge(
                        title: "RAM Server",
                        value: "\(String(format: "%.1f", client.serverRamPct))%",
                        icon: "memorychip",
                        tint: .purple
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Dung lượng đĩa" : "Storage",
                        value: client.serverDiskStr,
                        icon: "internaldrive",
                        tint: .cyan
                    )
                }
            }

            // 3. Computer Metrics (đang đặt để show spec)
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Thông số Máy tính (\(client.compDeviceName))" : "Computer Metrics (\(client.compDeviceName))",
                icon: "laptopcomputer",
                iconColor: .teal
            ) {
                HStack(spacing: 16) {
                    LiquidStatBadge(
                        title: "CPU Máy tính",
                        value: "\(String(format: "%.1f", client.compCpu))%",
                        icon: "laptopcomputer",
                        tint: .blue
                    )
                    LiquidStatBadge(
                        title: "RAM Máy tính",
                        value: "\(String(format: "%.1f", client.compRam))%",
                        icon: "memorychip",
                        tint: .indigo
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Pin máy" : "Battery",
                        value: "\(client.compBattery)%",
                        icon: "battery.75",
                        tint: client.compBattery > 20 ? .green : .red
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Trạng thái" : "Status",
                        value: client.compIsOnline ? "Online" : "Offline",
                        icon: "circle.fill",
                        tint: client.compIsOnline ? .green : .red
                    )
                }
            }

            // 4. Groq AI Motivational Quote Generator
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Danh ngôn tạo động lực (Groq AI Quotes)" : "Motivational AI Quote (Groq AI)",
                icon: "sparkles",
                iconColor: .pink
            ) {
                VStack(alignment: .leading, spacing: 14) {
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [
                                        Color(hex: "#6366f1").opacity(0.12),
                                        Color(hex: "#a855f7").opacity(0.08)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                        VStack(alignment: .leading, spacing: 6) {
                            Text(generatedQuote.isEmpty ? "“Kiên trì hôm nay, thành công ngày mai!”" : "“\(generatedQuote)”")
                                .font(.system(.title3, design: .serif).italic().weight(.medium))
                                .foregroundColor(Color(hex: "#e0e7ff"))
                            if !quoteAuthor.isEmpty {
                                Text("— \(quoteAuthor)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(14)
                    }

                    HStack(spacing: 12) {
                        TextField(client.appLanguage == "vi" ? "Chủ đề (ví dụ: học tập, sáng tạo)" : "Topic (e.g. perseverance, study)", text: $quoteTopic)
                            .textFieldStyle(.plain)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                            .frame(maxWidth: 300)

                        Button(isGeneratingQuote ? (client.appLanguage == "vi" ? "Đang tạo..." : "Generating...") : (client.appLanguage == "vi" ? "Sinh câu danh ngôn AI" : "Generate Quote")) {
                            Task {
                                isGeneratingQuote = true
                                if let res = await client.generateQuote(topic: quoteTopic.isEmpty ? "học tập và kiên trì" : quoteTopic) {
                                    generatedQuote = res.quote
                                    quoteAuthor = res.author
                                }
                                isGeneratingQuote = false
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isGeneratingQuote)

                        if !generatedQuote.isEmpty {
                            Button(client.appLanguage == "vi" ? "Ghim lên ESP32" : "Pin to ESP32") {
                                Task {
                                    await client.createNote(text: generatedQuote)
                                }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
            }

            // 5. 48-Hour Weather Forecast
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Dự báo thời tiết 48h (Weather Forecast)" : "48-Hour Weather Forecast",
                icon: "cloud.sun.fill",
                iconColor: .cyan
            ) {
                VStack(alignment: .leading, spacing: 10) {
                    if client.weatherIntervals.isEmpty {
                        HStack {
                            ProgressView()
                            Text(client.appLanguage == "vi" ? "Đang tải dữ liệu thời tiết..." : "Loading forecast...")
                                .foregroundColor(.secondary)
                            Button(tr("Tải lại")) {
                                Task { await client.fetchWeatherForecast() }
                            }
                        }
                        .padding(8)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(client.weatherIntervals) { iv in
                                    VStack(spacing: 6) {
                                        Text(iv.displayTime.components(separatedBy: " ").last ?? iv.displayTime)
                                            .font(.system(size: 11, weight: .bold, design: .rounded))
                                            .foregroundColor(.secondary)
                                        Text(String(format: "%.1f°C", iv.temp ?? 0.0))
                                            .font(.system(.title3, design: .rounded).weight(.bold))
                                            .foregroundColor(.cyan)
                                        Text(iv.displayDescription)
                                            .font(.system(size: 10))
                                            .foregroundColor(.primary)
                                            .lineLimit(1)
                                        HStack(spacing: 4) {
                                            if iv.isRain ?? false {
                                                Image(systemName: "drop.fill")
                                                    .font(.system(size: 8))
                                            }
                                            Text("\(Int(iv.effectiveRainProb))%")
                                                .font(.system(size: 10, weight: .semibold, design: .rounded))
                                        }
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(
                                            Capsule()
                                                .fill((iv.isRain ?? false) ? Color.blue.opacity(0.25) : Color.white.opacity(0.06))
                                        )
                                        .foregroundColor((iv.isRain ?? false) ? .cyan : .secondary)
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(Color.white.opacity(0.04))
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                    )
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }

            // 6. Ecosystem Summary
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Tổng quan hệ sinh thái" : "Ecosystem Summary",
                icon: "square.stack.3d.up",
                iconColor: .mint
            ) {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 180))], spacing: 12) {
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Giấy nhớ đã lưu" : "Sticky Notes",
                        value: "\(client.notes.count)",
                        icon: "note.text",
                        tint: .yellow
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Đếm ngược đang chạy" : "Active Countdowns",
                        value: "\(client.countdowns.count)",
                        icon: "calendar.badge.clock",
                        tint: .orange
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Khoảng dự báo" : "Forecast Intervals",
                        value: "\(client.weatherIntervals.count)",
                        icon: "cloud.sun.fill",
                        tint: .cyan
                    )
                    LiquidStatBadge(
                        title: client.appLanguage == "vi" ? "Chế độ tập trung" : "Focus Mode",
                        value: client.focusActive ? "ĐANG CHẠY" : "Nghỉ",
                        icon: "timer",
                        tint: client.focusActive ? .green : .purple
                    )
                }
            }
        }
        .task {
            await client.fetchWeatherForecast()
        }
    }
}

private struct ToolsMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @Binding var notePendingDelete: NoteItem?
    @State private var toolTab: Int = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Picker(tr("Chức năng công cụ"), selection: $toolTab) {
                Text(client.appLanguage == "vi" ? "Danh ngôn AI & Chế độ tập trung" : "AI Quotes & Focus Mode").tag(0)
                Text(client.appLanguage == "vi" ? "Giấy nhớ & Đếm ngược" : "Notes & Countdowns").tag(1)
                Text(client.appLanguage == "vi" ? "Mã Morse Studio" : "Morse Studio").tag(2)
            }
            .pickerStyle(.segmented)

            if toolTab == 0 {
                FocusMacView(client: client)
            } else if toolTab == 1 {
                VStack(alignment: .leading, spacing: 20) {
                    NotesMacView(client: client, notePendingDelete: $notePendingDelete)
                    Divider()
                    CountdownsMacView(client: client)
                }
            } else {
                MorseMacView(client: client)
            }
        }
    }
}

private struct FocusMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var duration: Int = 25
    @State private var reason: String = "Deep Work Session"
    @State private var answerInput: String = ""
    @State private var quoteTopic: String = "học tập và kiên trì"
    @State private var generatedQuote: String = ""
    @State private var quoteAuthor: String = ""
    @State private var isGeneratingQuote: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Groq AI Motivational Quote
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Danh ngôn tạo động lực (Groq AI Quotes)" : "Motivational AI Quote (Groq AI)",
                icon: "sparkles",
                iconColor: .pink
            ) {
                VStack(alignment: .leading, spacing: 10) {
                    Text(generatedQuote.isEmpty ? "“Kiên trì hôm nay, thành công ngày mai!”" : "“\(generatedQuote)”")
                        .font(.title3.italic())
                        .foregroundColor(.cyan)
                    if !quoteAuthor.isEmpty {
                        Text("— \(quoteAuthor)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    HStack(spacing: 12) {
                        TextField(client.appLanguage == "vi" ? "Chủ đề (ví dụ: học tập, sáng tạo)" : "Topic (e.g. study, perseverance)", text: $quoteTopic)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 260)
                        Button(isGeneratingQuote ? (client.appLanguage == "vi" ? "Đang tạo..." : "Generating...") : (client.appLanguage == "vi" ? "Sinh câu danh ngôn AI" : "Generate Quote")) {
                            Task {
                                isGeneratingQuote = true
                                if let res = await client.generateQuote(topic: quoteTopic.isEmpty ? "học tập và kiên trì" : quoteTopic) {
                                    generatedQuote = res.quote
                                    quoteAuthor = res.author
                                }
                                isGeneratingQuote = false
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isGeneratingQuote)

                        if !generatedQuote.isEmpty {
                            Button(client.appLanguage == "vi" ? "Ghim lên ESP32" : "Pin to ESP32") {
                                Task {
                                    await client.createNote(text: generatedQuote)
                                }
                            }
                        }
                    }
                }
            }

            LiquidGlassCard(client.appLanguage == "vi" ? "Bắt đầu phiên tập trung" : "Start Focus Session", icon: "timer", iconColor: .purple) {
                VStack(alignment: .leading, spacing: 12) {
                    Stepper("Duration: \(duration) minutes", value: $duration, in: 5...180, step: 5)
                    TextField(tr("Session Goal"), text: $reason)
                        .textFieldStyle(.roundedBorder)
                    HStack {
                        Button(tr("Start 25m Focus")) {
                            duration = 25
                            Task { await client.startFocus(durationMinutes: 25, reason: reason) }
                        }
                        .buttonStyle(.borderedProminent)
                        Button(tr("Start 50m Deep Work")) {
                            duration = 50
                            Task { await client.startFocus(durationMinutes: 50, reason: reason) }
                        }
                        if client.focusActive {
                            Button(tr("Stop Session"), role: .destructive) {
                                Task { await client.requestChallenge() }
                            }
                        }
                    }
                }
            }

            LiquidGlassCard("Early Unlock Math Challenge", icon: "brain.head.profile", iconColor: .teal) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(client.activeChallengeQuestion ?? "No active challenge. Click Request Challenge to unlock early.")
                        .font(.headline)
                    HStack {
                        Button(tr("Request Challenge")) {
                            Task { await client.requestChallenge() }
                        }
                        TextField(tr("Integer Answer"), text: $answerInput)
                            .textFieldStyle(.roundedBorder)
                        Button(tr("Verify Answer")) {
                            if let ans = Int(answerInput.trimmingCharacters(in: .whitespaces)) {
                                Task { await client.verifyChallenge(answer: ans) }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
    }
}

private struct NotesMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @Binding var notePendingDelete: NoteItem?
    @State private var newNoteText: String = ""
    @State private var quoteTopic: String = "học tập và kiên trì"
    @State private var generatedQuote: String = ""
    @State private var quoteAuthor: String = ""
    @State private var isGeneratingQuote: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            LiquidGlassCard(
                client.appLanguage == "vi" ? "AI Danh ngôn tạo động lực (Groq AI Quotes)" : "Motivational AI Quote (Groq AI)",
                icon: "sparkles",
                iconColor: .pink
            ) {
                VStack(alignment: .leading, spacing: 10) {
                    Text(generatedQuote.isEmpty ? "“Kiên trì hôm nay, thành công ngày mai!”" : "“\(generatedQuote)”")
                        .font(.title3.italic())
                        .foregroundColor(.cyan)
                    if !quoteAuthor.isEmpty {
                        Text("— \(quoteAuthor)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    HStack(spacing: 12) {
                        TextField(client.appLanguage == "vi" ? "Chủ đề (ví dụ: học tập, sáng tạo)" : "Topic (e.g. study, perseverance)", text: $quoteTopic)
                            .textFieldStyle(.roundedBorder)
                            .frame(maxWidth: 260)
                        Button(isGeneratingQuote ? (client.appLanguage == "vi" ? "Đang tạo..." : "Generating...") : (client.appLanguage == "vi" ? "Sinh câu danh ngôn AI" : "Generate AI Quote")) {
                            Task {
                                isGeneratingQuote = true
                                if let res = await client.generateQuote(topic: quoteTopic.isEmpty ? "học tập và kiên trì" : quoteTopic) {
                                    generatedQuote = res.quote
                                    quoteAuthor = res.author
                                }
                                isGeneratingQuote = false
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isGeneratingQuote)

                        if !generatedQuote.isEmpty {
                            Button(client.appLanguage == "vi" ? "Ghim lên ESP32" : "Pin to ESP32") {
                                Task {
                                    await client.createNote(text: generatedQuote)
                                }
                            }
                        }
                    }
                }
            }

            HStack {
                Text(tr("Sticky Notes"))
                    .font(.title.bold())
                Spacer()
                Button(tr("Unpin ESP32 Note")) {
                    Task { await client.unpinNote() }
                }
                Button(tr("Refresh")) {
                    Task { await client.fetchNotes() }
                }
            }

            HStack {
                TextField(tr("Write a sticky note for ESP32 OLED..."), text: $newNoteText)
                    .textFieldStyle(.roundedBorder)
                Button(tr("Add Note")) {
                    let text = newNoteText
                    newNoteText = ""
                    Task { await client.createNote(text: text) }
                }
                .buttonStyle(.borderedProminent)
            }

            if client.notes.isEmpty {
                Text(tr("No sticky notes saved."))
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 16)
            } else {
                ForEach(client.notes) { note in
                    LiquidGlassCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(note.displayText)
                                    .font(.headline)
                                Text("ID: \(note.id)" + (note.effectivePinned ? (L10n.lang == "vi" ? " • ĐÃ GHIM TRÊN ESP32" : " • PINNED ON ESP32") : ""))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button(note.effectivePinned ? "Pinned" : "Pin to ESP32") {
                                Task { await client.pinNote(noteId: note.id) }
                            }
                            Button(role: .destructive) {
                                notePendingDelete = note
                            } label: {
                                if client.deletingNoteIds.contains(note.id) {
                                    ProgressView().controlSize(.small)
                                } else {
                                    Image(systemName: "trash")
                                }
                            }
                            .disabled(client.deletingNoteIds.contains(note.id))
                        }
                        .padding(6)
                    }
                }
            }
        }
    }
}

private struct CountdownsMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var eventName: String = ""
    @State private var targetDate: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(tr("Exam & Event Countdowns"))
                .font(.title.bold())

            HStack {
                TextField(tr("Event Name"), text: $eventName)
                    .textFieldStyle(.roundedBorder)
                TextField(tr("Target Date (YYYY-MM-DD or DD/MM)"), text: $targetDate)
                    .textFieldStyle(.roundedBorder)
                Button(tr("Add Countdown")) {
                    Task {
                        await client.createCountdown(name: eventName, targetDate: targetDate)
                        eventName = ""
                        targetDate = ""
                    }
                }
                .buttonStyle(.borderedProminent)
            }

            ForEach(client.countdowns) { item in
                LiquidGlassCard {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(item.displayName).font(.headline)
                            Text(L10n.lang == "vi" ? "Mục tiêu: \(item.targetDate ?? "") • còn \(item.daysLeft ?? 0) ngày" : "Target: \(item.targetDate ?? "") • \(item.daysLeft ?? 0) days left")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive) {
                            Task { await client.deleteCountdown(countdownId: item.id) }
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
            }
        }
    }
}

private struct WeatherMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var city: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(client.appLanguage == "vi" ? "Dự báo thời tiết 48h (OpenWeatherMap)" : "48-Hour Weather Forecast")
                    .font(.title.bold())
                Spacer()
                TextField(client.appLanguage == "vi" ? "Thành phố (tuỳ chọn)" : "City Override", text: $city)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 200)
                Button(client.appLanguage == "vi" ? "Cập nhật dự báo" : "Fetch Forecast") {
                    Task { await client.fetchWeatherForecast(city: city.isEmpty ? nil : city) }
                }
                .buttonStyle(.borderedProminent)
            }

            if client.weatherIntervals.isEmpty {
                VStack(spacing: 12) {
                    ProgressView()
                    Text(client.appLanguage == "vi" ? "Đang tải dữ liệu dự báo thời tiết 48h từ máy chủ..." : "Loading 48-hour weather forecast from server...")
                        .foregroundColor(.secondary)
                    Button(client.appLanguage == "vi" ? "Thử lại ngay" : "Retry Now") {
                        Task { await client.fetchWeatherForecast() }
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 180)
                .background(Color.secondary.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                ForEach(client.weatherIntervals) { iv in
                    LiquidGlassCard {
                        HStack {
                            Text(iv.displayTime).font(.headline)
                            Spacer()
                            Text(String(format: "%.1f°C — %@", iv.temp ?? 0.0, iv.displayDescription))
                                .font(.body.bold())
                                .foregroundColor(.cyan)
                            Spacer()
                            Text((iv.isRain ?? false) ? "🌧 MƯA (\(Int(iv.effectiveRainProb))%)" : "Mưa: \(Int(iv.effectiveRainProb))%")
                                .font(.subheadline.bold())
                                .foregroundStyle((iv.isRain ?? false) ? .blue : .secondary)
                        }
                        .padding(8)
                    }
                }
            }
        }
        .task {
            await client.fetchWeatherForecast(city: city.isEmpty ? nil : city)
        }
    }
}

private struct MorseMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var plainText: String = "SOS DESKFLOW"
    @State private var morsePattern: String = "... --- ... / -.. . ... -.- ..-. .-.. --- .--"
    @State private var unitMs: Double = 80
    @State private var freqHz: Double = 2400
    @State private var isSyncing: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(tr("Morse Code Studio & ESP32 Buzzer"))
                .font(.title.bold())

            LiquidGlassCard("Live Bidirectional Converter", icon: "waveform", iconColor: .cyan) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(client.appLanguage == "vi" ? "Chuyển đổi trực tiếp 2 chiều theo thời gian thực (gõ văn bản hoặc mã Morse tự động dịch ngay):" : "Live real-time bidirectional conversion (type text or Morse to translate instantly):")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(client.appLanguage == "vi" ? "Văn bản thường (Plain Text):" : "Plain Text:")
                            .font(.caption.bold())
                        HStack {
                            TextField(tr("Plain Text"), text: $plainText)
                                .textFieldStyle(.plain)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                                .onChange(of: plainText) { newText in
                                    guard !isSyncing else { return }
                                    isSyncing = true
                                    morsePattern = MorseCodeConverter.textToMorse(newText)
                                    isSyncing = false
                                }
                            Button(tr("Text → Morse")) {
                                isSyncing = true
                                morsePattern = MorseCodeConverter.textToMorse(plainText)
                                isSyncing = false
                            }
                            .buttonStyle(.bordered)
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text(client.appLanguage == "vi" ? "Mã Morse (Morse Pattern):" : "Morse Pattern:")
                            .font(.caption.bold())
                        HStack {
                            TextField(tr("Morse Pattern (. - /)"), text: $morsePattern)
                                .textFieldStyle(.plain)
                                .font(.system(.body, design: .monospaced))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                                .onChange(of: morsePattern) { newMorse in
                                    guard !isSyncing else { return }
                                    isSyncing = true
                                    plainText = MorseCodeConverter.morseToText(newMorse)
                                    isSyncing = false
                                }
                            Button(tr("Morse → Text")) {
                                isSyncing = true
                                plainText = MorseCodeConverter.morseToText(morsePattern)
                                isSyncing = false
                            }
                            .buttonStyle(.bordered)
                        }
                    }

                    // Quick Tap Keypad for Morse Characters
                    HStack(spacing: 8) {
                        Text(tr("Phím gõ nhanh:"))
                            .font(.caption2.bold())
                            .foregroundColor(.secondary)
                        Button(tr("• (Dot)")) {
                            morsePattern.append(".")
                            isSyncing = true
                            plainText = MorseCodeConverter.morseToText(morsePattern)
                            isSyncing = false
                        }
                        .buttonStyle(.bordered)
                        Button(tr("— (Dash)")) {
                            morsePattern.append("-")
                            isSyncing = true
                            plainText = MorseCodeConverter.morseToText(morsePattern)
                            isSyncing = false
                        }
                        .buttonStyle(.bordered)
                        Button(tr("[Ký tự] (Space)")) {
                            morsePattern.append(" ")
                            isSyncing = true
                            plainText = MorseCodeConverter.morseToText(morsePattern)
                            isSyncing = false
                        }
                        .buttonStyle(.bordered)
                        Button(tr("/ [Từ]")) {
                            morsePattern.append(" / ")
                            isSyncing = true
                            plainText = MorseCodeConverter.morseToText(morsePattern)
                            isSyncing = false
                        }
                        .buttonStyle(.bordered)
                        Button(tr("Xóa")) {
                            morsePattern = ""
                            plainText = ""
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            LiquidGlassCard("Hardware Buzzer Playback", icon: "speaker.wave.2.fill", iconColor: .yellow) {
                VStack(alignment: .leading, spacing: 10) {
                    Slider(value: $unitMs, in: 30...300, step: 10) {
                        Text(L10n.lang == "vi" ? "Đơn vị chấm: \(Int(unitMs)) ms" : "Dot Unit: \(Int(unitMs)) ms")
                    }
                    Slider(value: $freqHz, in: 500...4500, step: 100) {
                        Text(L10n.lang == "vi" ? "Tần số: \(Int(freqHz)) Hz" : "Frequency: \(Int(freqHz)) Hz")
                    }
                    HStack {
                        Button(tr("Play on ESP32")) {
                            Task {
                                await client.playMorse(
                                    pattern: morsePattern,
                                    unitMs: Int(unitMs),
                                    freq: Int(freqHz)
                                )
                            }
                        }
                        .buttonStyle(.borderedProminent)

                        Button(tr("Stop Playback"), role: .destructive) {
                            Task { await client.stopMorse() }
                        }
                    }
                }
            }
        }
    }
}

private struct AlarmsMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var timeStr: String = "07:00"
    @State private var labelStr: String = "Morning Alarm"
    @State private var buzzerDur: Double = 250
    @State private var repeatMon: Bool = true
    @State private var repeatTue: Bool = true
    @State private var repeatWed: Bool = true
    @State private var repeatThu: Bool = true
    @State private var repeatFri: Bool = true
    @State private var repeatSat: Bool = false
    @State private var repeatSun: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(tr("Backend-Authoritative Alarms"))
                .font(.title.bold())

            LiquidGlassCard("Create New Alarm", icon: "plus.circle.fill", iconColor: .green) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        TextField(tr("Time (HH:MM)"), text: $timeStr)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: 120)
                        TextField(tr("Label"), text: $labelStr)
                            .textFieldStyle(.roundedBorder)
                        Button(tr("Save Alarm")) {
                            var days: [Int] = []
                            if repeatMon { days.append(0) }
                            if repeatTue { days.append(1) }
                            if repeatWed { days.append(2) }
                            if repeatThu { days.append(3) }
                            if repeatFri { days.append(4) }
                            if repeatSat { days.append(5) }
                            if repeatSun { days.append(6) }
                            Task {
                                await client.saveAlarm(
                                    time: timeStr.trimmingCharacters(in: .whitespacesAndNewlines),
                                    label: labelStr.trimmingCharacters(in: .whitespacesAndNewlines),
                                    repeatDays: days,
                                    buzzerDuration: Int(buzzerDur)
                                )
                            }
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    HStack(spacing: 8) {
                        Text(tr("Repeat:"))
                        Toggle(tr("Mon"), isOn: $repeatMon)
                        Toggle(tr("Tue"), isOn: $repeatTue)
                        Toggle(tr("Wed"), isOn: $repeatWed)
                        Toggle(tr("Thu"), isOn: $repeatThu)
                        Toggle(tr("Fri"), isOn: $repeatFri)
                        Toggle(tr("Sat"), isOn: $repeatSat)
                        Toggle(tr("Sun"), isOn: $repeatSun)
                    }
                    .toggleStyle(.checkbox)
                }
            }

            LiquidGlassCard("Active Alarms", icon: "alarm.fill", iconColor: .orange) {
                if client.alarms.isEmpty {
                    Text(tr("No alarms configured on server. Create one above."))
                        .foregroundColor(.secondary)
                        .padding(8)
                } else {
                    VStack(spacing: 8) {
                        ForEach(client.alarms) { a in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(a.displayTime) — \(a.displayLabel)")
                                        .font(.headline)
                                    Text(L10n.lang == "vi" ? "Lặp lại: \(a.repeatSummary) • Còi: \(a.buzzerDuration ?? 250)ms" : "Repeat: \(a.repeatSummary) • Buzzer: \(a.buzzerDuration ?? 250)ms")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Toggle("", isOn: Binding(
                                    get: { a.isEnabled },
                                    set: { val in Task { await client.toggleAlarm(alarmId: a.id, enabled: val) } }
                                ))
                                .toggleStyle(.switch)
                                Button(role: .destructive) {
                                    Task { await client.deleteAlarm(alarmId: a.id) }
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                            .padding(.vertical, 4)
                            Divider()
                        }
                    }
                    .padding(8)
                }
            }
        }
        .onAppear {
            Task { await client.fetchAlarms() }
        }
    }
}

private struct ClockStyleOption: Identifiable {
    let id: Int
    let code: String
    let name: String
    let desc: String
}

private struct OledViewOption: Identifiable {
    let id: String
    let name: String
    let icon: String
    let desc: String
}

private struct StationMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var hexColor: String = "#8B5CF6"
    @State private var selectedPaletteColor: Color = Color(hex: "#8B5CF6")
    @State private var ledBrightness: Double = 255.0
    @State private var screenBrightness: Double = 255.0
    @State private var previewView: String = "clock"
    @State private var selectedClockStyle: Int = 1
    @State private var isApplying: Bool = false

    private let clockStyles: [ClockStyleOption] = [
        ClockStyleOption(id: 1, code: "digital_classic", name: "Digital Classic", desc: "Bold digits with seconds & date"),
        ClockStyleOption(id: 2, code: "digital_minimal", name: "Minimalist", desc: "Clean borderless typography"),
        ClockStyleOption(id: 3, code: "digital_neon", name: "Neon Cyber", desc: "Cyber neon outline frame"),
        ClockStyleOption(id: 4, code: "analog", name: "Analog Dial", desc: "Round 3-hand analog clock"),
        ClockStyleOption(id: 5, code: "flip", name: "Flip Clock", desc: "Retro split-card flip display"),
        ClockStyleOption(id: 6, code: "binary", name: "Binary Matrix", desc: "BCD 4×4 LED matrix"),
        ClockStyleOption(id: 7, code: "word", name: "Word Clock", desc: "Spelled out text clock")
    ]

    private let clockPresetColors: [(hex: String, name: String)] = [
        ("#38BDF8", "Sky Blue"),
        ("#10B981", "Emerald"),
        ("#F59E0B", "Amber"),
        ("#EF4444", "Rose"),
        ("#8B5CF6", "Purple"),
        ("#EC4899", "Pink"),
        ("#FFFFFF", "White")
    ]

    private var clockAccent: Color {
        Color(hex: client.clockAccentColor)
    }

    private let oledViews: [OledViewOption] = [
        OledViewOption(id: "main", name: "Main Dashboard (Default)", icon: "square.grid.2x2", desc: "Clock, Weather, Music & Quotes"),
        OledViewOption(id: "clock", name: "Clock Face", icon: "clock.fill", desc: "Active watch face"),
        OledViewOption(id: "weather", name: "Weather", icon: "cloud.sun.fill", desc: "24h forecast & temp"),
        OledViewOption(id: "notes", name: "Sticky Note", icon: "note.text", desc: "Pinned note display"),
        OledViewOption(id: "countdown", name: "Countdown", icon: "calendar.badge.clock", desc: "Exam & target days left"),
        OledViewOption(id: "focus", name: "Focus Mode", icon: "timer", desc: "Focus Mode countdown"),
        OledViewOption(id: "stats", name: "Server Stats", icon: "cpu", desc: "CPU, RAM & Uptime"),
        OledViewOption(id: "tkb", name: "Timetable", icon: "book.closed.fill", desc: "School class schedule"),
        OledViewOption(id: "lyrics", name: "Spotify Lyrics", icon: "music.note", desc: "Real-time music lyrics")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(client.appLanguage == "vi" ? "Màn hình OLED & Điều khiển phần cứng ESP32" : "ESP32 Station OLED & Hardware Controls")
                    .font(.title.bold())

                // 1. OLED Screen Live Monitor
                
                // Independent ESP32 Dark Mode Quick Control
                LiquidGlassCard(
                    client.appLanguage == "vi" ? "Chế độ hiển thị màn hình ESP32 độc lập" : "Independent ESP32 Display Mode",
                    icon: "sun.max.and.sunset.fill",
                    iconColor: .orange
                ) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(client.appLanguage == "vi" ? "Cài đặt chế độ Sáng hoặc Tối riêng cho màn hình OLED ESP32:" : "Configure Light or Dark mode for the ESP32 OLED display independently:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        HStack(spacing: 12) {
                            Button(client.appLanguage == "vi" ? "☀️ Luôn Sáng (Light Mode)" : "☀️ Always Light") {
                                client.esp32ThemeMode = "light"
                                Task { await client.setEsp32Theme(mode: "light") }
                            }
                            .buttonStyle(.bordered)

                            Button(client.appLanguage == "vi" ? "🌙 Luôn Tối (Dark Mode)" : "🌙 Always Dark") {
                                client.esp32ThemeMode = "dark"
                                Task { await client.setEsp32Theme(mode: "dark") }
                            }
                            .buttonStyle(.borderedProminent)

                            Button(client.appLanguage == "vi" ? "⏰ Theo lịch (Scheduled)" : "⏰ Scheduled") {
                                client.esp32ThemeMode = "sync"
                                Task { await client.setEsp32Theme(mode: "sync") }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                LiquidGlassCard("Live ESP32 OLED Display Monitor (128×64)", icon: "display.2", iconColor: .cyan) {
                    VStack(spacing: 8) {
                        HStack {
                            Circle().fill(Color.green).frame(width: 7, height: 7)
                            Text(tr("128×64 SSD1306 OLED MONITOR"))
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(previewView.uppercased())
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(.cyan)
                        }

                        // OLED Display Screen Box
                        ZStack {
                            Color(red: 0.04, green: 0.06, blue: 0.09) // OLED deep black

                            // Content rendering
                            Group {
                                if previewView == "main" {
                                    mainDashboardScreenPreview
                                } else if previewView == "clock" {
                                    clockFacePreview(style: selectedClockStyle)
                                } else if previewView == "weather" {
                                    weatherScreenPreview
                                } else if previewView == "notes" {
                                    notesScreenPreview
                                } else if previewView == "countdown" {
                                    countdownScreenPreview
                                } else if previewView == "focus" {
                                    focusScreenPreview
                                } else if previewView == "stats" {
                                    statsScreenPreview
                                } else if previewView == "tkb" {
                                    tkbScreenPreview
                                } else if previewView == "lyrics" {
                                    lyricsScreenPreview
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 116)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.cyan.opacity(0.35), lineWidth: 1.5)
                        )

                        HStack {
                            Text(client.appLanguage == "vi" ? "Bấm vào bất kỳ phong cách mặt đồng hồ hoặc trang bên dưới để xem trước. Bấm Áp dụng để gửi đến ESP32." : "Click any clock style or view below to preview. Click Apply to send to ESP32.")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Button(client.appLanguage == "vi" ? "Áp dụng lên trạm OLED" : "Apply to Station OLED") {
                                Task {
                                    isApplying = true
                                    if previewView == "clock" {
                                        let code = clockStyles.first(where: { $0.id == selectedClockStyle })?.code ?? "digital_classic"
                                        await client.setClockStyle(style: code, accentColor: client.clockAccentColor)
                                        await client.switchDisplayView(view: "clock")
                                    } else {
                                        await client.switchDisplayView(view: previewView)
                                    }
                                    isApplying = false
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.small)
                            .disabled(isApplying)
                        }
                    }
                    .padding(10)
                }

                // 2. Clock Face Studio (7 Styles with Live Visual Cards)
                LiquidGlassCard(
                    client.appLanguage == "vi" ? "Bộ sưu tập mặt đồng hồ (7 Phong cách)" : "Clock Face Studio (7 Watch Face Styles)",
                    icon: "clock.arrow.circlepath",
                    iconColor: .indigo
                ) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(client.appLanguage == "vi" ? "Chọn và xem trước mặt đồng hồ cho màn hình OLED ESP32:" : "Select and preview clock faces for the ESP32 OLED display:")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        // Clock Accent Color Selector
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(client.appLanguage == "vi" ? "Màu nhấn đồng hồ (Accent Color):" : "Clock Accent Color:")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(client.clockAccentColor)
                                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                    .foregroundColor(clockAccent)
                            }

                            HStack(spacing: 12) {
                                ForEach(clockPresetColors, id: \.hex) { item in
                                    let isSelected = client.clockAccentColor.uppercased() == item.hex.uppercased()
                                    Button {
                                        client.clockAccentColor = item.hex
                                        Task {
                                            let code = clockStyles.first(where: { $0.id == selectedClockStyle })?.code ?? "digital_classic"
                                            await client.setClockStyle(style: code, accentColor: item.hex)
                                        }
                                    } label: {
                                        ZStack {
                                            Circle()
                                                .fill(Color(hex: item.hex))
                                                .frame(width: 24, height: 24)
                                                .shadow(color: Color(hex: item.hex).opacity(0.4), radius: isSelected ? 4 : 0)
                                            if isSelected {
                                                Circle()
                                                    .stroke(Color.white, lineWidth: 2)
                                                    .frame(width: 30, height: 30)
                                            }
                                        }
                                    }
                                    .buttonStyle(.plain)
                                    .help(item.name)
                                }
                            }
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.04)))

                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150))], spacing: 12) {
                            ForEach(clockStyles) { style in
                                let isSelected = (selectedClockStyle == style.id && previewView == "clock")
                                Button(action: {
                                    selectedClockStyle = style.id
                                    previewView = "clock"
                                    Task { await client.setClockStyle(style: style.code, accentColor: client.clockAccentColor) }
                                }) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        // Miniature Graphic Preview Box
                                        ZStack {
                                            Color(red: 0.05, green: 0.07, blue: 0.11)
                                            miniClockGraphic(style: style.id)
                                        }
                                        .frame(height: 56)
                                        .frame(maxWidth: .infinity)
                                        .clipShape(RoundedRectangle(cornerRadius: 6))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 6)
                                                .stroke(isSelected ? Color.cyan : Color.white.opacity(0.1), lineWidth: isSelected ? 1.5 : 0.8)
                                        )

                                        Text(style.name)
                                            .font(.caption.bold())
                                            .foregroundColor(isSelected ? .cyan : .primary)
                                            .lineLimit(1)
                                        Text(style.desc)
                                            .font(.system(size: 9))
                                            .foregroundColor(.secondary)
                                            .lineLimit(2)
                                    }
                                    .padding(8)
                                    .background(isSelected ? Color.cyan.opacity(0.12) : Color.clear)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(isSelected ? Color.cyan : Color.gray.opacity(0.2), lineWidth: isSelected ? 1.5 : 1)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                // 3. OLED Display View Switcher (8 Views with Visual Cards)
                LiquidGlassCard("OLED Display View Switcher (Clock Page System)", icon: "square.grid.2x2", iconColor: .blue) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(tr("Switch the active page displayed on the ESP32 station:"))
                            .font(.caption)
                            .foregroundColor(.secondary)

                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150))], spacing: 12) {
                            ForEach(oledViews) { viewOpt in
                                let isSelected = (previewView == viewOpt.id)
                                Button(action: {
                                    previewView = viewOpt.id
                                    Task { await client.switchDisplayView(view: viewOpt.id) }
                                }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: viewOpt.icon)
                                            .font(.title3)
                                            .foregroundColor(isSelected ? .cyan : .secondary)
                                            .frame(width: 28)

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(viewOpt.name)
                                                .font(.caption.bold())
                                                .foregroundColor(isSelected ? .cyan : .primary)
                                            Text(viewOpt.desc)
                                                .font(.system(size: 9))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                        }
                                        Spacer()
                                    }
                                    .padding(10)
                                    .background(isSelected ? Color.cyan.opacity(0.12) : Color.clear)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(isSelected ? Color.cyan : Color.gray.opacity(0.2), lineWidth: isSelected ? 1.5 : 1)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                // 4. WS2812B NeoPixel & Bảng màu tuỳ biến (Color Palette)
                LiquidGlassCard("WS2812B NeoPixel & Bảng màu tuỳ biến (Color Palette)", icon: "paintpalette.fill", iconColor: .pink) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 16) {
                            ColorPicker(tr("Bảng màu tuỳ biến (Color Palette):"), selection: $selectedPaletteColor)
                                .font(.headline)
                                .onChange(of: selectedPaletteColor) { newCol in
                                    if let hex = newCol.toHex() {
                                        hexColor = hex
                                        Task { await client.setLampColor(hex: hex) }
                                    }
                                }

                            Spacer()

                            Button(tr("Tắt đèn (Lamp Off)")) {
                                Task { await client.turnLampOff() }
                            }
                            .buttonStyle(.bordered)
                        }

                        HStack {
                            TextField(tr("Mã màu Hex (#RRGGBB)"), text: $hexColor)
                                .textFieldStyle(.plain)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                                .frame(width: 160)
                            Button(tr("Áp dụng mã Hex")) {
                                Task { await client.setLampColor(hex: hexColor) }
                            }
                            .buttonStyle(.borderedProminent)
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(tr("Màu Preset nhanh (Quick Presets):"))
                                .font(.caption.bold())
                                .foregroundColor(.secondary)
                            HStack(spacing: 8) {
                                ForEach([
                                    ("#5865F2", "Discord"),
                                    ("#FF5500", "Cam"),
                                    ("#10B981", "Xanh lục"),
                                    ("#38BDF8", "Xanh lam"),
                                    ("#EC4899", "Hồng"),
                                    ("#F59E0B", "Vàng")
                                ], id: \.0) { hex, label in
                                    Button(action: {
                                        hexColor = hex
                                        selectedPaletteColor = Color(hex: hex)
                                        Task { await client.setLampColor(hex: hex) }
                                    }) {
                                        HStack(spacing: 4) {
                                            Circle().fill(Color(hex: hex)).frame(width: 12, height: 12)
                                            Text(label)
                                                .font(.caption2)
                                        }
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.secondary.opacity(0.12))
                                        .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Độ sáng đèn LED: \(Int(ledBrightness)) / 255")
                                .font(.caption.bold())
                            Slider(value: $ledBrightness, in: 0...255, step: 1) { _ in
                                Task { await client.setLampDim(led: Int(ledBrightness), screen: nil) }
                            }
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("Độ sáng màn hình OLED: \(Int(screenBrightness)) / 255")
                                .font(.caption.bold())
                            Slider(value: $screenBrightness, in: 0...255, step: 1) { _ in
                                Task { await client.setLampDim(led: nil, screen: Int(screenBrightness)) }
                            }
                        }
                    }
                }

                // 5. Custom Beep & Hardware Buzzer
                LiquidGlassCard("Custom Beep & Hardware Buzzer (Phase 4)", icon: "speaker.wave.3.fill", iconColor: .yellow) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Text(tr("Duration Presets:"))
                            Button("40ms") { Task { await client.triggerBuzz(durationMs: 40) } }
                            Button("80ms") { Task { await client.triggerBuzz(durationMs: 80) } }
                            Button("150ms") { Task { await client.triggerBuzz(durationMs: 150) } }
                            Button("250ms") { Task { await client.triggerBuzz(durationMs: 250) } }
                            Button("500ms") { Task { await client.triggerBuzz(durationMs: 500) } }
                        }
                        HStack {
                            Button(tr("Emergency Stop"), role: .destructive) {
                                Task { await client.emergencyStop() }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                    .padding(8)
                }
            }
            .padding(16)
        }
    }

    // MARK: - OLED Full Screen Previews
    @ViewBuilder
    private func clockFacePreview(style: Int) -> some View {
        switch style {
        case 2: // Minimalist
            VStack(spacing: 4) {
                Text("12:30:45")
                    .font(.system(size: 26, weight: .light, design: .rounded))
                    .foregroundColor(.white)
                Text("Sun, 08 Nov • 28°C HCM")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(clockAccent)
            }
        case 3: // Neon Cyber
            VStack(spacing: 3) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(clockAccent, lineWidth: 1.5)
                        .frame(width: 130, height: 42)
                    Text("12:30")
                        .font(.system(size: 26, weight: .black, design: .monospaced))
                        .foregroundColor(clockAccent)
                }
                Text("CYBER STATION • SEC: 45")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(clockAccent.opacity(0.8))
            }
        case 4: // Analog Dial
            HStack(spacing: 20) {
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.3), lineWidth: 1.5)
                        .frame(width: 60, height: 60)
                    // 12 ticks
                    ForEach(0..<12) { i in
                        Rectangle()
                            .fill(Color.white.opacity(0.6))
                            .frame(width: 1, height: 4)
                            .offset(y: -26)
                            .rotationEffect(.degrees(Double(i) * 30))
                    }
                    // Hour Hand
                    Rectangle()
                        .fill(Color.white)
                        .frame(width: 2, height: 16)
                        .offset(y: -8)
                        .rotationEffect(.degrees(60))
                    // Minute Hand
                    Rectangle()
                        .fill(clockAccent)
                        .frame(width: 1.5, height: 22)
                        .offset(y: -11)
                        .rotationEffect(.degrees(180))
                    // Second Hand
                    Rectangle()
                        .fill(Color.orange)
                        .frame(width: 1, height: 25)
                        .offset(y: -12.5)
                        .rotationEffect(.degrees(270))
                    // Center pin
                    Circle().fill(Color.white).frame(width: 4, height: 4)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text("12:30 PM")
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                    Text("SUN 08 NOV")
                        .font(.system(size: 10, weight: .semibold, design: .monospaced))
                        .foregroundColor(clockAccent)
                    Text("ANALOG OLED")
                        .font(.system(size: 8, weight: .bold, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }
        case 5: // Flip Clock
            VStack(spacing: 4) {
                HStack(spacing: 5) {
                    flipBox(digits: "12")
                    Text(":")
                        .font(.system(size: 20, weight: .black, design: .monospaced))
                        .foregroundColor(clockAccent)
                    flipBox(digits: "30")
                }
                Text("RETRO FLIP • SEC 45")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(.secondary)
            }
        case 6: // Binary Matrix
            HStack(spacing: 16) {
                VStack(spacing: 3) {
                    ForEach(0..<4) { row in
                        HStack(spacing: 5) {
                            ForEach(0..<4) { col in
                                Circle()
                                    .fill((row + col) % 2 == 1 ? clockAccent : Color.white.opacity(0.15))
                                    .frame(width: 8, height: 8)
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text("BCD 8-4-2-1")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(clockAccent)
                    Text("12:30:45")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                    Text("BINARY CLOCK")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }
        case 7: // Word Clock
            VStack(spacing: 2) {
                Text("TWELVE")
                    .font(.system(size: 16, weight: .heavy, design: .monospaced))
                    .foregroundColor(.white)
                Text("THIRTY")
                    .font(.system(size: 16, weight: .heavy, design: .monospaced))
                    .foregroundColor(clockAccent)
                Text("FORTY FIVE SECONDS")
                    .font(.system(size: 9, weight: .semibold, design: .monospaced))
                    .foregroundColor(clockAccent.opacity(0.8))
            }
        default: // Style 1: Digital Classic
            VStack(spacing: 4) {
                HStack(alignment: .bottom, spacing: 2) {
                    Text("12:30")
                        .font(.system(size: 32, weight: .bold, design: .monospaced))
                        .foregroundColor(clockAccent)
                    Text(":45")
                        .font(.system(size: 16, weight: .semibold, design: .monospaced))
                        .foregroundColor(.white.opacity(0.8))
                        .padding(.bottom, 3)
                }
                HStack(spacing: 10) {
                    Text("SUN 08 NOV")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                    Text("28°C HCM")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(clockAccent)
                }
            }
        }
    }

    @ViewBuilder
    private func flipBox(digits: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 4)
                .fill(Color(red: 0.1, green: 0.14, blue: 0.22))
                .frame(width: 44, height: 42)
            VStack(spacing: 0) {
                Spacer()
                Divider().background(Color.black)
                Spacer()
            }
            .frame(width: 44, height: 42)
            Text(digits)
                .font(.system(size: 24, weight: .black, design: .monospaced))
                .foregroundColor(.white)
        }
    }

    // MARK: - Miniature Preview for Clock Style Cards
    @ViewBuilder
    private func miniClockGraphic(style: Int) -> some View {
        switch style {
        case 2: // Minimal
            Text("12:30:45")
                .font(.system(size: 12, weight: .light, design: .rounded))
                .foregroundColor(.white)
        case 3: // Neon
            Text("12:30")
                .font(.system(size: 13, weight: .black, design: .monospaced))
                .foregroundColor(clockAccent)
                .padding(3)
                .overlay(RoundedRectangle(cornerRadius: 3).stroke(clockAccent, lineWidth: 1))
        case 4: // Analog
            ZStack {
                Circle().stroke(Color.white.opacity(0.4), lineWidth: 1).frame(width: 28, height: 28)
                Rectangle().fill(Color.white).frame(width: 1.5, height: 9).offset(y: -4.5).rotationEffect(.degrees(60))
                Rectangle().fill(clockAccent).frame(width: 1, height: 12).offset(y: -6).rotationEffect(.degrees(180))
                Circle().fill(Color.orange).frame(width: 2.5, height: 2.5)
            }
        case 5: // Flip
            HStack(spacing: 2) {
                Text("12").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.white)
                    .padding(2).background(Color(red: 0.12, green: 0.16, blue: 0.24)).cornerRadius(2)
                Text(":").font(.system(size: 10, weight: .bold)).foregroundColor(clockAccent)
                Text("30").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.white)
                    .padding(2).background(Color(red: 0.12, green: 0.16, blue: 0.24)).cornerRadius(2)
            }
        case 6: // Binary
            HStack(spacing: 3) {
                ForEach(0..<4) { c in
                    VStack(spacing: 3) {
                        ForEach(0..<3) { r in
                            Circle().fill((c + r) % 2 == 1 ? clockAccent : Color.white.opacity(0.2)).frame(width: 3.5, height: 3.5)
                        }
                    }
                }
            }
        case 7: // Word
            VStack(spacing: 1) {
                Text("TWELVE").font(.system(size: 7, weight: .black, design: .monospaced)).foregroundColor(.white)
                Text("THIRTY").font(.system(size: 7, weight: .black, design: .monospaced)).foregroundColor(clockAccent)
            }
        default: // Classic
            HStack(alignment: .bottom, spacing: 1) {
                Text("12:30").font(.system(size: 14, weight: .bold, design: .monospaced)).foregroundColor(clockAccent)
                Text(":45").font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundColor(.white)
            }
        }
    }

    // MARK: - OLED Non-Clock Screen Previews
    private var mainDashboardScreenPreview: some View {
        VStack(spacing: 4) {
            Text("DESKFLOW STATION (DEFAULT)")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(.cyan.opacity(0.85))
            Text("12:45 • Ha Long 28°C")
                .font(.system(size: 15, weight: .black, design: .monospaced))
                .foregroundColor(.cyan)
            Text("♫ Starboy — The Weeknd • Quotes Ready")
                .font(.system(size: 9, design: .monospaced))
                .foregroundColor(.cyan.opacity(0.75))
                .lineLimit(1)
        }
    }

    private var weatherScreenPreview: some View {
        HStack(spacing: 14) {
            Image(systemName: "cloud.sun.fill")
                .font(.system(size: 32))
                .foregroundColor(.yellow)
            VStack(alignment: .leading, spacing: 3) {
                Text("28°C  HO CHI MINH")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Text("Clouds & Sunshine")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(.cyan)
                Text("POP: 20% | HUMIDITY: 65%")
                    .font(.system(size: 9, weight: .regular, design: .monospaced))
                    .foregroundColor(.secondary)
            }
        }
    }

    private var notesScreenPreview: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: "pin.fill").font(.caption).foregroundColor(.yellow)
                Text("STICKY NOTE").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.yellow)
                Spacer()
                Text("PINNED").font(.system(size: 8, weight: .semibold, design: .monospaced)).foregroundColor(.green)
            }
            Text("Meeting with Mentor at 2:00 PM")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(.white)
            Text("Review DeskFlow firmware & station OLED")
                .font(.system(size: 9, weight: .regular, design: .monospaced))
                .foregroundColor(.secondary)
        }
    }

    private var countdownScreenPreview: some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 3) {
                Text("TARGET COUNTDOWN")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(.cyan)
                Text("KHKT 2026")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Text("15/01/2026")
                    .font(.system(size: 9, weight: .regular, design: .monospaced))
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(spacing: 0) {
                Text("45")
                    .font(.system(size: 30, weight: .black, design: .monospaced))
                    .foregroundColor(.orange)
                Text("DAYS LEFT")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
            }
        }
    }

    private var focusScreenPreview: some View {
        VStack(spacing: 5) {
            HStack {
                Image(systemName: "timer").foregroundColor(.red)
                Text("FOCUS MODE ACTIVE")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(.red)
                Spacer()
                Text("FOCUS").font(.system(size: 8, weight: .bold, design: .monospaced)).foregroundColor(.secondary)
            }
            Text("24:58")
                .font(.system(size: 26, weight: .heavy, design: .monospaced))
                .foregroundColor(.white)
            // Simulated progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(Color.white.opacity(0.15))
                    RoundedRectangle(cornerRadius: 2).fill(Color.red).frame(width: geo.size.width * 0.72)
                }
            }
            .frame(height: 5)
        }
    }

    private var statsScreenPreview: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("DESKFLOW STATION TELEMETRY")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(.cyan)
            HStack {
                Text("CPU: 24%").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.white)
                Spacer()
                Text("RAM: 58%").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.white)
            }
            HStack {
                Text("UPTIME: 4d 12h").font(.system(size: 9, weight: .regular, design: .monospaced)).foregroundColor(.secondary)
                Spacer()
                Text("WIFI: -54 dBm (GOOD)").font(.system(size: 9, weight: .regular, design: .monospaced)).foregroundColor(.green)
            }
        }
    }

    private var tkbScreenPreview: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text("THOI KHOA BIEU — THU 2").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundColor(.orange)
                Spacer()
                Text("SANG").font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundColor(.white)
            }
            Text("1: Toan • 2: Vat Ly • 3: Hoa Hoc")
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(.white)
            Text("4: Tieng Anh • 5: Sinh Hoc")
                .font(.system(size: 10, weight: .medium, design: .monospaced))
                .foregroundColor(.secondary)
        }
    }

    private var lyricsScreenPreview: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Image(systemName: "music.note").foregroundColor(.green)
                Text("SPOTIFY NOW PLAYING")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(.green)
            }
            Text("Starboy — The Weeknd, Daft Punk")
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(.white)
                .lineLimit(1)
            Text("♫ \"I'm tryna put you in the worst mood, ah...\"")
                .font(.system(size: 10, weight: .regular, design: .monospaced))
                .foregroundColor(.cyan)
                .lineLimit(1)
        }
    }
}

// MARK: - Timetable (TKB) View

private struct TimetableMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var isRefreshing: Bool = false
    @State private var isSwitchingOled: Bool = false

    var body: some View {
        let isVi = client.appLanguage == "vi"

        VStack(alignment: .leading, spacing: 20) {
            // Header bar
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(isVi ? "Thời khóa biểu (TKB)" : "Class Timetable (TKB)")
                        .font(.system(.largeTitle, design: .rounded).bold())
                    Text(isVi ? "Lịch học theo tuần và đồng phục được đồng bộ tự động." : "Weekly school schedule and uniform auto-synchronized.")
                        .font(.system(.body, design: .rounded))
                        .foregroundColor(.secondary)
                }
                Spacer()

                HStack(spacing: 10) {
                    if let week = client.timetableData?.weekNumber, !week.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "number.circle.fill")
                                .foregroundColor(.cyan)
                            Text(isVi ? "Tuần \(week)" : "Week \(week)")
                                .font(.system(.callout, design: .rounded).bold())
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.cyan.opacity(0.15))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cyan.opacity(0.3), lineWidth: 1))
                    }

                    Button {
                        Task {
                            isSwitchingOled = true
                            await client.switchDisplayView(view: "tkb")
                            isSwitchingOled = false
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "display.2")
                            Text(isVi ? "Hiện lên ESP32" : "Display on ESP32")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.indigo)
                    .disabled(isSwitchingOled)

                    Button {
                        Task {
                            isRefreshing = true
                            await client.fetchTimetable()
                            isRefreshing = false
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if isRefreshing || client.isFetchingTimetable {
                                ProgressView().scaleEffect(0.7)
                            } else {
                                Image(systemName: "arrow.clockwise")
                            }
                            Text(isVi ? "Làm mới" : "Refresh")
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(isRefreshing || client.isFetchingTimetable)
                }
            }

            if client.isFetchingTimetable && client.timetableData == nil {
                VStack(spacing: 12) {
                    ProgressView()
                    Text(isVi ? "Đang tải dữ liệu thời khóa biểu..." : "Loading timetable data...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
            } else if let tt = client.timetableData {
                VStack(spacing: 18) {
                    // 1. TODAY AT TOP (MANDATORY REQUIREMENT)
                    DayTimetableCard(
                        title: isVi ? "Lịch học hôm nay" : "Today's Schedule",
                        dayDto: tt.today,
                        isToday: true,
                        isVi: isVi
                    )

                    // 2. TOMORROW / NEXT SCHOOL DAY UNDERNEATH
                    DayTimetableCard(
                        title: isVi ? "Lịch học kế tiếp" : "Next School Day",
                        dayDto: tt.tomorrow,
                        isToday: false,
                        isVi: isVi
                    )
                }
            } else {
                LiquidGlassCard(
                    isVi ? "Thời khóa biểu trống" : "No Timetable Available",
                    icon: "calendar.badge.exclamationmark",
                    iconColor: .orange
                ) {
                    VStack(spacing: 14) {
                        Text(isVi ? "Chưa có dữ liệu thời khóa biểu hoặc máy chủ đang ngoại tuyến." : "No timetable data available or server is offline.")
                            .font(.body)
                            .foregroundColor(.secondary)

                        Button(isVi ? "Tải lại lịch học" : "Fetch Schedule") {
                            Task { await client.fetchTimetable() }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                }
            }
        }
        .onAppear {
            if client.timetableData == nil {
                Task { await client.fetchTimetable() }
            }
        }
    }
}

private struct DayTimetableCard: View {
    let title: String
    let dayDto: DayScheduleDto?
    let isToday: Bool
    let isVi: Bool

    var body: some View {
        let dayName = dayDto?.displayDay ?? dayDto?.day ?? (isToday ? (isVi ? "Hôm nay" : "Today") : (isVi ? "Ngày mai" : "Tomorrow"))
        let hasClasses = dayDto?.hasAnyClass ?? false
        let dpSang = (dayDto?.uniformMorning ?? "").isEmpty ? (isVi ? "Tự do" : "Casual") : dayDto!.uniformMorning!
        let dpChieu = dayDto?.uniformAfternoon ?? ""
        let morningList = dayDto?.morning ?? []
        let afternoonList = dayDto?.afternoon ?? []

        LiquidGlassCard(
            "\(title) • \(dayName)",
            icon: isToday ? "sun.max.fill" : "calendar",
            iconColor: isToday ? .cyan : .indigo
        ) {
            VStack(alignment: .leading, spacing: 14) {
                // Uniform Badges Row
                HStack(spacing: 12) {
                    // Morning uniform badge
                    HStack(spacing: 6) {
                        Image(systemName: "tshirt.fill")
                            .foregroundColor(.orange)
                        Text("\(isVi ? "Đồng phục sáng" : "AM Uniform"): \(dpSang)")
                            .font(.system(.caption, design: .rounded).bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.orange.opacity(0.12))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.orange.opacity(0.25), lineWidth: 1))

                    // Afternoon uniform badge (if afternoon exists)
                    if (dayDto?.hasAfternoon == true || !dpChieu.isEmpty) && !dpChieu.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "figure.walk")
                                .foregroundColor(.pink)
                            Text("\(isVi ? "Đồng phục chiều" : "PM Uniform"): \(dpChieu)")
                                .font(.system(.caption, design: .rounded).bold())
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.pink.opacity(0.12))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.pink.opacity(0.25), lineWidth: 1))
                    }

                    Spacer()

                    if isToday {
                        Text(isVi ? "HÔM NAY" : "TODAY")
                            .font(.system(size: 10, weight: .heavy, design: .rounded))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.cyan.opacity(0.2))
                            .foregroundColor(.cyan)
                            .cornerRadius(6)
                    }
                }

                if !hasClasses && morningList.isEmpty && afternoonList.isEmpty {
                    HStack(spacing: 8) {
                        Image(systemName: "moon.stars.fill")
                            .foregroundColor(.secondary)
                        Text(isVi ? "Không có tiết học (Nghỉ học)" : "No classes scheduled (Day off)")
                            .font(.system(.body, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 10)
                } else {
                    HStack(alignment: .top, spacing: 18) {
                        // Morning Column
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 6) {
                                Image(systemName: "sun.and.horizon.fill")
                                    .foregroundColor(.orange)
                                Text(isVi ? "Tiết học sáng" : "Morning Sessions")
                                    .font(.system(.subheadline, design: .rounded).bold())
                            }

                            if morningList.isEmpty {
                                Text(isVi ? "Nghỉ học sáng" : "No morning classes")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.vertical, 4)
                            } else {
                                VStack(alignment: .leading, spacing: 6) {
                                    ForEach(Array(morningList.enumerated()), id: \.offset) { _, period in
                                        HStack(spacing: 8) {
                                            Circle()
                                                .fill(Color.cyan)
                                                .frame(width: 5, height: 5)
                                            Text(period)
                                                .font(.system(.body, design: .rounded).weight(.medium))
                                        }
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.04)))
                                    }
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        Divider()

                        // Afternoon Column
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 6) {
                                Image(systemName: "sunset.fill")
                                    .foregroundColor(.purple)
                                Text(isVi ? "Tiết học chiều" : "Afternoon Sessions")
                                    .font(.system(.subheadline, design: .rounded).bold())
                            }

                            if afternoonList.isEmpty {
                                Text(isVi ? "Nghỉ học chiều" : "No afternoon classes")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .padding(.vertical, 4)
                            } else {
                                VStack(alignment: .leading, spacing: 6) {
                                    ForEach(Array(afternoonList.enumerated()), id: \.offset) { _, period in
                                        HStack(spacing: 8) {
                                            Circle()
                                                .fill(Color.purple)
                                                .frame(width: 5, height: 5)
                                            Text(period)
                                                .font(.system(.body, design: .rounded).weight(.medium))
                                        }
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.04)))
                                    }
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct SettingsMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var showingRenameAlert: Bool = false
    @State private var renameTargetDeviceId: String = ""
    @State private var renameTargetNewName: String = ""

    var body: some View {
        let isVi = client.appLanguage == "vi"
        VStack(alignment: .leading, spacing: 16) {
            Text(isVi ? "Cài đặt & Giao diện ứng dụng" : "Settings & Appearance")
                .font(.title.bold())

            // 1. Language & Independent ESP32 Dark Mode
            LiquidGlassCard(isVi ? "Ngôn ngữ & Chế độ tối ESP32 Độc lập" : "Language & ESP32 Hardware Theme", icon: "gearshape.fill", iconColor: .indigo) {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(isVi ? "Ngôn ngữ ứng dụng:" : "App Language:")
                            .font(.headline)
                        Picker("", selection: $client.appLanguage) {
                            Text(tr("Tiếng Việt")).tag("vi")
                            Text("English").tag("en")
                        }
                        .pickerStyle(.segmented)
                    }

                    Divider()

                    VStack(alignment: .leading, spacing: 6) {
                        Text(isVi ? "Chế độ tối màn hình ESP32 độc lập:" : "Independent ESP32 Dark Mode:")
                            .font(.headline)
                        Text(isVi ? "Chế độ này chỉ áp dụng riêng cho màn hình OLED của trạm ESP32, hoàn toàn độc lập với giao diện ứng dụng." : "This setting only controls the ESP32 OLED display, independently from the desktop app.")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Picker("", selection: $client.esp32ThemeMode) {
                            Text(isVi ? "Đồng bộ theo giờ (Sync)" : "Sync with App").tag("sync")
                            Text(isVi ? "Luôn Sáng (Light)" : "Always Light").tag("light")
                            Text(isVi ? "Luôn Tối (Dark)" : "Always Dark").tag("dark")
                        }
                        .pickerStyle(.segmented)

                        HStack(spacing: 12) {
                            Button(isVi ? "Áp dụng Luôn Sáng (Light Mode)" : "Set Always Light") {
                                client.esp32ThemeMode = "light"
                                Task { await client.setEsp32Theme(mode: "light") }
                            }
                            .buttonStyle(.bordered)

                            Button(isVi ? "Áp dụng Luôn Tối (Dark Mode)" : "Set Always Dark") {
                                client.esp32ThemeMode = "dark"
                                Task { await client.setEsp32Theme(mode: "dark") }
                            }
                            .buttonStyle(.borderedProminent)

                            Button(isVi ? "Áp dụng Đồng bộ theo lịch" : "Set Scheduled") {
                                client.esp32ThemeMode = "sync"
                                Task { await client.setEsp32Theme(mode: "sync") }
                            }
                            .buttonStyle(.bordered)
                        }
                        .padding(.top, 4)
                    }
                }
            }

            // 2. macOS App Appearance & Scheduled Dark Mode
            LiquidGlassCard(isVi ? "Giao diện macOS & Chế độ tối theo lịch" : "macOS Appearance & Scheduled Dark Mode", icon: "moon.stars.fill", iconColor: .purple) {
                VStack(alignment: .leading, spacing: 12) {
                    Picker(isVi ? "Chế độ giao diện macOS:" : "macOS Theme Mode:", selection: $client.themeMode) {
                        ForEach(AppThemeMode.allCases) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(isVi ? "Bắt đầu (HH:MM):" : "Start Time (HH:MM):")
                                .font(.caption)
                            TextField("20:00", text: $client.darkModeStart)
                                .textFieldStyle(.roundedBorder)
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(isVi ? "Kết thúc (HH:MM):" : "End Time (HH:MM):")
                                .font(.caption)
                            TextField("06:00", text: $client.darkModeEnd)
                                .textFieldStyle(.roundedBorder)
                        }
                    }

                    HStack(spacing: 10) {
                        Text(isVi ? "Mẫu giờ nhanh:" : "Presets:")
                            .font(.caption.bold())
                        Button("20:00–06:00") {
                            client.darkModeStart = "20:00"
                            client.darkModeEnd = "06:00"
                            client.themeMode = .scheduled
                        }
                        .buttonStyle(.bordered)
                        Button("22:00–06:00") {
                            client.darkModeStart = "22:00"
                            client.darkModeEnd = "06:00"
                            client.themeMode = .scheduled
                        }
                        .buttonStyle(.bordered)
                        Button("21:00–07:00") {
                            client.darkModeStart = "21:00"
                            client.darkModeEnd = "07:00"
                            client.themeMode = .scheduled
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            // 3. Linked Devices & Dynamic Display Selection
            LiquidGlassCard(isVi ? "Thiết bị kết nối & Chọn máy hiển thị thông số trên ESP32 (Phase 2)" : "Linked Devices & Dynamic Display Selection (Phase 2)", icon: "laptopcomputer.and.iphone", iconColor: .teal) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Button(isVi ? "Làm mới danh sách thiết bị" : "Refresh Devices") {
                            Task {
                                await client.fetchPairedDevices()
                                await client.fetchSelectedDisplayDevice()
                            }
                        }
                        Spacer()
                        Text(isVi ? "Chọn một thiết bị để hiển thị CPU, RAM, Pin lên màn hình ESP32" : "Select a device to stream CPU, RAM, and Battery to ESP32")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    let devices = client.allAvailableDevices
                    if devices.isEmpty {
                        Text(isVi ? "Chưa tìm thấy thiết bị nào." : "No linked devices found.")
                            .foregroundColor(.secondary)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(devices) { dev in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(dev.deviceName ?? "Device") \(dev.deviceId == client.deviceId ? (isVi ? "★ Máy Mac này" : "★ Current Mac") : "")")
                                            .font(.headline)
                                        Text("Nền tảng: \(dev.platform ?? "macOS") • ID: \(dev.deviceId)")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    if dev.deviceId == client.selectedDisplayDeviceId {
                                        Text(isVi ? "● Đang hiển thị spec" : "● Displaying Spec")
                                            .font(.caption.bold())
                                            .padding(6)
                                            .background(Color.blue.opacity(0.18))
                                            .cornerRadius(6)
                                    } else {
                                        Button(isVi ? "Chọn hiển thị" : "Set Display") {
                                            Task { await client.setSelectedDisplayDevice(deviceId: dev.deviceId) }
                                        }
                                        .buttonStyle(.borderedProminent)
                                    }
                                    Button(isVi ? "Đổi tên" : "Rename") {
                                        renameTargetDeviceId = dev.deviceId
                                        renameTargetNewName = dev.deviceName ?? ""
                                        showingRenameAlert = true
                                    }
                                    Button(isVi ? "Hủy liên kết" : "Unlink", role: .destructive) {
                                        Task { await client.unpairDevice(deviceId: dev.deviceId) }
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }

            // 4. Backend Connection & Health Check
            LiquidGlassCard(isVi ? "Kết nối máy chủ & Kiểm tra tình trạng (/api/app/health)" : "Backend Connection & Health Check", icon: "network", iconColor: .cyan) {
                VStack(alignment: .leading, spacing: 12) {
                    TextField(isVi ? "Địa chỉ máy chủ API (Base URL)" : "API Base URL", text: $client.apiUrl)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white.opacity(0.06)))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                    HStack {
                        Button(isVi ? "Kiểm tra kết nối" : "Test Connection (/api/app/health)") {
                            Task { await client.testHealth() }
                        }
                        .buttonStyle(.borderedProminent)
                        Text(client.healthStatusText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .sheet(isPresented: $showingRenameAlert) {
            VStack(alignment: .leading, spacing: 16) {
                Text(isVi ? "Đổi tên thiết bị" : "Rename Device")
                    .font(.title3.bold())
                Text(isVi ? "Nhập tên mới cho thiết bị này để hiển thị trên DeskFlow và màn hình ESP32:" : "Enter a custom name for this device:")
                    .font(.caption)
                    .foregroundColor(.secondary)

                TextField(isVi ? "Nhập tên mới..." : "Enter new name...", text: $renameTargetNewName)
                    .textFieldStyle(.roundedBorder)
                    .frame(minWidth: 260)

                HStack {
                    Spacer()
                    Button(isVi ? "Hủy" : "Cancel") {
                        showingRenameAlert = false
                    }
                    .keyboardShortcut(.cancelAction)

                    Button(isVi ? "Lưu tên" : "Save") {
                        let clean = renameTargetNewName.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !clean.isEmpty {
                            Task { await client.renameDevice(deviceId: renameTargetDeviceId, newName: clean) }
                        }
                        showingRenameAlert = false
                    }
                    .buttonStyle(.borderedProminent)
                    .keyboardShortcut(.defaultAction)
                }
            }
            .padding(20)
            .frame(width: 360)
        }
        .onAppear {
            Task {
                await client.fetchPairedDevices()
                await client.fetchSelectedDisplayDevice()
            }
        }
    }
}

// MARK: - Spotify Media Studio View

public struct SpotifyMacView: View {
    @ObservedObject var client: DeskFlowApiClient
    @State private var volume: Double = 50.0

    public var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack {
                Text(client.appLanguage == "vi" ? "Spotify & Âm nhạc" : "Spotify & Media")
                    .font(.system(.largeTitle, design: .rounded).bold())
                Spacer()
                Button {
                    Task { await client.fetchSpotifyStatus() }
                } label: {
                    Label(client.appLanguage == "vi" ? "Làm mới" : "Refresh", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.bordered)
            }

            // Big Player Card
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Trình phát Spotify" : "Spotify Player",
                icon: "music.note",
                iconColor: Color(hex: "#1db954")
            ) {
                VStack(spacing: 20) {
                    HStack(spacing: 24) {
                        // Album Art box
                        ZStack {
                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [Color(hex: "#1db954").opacity(0.35), Color(hex: "#10b981").opacity(0.15)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 140, height: 140)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                                        .stroke(Color(hex: "#1db954").opacity(0.4), lineWidth: 1.5)
                                )

                            Image(systemName: "music.note.list")
                                .font(.system(size: 48, weight: .bold))
                                .foregroundColor(Color(hex: "#1db954"))
                        }
                        .shadow(color: Color(hex: "#1db954").opacity(0.25), radius: 16, x: 0, y: 8)

                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(client.spotifyState.isPlaying ? (client.appLanguage == "vi" ? "ĐANG PHÁT" : "NOW PLAYING") : (client.appLanguage == "vi" ? "TẠM DỪNG" : "PAUSED"))
                                    .font(.system(size: 11, weight: .bold, design: .rounded))
                                    .foregroundColor(client.spotifyState.isPlaying ? Color(hex: "#1db954") : .secondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill((client.spotifyState.isPlaying ? Color(hex: "#1db954") : Color.gray).opacity(0.15)))

                                if client.spotifyState.connected {
                                    Text(tr("CONNECT ONLINE"))
                                        .font(.system(size: 10, weight: .bold, design: .rounded))
                                        .foregroundColor(.green)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Capsule().fill(Color.green.opacity(0.12)))
                                }
                            }

                            Text(client.spotifyState.track ?? (client.appLanguage == "vi" ? "Chưa phát nhạc" : "Not Playing"))
                                .font(.system(.title2, design: .rounded).bold())
                                .foregroundColor(.white)
                                .lineLimit(2)

                            Text(client.spotifyState.artist ?? "Spotify Desktop Client")
                                .font(.system(.headline, design: .rounded))
                                .foregroundColor(.secondary)
                                .lineLimit(1)

                            if let album = client.spotifyState.album, !album.isEmpty {
                                Text(album)
                                    .font(.system(.caption, design: .rounded))
                                    .foregroundColor(.secondary.opacity(0.8))
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                    }

                    // Progress bar
                    VStack(spacing: 6) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.1))
                                    .frame(height: 6)
                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            colors: [Color(hex: "#1db954"), Color(hex: "#10b981")],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(client.spotifyState.progressPct) / 100.0)), height: 6)
                            }
                        }
                        .frame(height: 6)

                        HStack {
                            Text(formatDuration(ms: client.spotifyState.progressMs))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(formatDuration(ms: client.spotifyState.durationMs))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                    }

                    // Playback Controls
                    HStack(spacing: 24) {
                        Button {
                            Task { await client.controlSpotify(command: "previous") }
                        } label: {
                            Image(systemName: "backward.fill")
                                .font(.system(size: 20))
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.bordered)

                        Button {
                            Task {
                                let cmd = client.spotifyState.isPlaying ? "pause" : "play"
                                await client.controlSpotify(command: cmd)
                            }
                        } label: {
                            Image(systemName: client.spotifyState.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.black)
                                .frame(width: 58, height: 58)
                                .background(Circle().fill(Color(hex: "#1db954")))
                                .shadow(color: Color(hex: "#1db954").opacity(0.4), radius: 10, y: 4)
                        }
                        .buttonStyle(.plain)

                        Button {
                            Task { await client.controlSpotify(command: "next") }
                        } label: {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 20))
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.bordered)

                        Spacer()

                        // Volume Control
                        HStack(spacing: 10) {
                            Image(systemName: "speaker.wave.2.fill")
                                .foregroundColor(.secondary)
                            Slider(value: $volume, in: 0...100, step: 1) { _ in
                                Task { await client.setSpotifyVolume(volume: Int(volume)) }
                            }
                            .frame(width: 120)
                            Text("\(Int(volume))%")
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(.secondary)
                                .frame(width: 36, alignment: .trailing)
                        }
                    }
                }
            }

            // Real-time Synced Lyrics Box
            LiquidGlassCard(
                client.appLanguage == "vi" ? "Lời bài hát thời gian thực (Synced Lyrics)" : "Real-time Synced Lyrics",
                icon: "text.quote",
                iconColor: .purple
            ) {
                VStack(alignment: .leading, spacing: 14) {
                    if let curr = client.spotifyState.currentLyric, !curr.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(curr)
                                .font(.system(.title3, design: .rounded).bold())
                                .foregroundColor(Color(hex: "#38bdf8"))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color(hex: "#38bdf8").opacity(0.12))
                                )

                            if let next = client.spotifyState.nextLyric, !next.isEmpty {
                                Text(next)
                                    .font(.system(.body, design: .rounded))
                                    .foregroundColor(.secondary)
                                    .padding(.leading, 14)
                            }
                        }
                    } else {
                        HStack(spacing: 12) {
                            Image(systemName: "music.mic")
                                .font(.system(size: 20))
                                .foregroundColor(.secondary)
                            Text(client.appLanguage == "vi" ? "Đang đồng bộ lời bài hát từ Spotify / LRCLIB..." : "Syncing lyrics from Spotify / LRCLIB...")
                                .font(.system(.subheadline, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
        }
    }

    private func formatDuration(ms: Int) -> String {
        let totalSec = max(0, ms / 1000)
        let min = totalSec / 60
        let sec = totalSec % 60
        return String(format: "%02d:%02d", min, sec)
    }
}
