package com.langlez.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

object SecurityUtils {
    fun getCurrentUserEmail(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated) {
            throw IllegalStateException("Security Context에 인증 정보가 없습니다.")
        }
        val principal = authentication.principal
        return if (principal is UserDetails) {
            principal.username
        } else {
            principal.toString()
        }
    }
}
