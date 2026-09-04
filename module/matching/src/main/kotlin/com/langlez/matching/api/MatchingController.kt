package com.langlez.matching.api

import com.langlez.annotation.MemberId
import com.langlez.matching.api.response.MatchingMembersResponse
import com.langlez.matching.application.MatchingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/v1/matches")
class MatchingController(private val service: MatchingService) : MatchingAPI {

    @GetMapping
    override fun getMatches(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "false") refresh: Boolean,
        locale: Locale,
    ): MatchingMembersResponse = service.recommend(
        memberId = memberId,
        size = size.coerceIn(1, MAX_SIZE),
        offset = offset.coerceAtLeast(0),
        refresh = refresh,
        locale = locale,
    )

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 후보 전체를 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}
