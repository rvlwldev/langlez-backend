package com.langlez.member.domain.embedded

import jakarta.persistence.Embeddable

@Embeddable data class MemberLocation(val address: String?, val lat: Double?, val lon: Double?)
