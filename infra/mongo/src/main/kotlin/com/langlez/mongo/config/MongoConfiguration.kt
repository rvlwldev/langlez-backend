package com.langlez.mongo.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * Mongo 리포지토리 스캔 범위.
 *
 * `MainApplication` 이 Boot 의 `MongoRepositoriesAutoConfiguration` 을 제외한다 — 필터 없이
 * `com.langlez` 전체를 한 번 더 훑으면 JPA 쪽과 겹쳐 "Could not safely identify store assignment"
 * 노이즈가 난다. 그래서 `infra:rdb` 의 JPA 설정과 같은 방식으로 여기서 범위를 직접 좁혀 켠다.
 *
 * **이 설정의 소유자는 인프라 계층이다.** 원래 `module/chat` 에 있었는데, 그러면 Mongo 를 쓰는
 * 다른 모듈이 생겼을 때 그 모듈의 리포지토리가 `chat` 이 조립돼 있어야만 켜진다. `chat` 을 빼거나
 * 이 설정을 건드리는 순간 관계없는 모듈이 조용히 깨지는 구조라 여기로 올렸다.
 *
 * `basePackages` 는 도메인 이름을 담지 않는다. 도메인 모듈마다 `infrastructure/mongo` 패키지를
 * 갖는 것이 이 저장소 관례라 범위를 좁히면 새 모듈이 조용히 빠진다.
 */
@Configuration
@EnableMongoRepositories(basePackages = ["com.langlez.**.mongo"])
class MongoConfiguration
