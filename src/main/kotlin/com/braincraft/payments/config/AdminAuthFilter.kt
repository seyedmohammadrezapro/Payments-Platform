package com.braincraft.payments.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AdminAuthFilter(private val props: AppProperties) : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return !request.requestURI.startsWith("/admin")
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val key = request.getHeader("X-Admin-Key")
      ?: request.getHeader("Authorization")?.removePrefix("Bearer ")?.trim()

    if (key == null || key != props.adminApiKey) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      response.writer.write("{\"error\":\"unauthorized\"}")
      return
    }
    filterChain.doFilter(request, response)
  }
}
