package com.langlez.security.web

import com.langlez.exception.LanglezException
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer


@Component
class MemberRoleArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(MemberRole::class.java) && parameter.parameterType == String::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        container: ModelAndViewContainer?,
        request: NativeWebRequest,
        dataBinderFactory: WebDataBinderFactory?
    ): String {
        val auth = SecurityContextHolder.getContext().authentication

        if (auth == null || auth.principal == "anonymousUser")
            throw LanglezException(UNAUTHORIZED, "auth.unauthorized")

        return auth.authorities.firstOrNull()?.authority
            ?: throw LanglezException(UNAUTHORIZED, "auth.invalid-role")
    }

}