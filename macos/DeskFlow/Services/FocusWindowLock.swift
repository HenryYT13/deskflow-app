import AppKit
import Combine
import SwiftUI

/// Keeps the DeskFlow window above every other app and refuses to let the user
/// close or quit while a Focus Mode session is active.
///
/// The only supported way out is the product's own math challenge
/// (`/api/focus/challenge` + `/api/focus/verify`) or letting the session expire.
/// The server flips `focusActive` back to `false`, which unlocks the window here.
@MainActor
final class FocusWindowLock {
    static let shared = FocusWindowLock()

    private(set) var isLocked = false

    private weak var window: NSWindow?
    private weak var boundClient: DeskFlowApiClient?
    private var cancellables = Set<AnyCancellable>()
    private var keyMonitor: Any?

    private init() {}

    /// Subscribes to the shared API client's focus state. Safe to call repeatedly.
    func bind(client: DeskFlowApiClient) {
        guard boundClient !== client else { return }
        boundClient = client
        cancellables.removeAll()

        client.$focusActive
            .receive(on: RunLoop.main)
            .sink { [weak self] active in
                self?.setLocked(active)
            }
            .store(in: &cancellables)

        setLocked(client.focusActive)
    }

    /// Called from the SwiftUI window accessor once the hosting window exists.
    func attach(window: NSWindow) {
        if self.window !== window {
            self.window = window
        }
        applyLockState()
    }

    /// `false` means "cancel app termination".
    func allowTermination() -> Bool {
        !isLocked
    }

    // MARK: - State

    func setLocked(_ locked: Bool) {
        guard locked != isLocked else { return }
        isLocked = locked
        applyLockState()
    }

    private func applyLockState() {
        guard let window else { return }

        window.level = isLocked ? .floating : .normal

        // Closing is the visible escape hatch, so remove it while focused.
        window.standardWindowButton(.closeButton)?.isEnabled = !isLocked
        window.standardWindowButton(.miniaturizeButton)?.isEnabled = !isLocked
        window.standardWindowButton(.zoomButton)?.isEnabled = !isLocked

        if isLocked {
            installKeyMonitor()
        } else {
            removeKeyMonitor()
        }
    }

    // MARK: - Keyboard

    /// Blocks ⌘Q / ⌘W / ⌘M / ⌘H even when the user goes through the menu bar.
    private func installKeyMonitor() {
        guard keyMonitor == nil else { return }
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            guard let self, self.isLocked else { return event }
            let modifiers = event.modifierFlags.intersection(.deviceIndependentFlagsMask)
            guard modifiers.contains(.command),
                  let key = event.charactersIgnoringModifiers?.lowercased(),
                  ["q", "w", "m", "h"].contains(key) else {
                return event
            }
            NSSound.beep()
            return nil
        }
    }

    private func removeKeyMonitor() {
        guard let keyMonitor else { return }
        NSEvent.removeMonitor(keyMonitor)
        self.keyMonitor = nil
    }
}

/// Tiny invisible SwiftUI bridge that gives us the NSWindow SwiftUI created.
struct FocusWindowAccessor: NSViewRepresentable {
    final class WindowReader: NSView {
        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window {
                FocusWindowLock.shared.attach(window: window)
            }
        }
    }

    func makeNSView(context: Context) -> NSView {
        WindowReader(frame: .zero)
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

/// Blocks Cmd+Q / Dock Quit while Focus Mode is active.
@MainActor
final class FocusAppDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        FocusWindowLock.shared.allowTermination() ? .terminateNow : .terminateCancel
    }

    // Hiding the app would also let the user "escape" the focus session.
    func applicationDidHide(_ notification: Notification) {
        if FocusWindowLock.shared.isLocked {
            NSApp.unhide(nil)
        }
    }
}
