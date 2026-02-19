package com.langlez.member.domain.embedded

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.validation.constraints.Size

@Embeddable
data class MemberLocation(
    @field:Size(max = 200, message = "validation.member.address.size")
    @Column(name = "address", length = 200)
    var address: String? = null,

    @Column(name = "latitude")
    var lat: Double? = null,

    @Column(name = "longitude")
    var lon: Double? = null
)
