#import <Foundation/Foundation.h>

static inline NSData * _Nullable SNMakeDataFromBase64(NSString *base64) {
    return [[NSData alloc] initWithBase64EncodedString:base64 options:0];
}
