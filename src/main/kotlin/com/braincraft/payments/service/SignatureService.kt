package com.braincraft.payments.service

import com.braincraft.payments.config.AppProperties
import com.braincraft.payments.util.HashUtils
import org.springframework.stereotype.Service

@Service
class SignatureService(private val props: AppProperties) {
  fun verifySignature(rawBody: ByteArray, headerSignature: String?): Boolean {
    if (headerSignature.isNullOrBlank()) return false
    val expected = HashUtils.hmacSha256Hex(props.webhookSecret, rawBody)
    return HashUtils.constantTimeEquals(expected, headerSignature.trim())
  }
}
