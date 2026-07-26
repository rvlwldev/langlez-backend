# Interest Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 새 `module/interest`를 만들어 관심사(Interest)를 언어별 컬럼 + 회원별 매핑(MemberInterest)으로
관리하고, 검색(locale별 FULLTEXT)과 admin 병합(백필 포함) 기능을 제공하며, `matching`/`profile`이
이를 사용하도록 교체한다.

**Architecture:** 계층형 모듈(`api → application → domain ← infrastructure`), 다른 모듈과 동일한
Kotlin/Spring Boot/JPA/QueryDSL 컨벤션을 따른다. Spec: `docs/superpowers/specs/2026-07-26-interest-module-design.md`.

**Tech Stack:** Kotlin 2.2, Spring Boot 3.x, JPA(Hibernate), QueryDSL(KSP codegen), MySQL FULLTEXT(native DDL),
Kotest + MockK, Testcontainers(MySQL).

## Global Constraints

- 이 프로젝트는 Flyway/Liquibase 없이 `spring.jpa.hibernate.ddl-auto=update`만 사용한다 — 스키마는
  엔티티 어노테이션으로 자동 반영되고, FULLTEXT 인덱스처럼 JPA로 표현 불가능한 것만 기동 시 native
  DDL 러너로 보완한다.
- 지원 언어 12개(코드/필드명 고정): `ko, en, ja, zhTW, zhCN, de, vi, ind, fr, pt, es, ru`
  (BCP-47 태그: ko, en, ja, zh-TW, zh-CN, de, vi, id, fr, pt, es, ru — `id`는 Interest 엔티티의 PK와
  이름이 겹치므로 필드명은 `ind`로 둔다).
- `@MemberId`(신규, `com.langlez.security.web.MemberId`)를 쓴다. `@MemberID`는 `@Deprecated`.
- 개발 중인 프로젝트라 기존 `Profile.interests` 데이터 마이그레이션은 하지 않는다.
- 새 모듈은 `app/api/build.gradle.kts`의 `dependencies` 블록에 `implementation(project(":module:interest"))`를
  반드시 추가해야 부팅 시 컴포넌트 스캔에 포함된다(이 프로젝트 컨벤션, `settings.gradle.kts`의
  `includeModules("module")`가 자동으로 module 폴더를 include하지만 app:api 의존성 등록은 수동).

---

### Task 1: `module/interest` 모듈 스캐폴딩 + `Interest`/`MemberInterest` 엔티티 + JPA/도메인 레포지토리

**Files:**
- Create: `module/interest/build.gradle.kts`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/domain/Interest.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/domain/MemberInterest.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/domain/InterestRepository.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/domain/MemberInterestRepository.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/jpa/InterestJpaRepository.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/jpa/MemberInterestJpaRepository.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/InterestRepositoryImpl.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/MemberInterestRepositoryImpl.kt`
- Modify: `settings.gradle.kts` — 없음(`includeModules("module")`가 자동 인식, 확인만 할 것)
- Modify: `app/api/build.gradle.kts:15` 부근 — `implementation(project(":module:interest"))` 추가

**Interfaces:**
- Produces: `Interest(id, ko, en, ja, zhTW, zhCN, de, vi, ind, fr, pt, es, ru)`,
  `MemberInterest(id, memberId, interestId)`,
  `InterestRepository.findById(id): Interest?`, `.findByColumn(locale: String, value: String): Interest?`,
  `.save(interest): Interest`, `.delete(interest)`, `.searchByColumn(locale: String, term: String, limit: Int): List<Interest>`,
  `MemberInterestRepository.findByMemberId(memberId): List<MemberInterest>`,
  `.findByInterestId(interestId): List<MemberInterest>`,
  `.saveAll(list): List<MemberInterest>`, `.deleteAll(list)`

- [ ] **Step 1: `build.gradle.kts` 작성**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":infra:mysql"))

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.bundles.testcontainers)
}
```

- [ ] **Step 2: `Interest.kt` 작성**

```kotlin
package com.langlez.interest.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "interests")
class Interest(
    var ko: String? = null,
    var en: String? = null,
    var ja: String? = null,
    var zhTW: String? = null,
    var zhCN: String? = null,
    var de: String? = null,
    var vi: String? = null,
    var ind: String? = null,
    var fr: String? = null,
    var pt: String? = null,
    var es: String? = null,
    var ru: String? = null,
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    /** locale 필드명(ko/en/...)으로 현재 값을 읽는다. */
    fun get(localeField: String): String? = when (localeField) {
        "ko" -> ko; "en" -> en; "ja" -> ja; "zhTW" -> zhTW; "zhCN" -> zhCN; "de" -> de
        "vi" -> vi; "ind" -> ind; "fr" -> fr; "pt" -> pt; "es" -> es; "ru" -> ru
        else -> null
    }

    /** locale 필드명으로 값을 설정한다. */
    fun set(localeField: String, value: String?) {
        when (localeField) {
            "ko" -> ko = value; "en" -> en = value; "ja" -> ja = value
            "zhTW" -> zhTW = value; "zhCN" -> zhCN = value; "de" -> de = value
            "vi" -> vi = value; "ind" -> ind = value; "fr" -> fr = value
            "pt" -> pt = value; "es" -> es = value; "ru" -> ru = value
        }
    }

    companion object {
        /** FULLTEXT 인덱스를 만들어야 하는 언어 컬럼 전체(DB 컬럼명 기준, camelCase가 스네이크로 매핑됨). */
        val LOCALE_FIELDS = listOf("ko", "en", "ja", "zhTW", "zhCN", "de", "vi", "ind", "fr", "pt", "es", "ru")
    }
}
```

- [ ] **Step 3: `MemberInterest.kt` 작성**

```kotlin
package com.langlez.interest.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "member_interests",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_INTEREST", columnNames = ["member_id", "interest_id"])],
)
class MemberInterest(
    val memberId: Long,
    val interestId: Long,
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0
}
```

- [ ] **Step 4: JPA 레포지토리 작성**

`InterestJpaRepository.kt`:
```kotlin
package com.langlez.interest.infrastructure.jpa

import com.langlez.interest.domain.Interest
import org.springframework.data.jpa.repository.JpaRepository

interface InterestJpaRepository : JpaRepository<Interest, Long>
```

`MemberInterestJpaRepository.kt`:
```kotlin
package com.langlez.interest.infrastructure.jpa

import com.langlez.interest.domain.MemberInterest
import org.springframework.data.jpa.repository.JpaRepository

interface MemberInterestJpaRepository : JpaRepository<MemberInterest, Long> {
    fun findAllByMemberId(memberId: Long): List<MemberInterest>
    fun findAllByInterestId(interestId: Long): List<MemberInterest>
    fun deleteAllByMemberIdAndInterestIdIn(memberId: Long, interestIds: Collection<Long>)
}
```

- [ ] **Step 5: 도메인 레포지토리 인터페이스 + QueryDSL 구현체 작성**

`InterestRepository.kt`:
```kotlin
package com.langlez.interest.domain

interface InterestRepository {
    fun findById(id: Long): Interest?
    fun findByColumn(localeField: String, value: String): Interest?
    fun save(interest: Interest): Interest
    fun delete(interest: Interest)
    /** locale 컬럼 하나에 대해 FULLTEXT 검색(MATCH...AGAINST), 상위 limit개. */
    fun searchByColumn(localeField: String, term: String, limit: Int): List<Interest>
}
```

`MemberInterestRepository.kt`:
```kotlin
package com.langlez.interest.domain

interface MemberInterestRepository {
    fun findByMemberId(memberId: Long): List<MemberInterest>
    fun findByInterestId(interestId: Long): List<MemberInterest>
    fun saveAll(list: List<MemberInterest>): List<MemberInterest>
    fun deleteAll(list: List<MemberInterest>)
}
```

`InterestRepositoryImpl.kt` — locale 컬럼별 exact-match는 JPQL 동적 컬럼 접근이 안 되므로(컬럼명이
런타임 문자열) `EntityManager`의 native query로 처리한다:

```kotlin
package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.infrastructure.jpa.InterestJpaRepository
import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class InterestRepositoryImpl(
    private val jpa: InterestJpaRepository,
    private val em: EntityManager,
) : InterestRepository {

    private val allowedColumns = Interest.LOCALE_FIELDS.toSet()

    override fun findById(id: Long): Interest? = jpa.findByIdOrNull(id)

    override fun findByColumn(localeField: String, value: String): Interest? {
        require(localeField in allowedColumns) { "invalid locale field: $localeField" }
        val results = em.createQuery(
            "SELECT i FROM Interest i WHERE i.$localeField = :value",
            Interest::class.java,
        ).setParameter("value", value).setMaxResults(1).resultList
        return results.firstOrNull()
    }

    override fun save(interest: Interest): Interest = jpa.save(interest)

    override fun delete(interest: Interest) = jpa.delete(interest)

    override fun searchByColumn(localeField: String, term: String, limit: Int): List<Interest> {
        require(localeField in allowedColumns) { "invalid locale field: $localeField" }
        val column = camelToSnake(localeField)
        return em.createNativeQuery(
            "SELECT * FROM interests WHERE MATCH($column) AGAINST(:term IN NATURAL LANGUAGE MODE) LIMIT :limit",
            Interest::class.java,
        ).setParameter("term", term).setParameter("limit", limit).resultList as List<Interest>
    }

    private fun camelToSnake(s: String): String =
        s.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}
```

`MemberInterestRepositoryImpl.kt`:
```kotlin
package com.langlez.interest.infrastructure

import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import com.langlez.interest.infrastructure.jpa.MemberInterestJpaRepository
import org.springframework.stereotype.Repository

@Repository
class MemberInterestRepositoryImpl(
    private val jpa: MemberInterestJpaRepository,
) : MemberInterestRepository {
    override fun findByMemberId(memberId: Long): List<MemberInterest> = jpa.findAllByMemberId(memberId)
    override fun findByInterestId(interestId: Long): List<MemberInterest> = jpa.findAllByInterestId(interestId)
    override fun saveAll(list: List<MemberInterest>): List<MemberInterest> = jpa.saveAll(list)
    override fun deleteAll(list: List<MemberInterest>) = jpa.deleteAll(list)
}
```

- [ ] **Step 6: `app/api/build.gradle.kts`에 모듈 의존성 추가**

`implementation(project(":module:profile"))` 다음 줄에 추가:
```kotlin
    implementation(project(":module:interest"))
```

- [ ] **Step 7: 컴파일 확인**

```bash
cd /Users/hj/project/langlez/server/main
./gradlew :module:interest:compileKotlin :app:api:compileKotlin --console=plain -q
```
Expected: BUILD SUCCESSFUL, 에러 없음.

- [ ] **Step 8: 커밋**

```bash
git add module/interest app/api/build.gradle.kts
git commit -m "feat(interest): Interest/MemberInterest 엔티티 및 레포지토리 스캐폴딩"
```

---

### Task 2: `InterestService` — locale별 검색/신규생성/회원 관심사 설정

**Files:**
- Create: `module/interest/src/main/kotlin/com/langlez/interest/application/InterestService.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/application/LocaleField.kt`
- Test: `module/interest/src/test/kotlin/com/langlez/interest/application/InterestServiceTest.kt`

**Interfaces:**
- Consumes: `InterestRepository`(Task 1), `MemberInterestRepository`(Task 1)
- Produces: `InterestService.search(locale: Locale, term: String): List<InterestView>`,
  `.setMemberInterests(memberId: Long, locale: Locale, names: List<String>)`,
  `.getMemberInterests(memberId: Long, locale: Locale): List<InterestView>`,
  `.getMemberInterestIds(memberId: Long): Set<Long>`,
  `InterestView(id: Long, name: String)`,
  `LocaleField.of(locale: Locale): String` (BCP-47 → 엔티티 필드명, 매칭 없으면 `"en"` 폴백)

- [ ] **Step 1: `LocaleField.kt` 작성 (실패하는 테스트 없이 바로 유틸)**

```kotlin
package com.langlez.interest.application

import java.util.Locale

/** Accept-Language 등에서 얻은 Locale을 Interest 엔티티의 언어 필드명으로 매핑한다. */
object LocaleField {
    private val TAG_TO_FIELD = mapOf(
        "ko" to "ko", "en" to "en", "ja" to "ja",
        "zh-TW" to "zhTW", "zh-CN" to "zhCN", "de" to "de",
        "vi" to "vi", "id" to "ind", "fr" to "fr",
        "pt" to "pt", "es" to "es", "ru" to "ru",
    )

    fun of(locale: Locale): String = TAG_TO_FIELD[locale.toLanguageTag()] ?: "en"
}
```

- [ ] **Step 2: `InterestServiceTest.kt` 실패하는 테스트 작성**

```kotlin
package com.langlez.interest.application

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Locale

class InterestServiceTest : BehaviorSpec({

    val interestRepo = mockk<InterestRepository>()
    val memberInterestRepo = mockk<MemberInterestRepository>()
    val service = InterestService(interestRepo, memberInterestRepo)

    Given("회원이 자기 언어로 관심사를 설정할 때") {
        val memberId = 1L
        val ko = Locale.forLanguageTag("ko")

        When("이미 존재하는 이름이면 새로 만들지 않는다") {
            val existing = Interest(ko = "등산")
            every { interestRepo.findByColumn("ko", "등산") } returns existing
            every { memberInterestRepo.findByMemberId(memberId) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { memberInterestRepo.deleteAll(any()) } returns Unit

            service.setMemberInterests(memberId, ko, listOf("등산"))

            Then("새 Interest를 저장하지 않는다") {
                verify(exactly = 0) { interestRepo.save(any()) }
            }
        }

        When("없는 이름이면 새 Interest를 그 언어 컬럼만 채워 생성한다") {
            every { interestRepo.findByColumn("ko", "서핑") } returns null
            val created = Interest(ko = "서핑")
            every { interestRepo.save(match { it.ko == "서핑" && it.en == null }) } returns created
            every { memberInterestRepo.findByMemberId(memberId) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { memberInterestRepo.deleteAll(any()) } returns Unit

            service.setMemberInterests(memberId, ko, listOf("서핑"))

            Then("ko 컬럼만 채워진 새 Interest가 저장된다") {
                verify { interestRepo.save(match { it.ko == "서핑" && it.en == null }) }
            }
        }
    }
})
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

```bash
cd /Users/hj/project/langlez/server/main
./gradlew :module:interest:test --tests "*InterestServiceTest*" --console=plain
```
Expected: FAIL — `InterestService` 클래스가 없어 컴파일 에러.

- [ ] **Step 4: `InterestService.kt` 구현**

```kotlin
package com.langlez.interest.application

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.Locale

data class InterestView(val id: Long, val name: String)

@Service
class InterestService(
    private val interestRepo: InterestRepository,
    private val memberInterestRepo: MemberInterestRepository,
) {

    fun search(locale: Locale, term: String): List<InterestView> {
        val field = LocaleField.of(locale)
        return interestRepo.searchByColumn(field, term, SEARCH_LIMIT)
            .mapNotNull { interest -> interest.get(field)?.let { InterestView(interest.id, it) } }
    }

    fun setMemberInterests(memberId: Long, locale: Locale, names: List<String>) {
        val field = LocaleField.of(locale)
        val resolvedIds = names.distinct().map { resolveOrCreate(field, it).id }.toSet()

        val current = memberInterestRepo.findByMemberId(memberId)
        val currentIds = current.map { it.interestId }.toSet()

        val toAdd = resolvedIds - currentIds
        val toRemove = current.filter { it.interestId !in resolvedIds }

        if (toAdd.isNotEmpty()) {
            memberInterestRepo.saveAll(toAdd.map { MemberInterest(memberId, it) })
        }
        if (toRemove.isNotEmpty()) {
            memberInterestRepo.deleteAll(toRemove)
        }
    }

    fun getMemberInterests(memberId: Long, locale: Locale): List<InterestView> {
        val field = LocaleField.of(locale)
        return memberInterestRepo.findByMemberId(memberId).mapNotNull { mi ->
            val interest = interestRepo.findById(mi.interestId) ?: return@mapNotNull null
            val name = interest.get(field) ?: interest.get("en") ?: return@mapNotNull null
            InterestView(interest.id, name)
        }
    }

    fun getMemberInterestIds(memberId: Long): Set<Long> =
        memberInterestRepo.findByMemberId(memberId).map { it.interestId }.toSet()

    private fun resolveOrCreate(field: String, name: String): Interest {
        interestRepo.findByColumn(field, name)?.let { return it }
        val created = Interest()
        created.set(field, name)
        return try {
            interestRepo.save(created)
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 이미 같은 이름이 생성된 경우 — 재조회해서 사용
            interestRepo.findByColumn(field, name) ?: throw e
        }
    }

    companion object {
        private const val SEARCH_LIMIT = 10
    }
}
```

- [ ] **Step 5: 테스트 재실행해서 통과 확인**

```bash
./gradlew :module:interest:test --tests "*InterestServiceTest*" --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add module/interest/src/main/kotlin/com/langlez/interest/application module/interest/src/test
git commit -m "feat(interest): locale별 검색/신규생성/회원 관심사 설정 InterestService 구현"
```

---

### Task 3: 기동 시 시드 러너 + FULLTEXT 인덱스 생성 러너

**Files:**
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/InterestSeedRunner.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/infrastructure/InterestFullTextIndexRunner.kt`

**Interfaces:**
- Consumes: `InterestJpaRepository`(Task 1), `Interest`(Task 1)
- Produces: 없음(둘 다 `ApplicationRunner`, 부수효과만)

- [ ] **Step 1: `InterestSeedRunner.kt` 작성**

```kotlin
package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import com.langlez.interest.infrastructure.jpa.InterestJpaRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** 기동 시 기본 관심사 목록을 멱등하게 시드한다. en 컬럼 값 기준으로 존재 여부를 확인한다. */
@Component
class InterestSeedRunner(private val jpa: InterestJpaRepository) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        SEED_DATA.forEach { seed ->
            val exists = jpa.findAll().any { it.en == seed.en }
            if (!exists) {
                jpa.save(
                    Interest(
                        ko = seed.ko, en = seed.en, ja = seed.ja, zhTW = seed.zhTW, zhCN = seed.zhCN,
                        de = seed.de, vi = seed.vi, ind = seed.ind, fr = seed.fr, pt = seed.pt,
                        es = seed.es, ru = seed.ru,
                    )
                )
            }
        }
    }

    private data class Seed(
        val ko: String, val en: String, val ja: String, val zhTW: String, val zhCN: String,
        val de: String, val vi: String, val ind: String, val fr: String, val pt: String,
        val es: String, val ru: String,
    )

    companion object {
        private val SEED_DATA = listOf(
            Seed("여행", "Travel", "旅行", "旅行", "旅行", "Reisen", "Du lịch", "Perjalanan", "Voyage", "Viagem", "Viajar", "Путешествия"),
            Seed("영화", "Movies", "映画", "電影", "电影", "Filme", "Phim ảnh", "Film", "Cinéma", "Filmes", "Películas", "Кино"),
            Seed("음악", "Music", "音楽", "音樂", "音乐", "Musik", "Âm nhạc", "Musik", "Musique", "Música", "Música", "Музыка"),
            Seed("운동", "Sports", "スポーツ", "運動", "运动", "Sport", "Thể thao", "Olahraga", "Sport", "Esportes", "Deportes", "Спорт"),
            Seed("독서", "Reading", "読書", "閱讀", "阅读", "Lesen", "Đọc sách", "Membaca", "Lecture", "Leitura", "Lectura", "Чтение"),
            Seed("요리", "Cooking", "料理", "烹飪", "烹饪", "Kochen", "Nấu ăn", "Memasak", "Cuisine", "Culinária", "Cocina", "Готовка"),
            Seed("사진", "Photography", "写真", "攝影", "摄影", "Fotografie", "Nhiếp ảnh", "Fotografi", "Photographie", "Fotografia", "Fotografía", "Фотография"),
            Seed("등산", "Hiking", "ハイキング", "健行", "徒步", "Wandern", "Đi bộ đường dài", "Mendaki", "Randonnée", "Caminhada", "Senderismo", "Пеший туризм"),
        )
    }
}
```

- [ ] **Step 2: `InterestFullTextIndexRunner.kt` 작성**

```kotlin
package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 언어 컬럼 12개 각각에 단일 컬럼 FULLTEXT 인덱스를 기동 시 확인 후 없으면 생성한다.
 * Flyway/Liquibase가 없는 이 프로젝트에서 JPA 어노테이션만으로는 FULLTEXT를 선언할 수 없어
 * native DDL로 보완한다. `information_schema.STATISTICS`로 이미 있으면 건너뛴다(멱등).
 */
@Component
@Order(Int.MAX_VALUE) // ddl-auto=update로 테이블 생성이 끝난 뒤 실행되도록 가장 늦게
class InterestFullTextIndexRunner(private val em: EntityManager) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        Interest.LOCALE_FIELDS.forEach { field ->
            val column = camelToSnake(field)
            val indexName = "FT_INTEREST_${column.uppercase()}"
            val exists = (
                em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'interests' AND index_name = :indexName"
                ).setParameter("indexName", indexName).singleResult as Number
                ).toLong() > 0

            if (!exists) {
                log.info("Creating FULLTEXT index {} on interests.{}", indexName, column)
                em.createNativeQuery("ALTER TABLE interests ADD FULLTEXT INDEX $indexName ($column)").executeUpdate()
            }
        }
    }

    private fun camelToSnake(s: String): String =
        s.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}
```

- [ ] **Step 3: 실제 MySQL로 기동 확인 (통합테스트 대신 로컬 기동 확인)**

```bash
cd /Users/hj/project/langlez/server/main
./infra-start.sh
./gradlew :app:api:bootRun --args='--spring.profiles.active=' &
sleep 25
mysql -h 127.0.0.1 -P 3306 -u admin -padmin langlez_db -e "SHOW INDEX FROM interests WHERE Key_name LIKE 'FT_%';"
mysql -h 127.0.0.1 -P 3306 -u admin -padmin langlez_db -e "SELECT id, ko, en FROM interests LIMIT 5;"
kill %1
```
Expected: `SHOW INDEX` 결과에 `FT_INTEREST_KO`, `FT_INTEREST_EN` 등 12개 로우가 보이고,
`interests` 테이블에 8개 시드 로우가 있음.

- [ ] **Step 4: 커밋**

```bash
git add module/interest/src/main/kotlin/com/langlez/interest/infrastructure
git commit -m "feat(interest): 기동 시 관심사 시드 + FULLTEXT 인덱스 native DDL 러너 추가"
```

---

### Task 4: `InterestController` (검색 + 내 관심사 조회/설정)

**Files:**
- Create: `module/interest/src/main/kotlin/com/langlez/interest/api/InterestController.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/api/InterestResponse.kt`
- Create: `module/interest/src/main/kotlin/com/langlez/interest/api/InterestRequest.kt`
- Test: `module/interest/src/test/kotlin/com/langlez/interest/api/InterestControllerTest.kt`

**Interfaces:**
- Consumes: `InterestService`(Task 2)
- Produces: `GET /api/v1/interests/search?q=`, `GET /api/v1/interests/me`, `PUT /api/v1/interests/me`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.langlez.interest.api

import com.langlez.interest.application.InterestService
import com.langlez.interest.application.InterestView
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale

class InterestControllerTest : BehaviorSpec({

    val service = mockk<InterestService>()
    val controller = InterestController(service)
    val locale = Locale.forLanguageTag("ko")

    Given("관심사 검색 요청 시") {
        every { service.search(locale, "등") } returns listOf(InterestView(1, "등산"))

        Then("서비스 결과를 그대로 반환한다") {
            val result = controller.search(locale, "등")
            result.items shouldBe listOf(InterestResponse.Item(1, "등산"))
        }
    }

    Given("내 관심사 설정 요청 시") {
        every { service.setMemberInterests(1L, locale, listOf("등산", "서핑")) } returns Unit

        Then("서비스에 위임한다") {
            controller.setMyInterests(1L, locale, InterestRequest.Set(listOf("등산", "서핑")))
            verify { service.setMemberInterests(1L, locale, listOf("등산", "서핑")) }
        }
    }
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew :module:interest:test --tests "*InterestControllerTest*" --console=plain
```
Expected: FAIL — 컴파일 에러(클래스 없음).

- [ ] **Step 3: `InterestRequest.kt`/`InterestResponse.kt`/`InterestController.kt` 구현**

```kotlin
// InterestRequest.kt
package com.langlez.interest.api

class InterestRequest {
    data class Set(val names: List<String>)
}
```

```kotlin
// InterestResponse.kt
package com.langlez.interest.api

import com.langlez.interest.application.InterestView

class InterestResponse {
    data class Item(val id: Long, val name: String)
    data class List(val items: kotlin.collections.List<Item>)

    companion object {
        fun of(views: kotlin.collections.List<InterestView>) = List(views.map { Item(it.id, it.name) })
    }
}
```

```kotlin
// InterestController.kt
package com.langlez.interest.api

import com.langlez.interest.application.InterestService
import com.langlez.security.web.MemberId
import org.springframework.web.bind.annotation.*
import java.util.Locale

@RestController
@RequestMapping("/api/v1/interests")
class InterestController(private val service: InterestService) {

    @GetMapping("/search")
    fun search(locale: Locale, @RequestParam q: String): InterestResponse.List =
        InterestResponse.of(service.search(locale, q))

    @GetMapping("/me")
    fun getMyInterests(@MemberId memberId: Long, locale: Locale): InterestResponse.List =
        InterestResponse.of(service.getMemberInterests(memberId, locale))

    @PutMapping("/me")
    fun setMyInterests(
        @MemberId memberId: Long,
        locale: Locale,
        @RequestBody request: InterestRequest.Set,
    ) {
        service.setMemberInterests(memberId, locale, request.names)
    }
}
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
./gradlew :module:interest:test --tests "*InterestControllerTest*" --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add module/interest/src/main/kotlin/com/langlez/interest/api module/interest/src/test/kotlin/com/langlez/interest/api
git commit -m "feat(interest): 검색/내 관심사 조회·설정 API 추가"
```

---

### Task 5: Admin 관심사 병합 (백필 포함)

**Files:**
- Modify: `module/admin/build.gradle.kts` — `implementation(project(":module:interest"))` 추가
- Modify: `module/interest/src/main/kotlin/com/langlez/interest/application/InterestService.kt` — 병합 메서드 추가
- Test: `module/interest/src/test/kotlin/com/langlez/interest/application/InterestServiceTest.kt` — 병합 테스트 추가
- Create: `module/admin/src/main/kotlin/com/langlez/admin/api/AdminInterestController.kt`
- Create: `module/admin/src/main/resources/templates/admin/interests.html`
- Modify: `module/admin/src/main/resources/templates/admin/layout.html` — 사이드바에 메뉴 추가

**Interfaces:**
- Consumes: `InterestService`(Task 2), `InterestRepository`/`MemberInterestRepository`(Task 1)
- Produces: `InterestService.merge(fromId: Long, toId: Long)`, `InterestService.search(locale, term)`(재사용)

- [ ] **Step 1: `admin/build.gradle.kts`에 의존성 추가**

```kotlin
    implementation(project(":module:interest"))
```

- [ ] **Step 2: `InterestServiceTest.kt`에 병합 실패 테스트 추가**

```kotlin
    Given("admin이 관심사를 병합할 때") {
        val from = Interest(ko = "하이킹")
        val to = Interest(ko = null, en = "Hiking")

        When("from에만 있는 언어값은 to로 백필되고, from은 삭제된다") {
            every { interestRepo.findById(7L) } returns from
            every { interestRepo.findById(5L) } returns to
            every { interestRepo.save(match { it === to }) } returns to
            every { memberInterestRepo.findByInterestId(7L) } returns listOf(MemberInterest(1L, 7L))
            every { memberInterestRepo.findByInterestId(5L) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { interestRepo.delete(match { it === from }) } returns Unit

            service.merge(fromId = 7L, toId = 5L)

            Then("to.ko가 백필되고 from은 삭제된다") {
                to.ko shouldBe "하이킹"
                verify { interestRepo.delete(match { it === from }) }
            }
        }

        When("같은 회원이 from/to를 둘 다 가지고 있으면 중복 생성하지 않는다") {
            every { interestRepo.findById(7L) } returns from
            every { interestRepo.findById(5L) } returns to
            every { interestRepo.save(match { it === to }) } returns to
            every { memberInterestRepo.findByInterestId(7L) } returns listOf(MemberInterest(1L, 7L))
            every { memberInterestRepo.findByInterestId(5L) } returns listOf(MemberInterest(1L, 5L))
            val saved = slot<List<MemberInterest>>()
            every { memberInterestRepo.saveAll(capture(saved)) } answers { firstArg() }
            every { interestRepo.delete(any()) } returns Unit

            service.merge(fromId = 7L, toId = 5L)

            Then("추가로 저장되는 MemberInterest가 없다") {
                saved.captured shouldBe emptyList()
            }
        }
    }
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

```bash
./gradlew :module:interest:test --tests "*InterestServiceTest*" --console=plain
```
Expected: FAIL — `merge` 메서드 없음.

- [ ] **Step 4: `InterestService.merge` 구현**

`InterestService.kt`에 추가:
```kotlin
    fun merge(fromId: Long, toId: Long) {
        require(fromId != toId) { "fromId and toId must differ" }
        val from = interestRepo.findById(fromId) ?: throw IllegalArgumentException("interest not found: $fromId")
        val to = interestRepo.findById(toId) ?: throw IllegalArgumentException("interest not found: $toId")

        Interest.LOCALE_FIELDS.forEach { field ->
            if (to.get(field) == null && from.get(field) != null) {
                to.set(field, from.get(field))
            }
        }
        interestRepo.save(to)

        val fromMembers = memberInterestRepo.findByInterestId(fromId)
        val toMemberIds = memberInterestRepo.findByInterestId(toId).map { it.memberId }.toSet()
        val toRepoint = fromMembers.filter { it.memberId !in toMemberIds }
        if (toRepoint.isNotEmpty()) {
            memberInterestRepo.saveAll(toRepoint.map { MemberInterest(it.memberId, toId) })
        }
        if (fromMembers.isNotEmpty()) {
            memberInterestRepo.deleteAll(fromMembers)
        }

        interestRepo.delete(from)
    }
```

(`com.langlez.core.LanglezException`을 쓰려면 `core` 의존이 필요하므로, 여기서는
`IllegalArgumentException`으로 두고 `AdminInterestController`에서 잡아 400으로 변환한다 — interest
모듈이 `core`를 직접 의존하지 않게 하려는 선택.)

- [ ] **Step 5: 테스트 재실행 → 통과 확인**

```bash
./gradlew :module:interest:test --tests "*InterestServiceTest*" --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: `AdminInterestController.kt` 작성**

```kotlin
package com.langlez.admin.api

import com.langlez.interest.application.InterestService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.Locale

@Controller
@RequestMapping("/admin/interests")
class AdminInterestController(private val interestService: InterestService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val locale = Locale.forLanguageTag("en")
        val items = if (!q.isNullOrBlank()) interestService.search(locale, q) else emptyList()
        model.addAttribute("query", q ?: "")
        model.addAttribute("items", items)
        return "admin/interests"
    }

    @PostMapping("/merge")
    fun merge(@RequestParam fromId: Long, @RequestParam toId: Long): String {
        interestService.merge(fromId, toId)
        return "redirect:/admin/interests"
    }
}
```

- [ ] **Step 7: `admin/interests.html` 템플릿 작성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{admin/layout :: header('관심사 관리')}"></head>
<body>
    <div class="app-container">
        <div th:replace="~{admin/layout :: sidebar('interests')}"></div>
        <main class="main-content">
            <div class="page-header">
                <div>
                    <h1 class="page-title">관심사 관리</h1>
                    <p class="page-subtitle">중복된 관심사를 검색해 병합합니다(영어 이름 기준 검색).</p>
                </div>
            </div>

            <form method="get" th:action="@{/admin/interests}" style="display:flex; gap:8px; margin-bottom:16px;">
                <input type="text" name="q" th:value="${query}" placeholder="영어로 검색 (예: Hiking)"/>
                <button type="submit">검색</button>
            </form>

            <form method="post" th:action="@{/admin/interests/merge}" style="display:flex; gap:8px; margin-bottom:16px;">
                <input type="number" name="fromId" placeholder="합칠 대상 ID (사라짐)" required/>
                <input type="number" name="toId" placeholder="남길 ID" required/>
                <button type="submit">병합</button>
            </form>

            <div class="table-container">
                <table class="admin-table">
                    <thead><tr><th>ID</th><th>이름</th></tr></thead>
                    <tbody>
                        <tr th:each="item : ${items}">
                            <td th:text="${item.id}">1</td>
                            <td th:text="${item.name}">Hiking</td>
                        </tr>
                        <tr th:if="${#lists.isEmpty(items)}">
                            <td colspan="2" style="text-align: center; color: var(--text-secondary);">검색 결과가 없습니다.</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </main>
    </div>
</body>
</html>
```

- [ ] **Step 8: `layout.html` 사이드바에 메뉴 링크 추가**

`layout.html`을 Read해서 기존 사이드바 `<a>` 링크들의 정확한 마크업 패턴을 확인한 뒤, 같은 패턴으로
`/admin/interests` 메뉴 링크를 추가한다(예: 기존 항목이
`<a th:href="@{/admin/reports}" th:classappend="${activeMenu == 'reports'} ? 'active' : ''">신고 이력</a>`
형태라면 동일한 형태로 `interests` 버전을 추가).

- [ ] **Step 9: 전체 컴파일 확인**

```bash
cd /Users/hj/project/langlez/server/main
./gradlew :module:admin:compileKotlin :app:api:compileKotlin --console=plain -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: 커밋**

```bash
git add module/interest/src/main/kotlin/com/langlez/interest/application/InterestService.kt \
        module/interest/src/test/kotlin/com/langlez/interest/application/InterestServiceTest.kt \
        module/admin
git commit -m "feat(admin): 관심사 검색/병합(백필 포함) 관리 메뉴 추가"
```

---

### Task 6: `matching` 모듈이 interest 모듈을 사용하도록 교체

**Files:**
- Modify: `module/matching/build.gradle.kts` — `implementation(project(":module:interest"))` 추가
- Modify: `module/matching/src/main/kotlin/com/langlez/matching/application/MatchingService.kt:87` 부근
- Test: `module/matching/src/test/kotlin/com/langlez/matching/application/MatchingServiceTest.kt`

**Interfaces:**
- Consumes: `InterestService.getMemberInterestIds(memberId: Long): Set<Long>`(Task 2)

- [ ] **Step 1: `matching/build.gradle.kts`에 의존성 추가**

```kotlin
    implementation(project(":module:interest"))
```

- [ ] **Step 2: `MatchingService.kt` 수정**

생성자에 `private val interestService: InterestService` 추가. 기존:
```kotlin
                val commonInterests = candidateProfile.interests.intersect(myProfile.interests).size
```
를:
```kotlin
                val commonInterests = interestService.getMemberInterestIds(candidateId)
                    .intersect(interestService.getMemberInterestIds(memberId)).size
```
로 교체. (매 후보마다 두 번 조회하는 게 아깝다면 `attemptMatch` 상단에서
`val myInterestIds = interestService.getMemberInterestIds(memberId)`로 한 번만 구해 재사용하고,
후보별로는 `interestService.getMemberInterestIds(candidateId)`만 호출하도록 정리.)

- [ ] **Step 3: `MatchingServiceTest.kt`의 관련 mock 교체**

기존 `profile(id, level, interests=...)` 헬퍼가 만드는 `Profile.interests` 참조를 제거하고,
`interestService` mock을 추가해 `every { interestService.getMemberInterestIds(any()) } returns emptySet()`을
기본으로 깔고, 공통 관심사 테스트 케이스에서만 `every { interestService.getMemberInterestIds(2L) } returns setOf(100L)` 등으로 구체 stub.

- [ ] **Step 4: 테스트 실행**

```bash
cd /Users/hj/project/langlez/server/main
./gradlew :module:matching:test --console=plain
```
Expected: BUILD SUCCESSFUL, 기존 25개 테스트 전부 통과.

- [ ] **Step 5: 커밋**

```bash
git add module/matching
git commit -m "refactor(matching): Profile.interests 대신 interest 모듈로 공통 관심사 계산"
```

---

### Task 7: `profile` 모듈에서 `Profile.interests` 제거 + interest 모듈로 표시 위임

**Files:**
- Modify: `module/profile/build.gradle.kts` — `implementation(project(":module:interest"))` 추가
- Modify: `module/profile/src/main/kotlin/com/langlez/profile/domain/Profile.kt` — `interests` 필드 제거
- Modify: `module/profile/src/main/kotlin/com/langlez/profile/api/ProfileRequest.kt` — `interests` 필드 제거
- Modify: `module/profile/src/main/kotlin/com/langlez/profile/api/ProfileResponse.kt` — `interests`를 interest 모듈 조회로 교체
- Modify: `module/profile/src/main/kotlin/com/langlez/profile/application/ProfileService.kt` — `interests` 처리 로직 제거, 조회 시 interest 모듈 호출
- Modify: `module/profile/src/main/kotlin/com/langlez/profile/api/ProfileController.kt` — 응답 조립 시 locale 전달
- Test: `module/profile/src/test/kotlin/com/langlez/profile/application/ProfileServiceTest.kt`

**Interfaces:**
- Consumes: `InterestService.getMemberInterests(memberId, locale): List<InterestView>`(Task 2)

- [ ] **Step 1: `profile/build.gradle.kts`에 의존성 추가**

```kotlin
    implementation(project(":module:interest"))
```

- [ ] **Step 2: `Profile.kt`에서 `interests` 필드와 관련 import 제거**

`@ElementCollection`/`@CollectionTable` 블록(`var interests: MutableSet<String> = mutableSetOf()`) 삭제.

- [ ] **Step 3: `ProfileRequest.kt`/`ProfileResponse.kt`에서 `interests: Set<String>` 필드 제거**

`ProfileResponse.kt`의 `interests = profile.interests.toSet()` 라인들도 제거(생성자에서 그 필드 자체를
없애므로).

- [ ] **Step 4: `ProfileService.kt` 수정**

`updateProfile`에서 `request.interests?.let { profile.interests = it.toMutableSet() }` 줄 제거.
프로필 조회 메서드(`getProfileDetail` 등)에서 응답 조립 시 `interestService.getMemberInterests(targetMemberId, locale)`
결과를 별도로 넘겨 컨트롤러/응답 DTO가 조합하도록 시그니처 조정(정확한 조회 메서드 이름은 기존
`ProfileService.kt`를 열어서 확인 후 그 구조에 맞춰 최소 변경으로 반영).

- [ ] **Step 5: 관련 테스트 수정 후 실행**

```bash
cd /Users/hj/project/langlez/server/main
./gradlew :module:profile:test --console=plain
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 전체 프로젝트 컴파일 + 관련 모듈 테스트**

```bash
./gradlew compileKotlin compileTestKotlin --console=plain -q
./gradlew :module:interest:test :module:profile:test :module:matching:test :module:admin:test --console=plain
```
Expected: 전부 BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add module/profile
git commit -m "refactor(profile): Profile.interests 제거, 관심사는 interest 모듈로 완전히 이관"
```

---

## Self-Review 체크 결과

- **스펙 커버리지**: 검색(Task 4) / FULLTEXT(Task 3) / admin 병합+백필(Task 5) / profile-matching 교체
  (Task 6, 7) — 스펙의 모든 섹션에 대응하는 태스크 있음.
- **플레이스홀더**: 없음. 단, Task 5 Step 8(layout.html 메뉴 추가)과 Task 7 Step 4(ProfileService 정확한
  메서드 조정)는 기존 파일을 열어봐야 정확한 위치/시그니처를 알 수 있어 "확인 후 반영"으로 남겨둠 —
  이 두 곳은 실행 에이전트가 해당 파일을 먼저 Read해서 실제 구조에 맞춰 최소 diff로 반영할 것.
- **타입 일관성**: `InterestView(id, name)`, `LocaleField.of(Locale): String`, `InterestService`의
  메서드 시그니처가 Task 2~7 전체에서 동일하게 사용됨.
