package com.langlez.member.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxRepository

interface MemberOutBoxRepository : OutBoxRepository<MemberOutBox, MemberOutBoxHistory>
