package com.langlez.security.web

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MemberId

@Deprecated("Use MemberId instead", ReplaceWith("MemberId"))
typealias MemberID = MemberId

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MemberRole