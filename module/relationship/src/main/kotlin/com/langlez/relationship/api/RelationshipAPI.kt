package com.langlez.relationship.api

import com.langlez.relationship.api.request.RelationshipReportRequest
import com.langlez.relationship.api.response.RelationshipMemberResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Relationship", description = "팔로우/차단/신고 API")
interface RelationshipAPI {

    @Operation(summary = "팔로우", description = "차단 관계면 막힌다. 이미 팔로우 중이면 아무 일도 일어나지 않는다.")
    fun follow(memberId: Long, @Parameter(description = "팔로우할 회원 id") targetId: Long)

    @Operation(summary = "언팔로우", description = "팔로우 중이 아니어도 성공으로 끝난다.")
    fun unfollow(memberId: Long, @Parameter(description = "언팔로우할 회원 id") targetId: Long)

    @Operation(
        summary = "나를 팔로우한 사람 목록",
        description = "최신순. cursor 는 직전 페이지 마지막 항목의 cursor 값을 넣는다.",
    )
    fun listFollowers(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<RelationshipMemberResponse>

    @Operation(summary = "내가 팔로우한 사람 목록", description = "최신순. 커서 규칙은 팔로워 목록과 같다.")
    fun listFollowings(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<RelationshipMemberResponse>

    @Operation(
        summary = "특정 회원의 팔로워 목록",
        description = "남의 프로필용. 차단 관계면 403 이다. 커서 규칙은 내 팔로워 목록과 같다.",
    )
    fun listFollowersOf(
        viewerId: Long,
        @Parameter(description = "조회 대상 회원 id") targetId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<RelationshipMemberResponse>

    @Operation(
        summary = "특정 회원의 팔로잉 목록",
        description = "남의 프로필용. 차단 관계면 403 이다. 커서 규칙은 내 팔로워 목록과 같다.",
    )
    fun listFollowingsOf(
        viewerId: Long,
        @Parameter(description = "조회 대상 회원 id") targetId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<RelationshipMemberResponse>

    @Operation(summary = "차단", description = "차단하면 서로의 팔로우 관계가 양방향으로 해제된다.")
    fun block(memberId: Long, @Parameter(description = "차단할 회원 id") targetId: Long)

    @Operation(summary = "차단 해제", description = "차단 중이 아니어도 성공으로 끝난다. 팔로우는 복구되지 않는다.")
    fun unblock(memberId: Long, @Parameter(description = "차단 해제할 회원 id") targetId: Long)

    @Operation(summary = "내가 차단한 사람 목록", description = "최신순. 커서 규칙은 팔로워 목록과 같다.")
    fun listBlocks(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<RelationshipMemberResponse>

    @Operation(summary = "게시글 신고", description = "같은 글을 다시 신고해도 신고가 중복으로 쌓이지 않는다.")
    fun report(memberId: Long, request: RelationshipReportRequest)
}
