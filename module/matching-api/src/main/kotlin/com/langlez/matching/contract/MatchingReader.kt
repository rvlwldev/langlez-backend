package com.langlez.matching.contract

/**
 * 추천 후보 조회. matching 모듈이 구현한다.
 *
 * **아직 소비자가 없다.** 그래도 미리 두는 이유는 `report-api` 의 `ReportWriter` 와 같다 —
 * "추천을 누가 소유하는가"를 계약으로 못 박아 두는 것이다. 소비자(홈 화면의 추천 스트립,
 * 신규 가입 온보딩, 예정된 `interest` 모듈)가 붙을 때 matching 의 내부를 들여다보게 두면
 * 그 순간 경계가 무너지고, 되돌리려면 그때 이미 얽힌 호출부를 전부 고쳐야 한다.
 *
 * 정렬·필터 규칙(차단·이미 팔로우·정지·탈퇴 제외)은 구현이 갖는다. 소비자는 순서만 신뢰하면 된다.
 */
interface MatchingReader {

    /**
     * 랭킹 순 추천 회원 id. 언어를 등록하지 않았거나 후보가 없으면 빈 목록이다 — 예외가 아니다.
     *
     * 순서는 캐시된 것이라 최대 10분까지 고정이다. 접속 상태는 그 순서에 이미 반영돼 있지만
     * 호출 시점의 실시간 값은 아니다. 실시간 접속 표시가 필요하면 `OnlineTracker` 를 따로 본다.
     */
    fun recommendedIds(memberId: Long, limit: Int): List<Long>
}
