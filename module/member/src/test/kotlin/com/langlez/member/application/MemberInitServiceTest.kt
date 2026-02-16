package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.file.application.FileStorage
import com.langlez.member.domain.*
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDate
import org.springframework.mock.web.MockMultipartFile

@DisplayName("MemberInitService: 회원 초기화 로직 테스트")
class MemberInitServiceTest :
    BehaviorSpec({
        val repo = mockk<MemberRepository>()
        val fileStorage = mockk<FileStorage>()
        val service = MemberInitService(repo, fileStorage)

        Given("handle 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )
                    .apply { init = false }

            coEvery { repo.findByEmail("test@example.com") } returns member
            coEvery { repo.existsByHandle("langlez_user") } returns false

            When("유효한 handle을 설정하면") {
                val result = service.initHandle("test@example.com", "langlez_user", "랭글레즈")

                Then("handle과 nickname이 설정되어야 한다") {
                    result.handle shouldBe "langlez_user"
                    result.nickname shouldBe "랭글레즈"
                }
            }
        }

        Given("handle 중복 체크 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            coEvery { repo.findByEmail("test@example.com") } returns member
            coEvery { repo.existsByHandle("duplicated") } returns true

            When("이미 존재하는 handle을 사용하면") {
                Then("LanglezException을 던져야 한다") {
                    shouldThrow<LanglezException> {
                        service.initHandle("test@example.com", "duplicated", "닉네임")
                    }
                }
            }
        }

        Given("잘못된 형식의 handle 입력 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("대문자가 포함된 handle을 사용하면") {
                coEvery { repo.existsByHandle("InvalidHandle") } returns false

                Then("유효한 handle로 설정되어야 한다") {
                    val result = service.initHandle("test@example.com", "InvalidHandle", "닉네임")
                    result.handle shouldBe "InvalidHandle"
                }
            }

            When("너무 짧은 handle을 사용하면") {
                Then("LanglezException을 던져야 한다") {
                    shouldThrow<LanglezException> {
                        service.initHandle("test@example.com", "ab", "닉네임")
                    }
                }
            }

            When("특수문자가 포함된 handle을 사용하면") {
                Then("LanglezException을 던져야 한다") {
                    shouldThrow<LanglezException> {
                        service.initHandle("test@example.com", "user@name", "닉네임")
                    }
                }
            }
        }

        Given("이미 초기화가 완료된 회원의 경우") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )
                    .apply { init = true }

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("handle을 재설정하려고 하면") {
                Then("LanglezException을 던져야 한다") {
                    shouldThrow<LanglezException> {
                        service.initHandle("test@example.com", "new_handle", "새 닉네임")
                    }
                }
            }
        }

        Given("성격 정보 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            val personality =
                MemberPersonality(
                    birthDay = LocalDate.of(1990, 1, 1),
                    nationality = MemberPersonality.Nationality.of("KR"),
                    gender = MemberPersonality.Gender.MALE,
                    mbti = MemberPersonality.MBTI.INTJ
                )

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("성격 정보를 설정하면") {
                val result = service.initPersonality("test@example.com", personality)

                Then("성격 정보가 설정되어야 한다") {
                    result.personality shouldNotBe null
                    result.personality!!.birthDay shouldBe LocalDate.of(1990, 1, 1)
                    result.personality!!.nationality?.code shouldBe "KR"
                    result.personality!!.gender shouldBe MemberPersonality.Gender.MALE
                    result.personality!!.mbti shouldBe MemberPersonality.MBTI.INTJ
                }
            }
        }

        Given("위치 정보 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            val location = MemberLocation("서울특별시 강남구", 37.4979, 127.0276)

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("위치 정보를 설정하면") {
                val result = service.initLocation("test@example.com", location)

                Then("위치 정보가 설정되어야 한다") {
                    result.location shouldNotBe null
                    result.location!!.address shouldBe "서울특별시 강남구"
                    result.location!!.lat shouldBe 37.4979
                    result.location!!.lon shouldBe 127.0276
                }
            }
        }

        Given("소개 정보 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            val introduction = MemberIntroduction("안녕하세요", "영어 회화 마스터", "적극적인 파트너")

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("소개 정보를 설정하면") {
                val result = service.initIntroduction("test@example.com", introduction)

                Then("소개 정보가 설정되어야 한다") {
                    result.introduction shouldNotBe null
                    result.introduction!!.bio shouldBe "안녕하세요"
                    result.introduction!!.goal shouldBe "영어 회화 마스터"
                    result.introduction!!.want shouldBe "적극적인 파트너"
                }
            }
        }

        Given("언어 정보 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            val languages =
                listOf(
                    MemberLanguage(
                        MemberLanguage.Language.KOREAN,
                        MemberLanguage.Level.NATIVE
                    ),
                    MemberLanguage(
                        MemberLanguage.Language.ENGLISH,
                        MemberLanguage.Level.MIDDLE
                    )
                )

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("언어 정보를 설정하면") {
                val result = service.initLanguages("test@example.com", languages)

                Then("언어 정보가 설정되어야 한다") { result.languages.size shouldBe 2 }
            }
        }

        Given("이미지 초기화 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )

            val image1 = MockMultipartFile("profileImage", "data".toByteArray())
            val image2 = MockMultipartFile("otherImage", "data".toByteArray())

            coEvery { repo.findByEmail("test@example.com") } returns member
            coEvery { fileStorage.upload(any(), any()) } returns "https://uploaded.url"
            coJustRun { fileStorage.delete(any()) }

            When("이미지를 업로드하면") {
                val result =
                    service.initProfileImages("test@example.com", image1, listOf(image2))

                Then("대표 이미지와 추가 이미지가 저장되어야 한다") {
                    result.images.size shouldBe 2
                    result.images[0].represent shouldBe true
                    result.images[0].sequence shouldBe 0
                    result.images[1].represent shouldBe false
                    result.images[1].sequence shouldBe 1
                }
            }
        }

        Given("초기화 완료 시 - 필수 정보가 모두 있는 경우") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )
                    .apply {
                        handle = "langlez_user"
                        personality =
                            MemberPersonality(
                                LocalDate.of(1990, 1, 1),
                                MemberPersonality.Nationality.of("KR"),
                                MemberPersonality.Gender.MALE,
                                MemberPersonality.MBTI.INTJ
                            )
                        location = MemberLocation("서울", 37.0, 127.0)
                        introduction = MemberIntroduction("bio", "goal", "want")
                        languages.add(
                            MemberLanguage(
                                MemberLanguage.Language.KOREAN,
                                MemberLanguage.Level.NATIVE
                            )
                        )
                        images.add(MemberImage(0, "url", 0, true))
                    }

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("필수 정보가 모두 입력된 상태에서 완료를 요청하면") {
                val result = service.finishInit("test@example.com")

                Then("init이 true로 변경되어야 한다") { result.init shouldBe true }
            }
        }

        Given("필수 정보가 없는 상태에서 초기화 완료 시") {
            val member =
                Member.create(
                    nickname = "test",
                    email = "test@example.com",
                    providerId = "test_id",
                    providerType = "GOOGLE",
                    providerUserName = "Test"
                )
                    .apply { handle = "langlez_user" }

            coEvery { repo.findByEmail("test@example.com") } returns member

            When("필수 정보가 없는 상태에서 완료를 요청하면") {
                Then("LanglezException을 던져야 한다") {
                    shouldThrow<LanglezException> { service.finishInit("test@example.com") }
                }
            }
        }
    })
