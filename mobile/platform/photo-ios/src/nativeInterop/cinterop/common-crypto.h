#include <CommonCrypto/CommonDigest.h>

static inline int SN_SHA256(
    const void *data,
    unsigned int length,
    unsigned char *output
) {
    return CC_SHA256(data, (CC_LONG)length, output) != 0;
}
