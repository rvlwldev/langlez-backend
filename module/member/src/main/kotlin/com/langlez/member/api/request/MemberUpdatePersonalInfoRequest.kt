package com.langlez.member.api.request

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** 부분 수정이다. null 인 필드는 건드리지 않는다. */
data class MemberUpdatePersonalInfoRequest(
    @field:Schema(description = "성별", example = "FEMALE", nullable = true)
    val gender: Member.Gender? = null,

    @field:Schema(description = "생년월일", example = "1995-03-14", nullable = true)
    val birthDay: LocalDate? = null,

    // Locale 이 아니라 국가 코드 문자열로 받는다. Member.country 가 실제로 저장하는 값이고,
    // Locale 로 받으면 언어 태그까지 들어와 저장 시점에 조용히 버려진다.
    @field:Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "KR", nullable = true)
    @field:Pattern(regexp = "^[A-Z]{2}$", message = "validation.member.country.invalid")
    val country: String? = null,

    // 공백만 있는 입력 거부와 앞뒤 공백 제거는 Bean Validation 으로는 못 하니 도메인(Member.changeNickname)이 한다.
    @field:Schema(description = "닉네임 (표시용 이름, handle 과 달리 유니크하지 않다)", example = "지수", nullable = true)
    @field:Size(max = Member.NICKNAME_MAX_LENGTH, message = "member.nickname.invalid")
    val nickname: String? = null,
)
