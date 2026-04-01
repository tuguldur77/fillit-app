package com.fillit.app.notifications

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fillit.app.R
import com.fillit.app.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RecommendationNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun doWork(): Result {
        val enabled = runBlocking {
            UserPreferencesRepository(applicationContext).notifyRecommendationsFlow.first()
        }
        if (!enabled) return Result.success()

        val categories = inputData.getStringArray("categories")?.toList().orEmpty()
        val radius = inputData.getInt("radius", 0)
        val summary = if (categories.isEmpty()) {
            "설정한 관심 카테고리가 없습니다. 설정에서 추가해보세요."
        } else {
            categories.joinToString(", ")
        }
        val message = "곧 빈 시간이 시작됩니다. 근처(${radius}m) 추천 카테고리: $summary"
        showNotification(message)
        return Result.success()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showNotification(content: String) {
        NotificationHelper.init(applicationContext) // ensure channel
        val builder = NotificationCompat.Builder(applicationContext, NotificationHelper.RECOMMEND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // ensure this drawable exists or replace
            .setContentTitle("추천 장소 알림")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, builder.build())
    }

    companion object {
        private const val NOTIF_ID = 1001
    }
}
