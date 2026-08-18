package com.langlez.member.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "member_outbox_history", indexes = [Index(name = "IDX_MEMBER_OUTBOX_DOMAIN", columnList = "domain")])
class MemberOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)