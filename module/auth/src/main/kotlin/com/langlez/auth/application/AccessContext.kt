package com.langlez.auth.application

/** 요청이 들어온 기기와 IP. 1인 1기기 검증과 마지막 접속 기록에 쓴다. */
data class AccessContext(val ip: String? = null, val deviceId: String? = null)
