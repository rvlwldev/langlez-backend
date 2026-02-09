package com.langlez.member.domain.embedded

import jakarta.persistence.Embeddable
import jakarta.validation.constraints.Size

@Embeddable
data class MemberIntroduction(
        @field:Size(max = 500, message = "") val bio: String?, // 자기소개
        @field:Size(max = 500, message = "") val goal: String?, // 학습목표
        @field:Size(max = 500, message = "") val want: String?, // 원하는 학습 메이트
)
