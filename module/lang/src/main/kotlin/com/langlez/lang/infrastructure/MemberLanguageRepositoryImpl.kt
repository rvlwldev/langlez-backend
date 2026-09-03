package com.langlez.lang.infrastructure

import com.langlez.lang.domain.MemberLanguage
import com.langlez.lang.domain.MemberLanguage.Role
import com.langlez.lang.domain.MemberLanguageRepository
import com.langlez.lang.domain.QMemberLanguage
import com.langlez.lang.infrastructure.jpa.MemberLanguageJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * 캐시를 두지 않는다. 회원당 최대 8행이고 조회가 전부 유니크 인덱스 선두 컬럼(`member_id`)을 탄다.
 * 매칭 후보 목록은 lang 이 아니라 matching 이 자기 캐시에 담는다 — 거기에는 차단·팔로우 필터가
 * 이미 섞여 있어 이 계층에서 캐시하면 두 벌이 된다.
 */
@Repository
class MemberLanguageRepositoryImpl(
    private val jpa: MemberLanguageJpaRepository,
    private val dsl: JPAQueryFactory,
) : MemberLanguageRepository {

    override fun findAll(memberId: Long): List<MemberLanguage> = jpa.findAllByMemberId(memberId)

    override fun findAll(memberIds: Collection<Long>): List<MemberLanguage> {
        val ids = memberIds.toSet()
        if (ids.isEmpty()) return emptyList()
        return jpa.findAllByMemberIdIn(ids)
    }

    override fun saveAll(languages: Collection<MemberLanguage>): List<MemberLanguage> {
        if (languages.isEmpty()) return emptyList()
        return jpa.saveAll(languages)
    }

    /**
     * 벌크 DELETE 라 트랜잭션이 필요하다. 호출부(전체 교체)가 이미 트랜잭션 안이면 그대로 참여해
     * 삭제와 재삽입이 한 커밋에 묶인다 — 나뉘면 실패 시 언어가 통째로 사라진 상태로 남는다.
     */
    @Transactional
    override fun deleteAll(memberId: Long) {
        dsl.delete(QMemberLanguage.memberLanguage)
            .where(QMemberLanguage.memberLanguage.memberId.eq(memberId))
            .execute()
    }

    /**
     * 상호보완 후보를 셀프 조인 한 방으로 뽑는다.
     *
     * 같은 회원이 여러 언어로 걸리면 행이 여러 개 나오므로 `distinct` 가 필수다. 없으면 limit 이
     * 회원 수가 아니라 쌍 수를 자르게 돼, 다국어 회원 몇 명이 후보 슬롯을 통째로 먹는다.
     *
     * 정렬을 붙이지 않아 limit 에서 잘리는 후보가 어느 것인지는 정해지지 않는다. 의도한 것이다 —
     * 랭킹에 접속 상태(Redis)가 들어가 SQL 로는 못 섞고, 여기 limit 은 DB 부하 상한일 뿐이다.
     */
    override fun findComplementaryCandidates(
        myNativeLanguages: Collection<String>,
        myLearningLanguages: Collection<String>,
        excludeMemberId: Long,
        limit: Int,
    ): List<Long> {
        val natives = myNativeLanguages.toSet()
        val learnings = myLearningLanguages.toSet()
        if (natives.isEmpty() || learnings.isEmpty() || limit <= 0) return emptyList()

        val theirNative = QMemberLanguage("theirNative")
        val theirLearning = QMemberLanguage("theirLearning")

        return dsl.select(theirNative.memberId).distinct()
            .from(theirNative)
            .join(theirLearning).on(
                theirLearning.memberId.eq(theirNative.memberId),
                theirLearning.role.eq(Role.LEARNING),
                // 상대가 배우는 언어가 내 모국어여야 나에게도 돌아오는 게 있다.
                theirLearning.language.`in`(natives),
            )
            .where(
                theirNative.role.eq(Role.NATIVE),
                // 상대의 모국어가 내 학습언어여야 내가 얻는 게 있다.
                theirNative.language.`in`(learnings),
                theirNative.memberId.ne(excludeMemberId),
            )
            .limit(limit.toLong())
            .fetch()
    }
}
