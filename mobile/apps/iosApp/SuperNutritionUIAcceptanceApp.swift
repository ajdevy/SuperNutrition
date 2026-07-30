import SwiftUI
import SharedKit
import UIKit

@main
struct SuperNutritionUIAcceptanceApp: App {
    private let session: SharedKitPhotoAnalysisSession

    init() {
        _ = SharedKitBootstrap.shared.start()
        session = SharedKitPhotoAnalysisSession(attemptId: PhotoAnalysisHostConstants.attemptID)
    }

    var body: some Scene {
        WindowGroup {
            #if arch(x86_64)
            PhotoAnalysisIntelCompatibilityView(session: session)
            #else
            PhotoAnalysisComposeLifecycleHost(session: session)
            #endif
        }
    }
}
