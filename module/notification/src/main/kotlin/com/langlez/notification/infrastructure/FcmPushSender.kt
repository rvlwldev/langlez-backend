package com.langlez.notification.infrastructure

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.langlez.notification.domain.PushSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.FileInputStream
import com.google.firebase.messaging.Notification as FcmNotification

/**
 * FCM 푸시 어댑터.
 *
 * `fcm.credentials`(서비스 계정 JSON 경로)가 비어 있으면 전송하지 않고 **매번 경고를 남긴다.**
 * 로컬·테스트에 운영 키를 두지 않으려는 것이고, 운영에서 설정을 빠뜨리면 로그로 즉시 드러난다.
 * 조용히 성공한 척하면 "알림이 안 온다"는 제보가 올 때까지 아무도 모른다.
 *
 * 초기화를 지연시키는 이유: 빈 생성 시점에 자격증명을 읽으면 키가 없는 환경에서 컨텍스트가 통째로 안 뜬다.
 */
@Component
class FcmPushSender(
    @param:Value($$"${fcm.credentials:}") private val credentials: String,
) : PushSender {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val messaging: FirebaseMessaging? by lazy { initialize() }

    override fun send(token: String, title: String, body: String, data: Map<String, String>) {
        val fcm = messaging
        if (fcm == null) {
            logger.warn("FCM 자격증명(fcm.credentials)이 없어 푸시를 보내지 못했다. 알림 이력만 남는다.")
            return
        }

        fcm.send(
            Message.builder()
                .setToken(token)
                // notification 은 앱이 꺼져 있어도 OS 가 띄운다. data 는 탭했을 때 이동할 화면 정보다.
                .setNotification(FcmNotification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build()
        )
    }

    private fun initialize(): FirebaseMessaging? {
        if (credentials.isBlank()) return null

        return runCatching {
            // 컨텍스트가 여러 번 뜨는 테스트에서 같은 이름으로 재초기화하면 IllegalStateException 이 난다.
            val app = FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                        .setCredentials(FileInputStream(credentials).use(GoogleCredentials::fromStream))
                        .build(),
                    APP_NAME,
                )

            FirebaseMessaging.getInstance(app)
        }.onFailure { logger.error("FCM 초기화 실패: credentials={}", credentials, it) }.getOrNull()
    }

    private companion object {
        const val APP_NAME = "langlez-notification"
    }
}
