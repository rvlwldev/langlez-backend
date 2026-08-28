package com.langlez.member

import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.EnableRetry

/**
 * `MemberService.createMember` 의 `@Retryable` 은 이 설정이 없으면 조용히 무동작한다.
 * 랜덤 handle 이 기존 회원과 충돌하면 재시도 없이 500 으로 나간다.
 */
@Configuration
@EnableRetry
class MemberRetryConfiguration
