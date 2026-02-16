package com.langlez.member.api

import com.langlez.member.api.request.InitHandleNicknameRequestV1
import com.langlez.member.api.response.MemberResponseV1
import com.langlez.member.application.MemberInitService
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Member 초기화 플로우:
 * 1. OAuth2 로그인 성공 → CustomOAuth2UserService에서 Member 생성 (init=false)
 * 2. JWT 발급 (email 포함)
 * 3. 클라이언트가 JWT로 아래 API 순차 호출하여 정보 입력
 * 4. 모든 단계 완료 → init=true로 변경
 */
@RestController
@RequestMapping("/api/v1/members/init")
class MemberInitControllerV1(private val service: MemberInitService) {

    @PostMapping("/handle")
    fun initHandle(
        @AuthenticationPrincipal email: String,
        @RequestBody @Valid request: InitHandleNicknameRequestV1
    ): MemberResponseV1 =
        MemberResponseV1.from(service.initHandle(email, request.handle, request.nickname))

    @PostMapping("/personality")
    fun initPersonality(
        @AuthenticationPrincipal email: String,
        @RequestBody personality: MemberPersonality
    ): MemberResponseV1 = MemberResponseV1.from(service.initPersonality(email, personality))

    @PostMapping("/location")
    fun initLocation(
        @AuthenticationPrincipal email: String,
        @RequestBody location: MemberLocation
    ): MemberResponseV1 = MemberResponseV1.from(service.initLocation(email, location))

    @PostMapping("/introduction")
    fun initIntroduction(
        @AuthenticationPrincipal email: String,
        @RequestBody introduction: MemberIntroduction
    ): MemberResponseV1 = MemberResponseV1.from(service.initIntroduction(email, introduction))

    @PostMapping("/languages")
    fun initLanguages(
        @AuthenticationPrincipal email: String,
        @RequestBody languages: List<MemberLanguage>
    ): MemberResponseV1 = MemberResponseV1.from(service.initLanguages(email, languages))

    @PostMapping("/images", consumes = ["multipart/form-data"])
    fun initImages(
        @AuthenticationPrincipal email: String,
        @RequestPart("profileImage") profileImage: MultipartFile,
        @RequestPart("otherImages", required = false) otherImages: List<MultipartFile>?,
    ): MemberResponseV1 = MemberResponseV1.from(service.initProfileImages(email, profileImage, otherImages))

    @PostMapping("/finish")
    fun finishInit(@AuthenticationPrincipal email: String): MemberResponseV1 =
        MemberResponseV1.from(service.finishInit(email))
}
