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
import java.util.UUID

@RestController
@RequestMapping("/attachments/batches")
class AttachmentController(private val service: AttachmentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: IssueBatchRequest): IssuedBatch =
        service.issue(UUID.fromString(jwt.subject), request)

    @PostMapping("/{batchId}/complete")
    fun complete(@AuthenticationPrincipal jwt: Jwt, @PathVariable batchId: UUID): CompletedBatch =
        service.complete(UUID.fromString(jwt.subject), batchId)
}
