package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.TokenManager
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.Base64

/**
 * 리프레시 토큰 회전(rotation)이 정상 사용자를 스스로 잘라내지 않는지 본다.
 *
 * 회전은 매 갱신마다 저장된 토큰을 새 토큰으로 덮는다. 그래서 "저장값과 다르다" 는 탈취뿐 아니라
 * **다른 요청이 방금 갱신했다** 는 뜻이기도 하다. 불일치를 세션 삭제로 처리하면 앱이 포그라운드로
 * 복귀하며 같은 토큰으로 두 번 갱신하는 것만으로 재로그인을 강요당한다.
 *
 * Redis 버킷은 실제 값처럼 동작하는 대역으로 갈았다. relaxed 대역은 compareAndSet 이 늘 false 라
 * 회전 자체를 검증할 수 없다.
 */
class AuthSessionTest : BehaviorSpec({

    val secret = Base64.getEncoder().encodeToString("super-secret-key-12345678901234567890".toByteArray())

    // TokenManager 는 구체 클래스라 대역으로 갈지 않는다. 진짜 토큰을 발급해 서비스에 넘긴다.
    val tokens = TokenManager(secret, accessTokenTTL = 3600, refreshTokenTTL = 1209600, redisson = mockk(relaxed = true))

    val memberService = mockk<MemberService>()
    val redisson = mockk<RedissonClient>()
    val onlineTracker = mockk<MemberOnlineTracker>(relaxed = true)

    val tokenBucket = mockk<RBucket<String>>(relaxed = true)
    val deviceBucket = mockk<RBucket<String>>(relaxed = true)

    // 두 버킷의 실제 저장값. compareAndSet 은 참조가 아니라 값으로 비교해야 한다.
    var storedToken: String? = null
    var storedDevice: String? = null
    var tokenDeleted = false
    val lock = Any()

    every { redisson.getBucket<String>("refresh_token:1") } returns tokenBucket
    every { redisson.getBucket<String>("refresh_device:1") } returns deviceBucket

    every { tokenBucket.get() } answers { synchronized(lock) { storedToken } }
    every { tokenBucket.set(any(), any<Duration>()) } answers { synchronized(lock) { storedToken = firstArg() } }
    every { tokenBucket.compareAndSet(any(), any()) } answers {
        synchronized(lock) {
            if (storedToken == firstArg<String?>()) {
                storedToken = secondArg()
                true
            } else {
                false
            }
        }
    }
    every { tokenBucket.delete() } answers {
        synchronized(lock) {
            tokenDeleted = true
            (storedToken != null).also { storedToken = null }
        }
    }

    every { deviceBucket.get() } answers { synchronized(lock) { storedDevice } }
    every { deviceBucket.set(any(), any<Duration>()) } answers { synchronized(lock) { storedDevice = firstArg() } }
    every { deviceBucket.delete() } answers { synchronized(lock) { (storedDevice != null).also { storedDevice = null } } }

    every { memberService.findById(1L) } returns Member(
        id = 1L,
        email = "t@test.com",
        handle = "tester",
        status = Member.Status.ACTIVE,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    val service = AuthService(
        tokens, memberService, redisson, onlineTracker,
        accessTokenTtlSecs = 3600,
        refreshTokenTtlSecs = 1209600,
    )

    // TokenManager 의 iat/exp 는 초 단위라 같은 초에 같은 클레임으로 발급하면 문자열이 똑같다.
    // 회전 전후를 구분하려면 서비스가 만들지 않을 클레임(username)의 토큰이 필요하다.
    // refresh 경로는 subject(memberId)만 보므로 검증에는 영향이 없다.
    fun clientHeldToken() = tokens.issueRefreshToken(1L, "issued-before-rotation", "ROLE_MEMBER")

    /** 로그인 직후 상태로 되돌린다. */
    fun session(deviceId: String?, token: String) = synchronized(lock) {
        storedToken = token
        storedDevice = deviceId
        tokenDeleted = false
    }

    fun ctx(device: String?) = AccessContext("1.1.1.1", device)

    Given("같은 리프레시 토큰으로 갱신 요청이 두 번 들어오면") {
        // 동시 요청의 결과 상태를 그대로 재현한다. 둘 다 같은 토큰을 들고 있고, 하나가 먼저
        // 회전을 마친 뒤 나머지 하나가 이미 회전된 버킷을 본다.
        val held = clientHeldToken()
        session(deviceId = "device-A", token = held)

        val rotated = service.refresh(held, ctx("device-A")).first

        Then("두 번째 요청은 401 로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(held, ctx("device-A")) }
            ex.status.value() shouldBe 401
        }

        Then("세션은 살아남는다 - 먼저 발급된 토큰이 그대로 저장돼 있다") {
            storedToken shouldBe rotated
            tokenDeleted shouldBe false
        }

        Then("클라이언트가 받아 든 새 토큰으로 계속 갱신할 수 있다") {
            shouldNotThrowAny { service.refresh(rotated, ctx("device-A")) }
        }

        Then("기기 바인딩도 그대로다") {
            storedDevice shouldBe "device-A"
        }
    }

    Given("이미 회전돼 무효가 된 옛 리프레시 토큰으로 요청하면") {
        val stolen = clientHeldToken()
        session(deviceId = "device-A", token = stolen)

        val current = service.refresh(stolen, ctx("device-A")).first

        Then("401 로 거부된다") {
            val ex = shouldThrow<LanglezException> { service.refresh(stolen, ctx("device-A")) }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.token-expired"
        }

        Then("현재 세션은 지워지지 않는다") {
            // 옛 토큰을 던지는 것만으로 피해자 세션을 끊을 수 있으면 안 된다.
            verify(exactly = 0) { tokenBucket.delete() }
            tokenDeleted shouldBe false
            storedToken shouldBe current
        }

        Then("피해자는 그대로 갱신할 수 있다") {
            shouldNotThrowAny { service.refresh(current, ctx("device-A")) }
        }
    }

    Given("1인 1기기 - 세션에 묶이지 않은 기기가 현재 토큰을 쓰면") {
        val current = clientHeldToken()
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
            storedToken shouldBe current
            storedDevice shouldBe "device-A"
        }
    }

    Given("1인 1기기 - 다른 기기에서 새로 로그인하면") {
        val onDeviceA = clientHeldToken()
        session(deviceId = "device-A", token = onDeviceA)

        service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("2.2.2.2", "device-B"))

        Then("기기 A 는 밀려난 이유를 알 수 있는 401 을 받는다") {
            // 기기 검사가 토큰 비교보다 앞이라 token-expired 가 아니라 session-taken-over 가 나간다.
            val ex = shouldThrow<LanglezException> { service.refresh(onDeviceA, ctx("device-A")) }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.session-taken-over"
        }

        Then("바인딩이 기기 B 로 넘어간다") {
            storedDevice shouldBe "device-B"
        }
    }

    Given("기기 바인딩이 남은 회원이 기기 정보 없이 새로 로그인하면") {
        // OAuth2 콜백에 기기 식별자가 실리지 않은 경우다. 이 로그인이 이미 리프레시 토큰을 덮어써
        // 옛 기기 세션은 끝났는데 바인딩만 옛 기기로 남으면, 새 기기의 첫 갱신이
        // session-taken-over 로 잘리고 액세스 토큰 TTL 마다 무한 반복된다.
        session(deviceId = "device-A", token = clientHeldToken())

        val issued = service.issueTokens(1L, "tester", "ROLE_MEMBER", AccessContext("2.2.2.2", null)).first

        Then("옛 기기 바인딩이 남지 않는다") {
            storedDevice shouldBe null
        }

        Then("새 기기에서 갱신하면 그 기기로 다시 바인딩되며 성공한다") {
            shouldNotThrowAny { service.refresh(issued, AccessContext("2.2.2.2", "device-B")) }

            storedDevice shouldBe "device-B"
        }

        Then("다시 바인딩된 뒤에는 1인 1기기가 그대로 동작한다") {
            val ex = shouldThrow<LanglezException> {
                service.refresh(storedToken!!, AccessContext("9.9.9.9", "device-X"))
            }
            ex.status.value() shouldBe 401
            ex.message shouldBe "auth.session-taken-over"
        }
    }
})
