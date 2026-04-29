package com.fillit.app.ai

import android.util.Log
import com.fillit.app.model.CalendarContext
import com.fillit.app.model.CalendarGap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CalendarContextAnalyzer(
    private val apiKey: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "CalendarContext"
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)

        private val MASTER_KEYWORD_LIST = setOf(
            "조용", "감성", "편안", "작업하기좋은", "대화하기좋은",
            "좌석많은", "채광좋은", "인테리어좋은", "데이트", "모임적합",
            "시끄러운", "혼잡한", "주차편리", "반려동물가능", "루프탑"
        )

        private val MOODS = setOf("business", "social", "relaxed", "romantic", "energetic")
        private val ENERGIES = setOf("low", "medium", "high")
        private val SOCIALS = setOf("solo", "duo", "group")
    }

    suspend fun analyze(gap: CalendarGap, uid: String): CalendarContext? {
        Log.d(TAG, "=== CalendarContextAnalyzer START ===")
        Log.d(TAG, "gap.previousEvent: ${gap.previousEvent?.title}")
        Log.d(TAG, "gap.nextEvent: ${gap.nextEvent?.title}")
        Log.d(TAG, "gap.gapDurationMinutes: ${gap.gapDurationMinutes}")

        if (uid.isBlank()) {
            Log.d(TAG, "skip analyze: no user")
            Log.d(TAG, "=== CalendarContextAnalyzer END -> result: null ===")
            return null
        }

        if (gap.previousEvent == null && gap.nextEvent == null) {
            Log.w(TAG, "Both events null -> returning null")
            Log.d(TAG, "=== CalendarContextAnalyzer END -> result: null ===")
            return null
        }

        val gapId = buildGapId(gap) ?: return null

        // DEBUG: set true to always call Gemini fresh
        val debugBypassCache = true

        return try {
            val cached = if (!debugBypassCache) {
                Log.d(TAG, "Checking cache for gapId: $gapId")
                withContext(Dispatchers.IO) { loadFreshCache(uid, gapId) }
            } else {
                Log.d(TAG, "DEBUG: bypassing cache for fresh analysis")
                null
            }
            Log.d(TAG, "Cache hit: ${cached != null}")

            cached?.also {
                Log.d(TAG, "cache hit: $gapId")
            } ?: run {
                Log.d(TAG, "cache miss for $gapId")
                if (apiKey.isBlank()) {
                    Log.d(TAG, "skip Gemini: api key is blank")
                    return null
                }

                val timeOfDay = resolveTimeOfDay(gap.gapStartTime)
                val prompt = buildPrompt(gap, timeOfDay)

                Log.d(TAG, "Calling Gemini with prompt...")
                Log.d(TAG, "previousTitle: ${gap.previousEvent?.title ?: "없음"}")
                Log.d(TAG, "nextTitle: ${gap.nextEvent?.title ?: "없음"}")

                val rawResponse = withTimeout(15000L) {
                    GenerativeClient(apiKey).generate("models/gemini-2.5-flash", prompt)
                }
                Log.d(TAG, "Gemini raw response: $rawResponse")

                val parsed = parseContext(rawResponse)
                if (parsed == null) {
                    Log.d(TAG, "gemini parse failed -> null")
                    null
                } else {
                    Log.d(TAG, "Validated suggestedKeywords: ${parsed.suggestedKeywords}")
                    Log.d(TAG, "Validated avoidKeywords: ${parsed.avoidKeywords}")
                    Log.d(TAG, "Saving to cache: $gapId")
                    withContext(Dispatchers.IO) { saveCache(uid, gapId, gap, parsed) }
                    Log.d(TAG, "result: mood=${parsed.mood} keywords=${parsed.suggestedKeywords}")
                    parsed
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Gemini timed out after 5000ms: ${e.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "analyze failed: ${e.message}")
            null
        }.also { result ->
            Log.d(TAG, "=== CalendarContextAnalyzer END -> result: $result ===")
        }
    }

    private suspend fun loadFreshCache(uid: String, gapId: String): CalendarContext? {
        return try {
            val doc = db.collection("users").document(uid)
                .collection("calendarContexts")
                .document(gapId)
                .get()
                .await()

            if (!doc.exists()) return null

            val analyzedAt = doc.getTimestamp("analyzedAt")?.toDate()?.time ?: return null
            val age = System.currentTimeMillis() - analyzedAt
            if (age >= CACHE_TTL_MS) {
                Log.d(TAG, "cache stale gapId=$gapId ageMs=$age")
                return null
            }

            val mood = doc.getString("mood") ?: return null
            val energy = doc.getString("energy") ?: return null
            val social = doc.getString("social") ?: return null
            val suggested = (doc.get("suggestedKeywords") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val avoid = (doc.get("avoidKeywords") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            CalendarContext(
                mood = mood,
                energy = energy,
                social = social,
                suggestedKeywords = suggested.filter { MASTER_KEYWORD_LIST.contains(it) },
                avoidKeywords = avoid.filter { MASTER_KEYWORD_LIST.contains(it) }
            )
        } catch (e: Exception) {
            Log.d(TAG, "cache read failed: ${e.message}")
            null
        }
    }

    private suspend fun saveCache(uid: String, gapId: String, gap: CalendarGap, context: CalendarContext) {
        try {
            val previousTitle = gap.previousEvent?.title ?: "없음"
            val nextTitle = gap.nextEvent?.title ?: "없음"
            val payload = mapOf(
                "mood" to context.mood,
                "energy" to context.energy,
                "social" to context.social,
                "suggestedKeywords" to context.suggestedKeywords,
                "avoidKeywords" to context.avoidKeywords,
                "analyzedAt" to Timestamp.now(),
                "previousEventTitle" to previousTitle,
                "nextEventTitle" to nextTitle,
                "gapDurationMinutes" to gap.gapDurationMinutes
            )
            db.collection("users").document(uid)
                .collection("calendarContexts")
                .document(gapId)
                .set(payload, SetOptions.merge())
                .await()
            Log.d(TAG, "cached: $gapId")
        } catch (e: Exception) {
            Log.d(TAG, "cache save failed: ${e.message}")
        }
    }

    private fun parseContext(rawInput: String): CalendarContext? {
        return try {
            val raw = stripMarkdown(rawInput)
            val json = JSONObject(raw)

            val mood = json.optString("mood", "").trim()
            val energy = json.optString("energy", "").trim()
            val social = json.optString("social", "").trim()

            if (!MOODS.contains(mood) || !ENERGIES.contains(energy) || !SOCIALS.contains(social)) {
                return null
            }

            val suggested = mutableListOf<String>()
            val avoid = mutableListOf<String>()

            val suggestedArr = json.optJSONArray("suggestedKeywords")
            if (suggestedArr != null) {
                for (i in 0 until suggestedArr.length()) {
                    val k = suggestedArr.optString(i).trim()
                    if (MASTER_KEYWORD_LIST.contains(k)) suggested += k
                }
            }

            val avoidArr = json.optJSONArray("avoidKeywords")
            if (avoidArr != null) {
                for (i in 0 until avoidArr.length()) {
                    val k = avoidArr.optString(i).trim()
                    if (MASTER_KEYWORD_LIST.contains(k)) avoid += k
                }
            }

            CalendarContext(
                mood = mood,
                energy = energy,
                social = social,
                suggestedKeywords = suggested.distinct(),
                avoidKeywords = avoid.distinct()
            )
        } catch (e: Exception) {
            Log.d(TAG, "parseContext failed: ${e.message}")
            null
        }
    }

    private fun stripMarkdown(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```") ) return trimmed

        val firstNewLine = trimmed.indexOf('\n')
        if (firstNewLine <= 0) return trimmed.removePrefix("```").removeSuffix("```").trim()

        val withoutHeader = trimmed.substring(firstNewLine + 1)
        return withoutHeader.removeSuffix("```").trim()
    }

    private fun resolveTimeOfDay(epochMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> "MORNING"
            in 12..17 -> "AFTERNOON"
            else -> "NIGHT"
        }
    }

    private fun buildPrompt(gap: CalendarGap, timeOfDay: String): String {
        val previousTitle = gap.previousEvent?.title ?: "없음"
        val nextTitle = gap.nextEvent?.title ?: "없음"
        return """
You are a place recommendation assistant for a Korean lifestyle app.
Analyze this calendar schedule and extract place preference keywords.

Previous event: "$previousTitle"
Next event: "$nextTitle"
Free gap duration: "${gap.gapDurationMinutes} minutes"
Time of day: "${timeOfDay}"

Return ONLY a JSON object. No explanation. No markdown. No backticks.
{
  "mood": "business | social | relaxed | romantic | energetic",
  "energy": "low | medium | high",
  "social": "solo | duo | group",
  "suggestedKeywords": [],
  "avoidKeywords": []
}

Choose ONLY from this keyword list:
["조용", "감성", "편안", "작업하기좋은", "대화하기좋은",
 "좌석많은", "채광좋은", "인테리어좋은", "데이트", "모임적합",
 "시끄러운", "혼잡한", "주차편리", "반려동물가능", "루프탑"]

RULES:
- Only use keywords from the list above
- Return 2 to 5 suggestedKeywords
- Return 0 to 3 avoidKeywords
- No brand names, no location names

Examples:
Previous: "팀 미팅" -> suggestedKeywords: ["조용", "작업하기좋은", "대화하기좋은"]
Next: "데이트" -> suggestedKeywords: ["감성", "인테리어좋은", "데이트"]
Previous: "헬스장" -> suggestedKeywords: ["편안"], avoidKeywords: ["혼잡한"]
""".trimIndent()
    }

    private fun buildGapId(gap: CalendarGap): String? {
        val prevId = gap.previousEvent?.id
        val nextId = gap.nextEvent?.id
        val raw = when {
            !prevId.isNullOrBlank() && !nextId.isNullOrBlank() -> "${prevId}_${nextId}"
            !prevId.isNullOrBlank() -> "prev_${prevId}_${gap.gapStartTime}"
            !nextId.isNullOrBlank() -> "next_${nextId}_${gap.gapStartTime}"
            else -> return null
        }
        return raw.replace("/", "_")
    }
}
