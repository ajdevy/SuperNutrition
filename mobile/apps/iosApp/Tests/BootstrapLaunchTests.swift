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
        XCTAssertEqual(
            app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStage].value as? String,
            readyToCaptureStatus,
        )
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

    func testTextMealSaveHistoryCorrectionAndDeletion() throws {
        #if arch(x86_64)
        throw XCTSkip("Compose 1.11.1 has no Intel iOS renderer; execute on arm64")
        #else
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(captureSurfaceIsReady(in: app, timeout: 15))
        tapVisible(app.buttons[SharedKitAutomationIds.shared.captureText], in: app)
        let name = app.descendants(matching: .any)[SharedKitAutomationIds.shared.mealFormName]
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        dismissKeyboardIntroduction(in: app)
        name.typeText("Apple oatmeal")
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealFormSave], in: app)
        XCTAssertTrue(homeSurfaceIsReady(in: app, timeout: 15))
        app.buttons[SharedKitAutomationIds.shared.homeProfile].tap()
        tapVisible(app.buttons[SharedKitAutomationIds.shared.profileHistory], in: app)
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealHistoryRow(index: 0)], in: app)
        let title = app.staticTexts[SharedKitAutomationIds.shared.mealDetailsTitle]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertEqual(title.label, "Apple oatmeal")
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealDetailsEdit], in: app)
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        name.typeText(" corrected")
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealFormSave], in: app)
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertEqual(title.label, "Apple oatmeal corrected")
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealDetailsDelete], in: app)
        tapVisible(app.buttons[SharedKitAutomationIds.shared.mealDetailsDeleteConfirm], in: app)
        XCTAssertTrue(app.descendants(matching: .any)[SharedKitAutomationIds.shared.mealHistoryEmpty]
            .waitForExistence(timeout: 10))
        #endif
    }

    private func tapVisible(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 10))
        dismissKeyboardIntroduction(in: app)
        var scrolled = false
        for _ in 0..<8 where !element.isHittable {
            scrollAboveKeyboard(in: app)
            scrolled = true
        }
        XCTAssertTrue(element.isHittable)
        guard element.isHittable else { return }
        if scrolled {
            guard waitForSettledFrame(of: element) else { return }
        }
        element.tap()
    }

    private func waitForSettledFrame(of element: XCUIElement) -> Bool {
        var previousFrame = CGRect.null
        var stableSince = ProcessInfo.processInfo.systemUptime
        let settled = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                let now = ProcessInfo.processInfo.systemUptime
                guard element.exists && element.isHittable else {
                    previousFrame = .null
                    stableSince = now
                    return false
                }
                let frame = element.frame
                if frame != previousFrame {
                    previousFrame = frame
                    stableSince = now
                    return false
                }
                return now - stableSince >= 0.5
            },
            object: element,
        )
        let result = XCTWaiter.wait(for: [settled], timeout: 5)
        XCTAssertEqual(result, .completed, "Wait for scrolling to settle before one tap")
        return result == .completed
    }

    private func dismissKeyboardIntroduction(in app: XCUIApplication) {
        let introduction = app.otherElements["UIContinuousPathIntroductionView"]
        guard introduction.waitForExistence(timeout: 1) else { return }
        let button = introduction.buttons.firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 2))
        let buttonCount = introduction.buttons.count
        XCTAssertEqual(buttonCount, 1, "Only dismiss the identified keyboard introduction's sole action")
        XCTAssertTrue(button.isHittable)
        guard buttonCount == 1 && button.isHittable else { return }
        button.tap()
        let dismissed = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"),
            object: introduction,
        )
        XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 3), .completed)
    }

    private func scrollAboveKeyboard(in app: XCUIApplication) {
        let bounds = app.frame
        var visibleBottom = bounds.maxY
        let keyboardSurfaces = [
            app.keyboards.firstMatch,
            app.otherElements["SystemInputAssistantView"].firstMatch,
            app.otherElements["inputView"].firstMatch,
        ]
        for surface in keyboardSurfaces where surface.exists {
            let overlap = bounds.intersection(surface.frame)
            if !overlap.isNull && !overlap.isEmpty {
                visibleBottom = min(visibleBottom, overlap.minY)
            }
        }
        let visibleHeight = visibleBottom - bounds.minY
        XCTAssertGreaterThan(visibleHeight, 120, "The app needs unobscured space for a scroll gesture")
        guard visibleHeight > 120 else { return }
        let origin = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
        let x = bounds.width * 0.85
        let start = origin.withOffset(CGVector(dx: x, dy: visibleHeight * 0.85))
        let end = origin.withOffset(CGVector(dx: x, dy: visibleHeight * 0.30))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func nativeCameraControlIsVisible(in app: XCUIApplication) -> Bool {
        let capture = app.descendants(matching: .any)[SharedKitAutomationIds.shared.captureNative]
        return capture.waitForExistence(timeout: 10)
    }

    private func backCameraIsAvailable() -> Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    private func captureSurfaceIsReady(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let stage = app.descendants(matching: .any)[SharedKitAutomationIds.shared.photoAnalysisStage]
        let shutter = app.buttons[SharedKitAutomationIds.shared.captureShutter]
        let importButton = app.buttons[SharedKitAutomationIds.shared.captureImport]
        let close = app.buttons[SharedKitAutomationIds.shared.captureClose]
        guard stage.waitForExistence(timeout: timeout) else { return false }
        // Compose maps the stage's stateDescription to UIKit accessibilityValue.
        // Capture has no separate status Text; that belongs to progress/recovery.
        let readyStatus = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == %@", readyToCaptureStatus),
            object: stage,
        )
        return XCTWaiter.wait(for: [readyStatus], timeout: timeout) == .completed
            && shutter.waitForExistence(timeout: timeout)
            && importButton.waitForExistence(timeout: timeout)
            && close.waitForExistence(timeout: timeout)
    }

    private var readyToCaptureStatus: String {
        let key = "photo_analysis.status.ready_to_capture"
        let status = Bundle(for: BootstrapLaunchTests.self).localizedString(forKey: key, value: nil, table: nil)
        XCTAssertFalse(status.isEmpty, "Ready status copy must be bundled with the UI tests")
        XCTAssertNotEqual(status, key, "Ready status copy must resolve from the product localization")
        return status
    }

    private func homeSurfaceIsReady(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let title = app.descendants(matching: .any)[SharedKitAutomationIds.shared.homeTitle]
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
