package com.langlez.member.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberAuthenticator
import com.langlez.member.contract.MemberAuthenticator.AccountInfo
import com.langlez.member.domain.Member
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/**
 * 소셜 로그인 진입 유스케이스. `MemberAuthenticator` 계약을 이 클래스가 직접 구현한다.
 *
 * infrastructure 에 어댑터를 두지 않는 이유는 [MemberSuspender] 와 같다. 조회 포트
 * (`MemberReader`)는 `MemberRepository`(domain)를 읽어 매핑하는 것뿐이라 어댑터가 domain 만
 * 보지만, `authenticate` 는 조회+생성+상태검사가 묶인 유스케이스라 어댑터를 infrastructure 에
 * 두면 그 어댑터가 application 을 참조하게 되어 의존 방향이 뒤집힌다.
 */
@Component
class MemberSignIn(
    private val service: MemberService,
) : MemberAuthenticator {

    override fun authenticate(
        provider: String,
        providerId: String,
        email: String?,
        displayName: String?,
    ): AccountInfo {
        if (providerId.isBlank()) throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
        val type = runCatching { Member.Provider.valueOf(provider.uppercase()) }
            .getOrElse { throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request") }

        val member = service.findByProvider(type, providerId) ?: run {
            val safeEmail = email ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
            if (service.findByEmail(safeEmail) != null) {
                throw LanglezException(HttpStatus.CONFLICT, "auth.email-conflict")
            }
            val name = (displayName ?: "").take(20)

            return@run service.createMember(type, providerId, safeEmail, name)
        }

        // 정지/탈퇴 회원이 소셜 로그인으로 되살아나면 안 된다.
        try {
            member.requireActive()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.FORBIDDEN, e.message, e)
        }

        return member.toAccountInfo()
    }

    override fun findLoginable(memberId: Long): AccountInfo? {
        val member = service.findById(memberId) ?: return null

        // 정지/탈퇴 사유를 잃으면 안 된다 — authenticate 와 같은 규약.
        try {
            member.requireActive()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.FORBIDDEN, e.message, e)
        }

        return member.toAccountInfo()
    }

    private fun Member.toAccountInfo() = AccountInfo(id, handle, role.authority)
}
