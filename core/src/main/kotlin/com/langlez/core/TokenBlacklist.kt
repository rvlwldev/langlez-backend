package com.langlez.core

interface TokenBlacklist {
    fun blacklist(token: String, remainingValiditySeconds: Long)
    fun isBlacklisted(token: String): Boolean
}
