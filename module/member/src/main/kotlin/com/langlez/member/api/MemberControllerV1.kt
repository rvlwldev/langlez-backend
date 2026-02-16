package com.langlez.member.api

import com.langlez.member.api.response.MemberResponseV1
import com.langlez.member.application.MemberService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberControllerV1(private val service: MemberService) {

    /** 현재 로그인한 사용자 정보 조회 */
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal email: String): MemberResponseV1 =
            MemberResponseV1.from(service.getMember(email))

    /** Handle로 사용자 정보 조회 */
    @GetMapping("/@{handle}")
    fun getMemberByHandle(@PathVariable handle: String): MemberResponseV1 =
            MemberResponseV1.from(service.getMemberByHandle(handle))

    /** ID로 사용자 정보 조회 */
    @GetMapping("/{id}")
    fun getMemberById(@PathVariable id: Long): MemberResponseV1 =
            MemberResponseV1.from(service.getMemberById(id))

}
