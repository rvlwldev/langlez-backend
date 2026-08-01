# MemberRepositoryImpl2 캐시 리뷰 및 수정안

## 전제

- **미배포, 개발 초기.** 하위 호환·무중단 전환을 고려하지 않는다.
- Spring `CacheManager` / `@Cacheable` 은 **폐기**한다. `core` 의 `Cache` / `CacheProvider` 로 전면 교체한다. 포트 설계와 어댑터 구현은 [`CacheProvider.md`](./CacheProvider.md) 참고.
- `MemberRepositoryImpl` (구) 은 **`MemberRepositoryImpl2` 에 위임**한다. 캐시 구현을 하나로 만든다. 아직 리팩토링 안 된 모듈은 계속 `MemberRepository` 인터페이스를 쓰면 된다.
- 검증 환경: Spring Boot 3.5.8 / Spring Data Redis 3.5.6 / spring-tx 6.2.14.

## 아키텍처 변경으로 사라지는 문제

전면 교체 + 위임 구조가 아래를 **구조적으로** 없앤다. 개별 수정이 불필요하다.

| 기존 문제 | 왜 사라지나 |
|---|---|
| `Long::class.java` 가 프리미티브 `long.class` | 포트의 reified 헬퍼가 `javaObjectType` 을 강제. 게다가 키가 `String` |
| `transactionAware()` + 수동 `afterCommit` 이중 지연 | 지연 계층이 하나뿐. `RedisCache.write` 가 단독 책임 |
| `@Cacheable` self-invocation | 어노테이션을 안 쓴다 |
| 구/신 impl 캐시 이름 충돌 (`Member` vs `String`) | 캐시 구현이 하나. 구 impl 이 Impl2 에 위임 |
| `evictLegacyCaches` 필요 | 위와 동일. 구 impl 전용 캐시가 없다 |
| `disableCachingNullValues` 런타임 설정 | 포트가 `put(key, value: Any)` 로 컴파일 타임에 막는다 |
| 벌크 조회 N 왕복 | `getMany`(MGET) / `putMany`(파이프라인) |

## 남은 문제

| # | 심각도 | 문제 | 상태 |
|---|---|---|---|
| 1 | **빌드** | `MemberRepositoryImpl.kt:40` 인자 순서 오류 | ❌ |
| 2 | 치명 | Redis 역직렬화 타입 정보 없음 → `Member` 가 `LinkedHashMap` | ❌ |
| 3 | 치명 | `delete(ids)` / `delete(members)` 가 evict 를 실행하지 않음 | ❌ |
| 4 | 높음 | 삭제 경로가 곧 지울 데이터를 캐시에 채운다 | ❌ |
| 5 | 높음 | `findAll(usernames)` 에 stale 검증 누락 | ❌ |
| 6 | 높음 | 역인덱스 값을 `Long` 으로 저장 (읽기는 `String`) | 🔶 읽기만 전환됨 |
| 7 | 중간 | `MemberService` 의 잘못된 캐시 이름 → evict 무효 + 메모리 누수 | ❌ |
| 8 | 중간 | 죽은 import / 죽은 변수 / 죽은 메서드 | ❌ |
| 9 | 중간 | 구 `MemberRepositoryImpl.findByUsernames` 의 미스 계산이 항상 빈 리스트 | ❌ (위임으로 소멸) |

---

## 1. 빌드 실패

`MemberRepositoryImpl.kt:40`

```kotlin
override fun findByProvider(type: MemberProvider, id: String): Member? = jpa.findByProviderAndProviderId(id, type)
```

`MemberJpaRepository.findByProviderAndProviderId(provider: MemberProvider, providerId: String)` 인데 `(String, MemberProvider)` 를 넘긴다. 컴파일이 안 된다.

C 의 위임 구조로 가면 이 메서드가 `delegate.find(type, id)` 로 바뀌므로 같이 해소된다.

---

## 2. Redis 역직렬화 타입 정보 부재 (치명 — 최대 차단 요소)

`ResilientCacheConfiguration.kt:41-42`

```kotlin
val serializer = RedisSerializationContext.SerializationPair
    .fromSerializer(GenericJackson2JsonRedisSerializer(objectMapper))
```

`GenericJackson2JsonRedisSerializer(ObjectMapper)` 생성자는 default typing 을 **켜지 않는다.** 바이트코드상 넘겨받은 매퍼에 이미 켜져 있는지 *감지*만 한다.

```
39: invokedynamic #77   // ()Ljava/util/function/Supplier;
44: invokestatic  #81   // Lazy.of
47: putfield      #87   // Field defaultTypingEnabled   ← 감지일 뿐
```

주입되는 `objectMapper` 빈(`common/.../JacksonConfiguration.kt`)에 `activateDefaultTyping` 이 없다.

결과: `Member` 가 `@class` 없이 직렬화되고 `Object.class` 로 역직렬화되어 **`LinkedHashMap`** 이 나온다. `type.isInstance(value)` 가 false 라 `IllegalStateException` → `ResilientCache.runGuarded` 가 잡아 `markRedisDown()` → **캐시 계층 전체가 로컬 전용으로 전환.**

**이게 안 고쳐지면 `member` 캐시는 Redis 에서 단 한 번도 히트하지 않는다.** 다른 걸 아무리 고쳐도 소용없다. 수정 코드는 `CacheProvider.md` 4장 `cacheSerializer` 참고.

> `objectMapper.copy()` 필수. 공용 빈에 typing 을 켜면 모든 API 응답에 `@class` 가 붙는다.

---

## 3. `delete(ids)` / `delete(members)` 가 evict 를 실행하지 않는다 (치명)

**현재 코드** — `MemberRepositoryImpl2.kt:74, 76, 94`

```kotlin
override fun delete(ids: List<Long>) = jpa.deleteAllById(ids).also { ids.forEach(::evictCaches) }
override fun delete(members: Collection<Member>) = members.map { member -> member.id }.run(::delete)

private fun evictCaches(id: Long) = find(id)?.run(::evictCaches)
```

`deleteAllById` 이후 같은 영속성 컨텍스트에서 `find(id)` → `jpa.findByIdOrNull(id)` 를 호출하면, 엔티티가 `DELETED` 상태라 Hibernate 의 `DefaultLoadEventListener` 가 `REMOVED_ENTITY_MARKER` 를 거쳐 **null 을 반환한다.** `?.run` 이 스킵되어 evict 가 0회다.

`delete(members)` 는 이미 손에 쥔 `Member` 를 id 로 버리고 이 고장난 경로로 보낸다.

쿼리 수도 나쁘다. `JpaRepository.deleteAllById` 의 기본 구현은 `for (id in ids) deleteById(id)` 라 **N select + N delete** 다.

**수정:** `Member` 를 1쿼리로 확보한 뒤 `deleteAll` 로 넘긴다. `evictCaches(id: Long)` 오버로드는 삭제.

---

## 4. 삭제 경로가 곧 지울 데이터를 캐시에 채운다 (높음)

**현재 코드** — `MemberRepositoryImpl2.kt:70-72`

```kotlin
override fun delete(id: Long) {
    find(id)?.let(::delete)
}
```

`find(id)` 는 캐시 미스 시 `updateCaches` 를 부른다 → 4키 PUT → 곧바로 `delete` 가 4키 EVICT. 왕복 8회가 순수 낭비다.

정확성 문제도 있다. evict 대상 키(username / email / provider)는 **DB 의 현재 값**이어야 한다. 캐시에 낡은 `Member` 가 있으면 낡은 키로 evict 해서, 진짜 현재 키가 인덱스에 그대로 남는다.

**수정:** 삭제 경로는 캐시를 읽지도 쓰지도 않는다. `jpa` 로 직접 조회한다.

---

## 5. `findAll(usernames)` 에 stale 검증이 빠졌다 (높음)

**현재 코드** — `MemberRepositoryImpl2.kt:59-62`

```kotlin
val cached = distinct.mapNotNull { username ->
    cache.get(username, String::class.java)?.toLongOrNull()?.let(::find)   // ← takeIf 없음
}
val missing = distinct - cached.mapTo(mutableSetOf()) { it.username }
```

단건 `find(username)` 에는 `?.takeIf { it.username == username }` 이 있는데 여기엔 없다.

**실패 시나리오:** 회원 id=1 이 `"a"` → `"b"` 로 개명. 인덱스에 옛 키 `"a" -> "1"` 이 남아있다. `findAll(listOf("a"))`:

1. `get("a")` → `"1"` → `find(1)` → 최신 Member(username=`"b"`) 반환
2. 검증이 없으므로 `cached = [Member("b")]`
3. `missing = {"a"} - {"b"} = {"a"}` → DB 에서 진짜 `"a"` 주인을 가져옴
4. **요청하지 않은 `"b"` 가 결과에 섞여 나간다.** `"a"` 가 다른 회원 것이면 두 명이 반환된다.

`RecommendationService.kt:106` 이 `associateBy { it.username }` 로 받으므로 조용히 오염된다.

**수정 방침:** 벌크는 역인덱스를 거치지 않고 DB `IN` 쿼리 1회로 처리한다 (아래 A). 검증 문제가 함께 사라진다.

> 왜 검증이 필요한가: `updateCaches` 는 새 키만 `put` 한다. 개명하면 옛 키가 TTL 동안 남는다. 옛 값을 알아낼 방법이 없다 — `MemberService.kt:91` 이 발행하는 `MemberUsernameChangedEvent(id, member.username)` 의 `username` 은 이미 **새** 값이고, `MemberEventListener.kt:45-48` 의 주석 처리된 evict 는 되살려도 동작하지 않는다. 그래서 "역인덱스로 찾은 회원이 실제 그 키의 주인인지" 조회 시점에 검증하는 방식을 택했다.

### 인덱스에 `Member` 본체를 저장하면 안 되는 이유

`get<Member>(username)` 으로 한 번에 끝내면 왕복이 줄지만 **이 검증이 깨진다.**

- id 저장: `member-username["a"] = "1"` → `find(1)` 이 **최신** Member(username=`"b"`) 반환 → `takeIf` 불일치 → DB 폴백. 정상.
- Member 저장: `member-username["a"] = Member(username="a")` (개명 시점의 낡은 스냅샷) → `takeIf { it.username == "a" }` **통과** → 낡은 회원 반환.

본체의 단일 출처가 `member` 캐시 하나여야 자가 치유가 성립한다.

---

## 6. 역인덱스 값을 `Long` 으로 저장한다 (높음)

**현재 코드**

읽는 쪽 (`MemberRepositoryImpl2.kt:26`):

```kotlin
?.get(username, String::class.java)?.toLongOrNull()   // String 으로 읽는다
```

쓰는 쪽 (`MemberRepositoryImpl2.kt:82`):

```kotlin
getCache("member-username")?.put(member.username, member.id)   // Long 을 넣는다
```

읽기/쓰기 타입이 어긋나 캐시 히트마다 `IllegalStateException` → 서킷 오픈.

### `String` 이어야 하는 이유

`java.lang.Long` 은 **final** 이라 Jackson default typing 이 `@class` 를 붙이지 않는다. Redis 에서 읽으면 값 크기에 따라 `Integer` 로 복원되고, 로컬 Caffeine 은 직렬화를 안 거쳐 진짜 `Long` 을 돌려준다. **같은 코드가 Redis 냐 로컬이냐에 따라 다르게 동작한다.**

`String` 은 양쪽에서 동일하다. 타입 모호성 자체가 사라진다.

> 별개로 Kotlin `Long::class.java` 는 프리미티브 `long.class` 로 컴파일되고(`getstatic java/lang/Long.TYPE`), `Class.isInstance` 는 프리미티브 Class 에 대해 항상 false 다. 새 포트의 reified 헬퍼가 `javaObjectType` 을 쓰므로 이 함정은 구조적으로 막힌다.

**수정:** `put(member.username, member.id.toString())`

---

## 7. `MemberService` 의 잘못된 캐시 이름 (중간)

`MemberService.kt:86`

```kotlin
.apply { cacheManager.getCache(username)?.evict(username) }
```

캐시 **이름** 자리에 username 을 넣었다. 실제 역인덱스인 `member-username` 은 건드리지도 못한다.

게다가 provider 구현은 이름마다 캐시 인스턴스를 새로 만들어 내부 `ConcurrentHashMap` 에 영구 보관한다. **username 하나당 캐시 하나가 쌓이는 메모리 누수다.**

**수정:** 이 줄과 `cacheManager` 생성자 파라미터, `import org.springframework.cache.CacheManager` 를 삭제. 옛 키 처리는 `find(username)` 의 `takeIf` 검증이 담당한다.

---

## 8. 죽은 코드

- `MemberRepositoryImpl2.kt:10-11` — `TransactionSynchronization`, `TransactionSynchronizationManager as tx` import. 수동 동기화 제거 후 미사용
- `MemberRepositoryImpl2.kt:87` — `evictCaches` 안의 `val id = member.id.toString()`. 미사용
- `MemberRepositoryImpl2.kt:94` — `evictCaches(id: Long)`. 고장난 데다 수정 후 호출자 없음
- `MemberRepositoryImpl2.kt:65` — `findAllByUsernameIn(missing.toList())`. 시그니처가 `Collection<String>` 이라 `.toList()` 불필요
- `MemberEventListener.kt:22, 45-48` — 미사용 `cacheManager` 파라미터, 주석 처리된 `onUsernameChangedAndOffline`

## 9. 구 impl 의 `findByUsernames` 버그 (위임으로 소멸)

`MemberRepositoryImpl.kt:55`

```kotlin
val cachedNames = cached.map { it.username }.toSet()
val misses = cachedNames.filterNot { names.contains(it) }   // 항상 빈 리스트
```

`cachedNames` 는 `names` 의 부분집합이므로 `filterNot { names.contains(it) }` 는 **항상 비어 있다.** 캐시 미스분을 절대 조회하지 못한다. `names.filterNot { cachedNames.contains(it) }` 여야 했다.

C 의 위임 구조로 가면 이 메서드가 통째로 사라진다.

---

## 수정 코드

### A. `MemberRepositoryImpl2.kt`

```kotlin
package com.langlez.member.infrastructure

import com.langlez.core.CacheProvider
import com.langlez.core.get
import com.langlez.core.getMany
import com.langlez.member.application.MemberRepository2
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * 캐시 구성
 *
 * - `member`          : id -> Member (본체, 단일 출처)
 * - `member-username` : username -> id 문자열
 * - `member-email`    : email -> id 문자열
 * - `member-provider` : "provider:providerId" -> id 문자열
 *
 * 역인덱스는 Member 본체를 중복 저장하지 않고 id 만 들고 있다. 그래야 옛 키가 남았을 때
 * 최신 본체를 가져와 주인이 맞는지 검증할 수 있다. 본체를 저장하면 개명 시점의 낡은
 * 스냅샷이 검증을 그대로 통과해버린다.
 *
 * 역인덱스 값이 Long 이 아니라 String 인 이유: Long 은 final 이라 Jackson 이 타입 정보를
 * 남기지 않는다. Redis 는 Integer, 로컬 Caffeine 은 Long 을 돌려줘 읽는 쪽 타입이 갈린다.
 *
 * 트랜잭션 처리는 하지 않는다. `RedisCache.write` 가 커밋 전 즉시 무효화 + afterCommit
 * 반영을 책임진다. 여기서 또 미루면 afterCommit 실행 중 등록된 동기화가 유실된다.
 * (CacheProvider.md 3장)
 */
@Repository
class MemberRepositoryImpl2(
    private val jpa: MemberJpaRepository,
    caches: CacheProvider,
) : MemberRepository2 {

    // CacheProvider.get 은 non-null 이다. Redis 장애는 ResilientCache 가 로컬 폴백으로
    // 흡수하므로 "캐시를 못 얻는" 상태가 없다. 호출부에 `?.` 가 번지지 않는다.
    private val memberCache = caches.get("member")
    private val emailIndex = caches.get("member-email")
    private val usernameIndex = caches.get("member-username")
    private val providerIndex = caches.get("member-provider")

    override fun save(member: Member): Member = jpa.save(member).also(::updateCaches)

    override fun find(id: Long): Member? =
        memberCache.get<Member>(id)
            ?: jpa.findByIdOrNull(id)?.also(::updateCaches)

    override fun find(username: String): Member? =
        usernameIndex.get<String>(username)?.toLongOrNull()
            // 역인덱스는 개명 시 옛 키가 남는다. 실제 주인인지 검증하고 아니면 미스로
            // 취급한다. DB 폴백이 올바른 매핑으로 덮어쓴다.
            ?.let(::find)?.takeIf { it.username == username }
            ?: jpa.findByUsername(username)?.also(::updateCaches)

    override fun find(provider: MemberProvider, id: String): Member? =
        providerIndex.get<String>(providerKey(provider, id))?.toLongOrNull()
            ?.let(::find)?.takeIf { it.provider == provider && it.providerId == id }
            ?: jpa.findByProviderAndProviderId(provider, id)?.also(::updateCaches)

    override fun findByEmail(email: String): Member? =
        emailIndex.get<String>(email)?.toLongOrNull()
            ?.let(::find)?.takeIf { it.email == email }
            ?: jpa.findByEmail(email)?.also(::updateCaches)

    /**
     * MGET 1회로 히트분을 걷고, 미스만 DB 1쿼리로 채운 뒤 파이프라인 1회로 적재한다.
     * 왕복이 N + 4N 에서 3 으로 줄어든다.
     *
     * 미스분은 본체 캐시만 채운다. `updateCaches` 를 쓰면 건당 4 SET 이라 벌크에서
     * 왕복이 폭증한다. 역인덱스는 단건 조회 시 채워진다.
     */
    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()

        val distinct = ids.toSet()
        val cached = memberCache.getMany<Member>(distinct)
        // getMany 는 원본 키 객체를 그대로 돌려주므로 Long 으로 바로 비교된다.
        // 다만 cached.keys 는 Set<Any> 라 `distinct - cached.keys` 는 타입이 안 맞는다.
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = jpa.findAllById(missing)
        memberCache.putMany(loaded.associateBy { it.id })

        return cached.values + loaded
    }

    /**
     * 캐시를 거치지 않는다. 역인덱스를 타면 이름당 왕복 2회(인덱스 + 본체)인데,
     * stale 검증까지 필요해 실패분은 결국 DB 로 간다. 유니크 인덱스가 걸린
     * `IN` 쿼리 한 번이 더 빠르고 정확하다.
     *
     * 적재도 하지 않는다. 벌크 결과가 곧바로 단건 조회로 이어진다는 보장이 없는데
     * 건당 SET 비용은 확정이기 때문. 읽기는 캐시를 틀리게 만들지 않으므로 안전하다.
     */
    override fun findAll(usernames: Collection<String>): List<Member> =
        if (usernames.isEmpty()) emptyList() else jpa.findAllByUsernameIn(usernames.toSet())

    // 커서 페이징. 캐시 대상이 아니다. 구 impl 위임에 필요하다 (AdminService.kt:36)
    override fun findAll(size: Int, cursor: Long?): List<Member> = PageRequest.of(0, size).let { page ->
        if (cursor == null) jpa.findAllByOrderByIdDesc(page) else jpa.findByIdLessThanOrderByIdDesc(cursor, page)
    }

    override fun count(): Long = jpa.count()

    /**
     * 삭제 경로는 캐시를 읽지도 쓰지도 않는다.
     *
     * `find(id)` 를 쓰면 미스 시 4키를 채운 뒤 곧바로 4키를 evict 하는 낭비가 생긴다.
     * 그리고 evict 대상 키(username / email / provider)는 DB 의 현재 값이어야 정확하다.
     * 캐시에 낡은 Member 가 있으면 낡은 키를 지워서 진짜 키가 인덱스에 남는다.
     */
    override fun delete(id: Long) {
        jpa.findByIdOrNull(id)?.let(::delete)
    }

    // deleteAllById 는 기본 구현이 id 하나당 select + delete 라 N+N 쿼리다.
    // 엔티티를 1쿼리로 확보하고 deleteAll 로 넘긴다 (Hibernate JDBC 배치 적용 가능).
    override fun delete(ids: List<Long>) = delete(jpa.findAllById(ids))

    override fun delete(member: Member) = jpa.delete(member).also { evictCaches(member) }

    override fun delete(members: Collection<Member>) {
        if (members.isEmpty()) return
        jpa.deleteAll(members)
        // 이미 Member 를 들고 있다. id 로 되돌리면 evict 키를 다시 찾을 수 없다.
        members.forEach(::evictCaches)
    }

    private fun updateCaches(member: Member) {
        memberCache.put(member.id, member)
        // 역인덱스 "값"은 String 이어야 한다. find() 의 get<String>() 과 짝이다.
        // Long 을 넣으면 Redis 는 Integer, 로컬 Caffeine 은 Long 을 돌려준다.
        // (키는 Any 여도 된다. 구현체가 toString() 으로 인코딩한다)
        val id = member.id.toString()
        emailIndex.put(member.email, id)
        usernameIndex.put(member.username, id)
        providerIndex.put(providerKey(member.provider, member.providerId), id)
    }

    private fun evictCaches(member: Member) {
        memberCache.evict(member.id)
        emailIndex.evict(member.email)
        usernameIndex.evict(member.username)
        providerIndex.evict(providerKey(member.provider, member.providerId))
    }

    private fun providerKey(provider: MemberProvider, id: String): String = "$provider:$id"
}
```

### B. `MemberRepository.kt` — `MemberRepository2` 에 메서드 추가

```kotlin
interface MemberRepository2 {
    fun save(member: Member): Member

    fun find(id: Long): Member?
    fun find(username: String): Member?
    fun find(provider: MemberProvider, id: String): Member?
    fun findByEmail(email: String): Member?
    fun findAll(ids: Collection<Long>): List<Member>
    fun findAll(usernames: Collection<String>): List<Member>

    // 커서 페이징. AdminService.kt:36 이 쓴다. 구 impl 위임에 필요하다.
    fun findAll(size: Int, cursor: Long?): List<Member>

    fun count(): Long

    fun delete(id: Long)
    fun delete(ids: List<Long>)
    fun delete(member: Member)
    fun delete(members: Collection<Member>)
}
```

### C. `MemberRepositoryImpl.kt` — Impl2 에 위임

구 impl 의 캐시 코드를 새 포트로 이식하지 마라. **구현을 하나로 만든다.**

```kotlin
@Repository
class MemberRepositoryImpl(
    private val delegate: MemberRepository2,
) : MemberRepository {

    override fun save(member: Member): Member = delegate.save(member)

    override fun findById(id: Long): Member? = delegate.find(id)
    override fun findByIds(ids: List<Long>): List<Member> = delegate.findAll(ids)
    override fun findByEmail(email: String): Member? = delegate.findByEmail(email)
    override fun findByUsername(username: String): Member? = delegate.find(username)
    override fun findByUsernames(usernames: List<String>): List<Member> = delegate.findAll(usernames)
    override fun findByProvider(type: MemberProvider, id: String): Member? = delegate.find(type, id)
    override fun findAll(size: Int, cursor: Long?): List<Member> = delegate.findAll(size, cursor)

    override fun countAll(): Long = delegate.count()
    override fun deleteAll(members: List<Member>) = delegate.delete(members)
}
```

`@Cacheable` / `@Caching` / `@CacheEvict` / `ConcurrentMapCache` import 전부 제거. 1번(빌드 오류)과 9번(미스 계산 버그)이 함께 사라진다.

### D. `MemberService.kt`

```kotlin
@Service
class MemberService(
    private val repo: MemberRepository,
    private val creator: MemberCreator,
    // cacheManager 제거. 아래 updateUsername 의 유일한 사용처가 버그였다.
    private val tracker: MemberOnlineTracker,
    private val publisher: ApplicationEventPublisher
) {

    @Transactional
    fun updateUsername(id: Long, newUsername: String): Member {
        if (repo.findByUsername(newUsername) != null) throw LanglezException(409, "member.username.duplicated")

        val member = (repo.findById(id) ?: throw LanglezException(404, "member.not-found"))
            .apply { tracker.toOnline(username) }
            // 삭제된 줄:
            //   .apply { cacheManager.getCache(username)?.evict(username) }
            //
            // 캐시 "이름" 자리에 username 을 넘기고 있었다. 실제 역인덱스인
            // member-username 은 건드리지도 못한다.
            //
            // 게다가 provider 구현은 이름마다 캐시 인스턴스를 새로 만들어 내부 맵에
            // 영구 보관한다. username 하나당 캐시 하나가 쌓이는 메모리 누수였다.
            //
            // 옛 역인덱스 키 처리는 MemberRepositoryImpl2.find(username) 의
            // takeIf 검증이 담당한다.
            .apply { changeUsername(newUsername) }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(409, "member.username.duplicated", e) }
            .also { publisher.publishEvent(MemberUsernameChangedEvent(id, member.username)) }
    }
}
```

`import org.springframework.cache.CacheManager` 도 제거.

### E. `MemberEventListener.kt`

주석 처리된 `onUsernameChangedAndOffline` 블록(45-48행)과 미사용 `cacheManager` 생성자 파라미터를 삭제한다.

되살릴 수 없는 코드다. `MemberService.kt:91` 이 발행하는 `MemberUsernameChangedEvent(id, member.username)` 의 `username` 은 이미 **새** 값이다. `event.oldUsername` 에 해당하는 값이 애초에 이벤트에 없다.

---

## 성능 비교

| 연산 | 현재 | 수정 후 |
|---|---|---|
| `delete(id)` | 캐시 4 PUT + 4 EVICT + DB 1 select + 1 delete | DB 1 select + 1 delete + 4 EVICT |
| `delete(ids)` (N건) | DB N select + N delete, **evict 0회** | DB 1 select + N delete(배치), evict 정상 |
| `delete(members)` (N건) | id 변환 후 위와 동일 | DB N delete(배치), evict 정상 |
| `findAll(ids)` 전체 히트 (N건) | GET × N | **MGET × 1** |
| `findAll(ids)` 전체 미스 (N건) | GET × N + DB 1쿼리 + SET × 4N | MGET × 1 + DB 1쿼리 + 파이프라인 × 1 |
| `findAll(usernames)` (N건) | GET × 2N + DB 1쿼리 + SET × 4N | DB 1쿼리 |
| 단건 읽기 미스 | DB 1쿼리 + SET × 4 | 동일 |
| `save` | DB 1 + SET × 4 | 동일 |

## 남겨두는 것 (의도적)

- **Negative caching 없음.** 존재하지 않는 키 조회는 매번 DB 를 친다. 캐시 관통 경로지만 회원 조회는 대부분 인증된 id 기반이라 임의 키 폭격에 노출되지 않는다. 관측되면 그때 짧은 TTL 의 마커를 넣는다. (포트가 `value: Any` 라 null 저장 자체가 불가능하므로 별도 마커 값이 필요하다)
- **역인덱스 stale 키 자체는 남는다.** `takeIf` 검증으로 무해화만 하고 즉시 삭제하지 않는다. TTL 이면 사라진다. 옛 username 을 알아내려면 이벤트 스키마 변경이나 pre-image 조회가 필요한데 검증 대비 이득이 없다.
- **인스턴스 간 캐시 스탬피드 방지 없음.** 만료 jitter 로만 완화한다.
- **`deleteAllInBatch` 안 씀.** 단일 DELETE 문으로 줄일 수 있지만 `@Version` 낙관적 락 검사와 cascade 를 건너뛴다. `Member` 에 `@Version` 이 있으므로 `deleteAll` 유지. 대량 삭제가 병목이 되면 그때 바꾼다.
- **`findAll(usernames)` 는 캐시를 안 쓴다.** 왕복 2배 + stale 검증 필요 + 적재 비용 확정. DB 1쿼리가 낫다.

## 검증 방법

1. **2번** — `save` → 새 JVM 에서 `find(id)`. `Member` 가 정상 복원되고 로그에 `Redis operation failed` 가 없어야 한다
2. **트랜잭션** — `@Transactional` 안에서 `save` → 커밋 후 Redis 에 `member::{id}` 존재. 롤백 시 키 부재
3. **트랜잭션 (intra-tx)** — `@Transactional` 안에서 `save` → 같은 트랜잭션 안 `find(id)` 가 **새 값**을 반환해야 한다 (커밋 전 즉시 무효화가 동작하는지)
4. **3/4번** — `save` → `find` (캐시 적재) → `delete(listOf(id))` → `find` 가 null. 쿼리 로그에 select 1 + delete 1 만
5. **5번** — `save(username="a")` → `findAll(listOf("a"))` → `changeUsername("b")` → `save` → `findAll(listOf("a"))` 가 빈 리스트. `"b"` 가 섞이면 안 된다
6. **6번** — `save` → `find(username)` 두 번. 두 번째가 DB 를 안 치고 예외도 없어야 한다
7. **벌크** — `findAll(ids)` 50건 호출 시 Redis 명령이 MGET 1회여야 한다 (`MONITOR` 로 확인)

`MemberIntegrationTest` 에 케이스로 추가하는 게 가장 싸다.

## 권장 순서

| 순서 | 작업 | 이유 |
|---|---|---|
| 1 | `CacheProvider.md` 1~4장 — 포트 + 어댑터 + **직렬화** | 직렬화가 깨진 상태면 나머지를 고쳐도 Redis 히트가 0이다 |
| 2 | 트랜잭션 지연 (`RedisCache.write`) | `transactionAware()` 를 잃었으므로 이게 없으면 롤백 시 캐시 오염 |
| 3 | `MemberRepositoryImpl2` 전환 (A, B) | 3·4·5·6·8번 해소 |
| 4 | `MemberRepositoryImpl` 위임 (C) | 1·9번 해소, 캐시 구현 일원화 |
| 5 | `MemberService` / `MemberEventListener` 정리 (D, E) | 7번 해소 |
| 6 | `profile` / `relationship` 전환 후 `@EnableCaching` 제거 | `CacheProvider.md` 6·10장 |
