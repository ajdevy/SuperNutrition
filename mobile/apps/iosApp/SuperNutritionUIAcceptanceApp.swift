import SwiftUI
import SharedKit

@main
struct SuperNutritionUIAcceptanceApp: App {
    private let session = PhotoAnalysisAppBootstrap.makeSession()

    var body: some Scene {
        WindowGroup {
            PhotoAnalysisAppRootView(session: session)
        }
    }
}
