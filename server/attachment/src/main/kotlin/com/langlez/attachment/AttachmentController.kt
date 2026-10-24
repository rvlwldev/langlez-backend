package com.langlez.attachment

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/attachments/batches")
class AttachmentController(private val service: AttachmentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: IssueBatchRequest): IssuedBatch =
        service.issue(uploaderId(jwt), request)

    @PostMapping("/{batchId}/complete")
    fun complete(@AuthenticationPrincipal jwt: Jwt, @PathVariable batchId: UUID): CompletedBatch =
        service.complete(uploaderId(jwt), batchId)

    private fun uploaderId(jwt: Jwt): UUID =
        runCatching { UUID.fromString(jwt.subject) }
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰의 sub 가 UUID 가 아닙니다") }
}
