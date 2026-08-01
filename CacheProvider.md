# 캐시 추상화 — Spring `CacheManager` 전면 교체 설계

## 전제

- **미배포, 개발 초기.** 하위 호환이나 무중단 전환을 고려하지 않는다.
- Spring `CacheManager` / `RedisCacheManager` / `@Cacheable` / `@Caching` / `@CacheEvict` 를 **전부 걷어낸다.**
- `core` 에 `Cache` / `CacheProvider` 포트를 정의하고 `infra:redis` 가 구현한다.
- **Redis 접근은 Redisson 하나로 통일한다.** `RedisConnectionFactory`(Lettuce) 직접 사용은 캐시 경로에서 없앤다. 이미 락 / 레이트리밋 / 블랙리스트가 Redisson 을 쓰고 있어 커넥션 풀이 이원화될 이유가 없다.
- 교체 사유: Spring `Cache` 인터페이스에 **multi-get / multi-set 이 없다.** 컬렉션 조회가 건당 왕복으로 쪼개져 인덱스 걸린 DB 1쿼리보다 느려진다.

| 방식 | N=50 왕복 | 추정 (동일 AZ) |
|---|---|---|
| Spring `Cache` 건당 `get` | 50 | 5~15ms |
| DB `IN` 쿼리 | 1 | 1~3ms |
| **MGET (`RBuckets.get`)** | **1** | **~0.2ms** |

---

## 1. 포트 (`core`)

`core/build.gradle.kts` 는 의존성이 하나도 없다(`plugins { kotlin.jvm }`). `Notificator`, `TokenBlacklist`, `FileStorage` 와 같은 순수 포트 모듈이므로 **Spring 타입도 Redisson 타입도 노출하면 안 된다.**

### `core/src/main/kotlin/com/langlez/core/cache/Cache.kt`

```kotlin
package com.langlez.core.cache

interface Cache {
    fun <T : Any> get(key: Any, type: Class<T>): T?
    fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T>

    fun put(key: Any, value: Any)
    fun <T : Any> putMany(entries: Map<out Any, T>)

    fun evict(key: Any)
    fun evictMany(keys: Collection<Any>)
}

inline fun <reified T : Any> Cache.get(key: Any): T? =
    get(key, T::class.javaObjectType)

inline fun <reified T : Any> Cache.getMany(keys: Collection<Any>): Map<Any, T> =
    getMany(keys, T::class.javaObjectType)
```

계약 세 가지. 인터페이스 본문에는 안 적더라도 이건 지켜야 한다.

1. **키는 `Any` 를 받고 구현체가 `toString()` 으로 인코딩한다.** 호출부에서 `id.toString()` 이 사라진다. 대신 **키의 `toString()` 이 안정적이고 유일해야 한다.** `Long`, `String`, enum, data class 는 안전하다. `toString()` 을 재정의하지 않은 일반 클래스는 `Object` 기본 구현(`ClassName@1b6d3586`)이 나가서 실행할 때마다 키가 달라진다. 예외도 안 나고 영구 미스가 된다. 복합 키는 문자열을 직접 조립해 넘길 것.
2. **`getMany` 반환 맵의 키는 인코딩된 문자열이 아니라 호출자가 넘긴 키 객체 그대로다.** 호출자가 원본 컬렉션과 바로 차집합을 낼 수 있어야 하기 때문. 미스는 맵에 포함되지 않는다. 센티널 없음.
3. **값은 non-null.** `disableCachingNullValues()` 를 타입으로 대체한다. 런타임 설정이 아니라 컴파일 타임 보장.

`javaObjectType` 이 핵심이다. Kotlin 에서 `T::class.java` 는 `Long`, `Int` 등에 대해 프리미티브 Class(`long.class`)를 돌려주고, `Class.isInstance` 는 프리미티브 Class 에 대해 항상 false 라 캐시 히트마다 미스로 떨어진다.

> **현재 코드 수정 필요:** `Cache.kt:14` 의 reified 헬퍼가 `get(key: String)` 로 좁혀져 있다. 인터페이스는 `Any` 인데 헬퍼만 `String` 이라 `members.get<Member>(id)`(Long 키)가 헬퍼를 못 탄다. `key: Any` 로 되돌릴 것.

### `core/src/main/kotlin/com/langlez/core/cache/CacheProvider.kt`

```kotlin
package com.langlez.core.cache

interface CacheProvider {
    fun getCache(name: String): Cache
}
```

`getCache` 는 **non-null** 이다. 구현체는 요청받은 이름의 캐시를 없으면 만들어서라도 돌려준다.

Spring `CacheManager.getCache` 는 `@Nullable` 이라 호출부마다 `?.` 나 조기 반환이 붙었는데, `ResilientCacheProvider` 는 실제로 null 을 반환할 수 없었다. 죽은 분기를 문법이 강제하던 문제를 없앤다.

### 설계 판단

| 항목 | 결정 | 근거 |
|---|---|---|
| 키 타입 `Any` + 구현체 `toString()` | 채택 | 호출부에서 `id.toString()` 이 사라진다. 단 `toString()` 안정성은 호출자 책임 |
| `getMany` 반환 키 = 원본 객체 | 채택 | 인코딩된 문자열을 돌려주면 호출자가 원본과 차집합을 못 낸다 |
| 값 non-null (`value: Any`) | 채택 | `disableCachingNullValues()` 를 타입으로 대체 |
| `getMany` 반환에 미스 미포함 | 채택 | 호출자가 차집합으로 미스 계산. 센티널 불필요 |
| `getCache(name): Cache` **non-null** | 채택 | 구현체가 null 을 반환할 수 없는데 문법이 분기를 강제하던 문제 제거 |
| reified 헬퍼 `javaObjectType` | 채택 | 프리미티브 함정의 근본 차단 |
| 어댑터 백엔드 = Redisson | 채택 | 락/레이트리밋/스트림이 이미 Redisson. 커넥션 풀 이원화 제거. 클러스터 MGET 슬롯 분할을 라이브러리가 처리 |

non-null 이므로 호출부가 이렇게 짧아진다.

```kotlin
private val members = caches.getCache("member")
```

Redis 가 죽어도 `ResilientCache` 가 로컬 Caffeine 으로 폴백하므로 "캐시를 못 얻는" 상태 자체가 없다. 저장소 장애는 `Cache` 내부에서 흡수하고, 최악의 경우 조회가 미스로 떨어져 호출자가 DB 를 탄다.

---

## 2. 잃는 것과 대체 방법

`RedisCacheManager` 를 버리면 아래를 직접 책임진다. **트랜잭션 지연이 가장 위험하다.**

| 기능 | 기존 제공 | 새 구현 (Redisson) |
|---|---|---|
| 키 프리픽스 | `CacheKeyPrefix.simple()` (`name::key`) | `"$name:$key"` 직접. **구분자가 콜론 1개로 바뀌었다** |
| 키 변환 | `ConversionService` | **불필요.** 어댑터가 `toString()` 으로 인코딩 |
| 값 직렬화 | `SerializationPair` + `RedisSerializer` | **Redisson `Codec`.** 5장 |
| TTL + jitter | `RedisCacheConfiguration` + custom `RedisCacheWriter` | `RBucket.set(value, Duration)` 로 직접 |
| MGET | 없음 | `RBuckets.get(vararg String)` |
| 파이프라인 MSET+TTL | 없음 | `RBatch` + `setAsync(value, Duration)` |
| null 값 정책 | `disableCachingNullValues()` | **타입으로 대체.** `value: Any` |
| **트랜잭션 지연** | `transactionAware()` | **직접 구현 필수.** 3장 |
| 서킷 / 로컬 폴백 | `ResilientCache` | 유지 |
| `@Cacheable` | `CacheInterceptor` | 사용 안 함 |

Lettuce 직접 호출 대비 Redisson 으로 얻는 것:

- **커넥션 수명 관리가 사라진다.** `connectionFactory.connection` 은 호출마다 새 커넥션이라 `use {}` 로 닫아야 했다. Redisson 은 내부 풀이라 캐시 코드에 `use` 가 없다.
- **클러스터에서 MGET 이 안 깨진다.** `RedissonBuckets.getAsync` 는 `readBatchedAsync` 로 키를 슬롯별로 쪼개 보내고 결과를 합친다. 원시 MGET 을 크로스 슬롯으로 날리면 `CROSSSLOT` 에러다.
- **없는 키가 결과에서 빠져서 온다.** `RedissonBuckets` 가 `e.getValue() != null` 로 필터한다. null 자리 채우기 / 순서 맞춰 zip 하는 로직이 통째로 불필요해진다.

---

## 3. 트랜잭션 처리 (가장 중요)

`transactionAware()` 를 잃으면 **커밋 전에 캐시가 갱신된다.** 롤백 시 캐시에 유령 데이터가 남는다.

직접 구현한다. 규칙 하나로 정리된다.

> **트랜잭션 안의 쓰기 연산은 (1) 즉시 무효화하고 (2) 실제 반영은 afterCommit 으로 미룬다.**

```kotlin
private fun write(keys: Collection<Any>, commit: () -> Unit) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        return commit()
    }

    // (1) 즉시 무효화.
    //     같은 트랜잭션 안에서 이어지는 조회가 캐시 미스로 떨어져 DB(영속성 컨텍스트)를
    //     보게 만든다. 이게 없으면 save() 직후 find() 가 옛 값을 돌려준다.
    //     그 사이 다른 트랜잭션이 읽어 캐시를 다시 채워도, 그건 커밋된 상태라 정합하다.
    delete(keys)

    // (2) 실제 put/evict 는 커밋 후.
    //     롤백되면 afterCommit 이 안 불려 아무것도 반영되지 않는다.
    //     캐시는 (1) 때문에 비어 있을 뿐 틀리지 않다.
    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() = commit()
    })
}
```

### 절대 하지 말 것 — 이중 지연

이전 코드는 `RedisCacheManager.transactionAware()` **와** 수동 `registerSynchronization` 을 동시에 썼다. 그러면 데코레이터가 `afterCommit` **실행 도중에** 또 `registerSynchronization` 을 호출한다.

`TransactionSynchronizationManager.getSynchronizations()` 바이트코드 (spring-tx 6.2.14):

```
65: new  #141  // class java/util/ArrayList
70: invokespecial #143  // ArrayList."<init>":(Ljava/util/Collection;)V   ← 복사본
79: invokestatic  #152  // Collections.unmodifiableList
```

`AbstractPlatformTransactionManager.triggerAfterCommit` 은 순회 **전에** 복사본을 뜬다. 그 뒤 등록된 동기화의 `afterCommit` 은 영원히 호출되지 않는다(`afterCompletion` 만 불리는데 익명 객체는 그걸 구현하지 않음). **put 이 조용히 증발한다. 예외도 안 난다.**

전면 교체 후에는 지연 계층이 하나뿐이라 이 문제가 구조적으로 사라진다. 단 **`afterCommit` 콜백 안에서 캐시를 건드리지 말 것.** 같은 함정에 다시 걸린다.

`ResilientCacheConfiguration.redisCacheManager` 가 아직 `transactionAware()` 로 살아 있다. 구 `@Cacheable` 경로가 남아 있는 동안만 유효하고, 새 어댑터와 캐시 이름을 공유하지 않는다는 전제에서만 안전하다. 11장 순서대로 제거할 것.

---

## 4. 어댑터 (`infra:redis`)

### `RedisCache.kt`

```kotlin
package com.langlez.redis.cache

import com.langlez.core.cache.Cache
import org.redisson.api.RedissonClient
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import kotlin.random.Random

/**
 * Redisson 어댑터. 로컬 폴백과 서킷 브레이커는 `ResilientCache` 가 감싼다.
 *
 * 값 직렬화는 Redisson `Codec` 이 담당한다. 어댑터는 직렬화를 모른다. 5장 참고.
 */
class RedisCache(
    private val name: String,
    private val redisson: RedissonClient,
    private val ttl: Duration = Duration.ofMinutes(10),
) : Cache {

    override fun <T : Any> get(key: Any, type: Class<T>): T? =
        redisson.getBucket<T>(encode(key)).get()
            .let { value -> if (type.isInstance(value)) type.cast(value) else null }

    /**
     * MGET 1회.
     *
     * `RBuckets.get` 은 요청한 키 문자열을 그대로 맵의 키로 돌려주고, 없는 키는
     * 결과에서 아예 빠진다(RedissonBuckets 가 null 값을 필터). 순서 의존이 없다.
     *
     * 따라서 조회도 조립도 반드시 `encode(key)` 로 통일해야 한다.
     * 넣을 때와 찾을 때의 인코딩이 어긋나면 100% 미스가 되고 예외는 안 난다.
     *
     * 반환 맵의 키는 인코딩 문자열이 아니라 원본 키 객체다.
     * 인코딩된 문자열을 돌려주면 호출자가 원본 컬렉션과 차집합을 낼 수 없다.
     */
    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> {
        if (keys.isEmpty()) return emptyMap()

        val found = redisson.buckets
            .get<T>(*keys.map(::encode).toTypedArray())
            ?: return emptyMap()

        return keys.asSequence().mapNotNull { key ->
            val value = found[encode(key)]
            if (type.isInstance(value)) key to type.cast(value) else null
        }.toMap()
    }

    override fun put(key: Any, value: Any) = write(listOf(key)) {
        redisson.getBucket<Any>(encode(key)).set(value, expiration())
    }

    /**
     * MSET 은 TTL 을 못 건다. `RBatch` 로 SET+EX 를 묶어 1왕복으로 처리한다.
     * `RBuckets.set(map)` 은 TTL 인자가 없어 쓸 수 없다.
     */
    override fun <T : Any> putMany(entries: Map<out Any, T>) {
        if (entries.isEmpty()) return

        write(entries.keys) {
            val batch = redisson.createBatch()

            entries.forEach { (key, value) ->
                batch.getBucket<Any>(encode(key)).setAsync(value, expiration())
            }

            batch.execute()
        }
    }

    override fun evict(key: Any) = evictMany(listOf(key))

    override fun evictMany(keys: Collection<Any>) {
        if (keys.isEmpty()) return
        write(keys) { delete(keys) }
    }

    /**
     * 트랜잭션이 열려 있으면 즉시 무효화하고 실제 반영은 afterCommit 으로 미룬다.
     * 근거는 3장.
     *
     * afterCommit 콜백 안에서 이 메서드를 다시 타면 등록이 유실된다.
     * 캐시 연산을 afterCommit 안에서 하지 말 것.
     */
    private fun write(keys: Collection<Any>, commit: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return commit()
        }

        delete(keys)

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = commit()
        })
    }

    // 문자열 템플릿이 곧 toString() 호출이다. 키 객체의 toString() 이 불안정하면
    // 매번 다른 Redis 키가 만들어져 영구 미스가 된다. 포트 계약 1번 참고.
    private fun encode(key: Any) = "$name:$key"

    // 만료 시각이 몰려 한꺼번에 터지는 걸(cache stampede) 막는다.
    private fun expiration() = ttl.plusSeconds(Random.nextLong(0, 60))

    private fun delete(keys: Collection<Any>) {
        redisson.keys.delete(*keys.map(::encode).toTypedArray())
    }
}
```

#### 현재 코드에서 고쳐야 할 것

작업 중인 `RedisCache.kt` 기준. 셋 다 **예외 없이 조용히 틀리는** 종류다.

| 위치 | 현재 | 문제 | 수정 |
|---|---|---|---|
| `getMany` (`RedisCache.kt:34`) | `keys.map(Any::toString)` 로 조회 | 프리픽스 없는 키를 MGET 하고, 결과는 `map[encode(key)]` 로 찾는다. 요청 키와 조회 키가 달라 **항상 빈 결과** | `keys.map(::encode)` |
| `delete` (`RedisCache.kt:90`) | `keys.map(Any::toString)` | 프리픽스 없는 키를 지운다. **evict 와 트랜잭션 즉시 무효화가 둘 다 동작하지 않는다.** 3장의 안전장치가 통째로 무력화 | `keys.map(::encode)` |
| 생성자 (`RedisCache.kt:14`) | `serializer: RedisSerializer<Any>` | Redisson 전환 후 어댑터 안에서 한 번도 안 쓰인다. 직렬화 주체는 Codec | 파라미터 제거 |

jitter 는 현재 `Random.nextLong(1, 10)` 이라 TTL 10분에 1~9초다. 같은 순간 적재된 대량 키를 흩기에는 좁다. 60초 폭을 권장하되, TTL 대비 몇 %를 흩을지는 값만 조정하면 되는 문제다.

### `ResilientCache`

`org.springframework.cache.Cache` 대신 `com.langlez.core.cache.Cache` 를 구현한다. 위임/폴백 구조는 그대로. 현재 코드가 이미 이 형태다.

```kotlin
class ResilientCache(
    private val name: String,
    private val redis: Cache,
    private val local: Cache,
    private val onFailure: () -> Unit = {},
) : Cache {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <T : Any> get(key: Any, type: Class<T>): T? =
        runGuarded({ redis.get(key, type) }, { local.get(key, type) })

    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        runGuarded({ redis.getMany(keys, type) }, { local.getMany(keys, type) })

    override fun put(key: Any, value: Any) =
        runGuarded({ redis.put(key, value) }, { local.put(key, value) })

    override fun <T : Any> putMany(entries: Map<out Any, T>) =
        runGuarded({ redis.putMany(entries) }, { local.putMany(entries) })

    override fun evict(key: Any) =
        runGuarded({ redis.evict(key) }, {}).also { local.evict(key) }

    override fun evictMany(keys: Collection<Any>) =
        runGuarded({ redis.evictMany(keys) }, {}).also { local.evictMany(keys) }

    private fun <T> runGuarded(op: () -> T, fallback: () -> T): T = runCatching { op() }
        .getOrElse { e ->
            logger.error("Redis operation failed for cache: $name", e)
            onFailure()
            fallback()
        }
}
```

evict 계열이 Redis 성공/실패와 무관하게 항상 로컬도 지우는 게 핵심이다. 폴백을 `{}` 로 두고 `also` 로 로컬을 지우는 이유는, Redis 가 살아 있어도 로컬에 남은 옛 값이 나중에 폴백 시점에 되살아나면 안 되기 때문.

`local` 로 넣을 **`CaffeineCache : com.langlez.core.cache.Cache` 를 새로 만들어야 한다.** 아직 없다. 인메모리라 왕복이 없으므로 `getMany` / `putMany` 는 루프로 충분하고, 트랜잭션 지연도 필요 없다(로컬은 폴백 전용이라 유령 데이터가 남아도 TTL 로 사라지고 Redis 복구 시 마이그레이션에서 정리된다).

### `ResilientCacheProvider`

목표 형태.

```kotlin
class ResilientCacheProvider(
    private val redisson: RedissonClient,
) : CacheProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val caches = ConcurrentHashMap<String, Cache>()
    private val locals = ConcurrentHashMap<String, CaffeineCache>()
    private val isRedisAvailable = AtomicBoolean(true)

    // getOrPut 은 ConcurrentHashMap 에서 원자적이지 않아 인스턴스가 중복 생성될 수 있다.
    // 무해하지만 computeIfAbsent 면 사라진다.
    override fun getCache(name: String): Cache = caches.computeIfAbsent(name) {
        val local = locals.computeIfAbsent(name) { CaffeineCache(name) }
        ResilientCache(name, RedisCache(name, redisson), local) { markRedisDown() }
    }

    @Scheduled(cron = "*/1 * * * * *")
    fun checkRedisAndMigrate() { ... }   // 헬스체크도 Redisson 호출로
}
```

현재 코드에서 남은 작업.

| 위치 | 현재 | 처리 |
|---|---|---|
| `ResilientCacheProvider.kt:30` | `RedisCache(name, RedisCache(name, redisson, serializer))` | `RedisCache` 를 자기 자신에 중첩하고 있고 **컴파일되지 않는다.** `ResilientCache(name, RedisCache(...), CaffeineCache(name)) { markRedisDown() }` 로 교체 |
| `redisCacheManager` / `caffeineCacheManager` 주입 | Spring 캐시 매니저 의존 | 새 포트 전환이 끝나면 제거. 로컬은 `CaffeineCache` 를 직접 들고 있으면 된다 |
| `connectionFactory` 주입 | `ping()` 헬스체크, `migrateLocalToRedis` 의 `mSet` | Redisson 으로 통일. 마이그레이션은 `putMany`(RBatch)로 |
| `serializer` 주입 | `migrateLocalToRedis` 의 수동 직렬화 | Codec 이 처리하므로 불필요 |
| `ResilientCacheConfiguration.kt:72` | 팩토리가 `objectMapper = ...` 를 넘김 | 생성자는 `serializer` 를 받는다. **인자 이름 불일치로 컴파일 실패.** 생성자 정리와 함께 맞출 것 |
| `migrateLocalToRedis` 의 `"$name::$key"` | 콜론 2개 | 어댑터 인코딩은 콜론 1개(`"$name:$key"`). **이관된 키를 새 어댑터가 못 읽는다.** `putMany` 로 내려보내면 인코딩이 한 곳으로 모여 자동 해결 |

마이그레이션을 `putMany` 로 내리면 TTL·배치·키 인코딩이 한 번에 해결된다. 지금처럼 `mSet` 을 직접 부르면 TTL 이 안 걸려 **이관된 키만 영구 잔존**한다.

---

## 5. 직렬화 — Redisson `Codec`

`RedisSerializer` 는 캐시 경로에서 사라진다. 값 인코딩은 `Config.setCodec` 으로 정한다.

현재 `RedissonConfiguration` 은 codec 을 지정하지 않는다. 즉 **Redisson 3.51.0 기본값인 `Kryo5Codec`** 이 쓰인다.

| 항목 | `Kryo5Codec` (현재, 기본값) | `JsonJacksonCodec` |
|---|---|---|
| 타입 정보 | 바이너리에 포함. `Long` 이 `Long` 으로 돌아온다 | default typing 을 켜야 `@class` 가 붙음 |
| 크기 / 속도 | 작고 빠름 | 큼, 느림 |
| 사람이 읽기 | 불가 | `redis-cli` 로 확인 가능 |
| 스키마 변경 | 필드 추가/삭제에 민감. 배포 간 클래스가 다르면 역직렬화 실패 | 관대 |
| 언어 종속 | JVM 전용 | 무관 |

**Kryo5 유지가 기본 선택이다.** 미배포 단계라 스키마 호환이 문제되지 않고, `Long`/`Integer` 가 갈리던 Jackson default typing 함정이 통째로 사라진다. 캐시 값이 깨지면 지우면 그만인 데이터다.

Kryo5 를 쓸 때 확인할 것.

- 캐시에 넣는 값이 **Hibernate 프록시나 지연 컬렉션을 물고 있으면 안 된다.** `Member` 는 연관관계가 없는 평면 엔티티라 안전하다. 연관을 가진 엔티티를 캐싱하게 되는 시점에 DTO 로 분리하거나 codec 을 바꿔야 한다.
- 로컬 Caffeine 은 객체를 그대로 들고 있고 Redis 는 codec 왕복을 거친다. **폴백 전후로 같은 타입이 나오는지**가 정합성 기준이다. Kryo5 는 타입을 보존하므로 이 축에서 안전하다.

`redis-cli` 로 값을 눈으로 봐야 하는 상황이 잦다면 JSON 으로 바꾼다. 이때만 아래가 필요하다.

```kotlin
// RedissonConfiguration 안
val mapper = objectMapper.copy().apply {
    // copy() 는 필수다. 주입되는 매퍼는 REST 응답 직렬화에도 쓰이는 공용 빈이라,
    // 여기에 default typing 을 켜면 모든 API 응답에 @class 가 붙는다.
    activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.langlez.")
            .allowIfSubType("java.util.")
            .build(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY,
    )
}
config.codec = JsonJacksonCodec(mapper)
```

**JSON 으로 갈 경우의 함정:** `Long`, `String` 같은 final 타입에는 `NON_FINAL` default typing 이 타입 정보를 안 남긴다. `Long` 을 캐시 값으로 저장하면 Redis 는 `Integer`, 로컬 Caffeine 은 `Long` 을 돌려준다. **역인덱스 값은 `String` 으로 저장할 것.** 지금 `MemberRepositoryImpl2` 가 이미 id 를 `String` 으로 넣고 `toLongOrNull()` 로 읽고 있어 codec 을 어느 쪽으로 정하든 안전하다. 이 관례는 유지한다.

주의: `RedisStreamMessageProducer`, `TokenBlacklistImpl`, `DailyRateLimiter`, `RedisLockService` 도 같은 `RedissonClient` 를 쓴다. codec 을 바꾸면 그쪽 저장 포맷도 같이 바뀐다.

---

## 6. gradle

```kotlin
// infra/redis/build.gradle.kts
dependencies {
    implementation(project(":common"))
    api(project(":core"))                     // 추가. Cache/CacheProvider 를 노출한다
    implementation(libs.dependency.caffeine)
    api(libs.dependency.redisson)
    api(libs.dependency.springboot.redis)     // 스트림이 아직 쓴다
    // api(libs.dependency.springboot.cache)  ← 최종 단계에서 제거
    ...
}
```

`infra:redis` 는 지금 `:core` 를 직접 선언하지 않는다. `:common` 이 `api(project(":core"))` 라 전이로 들어오고 있다. **공개 시그니처(`Cache`, `CacheProvider`)로 노출하는 모듈이므로 `api` 로 명시 선언한다.**

```kotlin
// module/member/build.gradle.kts
dependencies {
    implementation(project(":common"))
    implementation(project(":core"))          // 추가 (명시적으로)
    implementation(project(":infra:mysql"))
    implementation(project(":infra:redis"))
    ...
}
```

`module:member` 도 `com.langlez.core.event.member.*` 를 쓰면서 `:core` 를 선언하지 않는다. 전이 의존에 기대지 말 것.

`spring-boot-starter-cache` 제거는 **`@EnableCaching` 과 모든 `@Cacheable` 을 걷어낸 뒤** 하라. 먼저 지우면 컴파일은 되는데 캐시가 조용히 꺼진다.

---

## 7. 마이그레이션 범위

`@Cacheable` / `@Caching` / `@CacheEvict` / `CacheManager` 사용처:

| 파일 | 사용 형태 | 처리 |
|---|---|---|
| `module/member/.../MemberRepositoryImpl.kt` | `@Caching`, `@Cacheable`, `@CacheEvict`, 수동 `CacheManager` | **Impl2 위임으로 대체** (8장) |
| `module/member/.../MemberRepositoryImpl2.kt` | `CacheManager` (아직 전환 전) | `CacheProvider` 로 교체 (9장) |
| `module/member/.../MemberService.kt` | `CacheManager` | **삭제.** 유일 사용처가 버그였다 (`REVIEW.md` 11번) |
| `module/member/.../MemberEventListener.kt` | `CacheManager` | **삭제.** 주석 처리된 코드에서만 쓰인다 |
| `module/profile/.../ProfileRepositoryImpl.kt` | 캐시 사용 | 별도 전환 |
| `module/relationship/.../RelationshipRepositoryImpl.kt` | 캐시 사용 | 별도 전환 |

`member` 가 끝날 때까지 `@Deprecated` 로 표시한 `cacheManager` 빈을 남겨두고, `profile` / `relationship` 전환 후 `@EnableCaching` 과 함께 제거한다.

---

## 8. `MemberRepositoryImpl` 은 Impl2 에 위임한다

구 impl 의 캐시 코드를 새 포트로 "이식"하지 마라. **구현을 하나로 만든다.**

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

이 한 수로 사라지는 문제들:

- 구/신 impl 이 같은 캐시 이름에 다른 값 타입을 넣던 충돌. **캐시 이름 분리(`-v2`)가 불필요해진다.**
- 구 impl 캐시를 무효화하는 `evictLegacyCaches`. **불필요해진다.**
- 구 impl 의 `@Cacheable` 을 새 포트로 옮기는 작업 자체.
- 구 impl 의 잠재 버그들 — `save()` 가 `member-username` 을 evict 하지 않던 것, `findByUsernames` 의 `misses` 계산 오류(`cachedNames.filterNot { names.contains(it) }` 는 항상 빈 리스트라 미스분을 절대 못 가져온다).

`MemberRepository2` 에 `findAll(size, cursor)` 를 추가해야 위임이 완성된다. 캐시 대상이 아니므로 `jpa` 로 그냥 위임한다.

다른 모듈은 계속 `MemberRepository` 를 주입받으면 되고, 각자 리팩토링될 때 `MemberRepository2` 로 갈아탄 뒤 마지막에 구 인터페이스를 지운다.

---

## 9. 목표 형태 — `MemberRepositoryImpl2`

현재는 아직 `CacheManager` 를 주입받고 건당 `get` 을 돈다. `findAll(ids)` 가 N회 왕복이라 이 전환의 본래 목적이 여기서 실현된다.

```kotlin
@Repository
class MemberRepositoryImpl2(
    private val jpa: MemberJpaRepository,
    caches: CacheProvider,
) : MemberRepository2 {

    // getCache 는 non-null 이다. `?.` 도 `?: return` 도 필요 없다.
    private val members = caches.getCache("member")
    private val emailIndex = caches.getCache("member-email")
    private val usernameIndex = caches.getCache("member-username")
    private val providerIndex = caches.getCache("member-provider")

    override fun find(id: Long): Member? =
        members.get<Member>(id)
            ?: jpa.findByIdOrNull(id)?.also(::updateCaches)

    // 역인덱스 값은 String 으로 저장한다. 5장 참고.
    override fun find(username: String): Member? =
        usernameIndex.get<String>(username)?.toLongOrNull()
            ?.let(::find)?.takeIf { it.username == username }
            ?: jpa.findByUsername(username)?.also(::updateCaches)

    /**
     * MGET 1회로 히트분을 걷고, 미스만 DB 1쿼리로 채운 뒤 RBatch 1회로 적재한다.
     * 왕복이 N + 4N 에서 3 으로 줄어든다.
     */
    override fun findAll(ids: Collection<Long>): List<Member> {
        if (ids.isEmpty()) return emptyList()

        val distinct = ids.toSet()
        val cached = members.getMany<Member>(distinct)
        // getMany 가 원본 키 객체를 그대로 돌려주므로 Long 으로 바로 비교된다.
        // cached.keys 는 Set<Any> 라 `distinct - cached.keys` 는 타입이 안 맞는다.
        val missing = distinct.filter { it !in cached }
        if (missing.isEmpty()) return cached.values.toList()

        val loaded = jpa.findAllById(missing)
        // 미스분은 본체 캐시만 채운다. 역인덱스는 단건 조회 시 채워진다.
        members.putMany(loaded.associateBy { it.id })

        return cached.values + loaded
    }

    private fun updateCaches(member: Member) {
        val id = member.id.toString()
        members.put(member.id, member)
        emailIndex.put(member.email, id)
        usernameIndex.put(member.username, id)
        providerIndex.put("${member.provider}:${member.providerId}", id)
    }

    private fun evictCaches(member: Member) {
        members.evict(member.id)
        emailIndex.evict(member.email)
        usernameIndex.evict(member.username)
        providerIndex.evict("${member.provider}:${member.providerId}")
    }
}
```

`findAll(usernames)` 도 같은 패턴이다. 역인덱스를 `getMany<String>` 으로 한 번에 걷고, 얻은 id 로 `members.getMany<Member>` 를 한 번 더 친 뒤, 남은 미스만 DB 로 간다. 왕복 2 + DB 1.

---

## 10. 함정

1. **`encode` 를 우회하지 마라.** 넣을 때와 찾을 때/지울 때의 키 인코딩이 어긋나면 예외 없이 영구 미스이고, evict 는 아무것도 안 지운다. 트랜잭션 즉시 무효화까지 같이 죽는다. 지금 `getMany` 와 `delete` 가 이 상태다.
2. **`afterCommit` 안에서 캐시를 건드리지 마라.** `getSynchronizations()` 가 순회 전 복사본을 뜨므로 그 시점에 등록한 동기화는 실행되지 않는다. put 이 조용히 사라진다.
3. **키의 `toString()` 이 안정적이어야 한다.** 어댑터가 `"$name:$key"` 로 인코딩한다. `toString()` 을 재정의하지 않은 클래스를 키로 쓰면 `Object` 기본 구현(`ClassName@1b6d3586`)이 나가서 실행할 때마다 키가 달라진다. `Long` / `String` / enum / data class 만 쓰거나, 복합 키는 문자열을 직접 조립할 것.
4. **`T::class.java` 금지, `javaObjectType` 사용.** 프리미티브 Class 는 `isInstance` 가 항상 false 다. 포트의 reified 헬퍼를 쓸 것. 단 현재 헬퍼 시그니처가 `key: String` 이라 Long 키에서 안 걸린다. `Any` 로 고칠 것.
5. **역인덱스 값은 `String`.** codec 을 JSON 으로 바꿀 여지가 있는 한 `Long` 직접 저장은 위험하다. Redis 는 `Integer`, 로컬 Caffeine 은 `Long` 을 돌려주는 상황이 만들어진다.
6. **codec 은 전역이다.** 캐시만의 설정이 아니라 스트림 / 블랙리스트 / 레이트리밋 / 락이 같은 클라이언트를 쓴다. 바꾸면 저장 포맷이 전부 바뀐다.
7. **`RBuckets.set(map)` 에는 TTL 이 없다.** `putMany` 는 반드시 `RBatch` + `setAsync(value, Duration)` 로.
8. **`RBatch.execute()` 는 원자적이지 않다.** MULTI/EXEC 이 아니라 파이프라인이다. 중간 실패 시 일부만 반영될 수 있다. 캐시라 허용 가능하지만, 부분 반영이 곤란한 데이터에는 쓰지 말 것.
9. **`redisson.getBucket(...)` 은 매번 새 핸들 객체다.** 커넥션이 아니라 얇은 래퍼라 `use` 가 필요 없다. Lettuce 때의 `connection.use {}` 습관을 가져오지 말 것.
10. **`putMany` 는 `Map<out Any, T>`.** Kotlin `Map` 의 키 타입은 무공변이라 `out` 없이는 `Map<Long, Member>` 를 넘길 수 없다. `getMany` 결과로 차집합을 낼 때도 `cached.keys` 가 `Set<Any>` 라 `Set<Long> - Set<Any>` 가 안 된다. `distinct.filter { it !in cached }` 를 쓸 것.
11. **`spring-boot-starter-cache` 제거 시점.** `@EnableCaching` 과 모든 어노테이션을 걷어낸 뒤.
12. **로컬 → Redis 마이그레이션에도 TTL 과 같은 인코딩을 써라.** 지금은 `mSet` 에 `"$name::$key"`(콜론 2개)로 밀어 넣어 TTL 도 없고 새 어댑터가 읽지도 못한다. `putMany` 로 내려보내면 둘 다 해결된다.

---

## 11. 이행 체크리스트

| 순서 | 작업 | 검증 |
|---|---|---|
| 1 | `Cache.get` reified 헬퍼 `key: Any` 로 수정 | `members.get<Member>(1L)` 컴파일 |
| 2 | `RedisCache` 의 `getMany` / `delete` 를 `encode` 로 통일, `serializer` 파라미터 제거 | put 후 getMany 히트, evict 후 miss |
| 3 | codec 확정 (Kryo5 유지 or `JsonJacksonCodec`) | 새 JVM 에서 `Member` 왕복 후 타입 그대로 |
| 4 | `CaffeineCache : com.langlez.core.cache.Cache` 신규 | 단건 put/get |
| 5 | `ResilientCacheProvider` 조립 수정 + 생성자에서 `redisCacheManager` / `caffeineCacheManager` / `connectionFactory` / `serializer` 정리, 팩토리 인자 이름 일치 | 컴파일, Redis 내렸을 때 로컬 폴백 |
| 6 | 마이그레이션을 `putMany` 로 교체 | 복구 후 이관 키에 TTL 존재, 새 어댑터로 읽힘 |
| 7 | 트랜잭션 지연 검증 | `@Transactional` 안 `save` → 커밋 후 키 존재, 롤백 후 키 부재 |
| 8 | `MemberRepositoryImpl2` 를 `CacheProvider` 로 전환 | `REVIEW.md` 검증 항목, `findAll` 왕복 수 |
| 9 | `MemberRepositoryImpl` 을 Impl2 위임으로 교체 | 기존 테스트 통과 |
| 10 | `MemberService` / `MemberEventListener` 의 `CacheManager` 제거 | 컴파일 |
| 11 | `profile` / `relationship` 전환 | — |
| 12 | `@Deprecated cacheManager` 빈, `redisCacheManager`, `@EnableCaching`, `spring-boot-starter-cache` 제거 | 전체 테스트 |

1~2번이 가장 앞이다. 키 인코딩이 어긋난 상태에서는 나머지를 아무리 고쳐도 캐시 히트가 0이고, **예외도 안 나고 테스트도 통과하면서 그냥 DB 만 탄다.**
