#import <AVFoundation/AVFoundation.h>
#import <dispatch/dispatch.h>

static inline AVAuthorizationStatus SNGetCameraAuthorizationStatus(void) {
    return [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
}

typedef void (^SNRequestCameraAccessCompletion)(BOOL granted);

static inline void SNRequestCameraAccess(SNRequestCameraAccessCompletion completion) {
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
        dispatch_async(dispatch_get_main_queue(), ^{
            completion(granted);
        });
    }];
}
