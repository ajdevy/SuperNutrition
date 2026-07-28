import XCTest
import SharedKit

final class BootstrapLaunchTests: XCTestCase {
    func testSharedKitBootstrapIsIdempotentAfterHostStartup() {
        // The application initializer owns the first bootstrap call. The test
        // verifies that the packaged host does not create a second Koin app.
        XCTAssertTrue(SharedKitBootstrap.shared.start())
        XCTAssertFalse(SharedKitBootstrap.shared.start())
    }

    func testCaptureSurfaceExposesNativeAndPickerActions() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.descendants(matching: .any)["bootstrap_shell_launch_di"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["photo_analysis_status"].exists)
        XCTAssertTrue(app.buttons["capture_shutter"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["capture_import"].exists)
    }

    func testSimulatorCameraFailureOffersRecovery() {
        let app = XCUIApplication()
        app.launch()

        let shutter = app.buttons["capture_shutter"]
        XCTAssertTrue(shutter.waitForExistence(timeout: 5))
        shutter.tap()

        let recovery = app.buttons["capture_open_settings"]
        XCTAssertTrue(recovery.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)
    }

    func testSimulatorPhotoPickerOpensAndCanBeCancelled() {
        let app = XCUIApplication()
        app.launch()

        let importButton = app.buttons["capture_import"]
        XCTAssertTrue(importButton.waitForExistence(timeout: 5))
        importButton.tap()

        let cancel = app.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        cancel.tap()

        XCTAssertTrue(importButton.waitForExistence(timeout: 5))
    }

    func testRecoverySurfaceRestoresAfterBackgroundForeground() {
        let app = XCUIApplication()
        app.launch()

        let shutter = app.buttons["capture_shutter"]
        XCTAssertTrue(shutter.waitForExistence(timeout: 5))
        shutter.tap()

        let recovery = app.buttons["capture_open_settings"]
        XCTAssertTrue(recovery.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)

        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(recovery.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)
    }
}
