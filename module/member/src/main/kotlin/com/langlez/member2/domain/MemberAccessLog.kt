package com.langlez.member2.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member_access_logs")
class MemberAccessLog(
    val memberId: Long,
    var ip: String,
    var deviceId: String,
    val accessedAt: Instant = Instant.now()
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0
}