package com.langlez.member.infrastructure

import com.langlez.core.PushTokenQuery
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Repository

/**
 * 푸시 토큰 조회 포트 구현.
 *
 * 토큰은 member 소유라 notification 이 직접 들여다보면 경계가 무너진다.
 * `MemberRepository.find` 는 2단계 캐시를 타므로 알림마다 DB 를 치지는 않는다.
 */
@Repository
class PushTokenQueryImpl(private val repo: MemberRepository) : PushTokenQuery {

    override fun findPushToken(memberId: Long): String? =
        repo.find(memberId)?.fcm?.takeIf { it.isNotBlank() }
}
