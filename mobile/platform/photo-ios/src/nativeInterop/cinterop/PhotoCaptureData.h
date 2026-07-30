#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

static inline AVCaptureDevice * _Nullable SNDefaultBackCamera(void) {
    return [AVCaptureDevice defaultDeviceWithDeviceType:AVCaptureDeviceTypeBuiltInWideAngleCamera
                                             mediaType:AVMediaTypeVideo
                                              position:AVCaptureDevicePositionBack];
}

static inline AVCaptureDeviceInput * _Nullable SNCreateCaptureInput(AVCaptureDevice *device) {
    NSError *error = nil;
    AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device error:&error];
    if (input == nil) {
        NSLog(@"Camera input creation failed: %@", error.localizedDescription ?: @"unknown error");
    }
    return input;
}

static inline void SNSetViewAccessibilityIdentifier(UIView *view, NSString *identifier) {
    view.accessibilityIdentifier = identifier;
}

static inline NSData * _Nullable SNFileDataRepresentation(AVCapturePhoto *photo) {
    return [photo fileDataRepresentation];
}
