package com.langlez.member.domain

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(
        name = "member_images",
        uniqueConstraints = [UniqueConstraint(columnNames = ["member_id", "represent"])]
)
@IdClass(MemberImage.Key::class)
class MemberImage(
        @Id @Column(name = "member_id") val memberId: Long,
        @Id val url: String,
        val sequence: Int,
        var represent: Boolean,
) {
    data class Key(val memberId: Long = 0, val url: String = "") : Serializable
}
