import SwiftUI
import SharedKit
import UIKit

/// Production host. UI-acceptance-only launch and compatibility code remains in
/// SuperNutritionUIAcceptanceApp.swift and is compiled only by the acceptance target.
@main
struct SuperNutritionApp: App {
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
