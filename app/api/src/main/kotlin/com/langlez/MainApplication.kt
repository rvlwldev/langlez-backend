package com.langlez

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

// JPA/Mongo Repository 스캔은 infra/rdb, infra/mongo의 커스텀 @Enable*Repositories(scope 제한 포함)로 대체.
// Boot의 기본 자동 설정을 그대로 두면 필터 없이 com.langlez 전체를 한 번 더 스캔해서
// "Could not safely identify store assignment" 노이즈 로그가 중복으로 발생한다.

@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = [MongoRepositoriesAutoConfiguration::class, JpaRepositoriesAutoConfiguration::class])
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}
