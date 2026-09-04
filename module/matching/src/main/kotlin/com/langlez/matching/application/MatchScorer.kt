package com.langlez.matching.application

import com.langlez.lang.contract.LanguageReader.LanguageInfo
import com.langlez.lang.contract.LanguageReader.Level
import com.langlez.lang.contract.LanguageReader.Role
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * 추천 점수 계산. 순수 함수만 담는다 — 포트도 캐시도 모른다.
 *
 * 랭킹이 틀려도 컴파일과 통합테스트는 전부 통과한다. 화면만 보고는 못 찾는 종류라
 * 계산을 여기 한 곳에 몰고 단위 테스트를 붙였다.
 */
@Component
class MatchScorer {

    /**
     * 상호보완 쌍. **내가 배우는 언어를 상대가 모국어로 하는** 경우다.
     *
     * 클라이언트가 "왜 추천됐는지"를 그려야 해서 응답에 그대로 실린다. 없으면 사용자가 목록을
     * 신뢰하지 않고, 랭킹이 잘못됐을 때 화면만 보고는 원인을 못 찾는다.
     *
     * 두 필드 값이 같은 언어 코드인 것은 정의상 당연하다. 그래도 이름을 나눠 두는 이유는
     * 클라이언트가 "내 학습언어"와 "상대 모국어" 중 어느 쪽을 강조해 그릴지 고르게 하기 위해서다.
     */
    data class MatchedPair(val myLearning: String, val theirNative: String)

    fun matchedPairs(mine: List<LanguageInfo>, theirs: List<LanguageInfo>): List<MatchedPair> {
        val theirNatives = theirs.filter { it.role == Role.NATIVE }.mapTo(mutableSetOf()) { it.language }
        return mine.asSequence()
            .filter { it.role == Role.LEARNING && it.language in theirNatives }
            .map { MatchedPair(myLearning = it.language, theirNative = it.language) }
            .toList()
    }

    /**
     * 상호 학습자 근접도(0~2).
     *
     * - `myLevel` = 내가 **상대의 모국어** 를 배우는 레벨
     * - `theirLevel` = 상대가 **내 모국어** 를 배우는 레벨
     *
     * 언어교환은 상호 교환이다. 내가 상대 모국어를 ADVANCED 로 하는데 상대가 내 모국어를
     * BEGINNER 로 하면 교환이 한쪽으로 기운다. 그리고 이것이 **양쪽 다 레벨이 존재하는 유일한
     * 조합**이다 — NATIVE 는 불변식상 level 이 null 이라 (내 LEARNING × 상대 NATIVE) 쌍으로는
     * 아예 계산이 안 된다. "둘 다 배우는 공통 언어의 레벨 비교"로 바꾸지 마라.
     *
     * 모국어가 여러 개면 쌍도 여러 개다. **가장 가까운 쌍 하나**를 쓴다 — 교환이 성립하는 최선의
     * 조합이 하나라도 있으면 그게 그 관계의 실제 궁합이고, 평균을 내면 안 맞는 쌍이 그걸 깎는다.
     *
     * 한쪽이라도 없으면 0 이다. 상호보완이 한 방향으로만 성립하는 후보가 있을 수 있다.
     */
    fun levelProximity(mine: List<LanguageInfo>, theirs: List<LanguageInfo>): Int {
        val myLevels = learningLevelsOf(mine, nativeLanguagesOf(theirs))
        val theirLevels = learningLevelsOf(theirs, nativeLanguagesOf(mine))
        if (myLevels.isEmpty() || theirLevels.isEmpty()) return 0

        // Level 상수 순서에 의존한다.
        val gap = myLevels.minOf { my -> theirLevels.minOf { their -> abs(my.ordinal - their.ordinal) } }
        return (2 - gap).coerceAtLeast(0)
    }

    fun score(mine: List<LanguageInfo>, theirs: List<LanguageInfo>, online: Boolean): Int =
        matchedPairs(mine, theirs).size * MATCHED_PAIR_WEIGHT +
            (if (online) ONLINE_BONUS else 0) +
            levelProximity(mine, theirs)

    /**
     * 후보를 점수 내림차순으로 정렬해 id 만 돌려준다.
     *
     * **동점은 id 오름차순으로 깬다.** 안 정하면 정렬 순서가 구현에 맡겨져 같은 입력에도
     * 새로고침마다 목록이 흔들리고, offset 페이징이 항목을 중복시키거나 빠뜨린다.
     * `sortedWith` 는 안정 정렬이지만 입력(Map)의 순회 순서 자체가 보장이 아니라 2차 키가 필요하다.
     *
     * 접속 상태가 점수에 들어가므로 이 순서는 **계산 시점의 스냅샷**이다. 호출자가 이 결과를
     * 캐시하고, 화면에 그리는 접속 점만 매 요청 다시 읽는다.
     */
    fun rank(
        mine: List<LanguageInfo>,
        candidates: Map<Long, List<LanguageInfo>>,
        online: Map<Long, Boolean>,
    ): List<Long> = candidates.entries
        .sortedWith(
            compareByDescending<Map.Entry<Long, List<LanguageInfo>>> { (id, theirs) ->
                score(mine, theirs, online[id] == true)
            }.thenBy { it.key }
        )
        .map { it.key }

    private fun nativeLanguagesOf(languages: List<LanguageInfo>): Set<String> =
        languages.filter { it.role == Role.NATIVE }.mapTo(mutableSetOf()) { it.language }

    private fun learningLevelsOf(languages: List<LanguageInfo>, targets: Set<String>): List<Level> =
        languages.filter { it.role == Role.LEARNING && it.language in targets }.mapNotNull { it.level }

    companion object {
        /** 양방향 성립이 1순위다. 접속·레벨 가점을 다 합쳐도 쌍 하나를 못 뒤집게 둔다. */
        private const val MATCHED_PAIR_WEIGHT = 10
        private const val ONLINE_BONUS = 5
    }
}
