package com.langlez.lang.contract

/**
 * 언어 프로필 조회. lang 모듈이 구현한다.
 *
 * 이 앱은 언어교환이라 "내가 하는 언어 / 배우는 언어"가 매칭의 1차 입력이다. 그 데이터의 소유는
 * lang 이고, matching 이 그걸 읽어 추천을 만든다.
 *
 * [complementaryCandidates] 를 여기 두는 이유: matching 이 SQL 로 후보를 뽑으려면
 * `member_languages` 를 직접 읽어야 하고 그건 모듈 경계 위반이다. 질의 자체를 포트가 갖는다.
 */
interface LanguageReader {

    /** 등록한 언어가 없으면 빈 목록. */
    fun languagesOf(memberId: Long): List<LanguageInfo>

    /** 목록 화면용. 회원 수만큼 단건 조회를 돌면 N+1 이다. 없는 id 는 결과에서 빠진다. */
    fun languagesOf(memberIds: Collection<Long>): Map<Long, List<LanguageInfo>>

    /**
     * 상호보완 후보. 내가 배우는 언어를 모국어로 하고, 동시에 내가 모국어로 하는 언어를 배우는 회원.
     *
     * 정렬하지 않는다 — 랭킹에 접속 상태(Redis)가 들어가 SQL 로는 못 섞는다.
     * 호출자가 자른다. [limit] 은 DB 부하 상한이지 페이지 크기가 아니다.
     *
     * 차단·팔로우·탈퇴 여부는 보지 않는다. 그건 각 소유 모듈의 계약이라 호출자가 겹쳐서 거른다.
     */
    fun complementaryCandidates(
        myNativeLanguages: Collection<String>,
        myLearningLanguages: Collection<String>,
        excludeMemberId: Long,
        limit: Int,
    ): List<Long>

    data class LanguageInfo(val language: String, val role: Role, val level: Level?)

    /**
     * `MemberLanguage.Role` 의 거울.
     *
     * 구현체가 `when` 으로 하나씩 매핑하니 원본에 값이 늘면 컴파일이 깨진다. 이름을 그대로
     * 옮기지 않고 매핑을 두는 건, 원본이 바뀌었는데 여기가 안 바뀐 상태로 조용히 통과하는 걸 막기 위해서다.
     */
    enum class Role { NATIVE, LEARNING }

    /**
     * `MemberLanguage.Level` 의 거울. 매핑을 두는 이유는 [Role] 과 같다.
     *
     * **상수 순서가 의미를 갖는다.** 매칭의 레벨 근접도가 ordinal 차이로 계산된다
     * (`MatchScorer.levelProximity`). 순서를 바꾸거나 중간에 값을 끼워 넣으면
     * 컴파일도 테스트도 통과한 채로 매칭 점수만 조용히 틀어진다.
     */
    enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }
}
