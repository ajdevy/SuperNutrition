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
    @StateObject private var sessions = ProductionSessionOwner()

    var body: some Scene {
        WindowGroup {
            #if arch(x86_64)
            ProductionUnsupportedRenderer()
            #else
            ProductionAppHost(sessions: sessions)
            #endif
        }
    }
}

/// One app lifetime owns the shared source; transient screens only borrow its sessions.
private final class ProductionSessionOwner: ObservableObject {
    let photoAnalysisSession: SharedKitPhotoAnalysisSession
    let rewardsSession: SharedKitRewardsSession
    let profileSession: SharedKitProfileSession

    init() {
        _ = SharedKitBootstrap.shared.start()
        let photo = SharedKitProductionSessions.shared.photoAnalysisSession(attemptId: "ios-photo-attempt")
        let rewards = SharedKitRewardsSession()
        photoAnalysisSession = photo
        rewardsSession = rewards
        profileSession = SharedKitProductionSessions.shared.profileSession(
            rewardsSession: rewards,
            photoAnalysisSession: photo,
        )
    }

    func startCatalog() {
        let retainedSession = photoAnalysisSession
        onProductionMain { retainedSession.startCatalog() }
    }

    deinit {
        let retainedSession = photoAnalysisSession
        onProductionMain { retainedSession.closeCatalog() }
    }
}

private func onProductionMain(_ action: @escaping () -> Void) {
    if Thread.isMainThread {
        action()
    } else {
        DispatchQueue.main.async { action() }
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
    let sessions: ProductionSessionOwner
    @Environment(\.scenePhase) private var scenePhase
    @State private var destination: ProductionDestination?

    private var session: SharedKitPhotoAnalysisSession { sessions.photoAnalysisSession }
    private var rewardsSession: SharedKitRewardsSession { sessions.rewardsSession }
    private var profileSession: SharedKitProfileSession { sessions.profileSession }

    var body: some View {
        MainComposeHost(
            session: session,
            rewardsSession: rewardsSession,
            isCovered: destination != nil,
            onCaptureRequested: showCapture,
            onProfileRequested: { destination = .profile },
        )
            .ignoresSafeArea()
            .onAppear(perform: sessions.startCatalog)
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
