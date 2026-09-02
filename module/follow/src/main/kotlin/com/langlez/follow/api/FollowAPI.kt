package com.langlez.follow.api

import com.langlez.follow.api.response.FollowMemberResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Follow", description = "팔로우 API")
interface FollowAPI {

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
    ): List<FollowMemberResponse>

    @Operation(summary = "내가 팔로우한 사람 목록", description = "최신순. 커서 규칙은 팔로워 목록과 같다.")
    fun listFollowings(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<FollowMemberResponse>

    @Operation(
        summary = "특정 회원의 팔로워 목록",
        description = "남의 프로필용. 차단 관계면 403 이다. 커서 규칙은 내 팔로워 목록과 같다.",
    )
    fun listFollowersOf(
        viewerId: Long,
        @Parameter(description = "조회 대상 회원 id") targetId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<FollowMemberResponse>

    @Operation(
        summary = "특정 회원의 팔로잉 목록",
        description = "남의 프로필용. 차단 관계면 403 이다. 커서 규칙은 내 팔로워 목록과 같다.",
    )
    fun listFollowingsOf(
        viewerId: Long,
        @Parameter(description = "조회 대상 회원 id") targetId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<FollowMemberResponse>
}
