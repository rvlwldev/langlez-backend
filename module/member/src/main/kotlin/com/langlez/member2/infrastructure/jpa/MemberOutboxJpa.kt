package com.langlez.member2.infrastructure.jpa

import com.langlez.member2.domain.MemberOutbox
import com.langlez.rdb.outbox.OutBox
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutboxJpa : JpaRepository<MemberOutbox, Long> {

    fun findAllByStatus(status: OutBox.Status): List<MemberOutbox>

}