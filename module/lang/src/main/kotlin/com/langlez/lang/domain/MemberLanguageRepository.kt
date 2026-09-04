package com.langlez.lang.domain

/**
 * 언어 프로필 저장소 포트.
 *
 * 개별 추가·삭제 메서드를 두지 않는다. API 가 전체 교체 하나뿐이라 상한·중복·역할 충돌 검사를
 * 한 트랜잭션에서 한 번에 하고, 그 경계를 application 이 잡는다.
 */
interface MemberLanguageRepository {

    fun findAll(memberId: Long): List<MemberLanguage>

    fun findAll(memberIds: Collection<Long>): List<MemberLanguage>

    fun saveAll(languages: Collection<MemberLanguage>): List<MemberLanguage>

    fun deleteAll(memberId: Long)

    /**
     * 상호보완 후보 id. 내가 배우는 언어를 모국어로 하면서, 동시에 내가 모국어로 하는 언어를
     * 배우는 회원. 정렬하지 않는다 — 랭킹은 호출자 몫이다.
     */
    fun findComplementaryCandidates(
        myNativeLanguages: Collection<String>,
        myLearningLanguages: Collection<String>,
        excludeMemberId: Long,
        limit: Int,
    ): List<Long>
}
