package com.langlez.core

open class LanglezException(
    val status: Int = 500,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
