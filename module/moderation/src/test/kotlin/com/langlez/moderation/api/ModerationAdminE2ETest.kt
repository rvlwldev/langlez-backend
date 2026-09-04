package com.langlez.moderation.api

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.moderation.domain.Report
import com.langlez.moderation.domain.ReportRepository
import com.langlez.security.TokenManager
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * `/api/v1/admin` 하위의 인가와 안전장치.
 *
 * **이 스펙이 이 PR 의 핵심 방어선이다.** 인가가 안 걸려도 200 이 나가므로 눈으로는 못 본다.
 * `hasRole("ADMIN")` 이 "ROLE_" 접두사를 자동으로 붙이고 `Member.Role.authority` 가
 * 내는 값(`ROLE_ADMIN`)과 실제로 맞는지를 HTTP 왕복으로 확인한다.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 브로커가 없는 테스트다. 신고 컨슈머를 띄울 이유가 없다.
        "spring.kafka.listener.auto-startup=false",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class ModerationAdminE2ETest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var members: MemberRepository

    @Autowired
    lateinit var reports: ReportRepository

    @Autowired
    lateinit var tokens: TokenManager

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @JvmField
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    private fun newMember(handle: String, role: Member.Role = Member.Role.MEMBER): Member = members.save(
        Member(
            email = "$handle@test.com",
            handle = handle,
            status = Member.Status.ACTIVE,
            role = role,
            provider = Member.Provider.GOOGLE,
            providerId = "p-$handle",
        )
    )

    private fun tokenOf(member: Member) = tokens.issueAccessToken(member.id, member.handle, member.role.authority)

    private fun headers(token: String?) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        token?.let { setBearerAuth(it) }
    }

    private fun call(method: HttpMethod, path: String, token: String?, body: String? = null) =
        rest.exchange(path, method, HttpEntity(body, headers(token)), String::class.java)

    // 스펙 생성 시점에는 @Autowired 필드가 아직 비어 있다. 픽스처는 beforeSpec 에서 만든다.
    private lateinit var admin: Member
    private lateinit var otherAdmin: Member
    private lateinit var normal: Member
    private lateinit var outsider: Member
    private lateinit var adminToken: String
    private lateinit var memberToken: String

    init {
        beforeSpec {
            admin = newMember("admin1", Member.Role.ADMIN)
            otherAdmin = newMember("admin2", Member.Role.ADMIN)
            normal = newMember("normal1")
            outsider = newMember("outsider")

            adminToken = tokenOf(admin)
            memberToken = tokenOf(normal)
        }

        Given("운영자 구역에 권한 없이 접근하면") {

            When("토큰이 아예 없으면") {
                Then("401 이다") {
                    call(HttpMethod.GET, "/api/v1/admin/reports", null).statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("일반 회원 토큰이면") {
                Then("신고 목록은 403 이다") {
                    call(HttpMethod.GET, "/api/v1/admin/reports", memberToken)
                        .statusCode shouldBe HttpStatus.FORBIDDEN
                }

                // 대상은 평범한 회원이어야 한다. 운영자를 대상으로 두면 MemberSuspender 의
                // 운영자 보호 가드로도 403 이 나서, URL 인가가 빠져도 이 단언이 통과한다.
                Then("회원 정지도 403 이다") {
                    call(HttpMethod.POST, "/api/v1/admin/members/${outsider.id}/suspend", memberToken, "{}")
                        .statusCode shouldBe HttpStatus.FORBIDDEN
                }

                Then("정지 해제도 403 이다") {
                    call(HttpMethod.DELETE, "/api/v1/admin/members/${outsider.id}/suspend", memberToken)
                        .statusCode shouldBe HttpStatus.FORBIDDEN
                }

                Then("신고 처리도 403 이다") {
                    call(HttpMethod.PATCH, "/api/v1/admin/reports/1", memberToken, """{"status":"ACTIONED"}""")
                        .statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("운영자 토큰이면") {

            When("신고 목록을 조회하면") {
                Then("200 이다") {
                    call(HttpMethod.GET, "/api/v1/admin/reports", adminToken).statusCode shouldBe HttpStatus.OK
                }
            }

            When("접수된 신고를 조치함으로 바꾸면") {
                val saved = reports.save(
                    Report(
                        reporterId = normal.id,
                        reportedUserId = otherAdmin.id,
                        sourceType = Report.SourceType.ECHO_POST,
                        sourceId = "1001",
                        reason = "스팸",
                    )
                )

                val response = call(
                    HttpMethod.PATCH,
                    "/api/v1/admin/reports/${saved.id}",
                    adminToken,
                    """{"status":"ACTIONED","note":"확인함"}""",
                )

                Then("200 이고 상태가 바뀐다") {
                    response.statusCode shouldBe HttpStatus.OK
                    reports.find(saved.id)!!.status shouldBe Report.Status.ACTIONED
                }

                Then("처리자가 남는다") {
                    reports.find(saved.id)!!.handledBy shouldBe admin.id
                }

                // 접수 시점 상태라 처리자·처리 시각이 채워진 행과 의미가 어긋난다.
                Then("RECEIVED 로 되돌리려 하면 400 이다") {
                    call(
                        HttpMethod.PATCH,
                        "/api/v1/admin/reports/${saved.id}",
                        adminToken,
                        """{"status":"RECEIVED"}""",
                    ).statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }

            When("일반 회원을 정지했다가 풀면") {
                val target = newMember("victim")

                Then("정지는 204 이고 상태가 SUSPENDED 가 된다") {
                    call(
                        HttpMethod.POST,
                        "/api/v1/admin/members/${target.id}/suspend",
                        adminToken,
                        """{"reason":"스팸","days":7}""",
                    ).statusCode shouldBe HttpStatus.NO_CONTENT

                    members.find(target.id)!!.status shouldBe Member.Status.SUSPENDED
                }

                Then("해제는 204 이고 상태가 ACTIVE 로 돌아온다") {
                    call(HttpMethod.DELETE, "/api/v1/admin/members/${target.id}/suspend", adminToken)
                        .statusCode shouldBe HttpStatus.NO_CONTENT

                    members.find(target.id)!!.status shouldBe Member.Status.ACTIVE
                }
            }

            When("자기 자신을 정지하려 하면") {
                Then("400 이고 자기 상태는 그대로다") {
                    call(HttpMethod.POST, "/api/v1/admin/members/${admin.id}/suspend", adminToken, "{}")
                        .statusCode shouldBe HttpStatus.BAD_REQUEST

                    members.find(admin.id)!!.status shouldBe Member.Status.ACTIVE
                }
            }

            // 운영자끼리 서로 잠그면 복구 수단이 DB 직접 수정밖에 남지 않는다.
            When("다른 운영자를 정지하려 하면") {
                Then("403 이고 대상 상태는 그대로다") {
                    call(HttpMethod.POST, "/api/v1/admin/members/${otherAdmin.id}/suspend", adminToken, "{}")
                        .statusCode shouldBe HttpStatus.FORBIDDEN

                    members.find(otherAdmin.id)!!.status shouldBe Member.Status.ACTIVE
                }
            }
        }
    }
}
