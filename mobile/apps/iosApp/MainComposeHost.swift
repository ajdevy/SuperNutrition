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

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SharedKitMain.shared.viewController(
            session: session,
            rewardsSession: rewardsSession,
            onCaptureRequested: onCaptureRequested,
            onProfileRequested: onProfileRequested,
        )
        applyCoveredState(to: controller)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        applyCoveredState(to: uiViewController)
    }

    private func applyCoveredState(to controller: UIViewController) {
        // Exclude the retained Compose subtree at its UIKit boundary.
        controller.view.accessibilityElementsHidden = isCovered
        controller.view.isUserInteractionEnabled = !isCovered
    }
}
#endif
