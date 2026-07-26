package com.langlez.interest.api

import com.langlez.interest.application.InterestService
import com.langlez.security.web.MemberId
import org.springframework.web.bind.annotation.*
import java.util.Locale

@RestController
@RequestMapping("/api/v1/interests")
class InterestController(private val service: InterestService) {

    @GetMapping("/search")
    fun search(locale: Locale, @RequestParam q: String): InterestResponse.List =
        InterestResponse.of(service.search(locale, q))

    @GetMapping("/me")
    fun getMyInterests(@MemberId memberId: Long, locale: Locale): InterestResponse.List =
        InterestResponse.of(service.getMemberInterests(memberId, locale))

    @PutMapping("/me")
    fun setMyInterests(
        @MemberId memberId: Long,
        locale: Locale,
        @RequestBody request: InterestRequest.Set,
    ) {
        service.setMemberInterests(memberId, locale, request.names)
    }
}
