package com.langlez.common

import org.springframework.http.HttpStatus

enum class GlobalCommonError(
    override val code: String,
    override val message: String,
    override val status: HttpStatus,
) : CommonError {
    INTERNAL_SERVER_ERROR("GLOBAL_500", "알 수 없는 서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST("GLOBAL_400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("GLOBAL_401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    RESOURCE_NOT_FOUND("GLOBAL_404", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONFLICT("GLOBAL_409", "리소스 충돌이 발생했습니다.", HttpStatus.CONFLICT),
}
