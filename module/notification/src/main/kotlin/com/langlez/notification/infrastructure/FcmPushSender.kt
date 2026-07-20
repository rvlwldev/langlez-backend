package com.langlez.notification.infrastructure

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification as FcmNotification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class FcmPushSender {
    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)
    private var initialized = false

    init {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build()
                FirebaseApp.initializeApp(options)
            }
            initialized = true
        } catch (e: Exception) {
            logger.warn("Failed to initialize FirebaseApp. Push notifications will be disabled: {}", e.message)
        }
    }

    @Async
    fun send(fcmToken: String, title: String, body: String, data: Map<String, String>) {
        if (!initialized) {
            logger.warn("FirebaseApp is not initialized. Skip sending push notification to token: {}", fcmToken)
            return
        }
        try {
            val fcmNotification = FcmNotification.builder()
                .setTitle(title)
                .setBody(body)
                .build()

            val message = Message.builder()
                .setToken(fcmToken)
                .setNotification(fcmNotification)
                .putAllData(data)
                .build()

            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send FCM push notification to token {}: {}", fcmToken, e.message)
        }
    }
}
