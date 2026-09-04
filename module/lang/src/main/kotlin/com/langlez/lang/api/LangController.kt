package com.langlez.lang.api

import com.langlez.annotation.MemberId
import com.langlez.lang.api.request.LangReplaceLanguagesRequest
import com.langlez.lang.api.response.LangMeResponse
import com.langlez.lang.application.LanguageService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/langs")
class LangController(private val service: LanguageService) : LangAPI {

    @GetMapping("/me")
    override fun getMyLanguages(@MemberId memberId: Long): LangMeResponse =
        LangMeResponse(service.findAll(memberId).map(LangMeResponse::Item))

    // PATCH 로 열지 않는다. 전체 교체 시맨틱을 PATCH 에 얹으면 클라이언트가 바뀐 항목만 보냈을 때
    // 나머지가 조용히 초기화된다. 실제로 그렇게 당한 적이 있다.
    @PutMapping("/me")
    override fun replaceMyLanguages(
        @MemberId memberId: Long,
        @RequestBody @Valid request: LangReplaceLanguagesRequest,
    ): LangMeResponse = LangMeResponse(service.replace(memberId, request).map(LangMeResponse::Item))
}
