package com.langlez.chat.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * Mongo 리포지토리 스캔 범위.
 *
 * `MainApplication` 이 Boot 의 `MongoRepositoriesAutoConfiguration` 을 제외한다 — 필터 없이
 * `com.langlez` 전체를 한 번 더 훑으면 JPA 쪽과 겹쳐 "Could not safely identify store assignment"
 * 노이즈가 난다. 그래서 `infra:rdb` 의 JPA 설정과 같은 방식으로 여기서 범위를 직접 좁혀 켠다.
 */
@Configuration
@EnableMongoRepositories(basePackages = ["com.langlez.**.mongo"])
class ChatMongoConfiguration
