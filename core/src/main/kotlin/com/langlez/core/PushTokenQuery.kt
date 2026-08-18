package com.langlez.core

/**
 * 푸시 토큰 조회. member 모듈이 구현한다.
 *
 * 토큰을 이벤트 페이로드에 실어 보내지 않는 이유: 브로커·DLT·로그에 기기 자격증명이 그대로 남고,
 * 발행 시점 값이라 그 사이 토큰이 재발급되면 죽은 토큰으로 쏘게 된다. 보낼 직전에 조회하는 게 맞다.
 */
interface PushTokenQuery {
    /** 토큰이 없으면(로그아웃·푸시 거부) null */
    fun findPushToken(memberId: Long): String?
}
