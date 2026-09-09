package com.langlez.datasource

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * 가상 스레드 환경에 적합한 HikariCP 커넥션 풀 및 타임아웃 설정이
 * 정상적으로 주입되는지 검증하는 테스트 (A-07).
 */
class HikariPoolConfigurationTest : BehaviorSpec({

    val baseRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration::class.java))

    Given("기본 application.yml 설정을 로드했을 때") {
        When("DataSource 빈을 조회하면") {
            Then("HikariCP 풀 크기와 타임아웃이 기본 설정값대로 주입된다") {
                baseRunner.run { context ->
                    val dataSource = context.getBean(HikariDataSource::class.java)
                    dataSource.maximumPoolSize shouldBe 30
                    dataSource.minimumIdle shouldBe 10
                    dataSource.connectionTimeout shouldBe 5000L
                    dataSource.idleTimeout shouldBe 600000L
                    dataSource.maxLifetime shouldBe 1800000L
                }
            }
        }
    }

    Given("application-production.yml 설정을 로드했을 때 (환경 변수 기본값)") {
        val prodRunner = baseRunner
            .withPropertyValues(
                "spring.profiles.active=production",
                "POSTGRES_URL=jdbc:postgresql://localhost:5432/langlez_db",
                "POSTGRES_USERNAME=admin",
                "POSTGRES_PASSWORD=admin",
            )

        When("DataSource 빈을 조회하면") {
            Then("HikariCP 풀 크기와 타임아웃의 프로덕션 기본값이 주입된다") {
                prodRunner.run { context ->
                    val dataSource = context.getBean(HikariDataSource::class.java)
                    dataSource.maximumPoolSize shouldBe 50
                    dataSource.minimumIdle shouldBe 10
                    dataSource.connectionTimeout shouldBe 5000L
                    dataSource.idleTimeout shouldBe 600000L
                    dataSource.maxLifetime shouldBe 1800000L
                }
            }
        }
    }

    Given("application-production.yml 설정을 로드하고 환경 변수를 재정의했을 때") {
        val prodOverrideRunner = baseRunner
            .withPropertyValues(
                "spring.profiles.active=production",
                "POSTGRES_URL=jdbc:postgresql://localhost:5432/langlez_db",
                "POSTGRES_USERNAME=admin",
                "POSTGRES_PASSWORD=admin",
                "DB_POOL_MAX_SIZE=80",
                "DB_POOL_MIN_IDLE=20",
                "DB_POOL_TIMEOUT_MS=8000",
            )

        When("DataSource 빈을 조회하면") {
            Then("재정의된 환경 변수 값이 HikariCP 설정에 반영된다") {
                prodOverrideRunner.run { context ->
                    val dataSource = context.getBean(HikariDataSource::class.java)
                    dataSource.maximumPoolSize shouldBe 80
                    dataSource.minimumIdle shouldBe 20
                    dataSource.connectionTimeout shouldBe 8000L
                    dataSource.idleTimeout shouldBe 600000L
                    dataSource.maxLifetime shouldBe 1800000L
                }
            }
        }
    }
})
