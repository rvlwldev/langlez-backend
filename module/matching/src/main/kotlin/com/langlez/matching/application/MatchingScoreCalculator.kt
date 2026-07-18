package com.langlez.matching.application

import com.langlez.profile.domain.Profile
import java.time.Duration
import java.time.Instant

/**
 * PLAN.md Phase 5 매칭 큐 점수 계산.
 *
 * Score = LanguageLevel_Weight (+ Interest_Weight는 매칭 시도 시점의 후보 비교 보너스로 별도 처리)
 * - LanguageLevel_Weight: BEGINNER=0, INTERMEDIATE=1000, ADVANCED=2000 (languageLevel.ordinal * 1000)
 * - WaitTime_Reduction: 대기 시간이 10초 지날 때마다 허용 오차(tolerance)를 300씩 넓힌다.
 *   기본 tolerance=200(같은 레벨끼리만), 최대 tolerance=2000(모든 레벨 허용)
 */
object MatchingScoreCalculator {

    private const val LEVEL_SCORE_STEP = 1000.0
    private const val BASE_TOLERANCE = 200.0
    private const val TOLERANCE_STEP = 300.0
    private const val MAX_TOLERANCE = 2000.0
    private val WAIT_STEP: Duration = Duration.ofSeconds(10)

    fun baseScore(languageLevel: Profile.LanguageLevel): Double =
        languageLevel.ordinal * LEVEL_SCORE_STEP

    fun tolerance(joinedAt: Instant, now: Instant = Instant.now()): Double {
        val waitedSeconds = Duration.between(joinedAt, now).seconds.coerceAtLeast(0)
        val steps = waitedSeconds / WAIT_STEP.seconds
        val tolerance = BASE_TOLERANCE + steps * TOLERANCE_STEP
        return tolerance.coerceAtMost(MAX_TOLERANCE)
    }
}
