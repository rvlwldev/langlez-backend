package com.langlez.member.domain

import com.langlez.member.domain.embedded.MemberAudit
import com.langlez.member.domain.embedded.MemberProvider
import jakarta.persistence.*
import jakarta.persistence.CascadeType.ALL

@Entity

@Table(

    name = "members",

    uniqueConstraints = [

        UniqueConstraint(name = "UNQ_PROVIDER", columnNames = ["provider_id", "provider_type"]),

        UniqueConstraint(name = "UNQ_EMAIL", columnNames = ["email"]),

        UniqueConstraint(name = "UNQ_USERNAME", columnNames = ["username"])

    ],

)

class Member(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)

    val id: Long = 0,



    val email: String,

    var password: String? = null,



    @Column(length = 20)

    var username: String? = null,



        @Version
        var version: Long = 0,



    var agreeToTerms: Boolean = false,

    var nickname: String,



    @Enumerated(EnumType.STRING)

    var role: Role = Role.MEMBER,



    var isInitDone: Boolean = false,



    @Embedded var provider: MemberProvider,

    @Embedded var audit: MemberAudit,



    @OneToMany(cascade = [ALL], orphanRemoval = true)

    @JoinColumn(name = "member_id")

    val images: MutableList<MemberImage> = mutableListOf(),

) {

    val isDeleted: Boolean get() = audit.isDeleted



    fun login() { audit.login() }

    fun upgradeToPremium() { role = Role.PREMIUM }

    fun delete() { audit.delete() }



    enum class Role { MEMBER, PREMIUM, ADMIN }

    companion object {

        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")

        fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matches(username)



        fun create(nickname: String, email: String, providerId: String, providerType: String, providerUserName: String) =

            Member(

                nickname = nickname,

                email = email,

                provider = MemberProvider(providerId, MemberProvider.Type.valueOf(providerType), providerUserName),

                audit = MemberAudit()

            )

    }

}




