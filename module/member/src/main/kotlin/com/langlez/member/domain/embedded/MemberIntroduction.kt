package com.langlez.member.domain.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.validation.constraints.Size

@Embeddable
data class MemberIntroduction(
    @field:Size(max = 200, message = "validation.member.bio.size")
    @Column(name = "bio", length = 200)
    var bio: String? = null,

    @field:Size(max = 500, message = "validation.member.goal.size")
    @Column(name = "goal", length = 500)
    var goal: String? = null,

    @field:Size(max = 500, message = "validation.member.want.size")
    @Column(name = "want", length = 500)
    var want: String? = null
)
