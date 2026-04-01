package com.fillit.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val RECOMMEND_CHANNEL_ID = "recommendations"
    const val SCHEDULE_CHANNEL_ID = "schedule"

    private const val RECOMMEND_CHANNEL_NAME = "추천 알림"
    private const val RECOMMEND_CHANNEL_DESC = "빈 시간 전 추천 장소 안내"
    private const val SCHEDULE_CHANNEL_NAME = "일정 알림"
    private const val SCHEDULE_CHANNEL_DESC = "다가오는 일정 안내"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (nm.getNotificationChannel(RECOMMEND_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        RECOMMEND_CHANNEL_ID,
                        RECOMMEND_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = RECOMMEND_CHANNEL_DESC }
                )
            }

            if (nm.getNotificationChannel(SCHEDULE_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        SCHEDULE_CHANNEL_ID,
                        SCHEDULE_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = SCHEDULE_CHANNEL_DESC }
                )
            }
        }
    }
}
