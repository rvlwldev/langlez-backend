package com.langlez.security.util

import com.langlez.exception.LanglezException
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

object ContextUser {

    val id: Long
        get() = runCatching { auth.principal.toString().toLong() }
            .getOrElse { throw LanglezException(UNAUTHORIZED, "auth.invalid-request") }

    val role: String
        get() = auth.authorities.firstOrNull()?.authority
            ?: throw LanglezException(UNAUTHORIZED, "auth.invalid-request")

    private val auth: Authentication
        get() {
            val auth = SecurityContextHolder.getContext().authentication

            if (auth == null || auth.principal == "anonymousUser")
                throw LanglezException(UNAUTHORIZED, "auth.unauthorized")

            return auth
        }

}