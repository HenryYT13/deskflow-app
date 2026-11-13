import SwiftUI
import AppKit
import Combine

@main
struct DeskFlowApp: App {
    @NSApplicationDelegateAdaptor(FocusAppDelegate.self) private var appDelegate
    @StateObject private var apiClient = DeskFlowApiClient()
    @State private var currentMinutes: Int = {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: Date())
        return (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
    }()

    private let timer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()
    private let telemetryTimer = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    init() {
        let args = CommandLine.arguments
        if args.contains("--version") {
            print("Deskflow macOS (Swift + SwiftUI) v1.0.9")
            exit(0)
        }
        if args.contains("--run-tests") {
            var finished = false
            var exitCode: Int32 = 1
            Task { @MainActor in
                exitCode = await MacSelfTestRunner.runAllTests()
                finished = true
            }
            while !finished {
                RunLoop.current.run(mode: .default, before: Date(timeIntervalSinceNow: 0.02))
            }
            exit(exitCode)
        }
        if args.contains("--self-test") {
            let client = DeskFlowApiClient()
            let hosting = NSHostingController(rootView: MainSplitView(client: client))
            _ = hosting.view.fittingSize
            var finished = false
            Task { @MainActor in
                let healthy = await client.testHealth()
                print("[DeskFlow-macOS] GUI Self-Test instantiated NSHostingController(MainSplitView) and verified health (healthy=\(healthy), status=\(client.healthStatusText)).")
                finished = true
            }
            while !finished {
                RunLoop.current.run(mode: .default, before: Date(timeIntervalSinceNow: 0.02))
            }
            exit(0)
        }

        if let iconUrl = Bundle.main.url(forResource: "AppIcon", withExtension: "icns"),
           let iconImg = NSImage(contentsOf: iconUrl) {
            NSApplication.shared.applicationIconImage = iconImg
        }
    }

    private var effectiveColorScheme: ColorScheme? {
        switch apiClient.themeMode {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        case .scheduled:
            let isDark = ScheduledDarkModeEvaluator.isScheduledDarkActive(
                nowMinutes: currentMinutes,
                startStr: apiClient.darkModeStart,
                endStr: apiClient.darkModeEnd,
                fallbackWhenEqual: false
            )
            return isDark ? .dark : .light
        }
    }

    var body: some Scene {
        WindowGroup("Deskflow") {
            MainSplitView(client: apiClient)
                .frame(minWidth: 960, minHeight: 640)
                .preferredColorScheme(effectiveColorScheme)
                .background(FocusWindowAccessor().frame(width: 0, height: 0))
                .onReceive(timer) { now in
                    let comps = Calendar.current.dateComponents([.hour, .minute], from: now)
                    currentMinutes = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
                    // Keep the Focus Mode lock in sync if the session ends
                    // (timeout or unlocked from another device).
                    Task { await apiClient.fetchFocusStatus() }
                }
                .onReceive(telemetryTimer) { _ in
                    Task {
                        await apiClient.reportTelemetry()
                        await apiClient.fetchSystemStatus()
                        await apiClient.fetchSpotifyStatus()
                    }
                }
                .task {
                    FocusWindowLock.shared.bind(client: apiClient)

                    let args = CommandLine.arguments
                    if args.contains("--run-tests") {
                        let code = await MacSelfTestRunner.runAllTests()
                        exit(code)
                    }
                    if args.contains("--self-test") {
                        let healthy = await apiClient.testHealth()
                        print("[DeskFlow-macOS] GUI Self-Test launched window and verified health (healthy=\(healthy), status=\(apiClient.healthStatusText)).")
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        exit(0)
                    }
                    await apiClient.refreshAll()
                }
        }
        .commands {
            CommandMenu(tr("Station")) {
                Button(tr("Refresh All Telemetry")) {
                    Task { await apiClient.refreshAll() }
                }
                .keyboardShortcut("r", modifiers: [.command])

                Button(tr("Test Backend Health")) {
                    Task { await apiClient.testHealth() }
                }
                .keyboardShortcut("t", modifiers: [.command, .shift])
            }
        }
    }
}
