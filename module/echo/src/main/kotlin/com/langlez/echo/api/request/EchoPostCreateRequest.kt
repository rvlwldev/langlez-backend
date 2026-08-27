package com.langlez.echo.api.request

import com.langlez.echo.domain.Post
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

data class EchoPostCreateRequest(
    @field:Schema(description = "본문. 첨부만 올릴 땐 비워도 된다. `#태그` 는 자동으로 해시태그가 된다")
    @field:Size(max = Post.MAX_CONTENT_LENGTH)
    val content: String = "",

    // 첨부 확정은 key 하나당 스토리지 왕복 1회다. 상한이 없으면 요청 한 번으로 서버를 붙잡아 둘 수 있다.
    @field:Schema(description = "upload-url 로 발급받아 업로드 완료한 첨부 key 목록")
    @field:Size(max = Post.MAX_MEDIA_COUNT)
    @field:NoBlankElements
    val keys: List<String> = emptyList(),
)
