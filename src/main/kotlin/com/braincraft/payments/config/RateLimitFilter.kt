package com.braincraft.payments.config

import com.braincraft.payments.service.RateLimiterService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RateLimitFilter(private val limiter: RateLimiterService, private val props: AppProperties) : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.requestURI
    return !(path == "/webhooks/provider" || (path == "/subscriptions" && request.method == "POST"))
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val clientKey = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
      ?: request.remoteAddr
      ?: "unknown"

    val route = request.requestURI
    val key = "${route}:${clientKey}"

    if (!limiter.allow(key, props.rateLimitPerMinute)) {
      response.status = HttpStatus.TOO_MANY_REQUESTS.value()
      response.writer.write("{\"error\":\"rate_limited\"}")
      return
    }

    filterChain.doFilter(request, response)
  }
}
