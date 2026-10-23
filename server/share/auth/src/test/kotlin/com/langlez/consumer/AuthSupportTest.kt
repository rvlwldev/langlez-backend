package com.langlez.consumer

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.Date
import java.util.UUID

@SpringBootTest(
    classes = [ConsumerApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["langlez.auth.public-paths=/open/**"],
)
class AuthSupportTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value("\${local.server.port}")
    private var port: Int = 0

    companion object {
        private val key: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()
        private val otherKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()

        private val jwks = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/auth/v1/.well-known/jwks.json") { exchange ->
                val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        private val baseUrl = "http://localhost:${jwks.address.port}"
        private val issuer = "$baseUrl/auth/v1"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("langlez.auth.supabase-url") { baseUrl }
        }
    }

    private val userId = UUID.randomUUID().toString()

    private fun claims(
        subject: String = userId,
        issuer: String = Companion.issuer,
        audience: String? = "authenticated",
        expiresInSeconds: Long = 600,
    ): JWTClaimsSet = JWTClaimsSet.Builder()
        .subject(subject)
        .issuer(issuer)
        .apply { if (audience != null) audience(audience) }
        .expirationTime(Date(System.currentTimeMillis() + expiresInSeconds * 1000))
        .build()

    private fun signed(claims: JWTClaimsSet, signingKey: ECKey = key): String =
        SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).build(), claims)
            .apply { sign(ECDSASigner(signingKey)) }
            .serialize()

    private fun get(path: String, token: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    init {
        Given("Supabase 가 서명한 정상 토큰으로") {
            When("보호된 경로를 호출하면") {
                Then("200 이고 sub 를 꺼낼 수 있다") {
                    val response = get("/me", signed(claims()))

                    response.statusCode() shouldBe 200
                    response.body() shouldBe userId
                }
            }
        }

        Given("토큰 없이") {
            When("보호된 경로를 호출하면") {
                Then("401 이다") {
                    get("/me").statusCode() shouldBe 401
                }
            }

            When("공개로 지정한 경로를 호출하면") {
                Then("200 이다") {
                    get("/open/ping").statusCode() shouldBe 200
                }
            }
        }

        Given("공개 경로에서 예외가 나면") {
            When("토큰 없이 호출하면") {
                Then("에러 디스패치가 막히지 않고 500 이 그대로 나온다") {
                    get("/open/boom").statusCode() shouldBe 500
                }
            }
        }

        Given("위조된 토큰이면") {
            When("페이로드를 바꾸고 서명은 그대로 두면") {
                Then("401 이다") {
                    val parts = signed(claims()).split(".")
                    val forged = JWTClaimsSet.Builder(claims(subject = "attacker")).build().toString().toByteArray()
                    val tampered = "${parts[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(forged)}.${parts[2]}"

                    get("/me", tampered).statusCode() shouldBe 401
                }
            }

            When("다른 키로 서명하면") {
                Then("401 이다") {
                    get("/me", signed(claims(), otherKey)).statusCode() shouldBe 401
                }
            }

            When("alg 가 none 이면") {
                Then("401 이다") {
                    get("/me", PlainJWT(claims()).serialize()).statusCode() shouldBe 401
                }
            }

            When("공개키를 HS256 시크릿으로 써서 서명하면") {
                Then("401 이다") {
                    val secret = key.toPublicJWK().toString().toByteArray().copyOf(32)
                    val token = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).keyID(key.keyID).build(), claims())
                        .apply { sign(MACSigner(secret)) }
                        .serialize()

                    get("/me", token).statusCode() shouldBe 401
                }
            }
        }

        Given("서명은 맞지만 클레임이 맞지 않는 토큰이면") {
            When("만료됐으면") {
                Then("401 이다") {
                    get("/me", signed(claims(expiresInSeconds = -120))).statusCode() shouldBe 401
                }
            }

            When("발급자가 다르면") {
                Then("401 이다") {
                    get("/me", signed(claims(issuer = "https://evil.example.com/auth/v1"))).statusCode() shouldBe 401
                }
            }

            When("aud 가 authenticated 가 아니면") {
                Then("401 이다") {
                    get("/me", signed(claims(audience = "anon"))).statusCode() shouldBe 401
                }
            }

            When("aud 가 없으면") {
                Then("401 이다") {
                    get("/me", signed(claims(audience = null))).statusCode() shouldBe 401
                }
            }
        }
    }
}
