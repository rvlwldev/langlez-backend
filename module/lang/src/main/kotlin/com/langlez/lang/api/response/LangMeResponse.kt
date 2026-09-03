package com.langlez.lang.api.response

import com.langlez.lang.domain.MemberLanguage
import io.swagger.v3.oas.annotations.media.Schema

data class LangMeResponse(
    @field:Schema(description = "등록한 언어 전체")
    val languages: List<Item>,
) {
    // 엔티티 목록을 받는 보조 생성자를 둘 수 없다. List<Item> 과 List<MemberLanguage> 가
    // 지워지고 나면 JVM 시그니처가 같아 platform declaration clash 가 난다. 변환은 Item 이 한다.
    data class Item(
        @field:Schema(description = "BCP-47 언어 코드", example = "ko")
        val language: String,

        @field:Schema(description = "NATIVE / LEARNING")
        val role: String,

        @field:Schema(description = "학습언어일 때만 값이 있다.", nullable = true)
        val level: String?,
    ) {
        constructor(entity: MemberLanguage) : this(
            language = entity.language,
            role = entity.role.name,
            level = entity.level?.name,
        )
    }
}
