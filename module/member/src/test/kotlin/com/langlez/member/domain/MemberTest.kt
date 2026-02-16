package com.langlez.member.domain

import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLanguage.Language
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import com.langlez.member.domain.embedded.MemberPersonality.Nationality
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate

@DisplayName("Member: 도메인 로직 테스트")
class MemberTest : FunSpec({
    test("유효한 handle 형식 검증") {
        Member.isValidHandle("langlez_user") shouldBe true
        Member.isValidHandle("user123") shouldBe true
        Member.isValidHandle("abc") shouldBe true
        Member.isValidHandle("a_b_c_12345_xyz") shouldBe true
        Member.isValidHandle("User_Name") shouldBe true
        Member.isValidHandle("ABC") shouldBe true
        Member.isValidHandle("LanglezUser") shouldBe true
    }

    test("잘못된 handle 형식 검증") {
        Member.isValidHandle("a") shouldBe false // 너무 짧음
        Member.isValidHandle("ab") shouldBe false // 너무 짧음
        Member.isValidHandle("AB") shouldBe false // 너무 짧음 (2자)
        Member.isValidHandle("user@name") shouldBe false // 특수문자
        Member.isValidHandle("user-name") shouldBe false // 하이픈
        Member.isValidHandle("user.name") shouldBe false // 점
        Member.isValidHandle("123456789012345678901") shouldBe false // 20자 초과
    }

    test("초기화 완료 가능 여부 - 모든 필수 정보가 있는 경우") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        ).apply {
            handle = "langlez_user"
            personality = MemberPersonality(
                birthDay = LocalDate.of(1990, 1, 1),
                nationality = Nationality.of("KR"),
                gender = MemberPersonality.Gender.MALE,
                mbti = MemberPersonality.MBTI.INTJ
            )
            location = MemberLocation("서울", 37.0, 127.0)
            introduction = MemberIntroduction("bio", "goal", "want")
            languages.add(MemberLanguage(Language.KOREAN, MemberLanguage.Level.NATIVE))
        }

        member.isReadyToFinishInit shouldBe true
    }

    test("초기화 완료 가능 여부 - handle이 없는 경우") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        ).apply {
            personality = MemberPersonality(
                LocalDate.of(1990, 1, 1),
                Nationality.of("KR"),
                MemberPersonality.Gender.MALE,
                MemberPersonality.MBTI.INTJ
            )
            location = MemberLocation("서울", 37.0, 127.0)
            introduction = MemberIntroduction("bio", "goal", "want")
            languages.add(MemberLanguage(Language.KOREAN, MemberLanguage.Level.NATIVE))
        }

        member.isReadyToFinishInit shouldBe false
    }

    test("초기화 완료 가능 여부 - personality가 없는 경우") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        ).apply {
            handle = "langlez_user"
            location = MemberLocation("서울", 37.0, 127.0)
            introduction = MemberIntroduction("bio", "goal", "want")
            languages.add(MemberLanguage(Language.KOREAN, MemberLanguage.Level.NATIVE))
        }

        member.isReadyToFinishInit shouldBe false
    }

    test("초기화 완료 가능 여부 - languages가 비어있는 경우") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        ).apply {
            handle = "langlez_user"
            personality = MemberPersonality(
                LocalDate.of(1990, 1, 1), Nationality.of("KR"),
                MemberPersonality.Gender.MALE,
                MemberPersonality.MBTI.INTJ
            )
            location = MemberLocation("서울", 37.0, 127.0)
            introduction = MemberIntroduction("bio", "goal", "want")
        }

        member.isReadyToFinishInit shouldBe false
    }

    test("로그인 처리") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        )

        member.audit.lastLoggedInAt shouldBe null

        member.login()
        member.audit.lastLoggedInAt shouldNotBe null
    }

    test("프리미엄 업그레이드") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        )

        member.role shouldBe Member.Role.MEMBER
        member.upgradeToPremium()
        member.role shouldBe Member.Role.PREMIUM
    }

    test("회원 삭제") {
        val member = Member.create(
            nickname = "test",
            email = "test@example.com",
            providerId = "test_id",
            providerType = "GOOGLE",
            providerUserName = "Test"
        )

        member.isDeleted shouldBe false
        member.delete()
        member.isDeleted shouldBe true
        member.audit.deletedAt shouldBe member.audit.deletedAt
    }
})
