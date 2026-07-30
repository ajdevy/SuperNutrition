import SwiftUI
import Foundation
import SharedKit
import UIKit

typealias PhotoAnalysisNativeAction = Photo_analysisPhotoAnalysisNativeAction

enum PhotoAnalysisHostConstants {
    static let attemptID: String = {
        guard let bundleIdentifier = Bundle.main.bundleIdentifier,
              !bundleIdentifier.isEmpty else {
            preconditionFailure("The application bundle must declare CFBundleIdentifier")
        }
        return bundleIdentifier
    }()
}

enum PhotoAnalysisAppBootstrap {
    static func makeSession() -> SharedKitPhotoAnalysisSession {
        _ = SharedKitBootstrap.shared.start()
        return SharedKitPhotoAnalysisSession(attemptId: PhotoAnalysisHostConstants.attemptID)
    }
}

struct PhotoAnalysisAppRootView: View {
    let session: SharedKitPhotoAnalysisSession

    var body: some View {
        #if arch(x86_64)
        PhotoAnalysisIntelCompatibilityView(session: session)
        #else
        PhotoAnalysisComposeLifecycleHost(session: session)
        #endif
    }
}

private enum PhotoAnalysisHostAction: Int {
    case takePhoto
    case importPhoto
    case openSettings

    init?(_ action: PhotoAnalysisNativeAction) {
        guard let mapped = Self(rawValue: Int(action.ordinal)) else {
            return nil
        }
        self = mapped
    }
}

struct PhotoAnalysisComposeLifecycleHost: View {
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

struct PhotoAnalysisComposeHost: UIViewControllerRepresentable {
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
            guard let mappedAction = PhotoAnalysisHostAction(action) else {
                session.captureFailed()
                return
            }
            switch mappedAction {
            case .takePhoto:
                requestCameraOrRecover()
            case .importPhoto:
                presentPhotoPicker()
            case .openSettings:
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
        }

        private func requestCameraOrRecover() {
            guard photoBridge.cameraAvailable() else {
                session.cameraUnavailable()
                return
            }

            photoBridge.ensureCameraAccess { [weak self] granted in
                guard let self else { return }
                guard granted.boolValue else {
                    self.session.permissionDenied()
                    return
                }
                guard let hostViewController = self.hostViewController else {
                    self.session.captureFailed()
                    return
                }
                self.photoBridge.presentCamera(
                    from: hostViewController,
                    cancelLabel: self.session.presenterCopy.capture.cancel,
                    shutterLabel: self.session.presenterCopy.capture.takeMealPhoto,
                    onResult: { [weak self] data in
                        guard let self else { return }
                        if let data {
                            self.session.photoReturned(
                                opaqueId: UUID().uuidString,
                                sourceData: data,
                            )
                        } else {
                            self.session.captureCancelled()
                        }
                    },
                    onFailure: { [weak self] in
                        self?.session.captureFailed()
                    },
                )
            }
        }

        private func presentPhotoPicker() {
            guard let hostViewController else {
                session.captureFailed()
                return
            }
            photoBridge.presentPhotoPicker(
                from: hostViewController,
                onResult: { [weak self] data in
                    guard let self else { return }
                    if let data {
                        self.session.photoReturned(
                            opaqueId: UUID().uuidString,
                            sourceData: data,
                        )
                    } else {
                        self.session.captureCancelled()
                    }
                },
                onFailure: { [weak self] in
                    self?.session.captureFailed()
                },
            )
        }
    }
}

#if arch(x86_64)
/// Compatibility renderer for Intel hosts whose pinned Compose graph has no iosX64 publication.
struct PhotoAnalysisIntelCompatibilityView: View {
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
                .accessibilityLabel(String(localized: "bootstrap.ready"))
                .frame(width: 1, height: 1)
                .opacity(0.01)
            Text(session.presenterCopy.capture.title)
                .accessibilityIdentifier(SharedKitAutomationIds.shared.photoAnalysisTitle)
            Text(status)
                .accessibilityIdentifier(SharedKitAutomationIds.shared.photoAnalysisStatus)

            Button(session.presenterCopy.capture.takeMealPhoto) {
                requestCameraOrRecover()
            }
            .accessibilityIdentifier(SharedKitAutomationIds.shared.captureShutter)

            Button(session.presenterCopy.capture.choosePhoto) {
                presentPhotoPicker()
            }
            .accessibilityIdentifier(SharedKitAutomationIds.shared.captureImport)

            if session.shouldOfferSettings() {
                Button(session.presenterCopy.capture.openSettings) {
                    openSettings()
                }
                .accessibilityIdentifier(SharedKitAutomationIds.shared.captureOpenSettings)
            }
        }
        .padding()
        .background(PhotoAnalysisNativePresenter { presenter = $0 })
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
                photoBridge.presentCamera(
                    from: presenter,
                    cancelLabel: session.presenterCopy.capture.cancel,
                    shutterLabel: session.presenterCopy.capture.takeMealPhoto,
                    onResult: { data in
                        DispatchQueue.main.async {
                            if let data {
                                session.photoReturned(opaqueId: UUID().uuidString, sourceData: data)
                            } else {
                                session.captureCancelled()
                            }
                            refreshStatus()
                        }
                    },
                    onFailure: {
                        session.captureFailed()
                        refreshStatus()
                    },
                )
            }
        }
    }

    private func presentPhotoPicker() {
        guard let presenter else {
            session.captureFailed()
            refreshStatus()
            return
        }
        photoBridge.presentPhotoPicker(
            from: presenter,
            onResult: { data in
                DispatchQueue.main.async {
                    if let data {
                        session.photoReturned(opaqueId: UUID().uuidString, sourceData: data)
                    } else {
                        session.captureCancelled()
                    }
                    refreshStatus()
                }
            },
            onFailure: {
                session.captureFailed()
                refreshStatus()
            },
        )
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

private struct PhotoAnalysisNativePresenter: UIViewControllerRepresentable {
    let onReady: (UIViewController) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        onReady(controller)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#endif
