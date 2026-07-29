import SwiftUI
import SharedKit
import UIKit

@main
struct SuperNutritionUIAcceptanceApp: App {
    private let session: SharedKitPhotoAnalysisSession

    init() {
        _ = SharedKitBootstrap.shared.start()
        session = SharedKitPhotoAnalysisSession(attemptId: "ios-photo-attempt")
    }

    var body: some Scene {
        WindowGroup {
            #if arch(x86_64)
            IntelCompatibilityCaptureView(session: session)
            #else
            PhotoAnalysisComposeLifecycleHost(session: session)
            #endif
        }
    }
}

#if arch(x86_64)
/// The pinned Compose release has no iosX64 publication. This temporary local
/// lane keeps the same shared presenter/reducer and native bridge semantics;
/// only the renderer is SwiftUI so the Intel simulator remains executable.
private struct IntelCompatibilityCaptureView: View {
    let session: SharedKitPhotoAnalysisSession
    private let photoBridge: IosPhotoBridge
    @Environment(\.scenePhase) private var scenePhase
    @State private var status: String
    @State private var presenter: UIViewController?

    init(session: SharedKitPhotoAnalysisSession) {
        self.session = session
        photoBridge = IosPhotoBridge()
        _status = State(initialValue: session.statusLabel())
    }

    var body: some View {
        VStack(spacing: 16) {
            Text(" ")
                .accessibilityIdentifier(SharedKitAutomationIds.shared.bootstrapLaunchMarker)
                .accessibilityLabel("Bootstrap ready")
                .frame(width: 1, height: 1)
                .opacity(0.01)
            Text("Capture")
                .accessibilityIdentifier(SharedKitAutomationIds.shared.photoAnalysisTitle)
            Text(status)
                .accessibilityIdentifier(SharedKitAutomationIds.shared.photoAnalysisStatus)

            Button("Take meal photo") {
                requestCameraOrRecover()
            }
            .accessibilityIdentifier(SharedKitAutomationIds.shared.captureShutter)

            Button("Choose photo") {
                presentPhotoPicker()
            }
            .accessibilityIdentifier(SharedKitAutomationIds.shared.captureImport)

            if status == "Camera permission is required" || status == "Camera is unavailable" {
                Button("Open settings") {
                    openSettings()
                }
                .accessibilityIdentifier(SharedKitAutomationIds.shared.captureOpenSettings)
            }
        }
        .padding()
        .background(NativeBridgePresenter { presenter = $0 })
        .onChange(of: scenePhase) { phase in
            handle(scenePhase: phase)
        }
    }

    private func refreshStatus() {
        status = session.statusLabel()
    }

    private func handle(scenePhase: ScenePhase) {
        switch scenePhase {
        case .background:
            session.backgrounded()
        case .active:
            session.restored()
            refreshStatus()
        case .inactive:
            break
        @unknown default:
            break
        }
    }

    private func requestCameraOrRecover() {
        guard photoBridge.cameraAvailable() else {
            session.cameraUnavailable()
            refreshStatus()
            return
        }

        photoBridge.ensureCameraAccess { granted in
            DispatchQueue.main.async {
                guard granted.boolValue else {
                    session.permissionDenied()
                    refreshStatus()
                    return
                }
                guard let presenter else {
                    session.captureFailed()
                    refreshStatus()
                    return
                }
                photoBridge.presentCamera(from: presenter) { data in
                    DispatchQueue.main.async {
                        if let data {
                            session.photoReturned(opaqueId: UUID().uuidString, sourceData: data)
                        } else {
                            session.captureCancelled()
                        }
                        refreshStatus()
                    }
                }
            }
        }
    }

    private func presentPhotoPicker() {
        guard let presenter else {
            session.captureFailed()
            refreshStatus()
            return
        }
        photoBridge.presentPhotoPicker(from: presenter) { data in
            DispatchQueue.main.async {
                if let data {
                    session.photoReturned(opaqueId: UUID().uuidString, sourceData: data)
                } else {
                    session.captureCancelled()
                }
                refreshStatus()
            }
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct NativeBridgePresenter: UIViewControllerRepresentable {
    let onReady: (UIViewController) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        DispatchQueue.main.async {
            onReady(controller)
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#else
/// Canonical iOS host: Compose renders the product surface; Swift only routes
/// native actions to the platform photo bridge and handles settings/lifecycle.
private struct PhotoAnalysisComposeLifecycleHost: View {
    let session: SharedKitPhotoAnalysisSession
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        PhotoAnalysisComposeHost(session: session)
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    session.backgrounded()
                case .active:
                    session.restored()
                case .inactive:
                    break
                @unknown default:
                    break
                }
            }
    }
}

private struct PhotoAnalysisComposeHost: UIViewControllerRepresentable {
    let session: SharedKitPhotoAnalysisSession

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SharedKitPhotoAnalysis.shared.viewController(
            session: session,
            onNativeAction: { action in
                context.coordinator.handle(action: action)
            },
        )
        context.coordinator.hostViewController = controller
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinator: NSObject {
        let session: SharedKitPhotoAnalysisSession
        let photoBridge: IosPhotoBridge
        weak var hostViewController: UIViewController?

        init(session: SharedKitPhotoAnalysisSession) {
            self.session = session
            photoBridge = IosPhotoBridge()
        }

        func handle(action: PhotoAnalysisNativeAction) {
            switch action.name {
            case "TakePhoto":
                requestCameraOrRecover()
            case "ImportPhoto":
                presentPhotoPicker()
            case "OpenSettings":
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            default:
                break
            }
        }

        private func requestCameraOrRecover() {
            guard photoBridge.cameraAvailable() else {
                session.cameraUnavailable()
                return
            }

            photoBridge.ensureCameraAccess { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self else { return }
                    guard granted.boolValue else {
                        self.session.permissionDenied()
                        return
                    }
                    guard let hostViewController = self.hostViewController else {
                        self.session.captureFailed()
                        return
                    }
                    self.photoBridge.presentCamera(from: hostViewController) { [weak self] data in
                        DispatchQueue.main.async {
                            guard let self else { return }
                            if let data {
                                self.session.photoReturned(
                                    opaqueId: UUID().uuidString,
                                    sourceData: data,
                                )
                            } else {
                                self.session.captureCancelled()
                            }
                        }
                    }
                }
            }
        }

        private func presentPhotoPicker() {
            guard let hostViewController else { return }
            photoBridge.presentPhotoPicker(from: hostViewController) { [weak self] data in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if let data {
                        self.session.photoReturned(
                            opaqueId: UUID().uuidString,
                            sourceData: data,
                        )
                    } else {
                        self.session.captureCancelled()
                    }
                }
            }
        }
    }
}
#endif
