import SwiftUI
import SharedKit
import UIKit

@main
struct SuperNutritionUIAcceptanceApp: App {
    private let session = PhotoAnalysisAppBootstrap.makeSession()

    var body: some Scene {
        WindowGroup {
            PhotoAnalysisAppRootView(session: session)
        }
    }
}
