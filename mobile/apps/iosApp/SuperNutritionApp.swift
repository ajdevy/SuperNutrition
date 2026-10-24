import SwiftUI
import SharedKit
import UIKit

typealias PhotoAnalysisNativeAction = SharedKitPhotoAnalysisNativeAction

private enum PhotoAnalysisNativeActionKind: Int {
    case takePhoto
    case importPhoto
    case openSettings
}

/// Production app shell. Screen content and navigation within each surface remain shared.
@main
struct SuperNutritionApp: App {
    private let session: SharedKitPhotoAnalysisSession

    init() {
        _ = SharedKitBootstrap.shared.start()
        session = SharedKitPhotoAnalysisSession(attemptId: "ios-photo-attempt")
    }

    var body: some Scene {
        WindowGroup {
            #if arch(x86_64)
            ProductionUnsupportedRenderer()
            #else
            ProductionAppHost(session: session)
            #endif
        }
    }
}

#if arch(x86_64)
private struct ProductionUnsupportedRenderer: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("Super Nutrition")
                .font(.title)
            Text("The main interface requires an arm64 iOS device or simulator. It is unavailable on this Intel simulator.")
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
#else
private enum ProductionDestination: String, Identifiable {
    case capture
    case profile

    var id: String { rawValue }
}

private struct ProductionAppHost: View {
    let session: SharedKitPhotoAnalysisSession
    @Environment(\.scenePhase) private var scenePhase
    @State private var rewardsSession: SharedKitRewardsSession
    @State private var profileSession: SharedKitProfileSession
    @State private var destination: ProductionDestination?

    init(session: SharedKitPhotoAnalysisSession) {
        self.session = session
        let rewards = SharedKitRewardsSession()
        _rewardsSession = State(initialValue: rewards)
        _profileSession = State(initialValue: SharedKitProfileSession(
            rewardsSession: rewards,
            photoAnalysisSession: session,
        ))
    }

    var body: some View {
        ProductionMainComposeHost(
            session: session,
            rewardsSession: rewardsSession,
            onCaptureRequested: showCapture,
            onProfileRequested: { destination = .profile },
        )
            .ignoresSafeArea()
            .fullScreenCover(item: $destination) { surface in
                switch surface {
                case .capture:
                    ProductionPhotoAnalysisComposeHost(
                        session: session,
                        onCloseRequested: closeCapture,
                        onMealReviewCompleted: closeCapture,
                    )
                    .ignoresSafeArea()
                    .interactiveDismissDisabled()
                case .profile:
                    ProductionProfileComposeHost(
                        session: profileSession,
                        onCloseRequested: { destination = nil },
                    )
                }
            }
            .onChange(of: scenePhase) { phase in
                guard destination == .capture else { return }
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

    private func showCapture() {
        session.scanAgain()
        destination = .capture
    }

    private func closeCapture() {
        session.cancel()
        destination = nil
    }
}

private struct ProductionMainComposeHost: UIViewControllerRepresentable {
    let session: SharedKitPhotoAnalysisSession
    let rewardsSession: SharedKitRewardsSession
    let onCaptureRequested: () -> Void
    let onProfileRequested: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        SharedKitMain.shared.viewController(
            session: session,
            rewardsSession: rewardsSession,
            onCaptureRequested: onCaptureRequested,
            onProfileRequested: onProfileRequested,
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private struct ProductionProfileComposeHost: UIViewControllerRepresentable {
    let session: SharedKitProfileSession
    let onCloseRequested: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        SharedKitProfile.shared.viewController(session: session, onCloseRequested: onCloseRequested)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private struct ProductionPhotoAnalysisComposeHost: UIViewControllerRepresentable {
    let session: SharedKitPhotoAnalysisSession
    let onCloseRequested: () -> Void
    let onMealReviewCompleted: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SharedKitPhotoAnalysis.shared.viewController(
            session: session,
            onNativeAction: { (action: SharedKitPhotoAnalysisNativeAction) in
                context.coordinator.handle(action: action)
            },
            onCloseRequested: onCloseRequested,
            onMealReviewCompleted: onMealReviewCompleted,
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
            guard let action = PhotoAnalysisNativeActionKind(rawValue: Int(action.ordinal)) else { return }
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
                    self.photoBridge.presentCamera(
                        from: hostViewController,
                        cancelLabel: self.session.presenterCopy.capture.cancel,
                        shutterLabel: self.session.presenterCopy.capture.takeMealPhoto,
                        onResult: { [weak self] data in
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
                        },
                        onFailure: { [weak self] in
                            DispatchQueue.main.async {
                                self?.session.captureFailed()
                            }
                        },
                    )
                }
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
                },
                onFailure: { [weak self] in
                    DispatchQueue.main.async {
                        self?.session.captureFailed()
                    }
                },
            )
        }
    }
}
#endif
