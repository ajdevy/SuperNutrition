import SwiftUI
import SharedKit
import UIKit

#if !arch(x86_64)
/// Retains Main while its native modal excludes the underlying UIKit surface.
struct MainComposeHost: UIViewControllerRepresentable {
    let session: SharedKitPhotoAnalysisSession
    let rewardsSession: SharedKitRewardsSession
    let isCovered: Bool
    let onCaptureRequested: () -> Void
    let onProfileRequested: () -> Void

    func makeUIViewController(context: Context) -> MainComposeContainerController {
        let controller = SharedKitMain.shared.viewController(
            session: session,
            rewardsSession: rewardsSession,
            onCaptureRequested: onCaptureRequested,
            onProfileRequested: onProfileRequested,
        )
        return MainComposeContainerController(content: controller, isCovered: isCovered)
    }

    func updateUIViewController(_ uiViewController: MainComposeContainerController, context: Context) {
        uiViewController.setCovered(isCovered)
    }
}

/// The Compose controller stays contained; only its view leaves the AX hierarchy.
final class MainComposeContainerController: UIViewController {
    private enum AppearancePhase {
        case hidden, appearing, visible, disappearing
    }

    private let content: UIViewController
    private var isCovered: Bool
    private var phase = AppearancePhase.hidden
    private var contentIsVisible = false
    private var pendingAppearance: Bool?

    init(content: UIViewController, isCovered: Bool) {
        self.content = content
        self.isCovered = isCovered
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Use init(content:isCovered:)")
    }

    override var shouldAutomaticallyForwardAppearanceMethods: Bool { false }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        if !isCovered { attachContentView() }
        content.didMove(toParent: self)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        phase = .appearing
        if !isCovered { beginContentAppearance(true, animated: animated) }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        phase = .visible
        finishContentAppearance()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        phase = .disappearing
        beginContentAppearance(false, animated: animated)
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        phase = .hidden
        finishContentAppearance()
    }

    func setCovered(_ covered: Bool) {
        guard covered != isCovered else { return }
        isCovered = covered
        guard isViewLoaded else { return }
        if covered {
            beginContentAppearance(false, animated: false)
            content.viewIfLoaded?.removeFromSuperview()
            finishContentAppearance()
        } else {
            if phase == .appearing || phase == .visible {
                beginContentAppearance(true, animated: false)
            }
            attachContentView()
            if phase == .visible { finishContentAppearance() }
        }
    }

    private func attachContentView() {
        let contentView: UIView = content.view
        contentView.frame = view.bounds
        contentView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(contentView)
    }

    private func beginContentAppearance(_ appearing: Bool, animated: Bool) {
        guard pendingAppearance != appearing else { return }
        // Finish an interrupted parent transition before reversing the child.
        finishContentAppearance()
        guard contentIsVisible != appearing else { return }
        pendingAppearance = appearing
        content.beginAppearanceTransition(appearing, animated: animated)
    }

    private func finishContentAppearance() {
        guard let appearing = pendingAppearance else { return }
        contentIsVisible = appearing
        pendingAppearance = nil
        content.endAppearanceTransition()
    }
}
#endif
