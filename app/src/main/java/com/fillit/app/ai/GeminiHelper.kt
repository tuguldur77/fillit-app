package com.fillit.app.ai

import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object GeminiHelper {
    private val MASTER_KEYWORDS = setOf(
        "조용", "감성", "편안", "작업하기좋은", "대화하기좋은",
        "좌석많은", "채광좋은", "인테리어좋은", "데이트", "모임적합",
        "시끄러운", "혼잡한", "주차편리", "반려동물가능", "루프탑"
    )

    suspend fun generateKeywords(
        apiKey: String,
        placeName: String,
        address: String? = null,
        rating: String? = null,
        types: List<String>? = null,
        primaryType: String? = null,
        editorialSummary: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || placeName.isBlank()) return@withContext emptyList()

        val prompt = """
You are a Korean place keyword tagger for a lifestyle recommendation app.
Analyze this place and assign atmosphere and feature keywords.

Place name: "${placeName}"
Place types: "${(types ?: emptyList()).joinToString(", ")}"
Rating: "${rating.orEmpty()}"
Address: "${address.orEmpty()}"

Return ONLY a JSON array. No explanation. No markdown. No backticks.
Example output: ["조용", "감성", "편안"]

MASTER KEYWORD LIST — choose ONLY from these exact strings:
["조용", "감성", "편안", "작업하기좋은", "대화하기좋은",
 "좌석많은", "채광좋은", "인테리어좋은", "데이트", "모임적합",
 "시끄러운", "혼잡한", "주차편리", "반려동물가능", "루프탑"]

STRICT RULES:
- ONLY return keywords from the master list above
- Do NOT include brand names (블루보틀, 스타벅스, 이디야 etc.)
- Do NOT include location names (명동, 성수, 홍대, 강남 etc.)
- Do NOT include any word not in the master list
- Return minimum 3, maximum 8 keywords
- Think about the atmosphere and features of this type of place
""".trimIndent()

        return@withContext try {
            val client = GenerativeClient(apiKey)
            val candidateModels = listOf(
                "models/gemini-2.5-flash",
                "models/gemini-2.5-pro",
                "models/gemini-1.5-flash"
            )

            var normalized: List<String> = emptyList()
            for (model in candidateModels) {
                val raw = client.generate(model, prompt)
                Log.d("GeminiHelper", "model=$model raw='${raw.take(240)}'")
                val parsed = parseJsonArrayKeywords(raw)
                normalized = normalizeKeywords(parsed)
                if (normalized.size >= 5) break
            }

            if (normalized.size >= 5) normalized
            else fallbackKeywords(placeName, address, rating, types, primaryType)
        } catch (e: Throwable) {
            Log.e("GeminiHelper", "Gemini REST failed: ${e.message}")
            fallbackKeywords(placeName, address, rating, types, primaryType)
        }
    }

    private fun parseJsonArrayKeywords(raw: String): List<String> {
        // 1) direct parse
        runCatching {
            val arr = JSONArray(raw.trim())
            return (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
        }

        // 2) extract first JSON array block if extra text exists
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start >= 0 && end > start) {
            val block = raw.substring(start, end + 1)
            runCatching {
                val arr = JSONArray(block)
                return (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
            }
        }
        return emptyList()
    }

    private fun normalizeKeywords(input: List<String>): List<String> {
        val unique = LinkedHashSet<String>()
        input.forEach { k ->
            val t = k.trim()
                .replace(Regex("\\s+"), " ")
                .replace("\"", "")
                .replace("'", "")
            if (t.isBlank()) return@forEach
            if (t !in MASTER_KEYWORDS) return@forEach
            if (t.length !in 2..24) return@forEach
            unique += t
        }
        val list = unique.toList()
        // keep min 3 ~ max 8 from strict master list
        return when {
            list.size >= 8 -> list.take(8)
            else -> list
        }
    }

    private fun fallbackKeywords(
        placeName: String,
        address: String?,
        rating: String?,
        types: List<String>?,
        primaryType: String?
    ): List<String> {
        val out = LinkedHashSet<String>()
        val lowerName = placeName.lowercase(Locale.KOREA)
        val addr = address.orEmpty()

        // 분위기
        out += listOf("조용한", "감성적인", "편안한")
        // 활동
        out += listOf("작업하기좋은", "대화하기좋은")
        // 공간 특징
        out += listOf("좌석많은", "채광좋은", "인테리어좋은")
        // 방문 목적
        out += listOf("데이트", "모임적합")
        // 사용자 유형
        out += listOf("혼자가기좋은", "친구와가기좋은")

        // location hints
        if (addr.contains("강남")) out += "강남권"
        if (addr.contains("홍대")) out += "홍대권"
        if (addr.contains("성수")) out += "성수권"
        if (addr.contains("종로")) out += "종로권"

        // type hints
        val mergedTypes = ((types ?: emptyList()) + listOfNotNull(primaryType)).joinToString(" ").lowercase(Locale.KOREA)
        if ("cafe" in mergedTypes || "카페" in mergedTypes) out += listOf("디저트강한", "브런치적합")
        if ("restaurant" in mergedTypes || "음식점" in mergedTypes) out += listOf("식사중심", "가성비좋은")
        if ("museum" in mergedTypes || "gallery" in mergedTypes || "전시" in mergedTypes) out += listOf("문화체험", "관람중심")
        if ("shopping" in mergedTypes || "store" in mergedTypes) out += listOf("쇼핑동선좋은", "둘러보기좋은")

        // brand/name hints
        if ("스터디" in lowerName) out += "집중하기좋은"
        if ("루프탑" in lowerName) out += "뷰좋은"
        if ("24" in lowerName) out += "늦은시간방문"

        // rating hint
        rating?.toDoubleOrNull()?.let { r ->
            if (r >= 4.5) out += "만족도높은"
            else if (r >= 4.0) out += "재방문의사높은"
        }

        val cleaned = out
            .map { it.trim() }
            .filter { it.isNotBlank() && it in MASTER_KEYWORDS }
            .distinct()
            .take(8)

        // fallback must still be rich enough
        return if (cleaned.size >= 3) cleaned else {
            (cleaned + listOf("감성", "편안", "대화하기좋은"))
                .distinct()
                .filter { it in MASTER_KEYWORDS }
                .take(8)
        }
    }
}
