#import <AVFoundation/AVFoundation.h>

static inline NSData *SNFileDataRepresentation(AVCapturePhoto *photo) {
    return [photo fileDataRepresentation];
}
