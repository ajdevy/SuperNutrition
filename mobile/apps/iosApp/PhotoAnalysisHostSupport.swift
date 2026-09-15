import SwiftUI
import Foundation
import SharedKit
import UIKit

typealias PhotoAnalysisNativeAction = SharedKitPhotoAnalysisNativeAction

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
    private let rewardsSession: SharedKitRewardsSession
    @State private var selectedTab = 0
    @State private var showingCapture = true
    @StateObject private var cameraAction: PhotoAnalysisCameraAction

    init(session: SharedKitPhotoAnalysisSession) {
        self.session = session
        rewardsSession = SharedKitRewardsSession()
        _cameraAction = StateObject(wrappedValue: PhotoAnalysisCameraAction())
    }

    var body: some View {
        Group {
            if selectedTab == 0 {
                if showingCapture {
                    Group {
                    #if arch(x86_64)
                        PhotoAnalysisIntelCompatibilityView(
                            session: session,
                            cameraAction: cameraAction,
                            onCloseRequested: { showingCapture = false },
                        )
                    #else
                        PhotoAnalysisComposeLifecycleHost(
                            session: session,
                            cameraAction: cameraAction,
                            onCloseRequested: { showingCapture = false },
                        )
                    #endif
                    }
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.captureTab)
                } else {
                    PhotoAnalysisHomeView(onAddFoodRequested: showCaptureSurface)
                        .accessibilityIdentifier(SharedKitAutomationIds.shared.homeTab)
                }
            } else {
                Group {
                #if arch(x86_64)
                    RewardsIntelCompatibilityView(session: rewardsSession)
                #else
                    RewardsComposeHost(session: rewardsSession)
                #endif
                }
                .accessibilityIdentifier(SharedKitAutomationIds.shared.rewardsTab)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if !showingCapture {
                PhotoAnalysisBottomBar(
                    selectedTab: $selectedTab,
                    onHomeSelected: {
                        selectedTab = 0
                        showingCapture = false
                    },
                    onRewardsSelected: {
                        selectedTab = 1
                        showingCapture = false
                    },
                    onCameraRequested: {
                        showCaptureSurface()
                    },
                )
                .padding(.bottom, 12)
            }
        }
    }

    private func showCaptureSurface() {
        selectedTab = 0
        showingCapture = true
    }
}

private struct PhotoAnalysisHomeView: View {
    let onAddFoodRequested: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Home")
                    .font(.title2.weight(.semibold))
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.homeTitle)
                    .padding(.top, 16)

                Text("Welcome back")
                    .font(.body)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.homeGreeting)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Today’s progress")
                        .font(.subheadline.weight(.semibold))
                    Text("Ready when you are")
                        .font(.title3.weight(.semibold))
                    Text("Add a meal whenever you’re ready to start your day.")
                        .font(.body)
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(red: 0.88, green: 0.92, blue: 0.76), in: RoundedRectangle(cornerRadius: 16))
                .accessibilityIdentifier(SharedKitAutomationIds.shared.homeSummary)

                Text("Build your day")
                    .font(.headline)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.homeEmptyTitle)

                Text("Keep track of what you eat and notice the good choices you make.")
                    .font(.body)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.homeEmptyDescription)

                Button("Add a meal", action: onAddFoodRequested)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.homeAddFood)
            }
            .padding(.horizontal, 20)
        }
    }
}

final class PhotoAnalysisCameraAction: ObservableObject {
    var action: (() -> Void)? {
        didSet {
            guard pendingInvocation, action != nil else { return }
            pendingInvocation = false
            DispatchQueue.main.async { [weak self] in self?.action?() }
        }
    }
    private var pendingInvocation = false

    func invoke() {
        guard let action else {
            pendingInvocation = true
            return
        }
        action()
    }
}

private struct PhotoAnalysisBottomBar: View {
    @Binding var selectedTab: Int
    let onHomeSelected: () -> Void
    let onRewardsSelected: () -> Void
    let onCameraRequested: () -> Void

    private let pillColor = Color(red: 0xF3 / 255.0, green: 0xEE / 255.0, blue: 0xEB / 255.0)
    private let activeColor = Color(red: 0x8E / 255.0, green: 0xA3 / 255.0, blue: 0x36 / 255.0)
    private let cameraColor = Color(red: 0x92 / 255.0, green: 0xA6 / 255.0, blue: 0x38 / 255.0)
    private let inactiveIconColor = Color(red: 0x76 / 255.0, green: 0x7B / 255.0, blue: 0x7D / 255.0)
    private let inactiveLabelColor = Color(red: 0x24 / 255.0, green: 0x37 / 255.0, blue: 0x40 / 255.0)

    var body: some View {
        ZStack(alignment: .top) {
            HStack {
                tabButton(
                    label: "Home",
                    systemImage: "house.fill",
                    selected: selectedTab == 0,
                    identifier: SharedKitAutomationIds.shared.homeTab,
                ) {
                    onHomeSelected()
                }
                Spacer()
                tabButton(
                    label: "Rewards",
                    systemImage: "trophy.fill",
                    selected: selectedTab == 1,
                    identifier: SharedKitAutomationIds.shared.rewardsTab,
                ) {
                    onRewardsSelected()
                }
            }
            .padding(.horizontal, 42)
            .frame(maxWidth: .infinity)
            .frame(height: 58)
            .background(
                PhotoAnalysisNavigationPillShape()
                    .fill(pillColor)
                    .shadow(color: Color.black.opacity(0.13), radius: 7, y: 4),
            )
            .clipShape(PhotoAnalysisNavigationPillShape())
            .padding(.top, 10)

            Button(action: onCameraRequested) {
                Image(systemName: "camera")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 50, height: 50)
                    .background(cameraColor, in: Circle())
                    .overlay(Circle().stroke(Color(red: 0x25 / 255.0, green: 0x27 / 255.0, blue: 0x26 / 255.0), lineWidth: 3))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(SharedKitAutomationIds.shared.cameraAction)
            .accessibilityLabel("Take photo")
        }
        .frame(maxWidth: .infinity)
        .frame(height: 80)
        .padding(.horizontal, 28)
    }

    private func tabButton(
        label: String,
        systemImage: String,
        selected: Bool,
        identifier: String,
        action: @escaping () -> Void,
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .semibold))
                    .frame(height: 18)
                Text(label)
                    .font(.system(size: 9, weight: .semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(selected ? activeColor : inactiveIconColor)
            .frame(width: 52, height: 58)
        }
        .buttonStyle(.plain)
        .foregroundStyle(selected ? activeColor : inactiveLabelColor)
        .accessibilityIdentifier(identifier)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct PhotoAnalysisNavigationPillShape: Shape {
    func path(in rect: CGRect) -> Path {
        let radius = rect.height / 2
        let center = rect.midX
        let notchHalfWidth: CGFloat = 50
        let notchDepth = rect.height * 0.64
        var path = Path()
        path.move(to: CGPoint(x: radius, y: rect.minY))
        path.addLine(to: CGPoint(x: center - notchHalfWidth, y: rect.minY))
        path.addCurve(
            to: CGPoint(x: center, y: rect.minY + notchDepth),
            control1: CGPoint(x: center - notchHalfWidth * 0.72, y: rect.minY),
            control2: CGPoint(x: center - notchHalfWidth * 0.72, y: rect.minY + notchDepth),
        )
        path.addCurve(
            to: CGPoint(x: center + notchHalfWidth, y: rect.minY),
            control1: CGPoint(x: center + notchHalfWidth * 0.72, y: rect.minY + notchDepth),
            control2: CGPoint(x: center + notchHalfWidth * 0.72, y: rect.minY),
        )
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: rect.minY))
        path.addArc(
            center: CGPoint(x: rect.maxX - radius, y: rect.midY),
            radius: radius,
            startAngle: .degrees(-90),
            endAngle: .degrees(90),
            clockwise: false,
        )
        path.addLine(to: CGPoint(x: radius, y: rect.maxY))
        path.addArc(
            center: CGPoint(x: radius, y: rect.midY),
            radius: radius,
            startAngle: .degrees(90),
            endAngle: .degrees(270),
            clockwise: false,
        )
        path.closeSubpath()
        return path
    }
}

#if !arch(x86_64)
struct RewardsComposeHost: UIViewControllerRepresentable {
    let session: SharedKitRewardsSession

    func makeUIViewController(context: Context) -> UIViewController {
        SharedKitRewards.shared.viewController(session: session)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#endif

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
    let cameraAction: PhotoAnalysisCameraAction
    let onCloseRequested: () -> Void
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        PhotoAnalysisComposeHost(
            session: session,
            cameraAction: cameraAction,
            onCloseRequested: onCloseRequested,
        )
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
    let cameraAction: PhotoAnalysisCameraAction
    let onCloseRequested: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            session: session,
            cameraAction: cameraAction,
            onCloseRequested: onCloseRequested,
        )
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SharedKitPhotoAnalysis.shared.viewController(
            session: session,
            onNativeAction: { action in
                context.coordinator.handle(action: action)
            },
            onCloseRequested: {
                context.coordinator.closeCapture()
            },
        )
        let launchMarker = UIView(frame: .zero)
        launchMarker.isAccessibilityElement = false
        launchMarker.accessibilityElementsHidden = true
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
        init(
            session: SharedKitPhotoAnalysisSession,
            cameraAction: PhotoAnalysisCameraAction,
            onCloseRequested: @escaping () -> Void,
        ) {
            presentation = PhotoAnalysisPresentationCoordinator(session: session)
            self.onCloseRequested = onCloseRequested
            super.init()
            cameraAction.action = { [weak self] in self?.presentation.takePhoto() }
        }

        let presentation: PhotoAnalysisPresentationCoordinator
        private let onCloseRequested: () -> Void
        weak var hostViewController: UIViewController? {
            didSet { presentation.presenter = hostViewController }
        }

        func handle(action: PhotoAnalysisNativeAction) {
            presentation.handle(action: action)
        }

        func closeCapture() {
            onCloseRequested()
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
        case .takePhoto:
            session.scanAgain()
            takePhoto()
        case .importPhoto:
            session.scanAgain()
            importPhoto()
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
            if let data {
                let opaqueId = UUID().uuidString
                session.photoReturned(opaqueId: opaqueId, sourceData: data)
            } else {
                session.captureCancelled()
            }
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
    let cameraAction: PhotoAnalysisCameraAction
    let onCloseRequested: () -> Void
    private let presentation: PhotoAnalysisPresentationCoordinator
    @Environment(\.scenePhase) private var scenePhase
    @State private var status: String
    @State private var presenter: UIViewController?

    init(
        session: SharedKitPhotoAnalysisSession,
        cameraAction: PhotoAnalysisCameraAction,
        onCloseRequested: @escaping () -> Void,
    ) {
        self.session = session
        self.cameraAction = cameraAction
        self.onCloseRequested = onCloseRequested
        presentation = PhotoAnalysisPresentationCoordinator(session: session)
        _status = State(initialValue: session.statusLabel())
    }

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Spacer()
                Button("×", action: onCloseRequested)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.captureClose)
            }
            Text(" ")
                .accessibilityIdentifier(SharedKitAutomationIds.shared.bootstrapLaunchMarker)
                .accessibilityLabel(String(localized: "bootstrap.ready"))
                .accessibilityHidden(true)
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
        .onAppear { cameraAction.action = { presentation.takePhoto() } }
        .onDisappear { cameraAction.action = nil }
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

#if arch(x86_64)
private struct RewardsIntelCompatibilityView: View {
    let session: SharedKitRewardsSession
    private let cards = [
        ("rewards.protein.title", "rewards.protein.description", "0 of 2"),
        ("rewards.fats.title", "rewards.fats.description", "0 of 1"),
        ("rewards.fiber.title", "rewards.fiber.description", "0 of 2"),
        ("rewards.colors.title", "rewards.colors.description", "0 of 3"),
        ("rewards.products.title", "rewards.products.description", "0 of 30"),
        ("rewards.fermented.title", "rewards.fermented.description", "0 of 3"),
        ("rewards.seven_days.title", "rewards.seven_days.description", "0 of 7"),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(String(localized: "rewards.title"))
                    .font(.title2)
                    .accessibilityIdentifier(SharedKitAutomationIds.shared.rewardsTitle)
                ForEach(Array(cards.enumerated()), id: \.offset) { index, card in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(String(localized: card.0)).font(.headline)
                        Text(String(localized: card.1)).font(.body)
                        Text(card.2).font(.subheadline)
                        Text(String(localized: "rewards.ready")).font(.subheadline)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                    .accessibilityIdentifier("rewards_row_\(index)")
                }
            }
            .padding()
        }
        .accessibilityIdentifier(SharedKitAutomationIds.shared.rewardsList)
    }
}
#endif
#endif
