import SwiftUI
import SharedKit
import UIKit

/// Production host. UI-acceptance-only launch and compatibility code remains in
/// SuperNutritionUIAcceptanceApp.swift and is compiled only by the acceptance target.
@main
struct SuperNutritionApp: App {
    private let session = PhotoAnalysisAppBootstrap.makeSession()

    var body: some Scene {
        WindowGroup {
            PhotoAnalysisAppRootView(session: session)
        }
    }
}
