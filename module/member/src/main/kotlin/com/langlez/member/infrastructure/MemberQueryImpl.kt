package com.langlez.member.infrastructure

import com.langlez.member.contract.MemberQuery
import com.langlez.member.contract.PushTokenQuery
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Repository

/**
 * member 가 다른 모듈에 내주는 조회 포트 구현.
 *
 * 계정 정보·상태·푸시 토큰이 전부 `MemberRepository.find` 하나를 읽어 매핑하는 것뿐이라
 * 포트별로 어댑터를 나누지 않고 한 클래스가 다 구현한다. (`MemberOnlineTracker` 는 예외다 —
 * 레디스 버킷/ZSET 을 직접 다루고 스케줄러로 DB 동기화까지 해서 성격이 다르다.)
 *
 * 상태 조회는 `JwtAuthenticationFilter` 가 매 요청 부른다. `MemberRepository.find` 는 2단계 캐시를 타고
 * 상태를 바꾸는 경로(`suspend`/`unsuspend`/`withdraw`)가 전부 `repo.save` 를 거쳐
 * 캐시를 갱신하므로, 정상 상황에서 요청당 DB 왕복은 생기지 않는다.
 *
 * 푸시 토큰을 이벤트 페이로드에 실어 보내지 않고 여기서 조회하는 이유: 브로커·DLT·로그에
 * 기기 자격증명이 그대로 남고, 발행 시점 값이라 그 사이 토큰이 재발급되면 죽은 토큰으로 쏘게 된다.
 * 보낼 직전에 조회하는 게 맞다.
 */
@Repository
class MemberQueryImpl(private val repo: MemberRepository) : MemberQuery, PushTokenQuery {

    override fun findIdByHandle(handle: String): Long? = repo.find(handle)?.id

    override fun findProfileInfo(memberId: Long): MemberQuery.ProfileInfo? = repo.find(memberId)?.toProfileInfo()

    override fun findProfileInfos(memberIds: Collection<Long>): Map<Long, MemberQuery.ProfileInfo> {
        if (memberIds.isEmpty()) return emptyMap()
        return repo.findAll(memberIds.toSet()).associate { it.id to it.toProfileInfo() }
    }

    override fun findStatus(memberId: Long): MemberQuery.Status? = when (repo.find(memberId)?.status) {
        Member.Status.CREATED -> MemberQuery.Status.CREATED
        Member.Status.ACTIVE -> MemberQuery.Status.ACTIVE
        Member.Status.SUSPENDED -> MemberQuery.Status.SUSPENDED
        Member.Status.WITHDRAWN -> MemberQuery.Status.WITHDRAWN
        null -> null
    }

    override fun findPushToken(memberId: Long): String? =
        repo.find(memberId)?.fcm?.takeIf { it.isNotBlank() }

    override fun findPushTokens(memberIds: Collection<Long>): Map<Long, String> =
        repo.findAll(memberIds)
            .mapNotNull { member -> member.fcm?.takeIf(String::isNotBlank)?.let { member.id to it } }
            .toMap()

    private fun Member.toProfileInfo() = MemberQuery.ProfileInfo(
        id = id,
        handle = handle,
        nickname = nickname,
        gender = gender.name,
        locale = locale,
        birthDay = birthDay,
        imageUrl = imageUrl,
    )
}
