package com.langlez.attachment

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Random
import java.util.UUID

private const val MEGABYTE = 1024L * 1024

@SpringBootTest(classes = [AttachmentTestApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var storage: ObjectStorage

    companion object {
        private const val BUCKET = "langlez-attachments-test"
        private const val MINIO_IMAGE =
            "cgr.dev/chainguard/minio@sha256:c47b1779bfd12cdf5a4fcd3bd50671b8d66465fdae45271babc2fb467958b712"

        private val postgres = PostgreSQLContainer("postgres:16").also { it.start() }
        private val minio = MinIOContainer(DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio"))
            .also { it.start() }

        private val key: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()

        private val jwks = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/auth/v1/.well-known/jwks.json") { exchange ->
                val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        private val supabaseUrl = "http://localhost:${jwks.address.port}"

        init {
            val s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.s3URL))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
            s3.createBucket { it.bucket(BUCKET) }
            s3.putBucketPolicy {
                it.bucket(BUCKET).policy(
                    """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::$BUCKET/*"]}]}
                    """.trimIndent(),
                )
            }
            s3.close()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("langlez.auth.supabase-url") { supabaseUrl }
            registry.add("langlez.attachment.endpoint") { minio.s3URL }
            registry.add("langlez.attachment.region") { "us-east-1" }
            registry.add("langlez.attachment.bucket") { BUCKET }
            registry.add("langlez.attachment.access-key") { minio.userName }
            registry.add("langlez.attachment.secret-key") { minio.password }
            registry.add("langlez.attachment.public-base-url") { "${minio.s3URL}/$BUCKET" }
            registry.add("langlez.attachment.path-style") { "true" }
        }
    }

    private val http = HttpClient.newHttpClient()
    private val random = Random(7)

    private fun tokenOf(userId: UUID): String = tokenOf(userId.toString())

    private fun tokenOf(subject: String): String {
        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("$supabaseUrl/auth/v1")
            .audience("authenticated")
            .expirationTime(Date(System.currentTimeMillis() + 600_000))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).build(), claims)
            .apply { sign(ECDSASigner(key)) }
            .serialize()
    }

    private fun post(path: String, token: String?, body: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun issue(token: String?, source: String, type: String, vararg files: Pair<String, Long>): HttpResponse<String> {
        val fileJson = files.joinToString(",") { """{"contentType":"${it.first}","size":${it.second}}""" }
        return post("/attachments/batches", token, """{"source":"$source","type":"$type","files":[$fileJson]}""")
    }

    private fun complete(token: String?, batchId: String): HttpResponse<String> =
        post("/attachments/batches/$batchId/complete", token)

    private fun upload(ticket: JsonNode, body: ByteArray, contentType: String? = null): Int {
        val builder = HttpRequest.newBuilder(URI(ticket["url"].asString())).PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        ticket["headers"].properties().forEach { (name, value) ->
            if (!name.equals("Content-Length", ignoreCase = true)) {
                builder.header(name, if (name.equals("Content-Type", ignoreCase = true) && contentType != null) contentType else value.asString())
            }
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun download(url: String): ByteArray =
        http.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body()

    private fun strings(array: JsonNode): List<String> = (0 until array.size()).map { array[it].asString() }

    private fun json(response: HttpResponse<String>): JsonNode = jsonMapper.readTree(response.body())

    private fun filler(size: Int, vararg header: Int): ByteArray =
        ByteArray(size).also { random.nextBytes(it) }.also { bytes -> header.forEachIndexed { i, b -> bytes[i] = b.toByte() } }

    private fun jpeg(size: Int) = filler(size, 0xFF, 0xD8, 0xFF, 0xE0)

    private fun png(size: Int) = filler(size, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun isoMedia(size: Int, brand: String) =
        filler(size, 0, 0, 0, 0x18, 'f'.code, 't'.code, 'y'.code, 'p'.code, *brand.map { it.code }.toIntArray())

    private fun mp3(size: Int) = filler(size, 'I'.code, 'D'.code, '3'.code, 4, 0)

    private fun statusesOf(batchId: String): List<String> =
        jdbc.queryForList("select status from attachments where batch_id = ?::uuid order by position", String::class.java, batchId)

    init {
        val uploader = UUID.randomUUID()
        val token = tokenOf(uploader)

        Given("이미지 2개를 발급받아 둘 다 올렸을 때") {
            val first = jpeg(2048)
            val second = png(4096)
            val issued = issue(token, "PROFILE", "IMAGE", "image/jpeg" to first.size.toLong(), "image/png" to second.size.toLong())
            val issuedBody = json(issued)
            val batchId = issuedBody["batchId"].asString()
            val uploads = issuedBody["uploads"]

            Then("발급은 201 이고 업로드 정보가 files 순서대로 온다") {
                issued.statusCode() shouldBe 201
                uploads.size() shouldBe 2
                uploads[0]["key"].asString() shouldEndWith ".jpg"
                uploads[1]["key"].asString() shouldEndWith ".png"
                uploads[0]["headers"]["Content-Type"].asString() shouldBe "image/jpeg"
                uploads[0]["headers"]["Content-Length"].asString() shouldBe first.size.toString()
            }

            Then("업로드 key 는 staging/source 소문자/UTC 날짜/UUID.확장자 이다") {
                val today = LocalDate.now(ZoneOffset.UTC)
                val keyPath = uploads[0]["key"].asString()

                keyPath shouldStartWith "staging/profile/$today/"
                UUID.fromString(keyPath.removePrefix("staging/profile/$today/").removeSuffix(".jpg")).shouldNotBeNull()
            }

            upload(uploads[0], first) shouldBe 200
            upload(uploads[1], second) shouldBe 200

            When("완료 확인을 하면") {
                val completed = complete(token, batchId)
                val urls = strings(json(completed)["urls"])

                Then("URL 이 발급 순서대로 오고 각 URL 의 내용이 올린 바이트와 같다") {
                    completed.statusCode() shouldBe 200
                    urls shouldHaveSize 2
                    urls[0] shouldBe "${minio.s3URL}/$BUCKET/${uploads[0]["key"].asString().removePrefix("staging/")}"
                    download(urls[0]).contentEquals(first) shouldBe true
                    download(urls[1]).contentEquals(second) shouldBe true
                }

                Then("DB 에 업로더, source 문자열, type, position, 상태, 업로드 시각이 남는다") {
                    val rows = jdbc.queryForList("select * from attachments where batch_id = ?::uuid order by position", batchId)

                    rows shouldHaveSize 2
                    rows.forEachIndexed { index, row ->
                        row["uploader_id"] shouldBe uploader
                        row["source"] shouldBe "PROFILE"
                        row["type"] shouldBe "IMAGE"
                        row["position"] shouldBe index
                        row["status"] shouldBe "UPLOADED"
                        row["uploaded_at"].shouldNotBeNull()
                        row["object_key"] shouldBe uploads[index]["key"].asString().removePrefix("staging/")
                    }
                    rows[0]["size_bytes"] shouldBe first.size.toLong()
                    rows[0]["content_type"] shouldBe "image/jpeg"
                }

                Then("공개 URL 은 staging 이 아닌 최종 key 이고 staging 객체도 남아 있다") {
                    urls.none { it.contains("staging/") } shouldBe true
                    download("${minio.s3URL}/$BUCKET/${uploads[0]["key"].asString()}").contentEquals(first) shouldBe true
                }

                Then("complete 뒤 같은 presigned URL 로 같은 크기의 다른 바이트를 올려도 공개 URL 내용은 그대로이다") {
                    val tampered = jpeg(first.size)

                    upload(uploads[0], tampered) shouldBe 200

                    download(urls[0]).contentEquals(first) shouldBe true
                }

                Then("다시 완료 확인해도 같은 URL 리스트이다") {
                    val again = complete(token, batchId)

                    again.statusCode() shouldBe 200
                    strings(json(again)["urls"]) shouldBe urls
                }
            }
        }

        Given("영상 1개를 발급받아 올렸을 때") {
            val video = isoMedia(5000, "isom")
            val issued = json(issue(token, "PROFILE", "VIDEO", "video/mp4" to video.size.toLong()))

            upload(issued["uploads"][0], video) shouldBe 200

            When("완료 확인을 하면") {
                val completed = complete(token, issued["batchId"].asString())

                Then("URL 이 오고 내용이 같다") {
                    completed.statusCode() shouldBe 200
                    download(json(completed)["urls"][0].asString()).contentEquals(video) shouldBe true
                }
            }
        }

        Given("음성 1개를 발급받아 올렸을 때") {
            val audio = mp3(3000)
            val issued = json(issue(token, "PROFILE", "AUDIO", "audio/mpeg" to audio.size.toLong()))

            upload(issued["uploads"][0], audio) shouldBe 200

            When("완료 확인을 하면") {
                val completed = complete(token, issued["batchId"].asString())

                Then("URL 이 오고 내용이 같다") {
                    completed.statusCode() shouldBe 200
                    download(json(completed)["urls"][0].asString()).contentEquals(audio) shouldBe true
                }
            }
        }

        Given("개수 규칙을 어기는 발급 요청은") {
            Then("음성 2개는 400 이다") {
                issue(token, "PROFILE", "AUDIO", "audio/mpeg" to 10L, "audio/mpeg" to 10L).statusCode() shouldBe 400
            }

            Then("이미지 11개는 400 이다") {
                val files = List(11) { "image/jpeg" to 10L }.toTypedArray()

                issue(token, "PROFILE", "IMAGE", *files).statusCode() shouldBe 400
            }

            Then("이미지 10개는 201 이다") {
                val files = List(10) { "image/jpeg" to 10L }.toTypedArray()

                issue(token, "PROFILE", "IMAGE", *files).statusCode() shouldBe 201
            }

            Then("파일 0개는 400 이다") {
                issue(token, "PROFILE", "IMAGE").statusCode() shouldBe 400
            }
        }

        Given("종류·source 규칙을 어기는 발급 요청은") {
            Then("이미지 batch 에 video/mp4 를 섞으면 400 이다") {
                issue(token, "PROFILE", "IMAGE", "image/jpeg" to 10L, "video/mp4" to 10L).statusCode() shouldBe 400
            }

            Then("허용 목록에 없는 contentType 은 400 이다") {
                issue(token, "PROFILE", "IMAGE", "image/bmp" to 10L).statusCode() shouldBe 400
                issue(token, "PROFILE", "IMAGE", "application/pdf" to 10L).statusCode() shouldBe 400
            }

            Then("허용 목록에 없는 source 는 400 이다") {
                issue(token, "CHAT", "IMAGE", "image/jpeg" to 10L).statusCode() shouldBe 400
            }

            Then("크기가 0 이하이면 400 이다") {
                issue(token, "PROFILE", "IMAGE", "image/jpeg" to 0L).statusCode() shouldBe 400
            }
        }

        Given("크기 상한 경계에서") {
            Then("이미지는 10MB 정확히는 201, 1바이트 넘으면 400 이다") {
                issue(token, "PROFILE", "IMAGE", "image/jpeg" to 10 * MEGABYTE).statusCode() shouldBe 201
                issue(token, "PROFILE", "IMAGE", "image/jpeg" to 10 * MEGABYTE + 1).statusCode() shouldBe 400
            }

            Then("영상은 500MB 정확히는 201, 1바이트 넘으면 400 이다") {
                issue(token, "PROFILE", "VIDEO", "video/mp4" to 500 * MEGABYTE).statusCode() shouldBe 201
                issue(token, "PROFILE", "VIDEO", "video/mp4" to 500 * MEGABYTE + 1).statusCode() shouldBe 400
            }

            Then("음성은 10MB 정확히는 201, 1바이트 넘으면 400 이다") {
                issue(token, "PROFILE", "AUDIO", "audio/mpeg" to 10 * MEGABYTE).statusCode() shouldBe 201
                issue(token, "PROFILE", "AUDIO", "audio/mpeg" to 10 * MEGABYTE + 1).statusCode() shouldBe 400
            }
        }

        Given("이미지 2개 중 하나만 올렸을 때") {
            val first = jpeg(1000)
            val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to first.size.toLong(), "image/jpeg" to 1000L))
            val batchId = issued["batchId"].asString()

            upload(issued["uploads"][0], first) shouldBe 200

            When("완료 확인을 하면") {
                val completed = complete(token, batchId)

                Then("404 이고 상태는 그대로 PENDING 이다") {
                    completed.statusCode() shouldBe 404
                    statusesOf(batchId) shouldBe listOf("PENDING", "PENDING")
                    jdbc.queryForObject("select count(uploaded_at) from attachments where batch_id = ?::uuid", Int::class.java, batchId) shouldBe 0
                }
            }
        }

        Given("발급 때와 다른 Content-Type 으로 올리면") {
            val bytes = jpeg(1000)
            val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to bytes.size.toLong()))

            Then("스토리지가 403 으로 거절한다") {
                upload(issued["uploads"][0], bytes, contentType = "image/png") shouldBe 403
            }
        }

        Given("발급 때와 다른 크기로 올리면") {
            val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to 1000L))

            Then("스토리지가 거절한다") {
                upload(issued["uploads"][0], jpeg(999)) shouldBe 403
            }
        }

        Given("image/jpeg 로 선언하고 다른 내용을 올렸을 때") {
            listOf("PNG 바이트" to png(1000), "텍스트" to "hello, this is not an image".toByteArray()).forEach { (name, bytes) ->
                val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to bytes.size.toLong()))
                val batchId = issued["batchId"].asString()

                upload(issued["uploads"][0], bytes) shouldBe 200

                When("$name 로 완료 확인을 하면") {
                    val completed = complete(token, batchId)

                    Then("400 이고 상태는 그대로 PENDING 이다") {
                        completed.statusCode() shouldBe 400
                        statusesOf(batchId) shouldBe listOf("PENDING")
                    }
                }
            }
        }

        Given("영상으로 선언하고 음성 브랜드의 ISO 파일을 올렸을 때") {
            val bytes = isoMedia(1000, "M4A ")
            val issued = json(issue(token, "PROFILE", "VIDEO", "video/mp4" to bytes.size.toLong()))

            upload(issued["uploads"][0], bytes) shouldBe 200

            Then("완료 확인은 400 이다") {
                complete(token, issued["batchId"].asString()).statusCode() shouldBe 400
            }
        }

        Given("다른 사용자가 만든 batch 를") {
            val bytes = jpeg(1000)
            val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to bytes.size.toLong()))
            upload(issued["uploads"][0], bytes) shouldBe 200

            When("내가 완료 확인하면") {
                val completed = complete(tokenOf(UUID.randomUUID()), issued["batchId"].asString())

                Then("404 이고 상태는 그대로 PENDING 이다") {
                    completed.statusCode() shouldBe 404
                    statusesOf(issued["batchId"].asString()) shouldBe listOf("PENDING")
                }
            }

            When("없는 batch 를 완료 확인하면") {
                Then("404 이다") {
                    complete(token, UUID.randomUUID().toString()).statusCode() shouldBe 404
                }
            }
        }

        Given("검증한 뒤 staging 객체가 바뀌면") {
            val original = jpeg(1000)
            val issued = json(issue(token, "PROFILE", "IMAGE", "image/jpeg" to original.size.toLong()))
            val ticket = issued["uploads"][0]
            val stagingKey = ticket["key"].asString()

            upload(ticket, original) shouldBe 200
            val verifiedEtag = storage.head(stagingKey).shouldNotBeNull().etag
            upload(ticket, jpeg(original.size)) shouldBe 200

            When("검증 때의 ETag 를 조건으로 복사하면") {
                Then("409 로 실패하고 최종 key 는 만들어지지 않는다") {
                    val failure = shouldThrow<ResponseStatusException> {
                        storage.copy(stagingKey, stagingKey.removePrefix("staging/"), verifiedEtag)
                    }

                    failure.statusCode.value() shouldBe 409
                    storage.head(stagingKey.removePrefix("staging/")).shouldBeNull()
                }
            }
        }

        Given("sub 가 UUID 가 아닌 토큰으로") {
            val badToken = tokenOf("not-a-uuid")

            Then("발급도 완료 확인도 401 이다") {
                issue(badToken, "PROFILE", "IMAGE", "image/jpeg" to 10L).statusCode() shouldBe 401
                complete(badToken, UUID.randomUUID().toString()).statusCode() shouldBe 401
            }
        }

        Given("토큰 없이") {
            Then("발급도 완료 확인도 401 이다") {
                issue(null, "PROFILE", "IMAGE", "image/jpeg" to 10L).statusCode() shouldBe 401
                complete(null, UUID.randomUUID().toString()).statusCode() shouldBe 401
            }
        }
    }
}
