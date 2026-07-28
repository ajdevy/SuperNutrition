#import <AVFoundation/AVFoundation.h>

static inline NSInteger SNGetCameraAuthorizationStatus(void) {
    return (NSInteger)[AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
}

typedef void (^SNRequestCameraAccessCompletion)(BOOL granted);

static inline void SNRequestCameraAccess(SNRequestCameraAccessCompletion completion) {
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:completion];
}
