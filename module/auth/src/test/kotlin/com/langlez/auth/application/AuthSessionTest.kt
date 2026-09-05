package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.util.Base64

/**
 * 리프레시 토큰 회전(rotation)이 정상 사용자를 스스로 잘라내지 않는지 본다.
 *
 * 회전은 매 갱신마다 저장된 토큰을 새 토큰으로 덮는다. 그래서 "저장값과 다르다" 는 탈취뿐 아니라
 * **다른 요청이 방금 갱신했다** 는 뜻이기도 하다. 불일치를 세션 삭제로 처리하면 앱이 포그라운드로
 * 복귀하며 같은 토큰으로 두 번 갱신하는 것만으로 재로그인을 강요당한다.
 *
 * **진짜 레디스에 붙인다.** 회전은 Lua 스크립트 한 방(비교+교체+만료)이라 대역으로는 검증이 안 된다.
 * 대역은 내가 짠 비교 로직을 확인할 뿐이고, 정작 확인해야 할 것은 스크립트가 실제로 원자적이며
 * 교체 후 TTL 이 남아 있는가다.
 */
class AuthSessionTest : BehaviorSpec({

    val container = GenericContainer("redis:7.0").withExposedPorts(6379)
    container.start()

    val redisson: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${container.host}:${container.getMappedPort(6379)}")
        }
    )

    afterSpec {
        redisson.shutdown()
        container.stop()
    }

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val memberService = mockk<MemberService>()

    every { memberService.findById(1L) } returns Member(
        id = 1L,
        email = "t@test.com",
        handle = "tester",
        status = Member.Status.ACTIVE,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    val service = AuthService(
        tokens, memberService, redisson, mockk(relaxed = true),
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    // 서비스가 쓰는 코덱과 같아야 같은 바이트를 본다.
    fun tokenBucket() = redisson.getBucket<String>("refresh_token:1", StringCodec.INSTANCE)
    fun deviceBucket() = redisson.getBucket<String>("refresh_device:1", StringCodec.INSTANCE)

    fun storedToken(): String? = tokenBucket().get()
    fun storedDevice(): String? = deviceBucket().get()

    fun newToken() = tokens.issueRefreshToken(1L, "tester", "ROLE_MEMBER")

    /** 로그인 직후 상태로 되돌린다. */
    fun session(deviceId: String?, token: String) {
        tokenBucket().set(token, service.refreshTokenTtl)
        deviceId?.let { deviceBucket().set(it, service.refreshTokenTtl) } ?: deviceBucket().delete()
    }

    fun ctx(device: String?) = AccessContext("1.1.1.1", device)

    Given("같은 리프레시 토큰으로 갱신 요청이 두 번 들어오면") {
        // 동시 요청의 결과 상태를 그대로 재현한다. 둘 다 같은 토큰을 들고 있고, 하나가 먼저
        // 회전을 마친 뒤 나머지 하나가 이미 회전된 버킷을 본다.
        val held = newToken()
        session(deviceId = "device-A", token = held)

        val rotated = service.refresh(held, ctx("device-A")).first

        Then("토큰이 실제로 회전한다") {
            // jti 가 없으면 같은 초 발급이 같은 문자열이라 제자리 교체가 된다.
            rotated shouldNotBe held
            storedToken() shouldBe rotated
        }

        Then("두 번째 요청은 401 로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(held, ctx("device-A")) }
            ex.status.value() shouldBe 401
        }

        Then("세션은 살아남는다 - 먼저 발급된 토큰이 그대로 저장돼 있다") {
            storedToken() shouldBe rotated
        }

        Then("클라이언트가 받아 든 새 토큰으로 계속 갱신할 수 있다") {
            shouldNotThrowAny { service.refresh(rotated, ctx("device-A")) }
        }

        Then("기기 바인딩도 그대로다") {
            storedDevice() shouldBe "device-A"
        }
    }

    Given("1초 안에 갱신이 연달아 들어오면") {
        val first = newToken()
        session(deviceId = "device-A", token = first)

        Then("매번 다른 토큰으로 회전하고 직전 토큰은 무효가 된다") {
            // TokenManager 의 iat/exp 는 초 단위다. jti 가 없으면 이 구간의 회전이 전부
            // 제자리 교체가 돼, 그 사이 탈취된 토큰이 무효화되지 않는다.
            val second = service.refresh(first, ctx("device-A")).first
            val third = service.refresh(second, ctx("device-A")).first

            setOf(first, second, third).size shouldBe 3
            storedToken() shouldBe third

            shouldThrow<LanglezException> { service.refresh(second, ctx("device-A")) }
        }
    }

    Given("회전이 성공하면") {
        val held = newToken()
        session(deviceId = "device-A", token = held)

        service.refresh(held, ctx("device-A"))

        Then("교체된 키에 만료가 남아 있다") {
            // 비교·교체·만료가 한 스크립트가 아니면, 그 사이에 프로세스가 죽었을 때
            // TTL 없는 영구 키(remainTimeToLive() == -1)가 남아 2주 만료 정책이 사라진다.
            tokenBucket().remainTimeToLive() shouldBeGreaterThan 0L
        }
    }

    Given("이미 회전돼 무효가 된 옛 리프레시 토큰으로 요청하면") {
        val stolen = newToken()
        session(deviceId = "device-A", token = stolen)

        val current = service.refresh(stolen, ctx("device-A")).first

        Then("401 로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(stolen, ctx("device-A")) }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.token-expired"
        }

        Then("현재 세션은 지워지지 않는다") {
            // 옛 토큰을 던지는 것만으로 피해자 세션을 끊을 수 있으면 안 된다.
            storedToken() shouldBe current
        }

        Then("피해자는 그대로 갱신할 수 있다") {
            shouldNotThrowAny { service.refresh(current, ctx("device-A")) }
        }
    }

    Given("1인 1기기 - 세션에 묶이지 않은 기기가 현재 토큰을 쓰면") {
        val current = newToken()
        session(deviceId = "device-A", token = current)

        Then("탈취 시도는 401 로 막힌다") {
            val ex = shouldThrow<LanglezException> { service.refresh(current, AccessContext("9.9.9.9", "device-X")) }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.session-taken-over"
        }

        Then("기기 id 를 빼도 막힌다") {
            // fail-open 이면 헤더를 빼는 것만으로 1인 1기기 검증이 통째로 우회된다.
            val ex = shouldThrow<LanglezException> { service.refresh(current, AccessContext("9.9.9.9", null)) }
            ex.status.value() shouldBe 401
        }

        Then("탈취 시도가 회전을 일으키지 않아 정상 기기는 영향을 받지 않는다") {
            storedToken() shouldBe current
            storedDevice() shouldBe "device-A"
        }
    }

    Given("1인 1기기 - 다른 기기에서 새로 로그인하면") {
        val onDeviceA = newToken()
        session(deviceId = "device-A", token = onDeviceA)

        service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("2.2.2.2", "device-B"))

        Then("기기 A 는 밀려난 이유를 알 수 있는 401 을 받는다") {
            // 기기 검사가 토큰 비교보다 앞이라 token-expired 가 아니라 session-taken-over 가 나간다.
            val ex = shouldThrow<LanglezException> { service.refresh(onDeviceA, ctx("device-A")) }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.session-taken-over"
        }

        Then("바인딩이 기기 B 로 넘어간다") {
            storedDevice() shouldBe "device-B"
        }
    }

    Given("기기 바인딩이 남은 회원이 기기 정보 없이 새로 로그인하면") {
        // OAuth2 콜백에 기기 식별자가 실리지 않은 경우다. 이 로그인이 이미 리프레시 토큰을 덮어써
        // 옛 기기 세션은 끝났는데 바인딩만 옛 기기로 남으면, 새 기기의 첫 갱신이
        // session-taken-over 로 잘리고 액세스 토큰 TTL 마다 무한 반복된다.
        session(deviceId = "device-A", token = newToken())

        val issued = service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("2.2.2.2", null)).first

        Then("옛 기기 바인딩이 남지 않는다") {
            storedDevice() shouldBe null
        }

        Then("새 기기에서 갱신하면 그 기기로 다시 바인딩되며 성공한다") {
            shouldNotThrowAny { service.refresh(issued, AccessContext("2.2.2.2", "device-B")) }

            storedDevice() shouldBe "device-B"
        }

        Then("다시 바인딩된 뒤에는 1인 1기기가 그대로 동작한다") {
            val ex = shouldThrow<LanglezException> {
                service.refresh(storedToken()!!, AccessContext("9.9.9.9", "device-X"))
            }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.session-taken-over"
        }
    }
})
