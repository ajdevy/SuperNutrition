import SwiftUI
import SharedKit
import UIKit

typealias PhotoAnalysisNativeAction = SharedKitPhotoAnalysisNativeAction

/// Production host. UI-acceptance-only launch and compatibility code remains in
/// SuperNutritionUIAcceptanceApp.swift and is compiled only by the acceptance target.
@main
struct SuperNutritionApp: App {
    private let session: SharedKitPhotoAnalysisSession

    init() {
        _ = SharedKitBootstrap.shared.start()
        session = SharedKitPhotoAnalysisSession(attemptId: "ios-photo-attempt")
    }

    var body: some Scene {
        WindowGroup {
            ProductionPhotoAnalysisHost(session: session)
        }
    }
}

private struct ProductionPhotoAnalysisHost: View {
    let session: SharedKitPhotoAnalysisSession
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ProductionPhotoAnalysisComposeHost(session: session)
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

private struct ProductionPhotoAnalysisComposeHost: UIViewControllerRepresentable {
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
            switch action {
            case .takePhoto:
                requestCameraOrRecover()
            case .importPhoto:
                presentPhotoPicker()
            case .openSettings:
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
            guard let hostViewController else {
                session.captureFailed()
                return
            }
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
