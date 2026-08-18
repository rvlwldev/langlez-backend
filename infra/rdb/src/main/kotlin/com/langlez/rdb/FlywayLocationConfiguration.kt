package com.langlez.rdb

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.flywaydb.core.Flyway

/**
 * 마이그레이션 위치를 이 모듈이 직접 알린다.
 *
 * app 의 application.yml 에만 적으면 모듈 단위 통합 테스트에는 그 설정이 안 실려
 * Flyway 가 기본 경로(db/migration)를 보고 아무것도 실행하지 않는다.
 * 그 상태에서 ddl-auto=validate 면 "missing table" 로 컨텍스트가 죽는다.
 *
 * RdbConfiguration 과 분리해야 한다. 거기엔 EntityManager 가 주입되는데,
 * Flyway 는 EntityManagerFactory 보다 먼저 떠야 해서 같은 클래스에 두면 순환이 생긴다.
 */
@AutoConfiguration
@ConditionalOnClass(Flyway::class)
class FlywayLocationConfiguration {

    @Bean
    fun flywayMigrationLocation() = FlywayConfigurationCustomizer { it.locations(MIGRATION_LOCATION) }

    companion object {
        const val MIGRATION_LOCATION = "classpath:migration"
    }
}
