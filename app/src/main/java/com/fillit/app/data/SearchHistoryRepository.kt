package com.fillit.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class SearchHistoryItem(
    val query: String,
    val timestamp: Timestamp
)

class SearchHistoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun add(userId: String, query: String) {
        val uid = userId.trim()
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (uid.isBlank() || q.isBlank()) return

        db.collection("search_history").add(
            mapOf(
                "userId" to uid,
                "query" to q,
                "timestamp" to Timestamp.now()
            )
        ).await()
    }

    suspend fun getRecent(userId: String, limit: Long = 20): List<SearchHistoryItem> {
        val uid = userId.trim()
        if (uid.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)

        return try {
            db.collection("search_history")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .await()
                .documents
                .mapNotNull {
                    val q = it.getString("query") ?: return@mapNotNull null
                    val ts = it.getTimestamp("timestamp") ?: Timestamp.now()
                    SearchHistoryItem(q, ts)
                }
        } catch (e: FirebaseFirestoreException) {
            // Composite index not ready -> fallback
            if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                db.collection("search_history")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                    .documents
                    .mapNotNull {
                        val q = it.getString("query") ?: return@mapNotNull null
                        val ts = it.getTimestamp("timestamp") ?: Timestamp.now()
                        SearchHistoryItem(q, ts)
                    }
                    .sortedByDescending { it.timestamp.seconds }
                    .take(safeLimit.toInt())
            } else {
                throw e
            }
        }
    }

    suspend fun getFrequency(userId: String, limit: Long = 200): Map<String, Int> {
        val uid = userId.trim()
        if (uid.isBlank()) return emptyMap()
        val safeLimit = limit.coerceIn(1, 500)

        val items: List<SearchHistoryItem> = try {
            db.collection("search_history")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .await()
                .documents
                .mapNotNull {
                    val q = it.getString("query") ?: return@mapNotNull null
                    val ts = it.getTimestamp("timestamp") ?: Timestamp.now()
                    SearchHistoryItem(q, ts)
                }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                db.collection("search_history")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                    .documents
                    .mapNotNull {
                        val q = it.getString("query") ?: return@mapNotNull null
                        val ts = it.getTimestamp("timestamp") ?: Timestamp.now()
                        SearchHistoryItem(q, ts)
                    }
                    .sortedByDescending { it.timestamp.seconds }
                    .take(safeLimit.toInt())
            } else {
                throw e
            }
        }

        return items
            .mapNotNull {
                it.query.trim()
                    .lowercase(Locale.getDefault())
                    .takeIf(String::isNotBlank)
            }
            .groupingBy { it }
            .eachCount()
    }
}
