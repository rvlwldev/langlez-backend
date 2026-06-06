package com.langlez.security.util

import com.langlez.core.LanglezException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

object ContextUser {

    val id: Long
        get() = runCatching { auth.principal.toString().toLong() }
            .getOrElse { throw LanglezException(401, "auth.invalid-request") }

    val role: String
        get() = auth.authorities.firstOrNull()?.authority
            ?: throw LanglezException(401, "auth.invalid-request")

    private val auth: Authentication
        get() {
            val auth = SecurityContextHolder.getContext().authentication

            if (auth == null || auth.principal == "anonymousUser")
                throw LanglezException(401, "auth.unauthorized")

            return auth
        }

}