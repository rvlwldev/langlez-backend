package com.langlez.core

// TODO : TokenRevoker 로 이름 변경, remainingValiditySeconds 를 Duration 으로 변경
interface TokenBlacklist {
    fun blacklist(token: String, remainingValiditySeconds: Long)
    fun isBlacklisted(token: String): Boolean
}
