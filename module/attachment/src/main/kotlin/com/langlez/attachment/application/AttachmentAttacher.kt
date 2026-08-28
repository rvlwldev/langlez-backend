package com.langlez.attachment.application

import com.langlez.attachment.domain.Attachment
import com.langlez.exception.LanglezException
import org.springframework.http.HttpStatus

// Attachment.attach() 의 IllegalArgumentException 을 LanglezException(400) 으로 바꾸는 지점을 CloudAttachmentService / LocalAttachmentService 가 공유한다.
internal fun Attachment.attachOrThrow(sourceId: String?) {
    try {
        attach(sourceId)
    } catch (e: IllegalArgumentException) {
        throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
    }
}
