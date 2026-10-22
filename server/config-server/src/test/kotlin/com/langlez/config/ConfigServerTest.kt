package com.langlez.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.RestClient

/**
 * 클라이언트가 `/{앱 이름}/{프로필}` 로 물으면 config-repo 의 값을 내려주는지 본다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.cloud.config.server.native.search-locations=file:./config-repo"],
)
class ConfigServerTest {
    @Value("\${local.server.port}")
    private var port: Int = 0

    private fun get(path: String) =
        RestClient.create("http://localhost:$port").get().uri(path).retrieve().body(String::class.java)!!

    @Test
    fun `앱 이름으로 그 앱의 설정을 내려준다`() {
        assertThat(get("/langlez-gateway/default")).contains("gateway.legacy-uri", "http://localhost:8080")
    }

    @Test
    fun `다른 앱의 설정은 섞이지 않는다`() {
        assertThat(get("/langlez-api/default"))
            .contains("app.config-source")
            .doesNotContain("gateway.legacy-uri")
    }
}
