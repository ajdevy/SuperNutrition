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
        let launchMarker = UIView(frame: .zero)
        launchMarker.isAccessibilityElement = true
        launchMarker.accessibilityIdentifier = SharedKitAutomationIds.shared.bootstrapLaunchMarker
        launchMarker.accessibilityLabel = String(localized: "bootstrap.ready")
        launchMarker.alpha = 0.01
        launchMarker.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(launchMarker)
        NSLayoutConstraint.activate([
            launchMarker.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor),
            launchMarker.topAnchor.constraint(equalTo: controller.view.topAnchor),
            launchMarker.widthAnchor.constraint(equalToConstant: 1),
            launchMarker.heightAnchor.constraint(equalToConstant: 1),
        ])
        context.coordinator.hostViewController = controller
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinator: NSObject {
        init(session: SharedKitPhotoAnalysisSession) {
            presentation = PhotoAnalysisPresentationCoordinator(session: session)
        }

        let presentation: PhotoAnalysisPresentationCoordinator
        weak var hostViewController: UIViewController? {
            didSet { presentation.presenter = hostViewController }
        }

        func handle(action: PhotoAnalysisNativeAction) {
            presentation.handle(action: action)
        }
    }
}

/// Shared orchestration for both Swift hosts. Native implementation remains an injected port.
final class PhotoAnalysisPresentationCoordinator {
    let session: SharedKitPhotoAnalysisSession
    private let photoBridge: IosPhotoPresentationPort
    weak var presenter: UIViewController?
    var didChange: () -> Void = {}

    init(session: SharedKitPhotoAnalysisSession, photoBridge: IosPhotoPresentationPort = IosPhotoBridge()) {
        self.session = session
        self.photoBridge = photoBridge
    }

    func handle(action: PhotoAnalysisNativeAction) {
        guard let action = PhotoAnalysisHostAction(action) else { fail(); return }
        switch action {
        case .takePhoto: takePhoto()
        case .importPhoto: importPhoto()
        case .openSettings: openSettings()
        }
    }

    func takePhoto() { requestCamera() }
    func importPhoto() { presentPhotoPicker() }

    private func requestCamera() {
        guard photoBridge.cameraAvailable() else { session.cameraUnavailable(); changed(); return }
        photoBridge.ensureCameraAccess { [weak self] granted in
            self?.onMain {
                guard granted.boolValue else { self?.session.permissionDenied(); self?.changed(); return }
                self?.presentCamera()
            }
        }
    }

    private func presentCamera() {
        guard let presenter else { fail(); return }
        photoBridge.presentCamera(
            from: presenter,
            cancelLabel: session.presenterCopy.capture.cancel,
            shutterLabel: session.presenterCopy.capture.takeMealPhoto,
            onResult: { [weak self] data in self?.complete(data) },
            onFailure: { [weak self] in self?.fail() },
        )
    }

    private func presentPhotoPicker() {
        guard let presenter else { fail(); return }
        photoBridge.presentPhotoPicker(
            from: presenter,
            onResult: { [weak self] data in self?.complete(data) },
            onFailure: { [weak self] in self?.fail() },
        )
    }

    private func complete(_ data: Data?) {
        onMain { [weak self] in
            guard let self else { return }
            if let data { session.photoReturned(opaqueId: UUID().uuidString, sourceData: data) }
            else { session.captureCancelled() }
            changed()
        }
    }

    private func fail() { onMain { [weak self] in self?.session.captureFailed(); self?.changed() } }
    private func changed() { didChange() }
    private func openSettings() { guard let url = URL(string: UIApplication.openSettingsURLString) else { return }; UIApplication.shared.open(url) }
    private func onMain(_ work: @escaping () -> Void) { if Thread.isMainThread { work() } else { DispatchQueue.main.async(execute: work) } }
}

#if arch(x86_64)
/// Compatibility renderer for Intel hosts whose pinned Compose graph has no iosX64 publication.
struct PhotoAnalysisIntelCompatibilityView: View {
    let session: SharedKitPhotoAnalysisSession
    private let presentation: PhotoAnalysisPresentationCoordinator
    @Environment(\.scenePhase) private var scenePhase
    @State private var status: String
    @State private var presenter: UIViewController?

    init(session: SharedKitPhotoAnalysisSession) {
        self.session = session
        presentation = PhotoAnalysisPresentationCoordinator(session: session)
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
                presentation.takePhoto()
            }
            .accessibilityIdentifier(SharedKitAutomationIds.shared.captureShutter)

            Button(session.presenterCopy.capture.choosePhoto) {
                presentation.importPhoto()
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
        .background(PhotoAnalysisNativePresenter { controller in
            presenter = controller
            presentation.presenter = controller
        })
        .onAppear { presentation.didChange = refreshStatus }
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
