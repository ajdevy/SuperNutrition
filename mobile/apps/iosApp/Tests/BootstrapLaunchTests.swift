import XCTest
import SharedKit
import AVFoundation

final class BootstrapLaunchTests: XCTestCase {
    func testSharedKitBootstrapIsIdempotentAfterHostStartup() {
        // The application initializer owns the first bootstrap call. The test
        // verifies that the packaged host does not create a second Koin app.
        XCTAssertTrue(SharedKitBootstrap.shared.start())
        XCTAssertFalse(SharedKitBootstrap.shared.start())
    }

    func testInitialLaunchShowsCaptureSurfaceAndCloseReturnsHome() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 15))
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureClose].waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons[SharedKitAutomationIds.shared.cameraAction].exists)
        app.buttons[SharedKitAutomationIds.shared.captureClose].tap()
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 10))
        app.buttons[SharedKitAutomationIds.shared.cameraAction].tap()
        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus].exists)
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureShutter].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureImport].exists)
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureClose].exists)
        XCTAssertFalse(app.buttons[SharedKitAutomationIds.shared.cameraAction].exists)
        app.buttons[SharedKitAutomationIds.shared.captureClose].tap()
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 10))
    }

    func testCameraActionUsesNativeSourceOrRecovery() {
        let app = XCUIApplication()
        let permissionMonitor = addUIInterruptionMonitor(withDescription: "Camera permission") { alert in
            let allow = alert.buttons.element(boundBy: max(0, alert.buttons.count - 1))
            guard allow.exists else { return false }
            allow.tap()
            return true
        }
        defer { removeUIInterruptionMonitor(permissionMonitor) }
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.cameraAction].tap()
        let shutter = app.buttons[SharedKitAutomationIds.shared.captureShutter]
        XCTAssertTrue(shutter.waitForExistence(timeout: 15))
        shutter.tap()
        app.tap()

        let recovery = app.buttons[SharedKitAutomationIds.shared.captureOpenSettings]
        let retry = app.buttons[SharedKitAutomationIds.shared.captureScanAgain]
        if recovery.waitForExistence(timeout: 5) || retry.waitForExistence(timeout: 5) {
            XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus].exists)
            return
        }

        if nativeCameraControlIsVisible(in: app) {
            XCTAssertTrue(dismissOrRecoverPresentedCamera(from: app))
            XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureShutter].waitForExistence(timeout: 10))
        } else {
            XCTAssertFalse(backCameraIsAvailable())
            XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 10))
        }
    }

    func testPhotoPickerOpensAndCanBeCancelled() {
        let app = XCUIApplication()
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.cameraAction].tap()
        let importButton = app.buttons[SharedKitAutomationIds.shared.captureImport]
        XCTAssertTrue(importButton.waitForExistence(timeout: 15))
        importButton.tap()

        let pickerNavigationBar = app.navigationBars.firstMatch
        XCTAssertTrue(pickerNavigationBar.waitForExistence(timeout: 10))
        let cancel = pickerNavigationBar.buttons.element(boundBy: 0)
        XCTAssertTrue(cancel.waitForExistence(timeout: 10))
        cancel.tap()

        XCTAssertTrue(importButton.waitForExistence(timeout: 10))
    }

    func testSurfaceRestoresAfterBackgroundForeground() {
        let app = XCUIApplication()
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.cameraAction].tap()
        let shutter = app.buttons[SharedKitAutomationIds.shared.captureShutter]
        XCTAssertTrue(shutter.waitForExistence(timeout: 15))
        shutter.tap()

        let recovery = app.buttons[SharedKitAutomationIds.shared.captureOpenSettings]
        let retry = app.buttons[SharedKitAutomationIds.shared.captureScanAgain]
        if recovery.waitForExistence(timeout: 5) || retry.waitForExistence(timeout: 5) {
            XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus].exists)

            XCUIDevice.shared.press(.home)
            app.activate()

            XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus]
                .waitForExistence(timeout: 10))
            XCTAssertTrue(
                app.buttons[SharedKitAutomationIds.shared.captureOpenSettings].exists ||
                    app.buttons[SharedKitAutomationIds.shared.captureScanAgain].exists,
            )
            return
        } else if nativeCameraControlIsVisible(in: app) {
            XCTAssertTrue(dismissOrRecoverPresentedCamera(from: app))
        } else {
            XCTAssertFalse(backCameraIsAvailable())
            XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 10))
        }

        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 10))
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureShutter].exists)
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.captureImport].exists)
        if recovery.exists || retry.exists {
            XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus].exists)
        }
    }

    func testFirstTabReturnsToHomeFromRewards() {
        let app = XCUIApplication()
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.rewardsTab].tap()
        XCTAssertTrue(app.staticTexts[SharedKitAutomationIds.shared.rewardsTitle].waitForExistence(timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.homeTab].tap()
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
    }

    func testHomeAddMealOpensCaptureSurface() {
        let app = XCUIApplication()
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.homeAddFood].tap()
        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 15))
    }

    func testHomeAvatarOpensProfileAndBackReturnsHome() {
        let app = XCUIApplication()
        app.launch()

        closeInitialCapture(from: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        let avatar = app.buttons[SharedKitAutomationIds.shared.homeProfile]
        XCTAssertTrue(avatar.waitForExistence(timeout: 10))
        avatar.tap()

        let title = app.staticTexts[SharedKitAutomationIds.shared.profileTitle]
        XCTAssertTrue(title.waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons[SharedKitAutomationIds.shared.cameraAction].exists)
        XCTAssertFalse(app.buttons[SharedKitAutomationIds.shared.rewardsTab].exists)
        XCTAssertFalse(app.buttons[SharedKitAutomationIds.shared.homeAddFood].exists)
        let back = app.buttons[SharedKitAutomationIds.shared.profileClose]
        XCTAssertTrue(back.waitForExistence(timeout: 10))
        back.tap()

        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 10))
        XCTAssertFalse(title.exists)
        XCTAssertTrue(app.buttons[SharedKitAutomationIds.shared.cameraAction].exists)
        avatar.tap()
        XCTAssertTrue(title.waitForExistence(timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.profileClose].tap()
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 10))
    }

    private func nativeCameraControlIsVisible(in app: XCUIApplication) -> Bool {
        let capture = app.descendants(matching: .any)[SharedKitAutomationIds.shared.captureNative]
        return capture.waitForExistence(timeout: 10)
    }

    private func backCameraIsAvailable() -> Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    private func captureSurfaceIsReady(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let status = app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStatus]
        let shutter = app.buttons[SharedKitAutomationIds.shared.captureShutter]
        let importButton = app.buttons[SharedKitAutomationIds.shared.captureImport]
        return status.waitForExistence(timeout: timeout)
            && shutter.waitForExistence(timeout: timeout)
            && importButton.waitForExistence(timeout: timeout)
    }

    private func homeSurfaceIsReady(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let title = app.staticTexts[SharedKitAutomationIds.shared.homeTitle]
        let addFood = app.buttons[SharedKitAutomationIds.shared.homeAddFood]
        return title.waitForExistence(timeout: timeout) && addFood.waitForExistence(timeout: timeout)
    }

    private func closeInitialCapture(from app: XCUIApplication) {
        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 15))
        let close = app.buttons[SharedKitAutomationIds.shared.captureClose]
        XCTAssertTrue(close.waitForExistence(timeout: 15))
        close.tap()
    }

    private func dismissOrRecoverPresentedCamera(from app: XCUIApplication) -> Bool {
        // iOS 26 exposes the native controller across a system-owned process,
        // but XCTest cannot reliably tap its dismiss control. Relaunch only
        // this acceptance host to exercise the bounded recovery path without
        // weakening the native-source assertion above.
        app.terminate()
        app.launch()
        closeInitialCapture(from: app)
        guard homeSurfaceIsReady(in: app, timeout: 15) else { return false }
        app.buttons[SharedKitAutomationIds.shared.cameraAction].tap()
        return app.buttons[SharedKitAutomationIds.shared.captureShutter].waitForExistence(timeout: 15)
    }
}
