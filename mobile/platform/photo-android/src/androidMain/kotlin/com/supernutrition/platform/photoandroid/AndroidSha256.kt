package com.supernutrition.platform.photoandroid

import java.security.MessageDigest

/** Platform crypto shared by photo normalization and immutable catalog verification. */
fun androidSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte ->
        byte.toInt().and(0xff).toString(16).padStart(2, '0')
    }
