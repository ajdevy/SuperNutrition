#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <ImageIO/ImageIO.h>
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

static inline void SNConfigureCaptureDevice(AVCaptureDevice *device) {
    NSError *error = nil;
    if (![device lockForConfiguration:&error]) {
        NSLog(@"Camera configuration lock failed: %@", error.localizedDescription ?: @"unknown error");
        return;
    }
    if ([device isFocusModeSupported:AVCaptureFocusModeContinuousAutoFocus]) {
        device.focusMode = AVCaptureFocusModeContinuousAutoFocus;
    }
    if ([device isExposureModeSupported:AVCaptureExposureModeContinuousAutoExposure]) {
        device.exposureMode = AVCaptureExposureModeContinuousAutoExposure;
    }
    if ([device isWhiteBalanceModeSupported:AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance]) {
        device.whiteBalanceMode = AVCaptureWhiteBalanceModeContinuousAutoWhiteBalance;
    }
    [device unlockForConfiguration];
}

static inline void SNSetViewAccessibilityIdentifier(UIView *view, NSString *identifier) {
    view.accessibilityIdentifier = identifier;
}

// Native camera controls use the same bundled Figma exports as the shared renderer.
static inline void SNConfigureCameraArtwork(UIButton *button, NSString *name, NSString *label) {
    UIImageView *artwork = [[UIImageView alloc] initWithImage:[UIImage imageNamed:name]];
    artwork.tag = 1;
    artwork.isAccessibilityElement = NO;
    artwork.userInteractionEnabled = NO;
    [button addSubview:artwork];
    button.accessibilityLabel = label;
    button.isAccessibilityElement = YES;
}

// Keep the exported artwork anchored while giving small controls a full touch target.
static inline void SNSetCameraArtworkFrame(UIButton *button, CGRect artworkFrame) {
    CGFloat width = MAX(48.0, CGRectGetWidth(artworkFrame));
    CGFloat height = MAX(48.0, CGRectGetHeight(artworkFrame));
    button.frame = CGRectMake(CGRectGetMidX(artworkFrame) - width / 2.0,
                             CGRectGetMidY(artworkFrame) - height / 2.0,
                             width, height);
    [button viewWithTag:1].frame = CGRectMake((width - CGRectGetWidth(artworkFrame)) / 2.0,
                                           (height - CGRectGetHeight(artworkFrame)) / 2.0,
                                           CGRectGetWidth(artworkFrame), CGRectGetHeight(artworkFrame));
}

static inline NSData * _Nullable SNFileDataRepresentation(AVCapturePhoto *photo) {
    return [photo fileDataRepresentation];
}

static inline UIImage * _Nullable SNCreateBoundedImage(NSData *data, unsigned int maxDimension) {
    CGImageSourceRef source = CGImageSourceCreateWithData((__bridge CFDataRef)data, NULL);
    if (source == NULL) return nil;

    NSDictionary *options = @{
        (NSString *)kCGImageSourceCreateThumbnailFromImageAlways: @YES,
        (NSString *)kCGImageSourceCreateThumbnailWithTransform: @YES,
        (NSString *)kCGImageSourceThumbnailMaxPixelSize: @(maxDimension),
    };
    CGImageRef thumbnail = CGImageSourceCreateThumbnailAtIndex(
        source,
        0,
        (__bridge CFDictionaryRef)options
    );
    CFRelease(source);
    if (thumbnail == NULL) return nil;

    UIImage *image = [UIImage imageWithCGImage:thumbnail scale:1.0 orientation:UIImageOrientationUp];
    CGImageRelease(thumbnail);
    return image;
}
