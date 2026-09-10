package com.langlez.member.api

import com.langlez.attachment.contract.Storage
import com.langlez.member.api.request.MemberUpdateFcmTokenRequest
import com.langlez.member.api.request.MemberUpdateHandleRequest
import com.langlez.member.api.request.MemberUpdateImageRequest
import com.langlez.member.api.request.MemberUpdatePersonalInfoRequest
import com.langlez.member.api.response.MemberMeResponse
import com.langlez.member.api.response.MemberOnlineStatusResponse
import com.langlez.member.api.response.MemberPublicResponse
import com.langlez.member.api.response.MemberSearchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Member", description = "회원 계정 관리 API")
interface MemberAPI {

    @Operation(summary = "내 정보 조회", description = "로그인한 회원 본인의 정보를 조회한다.")
    fun getMe(memberId: Long): MemberMeResponse

    @Operation(summary = "핸들 변경", description = "로그인한 회원의 handle(고유 아이디)을 변경한다. 15일 쿨다운 및 중복 검사가 있다.")
    fun patchHandle(memberId: Long, request: MemberUpdateHandleRequest): MemberMeResponse

    @Operation(
        summary = "개인정보 수정",
        description = "성별/생년월일/국가/닉네임을 부분 수정한다. 보내지 않은 항목은 그대로 유지된다. " +
            "성별/생년월일/국가는 프로필(PATCH /api/v1/profiles/me)에서 이쪽으로 옮겨왔다. " +
            "닉네임은 handle 과 달리 유니크하지 않고, 지우는 기능은 없다.",
    )
    fun patchPersonalInfo(memberId: Long, request: MemberUpdatePersonalInfoRequest): MemberMeResponse

    @Operation(summary = "FCM 토큰 갱신", description = "푸시 알림 발송을 위한 FCM 토큰을 갱신한다.")
    fun patchFcmToken(memberId: Long, request: MemberUpdateFcmTokenRequest)

    @Operation(summary = "프로필 이미지 업로드 URL 발급", description = "프로필 이미지를 업로드할 Presigned URL과 key를 발급받는다.")
    fun getImageUploadUrl(
        memberId: Long,
        @Parameter(description = "원본 파일명") filename: String,
    ): Storage.PresignedResult

    @Operation(summary = "프로필 이미지 확정", description = "업로드 완료된 key로 실제 업로드 여부를 확인하고 프로필 이미지로 확정한다.")
    fun patchImage(memberId: Long, request: MemberUpdateImageRequest): MemberMeResponse

    @Operation(summary = "회원 탈퇴", description = "로그인한 회원 본인을 탈퇴 처리한다.")
    fun withdraw(memberId: Long)

    @Operation(
        summary = "회원 검색",
        description = "handle 또는 nickname 부분 일치로 활성 회원을 검색한다. 검색어는 최소 2글자 이상이어야 한다.",
    )
    fun search(
        @Parameter(description = "검색어 (최소 2자)") query: String,
        @Parameter(description = "가져올 개수") size: Int,
        @Parameter(description = "커서(직전 페이지 마지막 회원 id)", required = false) cursor: Long?,
    ): List<MemberSearchResponse>

    @Operation(summary = "공개 프로필 조회", description = "handle로 특정 회원의 공개 정보를 조회한다.")
    fun getMember(@Parameter(description = "회원 handle") handle: String): MemberPublicResponse

    @Operation(summary = "온라인 상태 조회", description = "handle로 특정 회원의 온라인 여부를 조회한다.")
    fun getOnlineStatus(@Parameter(description = "회원 handle") handle: String): MemberOnlineStatusResponse
}
