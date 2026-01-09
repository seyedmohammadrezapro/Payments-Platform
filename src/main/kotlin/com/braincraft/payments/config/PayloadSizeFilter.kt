package com.braincraft.payments.config

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
class PayloadSizeFilter(private val props: AppProperties) : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return when (request.method.uppercase()) {
      "POST", "PUT", "PATCH" -> false
      else -> true
    }
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val maxBytes = props.maxPayloadBytes
    val declared = request.contentLengthLong
    if (declared > 0 && declared > maxBytes) {
      respondTooLarge(response)
      return
    }

    try {
      val wrapped = SizeLimitRequestWrapper(request, maxBytes)
      filterChain.doFilter(wrapped, response)
    } catch (ex: PayloadTooLargeException) {
      respondTooLarge(response)
    }
  }

  private fun respondTooLarge(response: HttpServletResponse) {
    response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
    response.contentType = "application/json"
    response.writer.write("{\"error\":\"payload_too_large\"}")
  }
}

private class SizeLimitRequestWrapper(
  request: HttpServletRequest,
  private val maxBytes: Long
) : HttpServletRequestWrapper(request) {
  override fun getInputStream(): ServletInputStream {
    return LimitedServletInputStream(super.getInputStream(), maxBytes)
  }

  override fun getReader(): BufferedReader {
    val charset = characterEncoding ?: "UTF-8"
    return BufferedReader(InputStreamReader(getInputStream(), charset))
  }
}

private class LimitedServletInputStream(
  private val delegate: ServletInputStream,
  private val maxBytes: Long
) : ServletInputStream() {
  private var count = 0L

  override fun read(): Int {
    val value = delegate.read()
    if (value != -1) {
      count += 1
      if (count > maxBytes) throw PayloadTooLargeException()
    }
    return value
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val read = delegate.read(b, off, len)
    if (read > 0) {
      count += read.toLong()
      if (count > maxBytes) throw PayloadTooLargeException()
    }
    return read
  }

  override fun isFinished(): Boolean = delegate.isFinished

  override fun isReady(): Boolean = delegate.isReady

  override fun setReadListener(readListener: ReadListener?) {
    delegate.setReadListener(readListener)
  }
}

private class PayloadTooLargeException : RuntimeException()
