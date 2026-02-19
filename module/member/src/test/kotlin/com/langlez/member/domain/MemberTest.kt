package com.langlez.member.domain

import com.langlez.member.domain.embedded.*
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

@DisplayName("Member & MemberProfile 도메인 로직 테스트")
class MemberTest : StringSpec({

    "username 검증 로직 테스트" {
        Member.isValidUsername("valid_user") shouldBe true
        Member.isValidUsername("User123") shouldBe true
        
        Member.isValidUsername("ab") shouldBe false // 너무 짧음
        Member.isValidUsername("toolongusername1234567") shouldBe false // 너무 김
        Member.isValidUsername("invalid-char") shouldBe false // 특수문자
    }

    "MemberProfile 초기화 완료 조건 테스트 - 필수 정보가 있을 때" {
        val member = Member.create("nick", "email@test.com", "id", "GOOGLE", "u").apply { 
            username = "username"
            images.add(MemberImage(0, "url", 0, true))
        }
        val profile = MemberProfile(member = member).apply {
            personality = MemberPersonality(LocalDate.now(), MemberPersonality.Nationality("KR"), MemberPersonality.Gender.MALE, MemberPersonality.MBTI.INTJ)
            location = MemberLocation("Seoul", 37.0, 127.0)
            introduction = MemberIntroduction("bio", "goal", "want")
            languages.add(MemberLanguage(MemberLanguage.Language.KOREAN, MemberLanguage.Level.NATIVE))
        }

        profile.isReadyToFinishInit shouldBe true
    }

    "MemberProfile 초기화 완료 조건 테스트 - 필수 정보 누락 시" {
        val member = Member.create("nick", "email@test.com", "id", "GOOGLE", "u")
        val profile = MemberProfile(member = member)

        profile.isReadyToFinishInit shouldBe false
    }
})
