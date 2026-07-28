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

        XCTAssertTrue(app.descendants(matching: .any)["bootstrap_shell_launch_di"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any)["photo_analysis_status"].exists)
        XCTAssertTrue(app.buttons["capture_shutter"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["capture_import"].exists)
    }

    func testCameraActionUsesNativeSourceOrRecovery() {
        let app = XCUIApplication()
        let permissionMonitor = addUIInterruptionMonitor(withDescription: "Camera permission") { alert in
            let allow = alert.buttons["Allow"]
            guard allow.exists else { return false }
            allow.tap()
            return true
        }
        defer { removeUIInterruptionMonitor(permissionMonitor) }
        app.launch()

        let shutter = app.buttons["capture_shutter"]
        XCTAssertTrue(shutter.waitForExistence(timeout: 15))
        shutter.tap()
        app.tap()

        let recovery = app.buttons["capture_open_settings"]
        if recovery.waitForExistence(timeout: 5) {
            XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)
            return
        }

        let cameraCancel = app.buttons["Cancel"]
        XCTAssertTrue(cameraCancel.waitForExistence(timeout: 10))
        cameraCancel.tap()
        XCTAssertTrue(shutter.waitForExistence(timeout: 10))
    }

    func testPhotoPickerOpensAndCanBeCancelled() {
        let app = XCUIApplication()
        app.launch()

        let importButton = app.buttons["capture_import"]
        XCTAssertTrue(importButton.waitForExistence(timeout: 15))
        importButton.tap()

        let cancel = app.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 10))
        cancel.tap()

        XCTAssertTrue(importButton.waitForExistence(timeout: 10))
    }

    func testSurfaceRestoresAfterBackgroundForeground() {
        let app = XCUIApplication()
        app.launch()

        let shutter = app.buttons["capture_shutter"]
        XCTAssertTrue(shutter.waitForExistence(timeout: 15))
        shutter.tap()

        let recovery = app.buttons["capture_open_settings"]
        let cameraCancel = app.buttons["Cancel"]
        if recovery.waitForExistence(timeout: 5) {
            XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)
        } else {
            XCTAssertTrue(cameraCancel.waitForExistence(timeout: 10))
            cameraCancel.tap()
        }

        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(app.descendants(matching: .any)["bootstrap_shell_launch_di"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["capture_shutter"].exists)
        XCTAssertTrue(app.buttons["capture_import"].exists)
        if recovery.exists {
            XCTAssertTrue(app.staticTexts["Camera is unavailable"].exists)
        }
    }
}
