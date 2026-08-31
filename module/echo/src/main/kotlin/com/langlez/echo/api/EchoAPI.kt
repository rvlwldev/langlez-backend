package com.langlez.echo.api

import com.langlez.attachment.contract.Storage
import com.langlez.echo.api.request.EchoCommentCreateRequest
import com.langlez.echo.api.request.EchoPostCreateRequest
import com.langlez.echo.api.response.EchoCommentResponse
import com.langlez.echo.api.response.EchoPostResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Echo", description = "피드(글·댓글·좋아요) API")
interface EchoAPI {

    @Operation(
        summary = "글 작성",
        description = "첨부는 upload-url 로 발급받은 key 로만 확정한다. 본문의 `#태그` 는 자동으로 해시태그가 된다.",
    )
    fun createPost(memberId: Long, request: EchoPostCreateRequest): EchoPostResponse

    @Operation(summary = "글 삭제", description = "본인이 쓴 글만 지울 수 있다. 남의 글이면 403 이다.")
    fun deletePost(memberId: Long, @Parameter(description = "글 id") postId: Long)

    @Operation(
        summary = "홈 타임라인",
        description = "내가 팔로우한 사람의 글을 최신순으로. cursor 는 직전 페이지 마지막 글의 id 를 넣는다.",
    )
    fun homeTimeline(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 글 id") cursor: Long?,
    ): List<EchoPostResponse>

    @Operation(summary = "회원 타임라인", description = "특정 회원의 글. 차단 관계면 403 이다. 커서 규칙은 홈 타임라인과 같다.")
    fun memberTimeline(
        viewerId: Long,
        @Parameter(description = "조회 대상 회원 id") authorId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 글 id") cursor: Long?,
    ): List<EchoPostResponse>

    @Operation(
        summary = "해시태그 타임라인",
        description = "태그가 달린 글. `#` 은 붙여도 되고 빼도 되며 대소문자를 가리지 않는다.",
    )
    fun hashtagTimeline(
        viewerId: Long,
        @Parameter(description = "해시태그", example = "seoul") tag: String,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 글 id") cursor: Long?,
    ): List<EchoPostResponse>

    @Operation(summary = "글 상세", description = "차단 관계면 403 이다.")
    fun getPost(viewerId: Long, @Parameter(description = "글 id") postId: Long): EchoPostResponse

    @Operation(summary = "좋아요", description = "이미 누른 글이면 409 다.")
    fun like(memberId: Long, @Parameter(description = "글 id") postId: Long)

    @Operation(summary = "좋아요 취소", description = "누르지 않은 글이어도 성공으로 끝난다.")
    fun unlike(memberId: Long, @Parameter(description = "글 id") postId: Long)

    @Operation(summary = "댓글 작성", description = "차단 관계면 403 이다. 글쓴이에게 알림이 간다.")
    fun comment(
        memberId: Long,
        @Parameter(description = "글 id") postId: Long,
        request: EchoCommentCreateRequest,
    ): EchoCommentResponse

    @Operation(summary = "댓글 목록", description = "오래된 순. cursor 는 직전 페이지 마지막 댓글의 id 를 넣는다.")
    fun listComments(
        viewerId: Long,
        @Parameter(description = "글 id") postId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 댓글 id") cursor: Long?,
    ): List<EchoCommentResponse>

    @Operation(summary = "댓글 삭제", description = "본인이 쓴 댓글만 지울 수 있다. 남의 댓글이면 403 이다.")
    fun deleteComment(memberId: Long, @Parameter(description = "댓글 id") commentId: Long)

    @Operation(
        summary = "첨부 업로드 URL 발급",
        description = "업로드용 presigned URL 과 글 작성에 쓸 key 를 함께 발급한다. image/·video/ 계열만 허용한다.",
    )
    fun getUploadUrl(
        memberId: Long,
        @Parameter(description = "원본 파일명") filename: String,
        @Parameter(description = "Content-Type", example = "image/jpeg") contentType: String,
    ): Storage.PresignedResult
}
