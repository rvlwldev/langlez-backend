package com.langlez.redis.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.Codec
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedissonConfiguration(
    private val properties: RedisProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.codec = redisCodec(objectMapper)

        val prefix = if (properties.ssl.isEnabled) "rediss://" else "redis://"

        config.useSingleServer()
            .setAddress("$prefix${properties.host}:${properties.port}")
            .setDatabase(properties.database)
            .setConnectTimeout(properties.timeout?.toMillis()?.toInt() ?: 10000)
            .setConnectionPoolSize(64)
            .setConnectionMinimumIdleSize(10)
            .setPassword(properties.password.takeIf { !it.isNullOrBlank() })

        return Redisson.create(config)
    }

    companion object {
        /** 캐시에 담기는 타입만 허용한다. */
        private val VALIDATOR: PolymorphicTypeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.langlez.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .build()

        /**
         * 레디스 값 직렬화 코덱.
         *
         * `JsonJacksonCodec` 은 생성자에서 `initTypeInclusion()` 으로 `setDefaultTyping()` 을 호출해
         * 넘겨준 매퍼의 타이핑 설정을 통째로 덮어쓴다. 그 기본 typer 는 `!t.isFinal()` 이라
         * 코틀린 클래스(기본 final)에는 `@class` 를 안 붙이고, 디코딩은 `readValue(.., Object::class)` 라
         * 타입 정보를 요구한다. 그래서 쓰기는 되고 읽기만 전부 실패한다.
         * 이를 막으려면 `initTypeInclusion` 을 우리 것으로 갈아끼워야 한다.
         */
        fun redisCodec(objectMapper: ObjectMapper): Codec {
            // copy() 필수. objectMapper 는 REST 응답 직렬화에도 쓰이는 공용 빈이라
            // 그대로 건드리면 모든 API 응답에 @class 가 붙는다.
            return object : JsonJacksonCodec(objectMapper.copy()) {
                // 주의: 이 메서드는 상위 생성자에서 불린다. 하위 클래스 필드를 참조하면
                // 아직 대입 전이라 null 이다. 반드시 companion 의 VALIDATOR 만 쓴다.
                override fun initTypeInclusion(mapObjectMapper: ObjectMapper) {
                    val typer = object : DefaultTypeResolverBuilder(
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        VALIDATOR,
                    ) {
                        // 코틀린 클래스는 기본 final 이라 NON_FINAL 규칙만으론 @class 가 안 붙는다.
                        // 우리 도메인 타입은 final 이어도 타입 정보를 남겨야 디코딩이 된다.
                        override fun useForType(t: JavaType): Boolean =
                            t.rawClass.name.startsWith("com.langlez.") || super.useForType(t)
                    }
                    typer.init(JsonTypeInfo.Id.CLASS, null)
                    typer.inclusion(JsonTypeInfo.As.PROPERTY)
                    mapObjectMapper.setDefaultTyping(typer)
                }
            }
        }
    }
}
