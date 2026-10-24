package com.langlez.consumer

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object TestLoki {
    val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("grafana/loki:3.3.2"))
            .withExposedPorts(3100)
            .waitingFor(Wait.forHttp("/ready").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))
            .also { it.start() }

    private val baseUrl: String get() = "http://${container.host}:${container.getMappedPort(3100)}"

    val pushUrl: String get() = "$baseUrl/loki/api/v1/push"

    private val http = HttpClient.newHttpClient()

    fun query(logql: String): String {
        val query = URLEncoder.encode(logql, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI("$baseUrl/loki/api/v1/query_range?query=$query&limit=100")).build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}
