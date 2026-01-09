package com.braincraft.payments.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HashUtils {
  fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun hmacSha256Hex(secret: String, body: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    mac.init(key)
    val bytes = mac.doFinal(body)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
      result = result or (a[i].code xor b[i].code)
    }
    return result == 0
  }
}
