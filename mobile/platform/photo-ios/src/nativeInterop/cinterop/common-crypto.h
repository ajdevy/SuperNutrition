#include <CommonCrypto/CommonDigest.h>

static inline void SN_SHA256(
    const void *data,
    unsigned int length,
    unsigned char *output
) {
    CC_SHA256(data, (CC_LONG)length, output);
}
