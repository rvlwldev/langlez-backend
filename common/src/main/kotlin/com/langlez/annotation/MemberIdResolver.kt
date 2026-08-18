package com.langlez.annotation

import com.langlez.exception.LanglezException
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class MemberIdResolver : HandlerMethodArgumentResolver {

    private val auth: Authentication
        get() {
            val auth = SecurityContextHolder.getContext().authentication

            if (auth == null || auth.principal == "anonymousUser")
                throw LanglezException(401, "auth.unauthorized")

            return auth
        }

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(MemberId::class.java) && parameter.parameterType == Long::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        dataBinderFactory: WebDataBinderFactory?
    ): Any {
        val principal = auth.principal

        return (principal as? Long)
            ?: (principal as? String)?.toLongOrNull()
            ?: throw LanglezException(HttpStatus.UNAUTHORIZED, "auth.invalid-request")
    }

}