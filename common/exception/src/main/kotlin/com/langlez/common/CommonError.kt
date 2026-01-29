package com.langlez.common

import org.springframework.http.HttpStatus

interface CommonError {
    val code: String
    val message: String
    val status: HttpStatus
}
