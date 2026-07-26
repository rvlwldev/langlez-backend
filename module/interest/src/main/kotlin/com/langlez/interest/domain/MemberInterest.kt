package com.langlez.interest.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "member_interests",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_MEMBER_INTEREST", columnNames = ["member_id", "interest_id"])],
)
class MemberInterest(
    val memberId: Long,
    val interestId: Long,
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0
}
