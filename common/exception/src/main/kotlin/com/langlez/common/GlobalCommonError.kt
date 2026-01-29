package com.langlez.common

import org.springframework.http.HttpStatus

enum class GlobalCommonError(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : CommonError {
    INTERNAL_SERVER_ERROR("GLOBAL_500", "알 수 없는 서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST("GLOBAL_400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
}
