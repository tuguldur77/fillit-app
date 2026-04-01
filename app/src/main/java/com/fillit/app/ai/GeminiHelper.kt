package com.fillit.app.ai

import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object GeminiHelper {
    // Common generic words to avoid in final keywords
    private val banned = setOf(
        "the","a","an","of","and","at","in","on","for","to","with","by",
        "place","spot","area","center","downtown","uptown",
        "popular","famous","nice","great","good","best",
        "landmark","scenic","walking","tourist","seoul" // prefer more specific locales like jongno, myeongdong
    )

    private val koreanBlacklist = setOf("카페", "커피", "맛집", "사진", "음식", "장소")

    suspend fun generateKeywords(
        apiKey: String,
        placeName: String,
        address: String? = null,
        rating: String? = null,
        types: List<String>? = null,
        primaryType: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || placeName.isBlank()) return@withContext emptyList()

        val prompt = """
다음 장소를 사용자의 취향 분석용으로 태깅하려고 합니다.

목표:
이 장소의 일반 카테고리(예: 카페, 커피, 맛집, 사진)가 아니라,
추천 알고리즘에 도움이 되는 더 구체적인 취향 키워드를 생성하세요.

반드시 지켜야 할 조건:
- 총 8~10개의 키워드를 생성하세요.
- 서로 비슷한 말만 반복하지 마세요.
- 너무 일반적인 단어는 제외하세요.
  제외 예시: 카페, 커피, 맛집, 사진, 음식, 장소
- 아래 5개 관점을 고르게 반영하세요:
  1) 분위기
  2) 활동
  3) 공간 특징
  4) 방문 목적
  5) 어울리는 사용자 유형
- 각 키워드는 짧은 한국어 표현으로 작성하세요.
- 한 단어 또는 짧은 구 형태로 작성하세요.
- 설명 없이 JSON 배열만 출력하세요.

장소 정보:
이름: ${placeName}
주소: ${address.orEmpty()}
평점: ${rating.orEmpty()}
타입들: ${(types ?: emptyList()).joinToString(", ")}
대표 타입: ${primaryType.orEmpty()}

좋은 예시:
["조용한", "작업하기좋은", "채광좋은", "데이트", "대화하기좋은", "혼자가기좋은", "좌석많은", "감성적인", "인테리어좋은", "디저트강한"]

나쁜 예시:
["카페", "커피", "사진", "맛집"]

JSON 배열만 출력하세요.
""".trimIndent()

        return@withContext try {
            val client = GenerativeClient(apiKey)
            val raw = client.generate("models/gemini-1.5-flash", prompt)
            Log.d("GeminiHelper", "Raw Gemini response='${raw.take(240)}'")

            val parsed = parseJsonArrayKeywords(raw)
            val normalized = normalizeKeywords(parsed)

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
            if (t in koreanBlacklist) return@forEach
            if (t.length !in 2..24) return@forEach
            unique += t
        }
        val list = unique.toList()
        // keep 8~10 if possible
        return when {
            list.size >= 10 -> list.take(10)
            list.size >= 8 -> list
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
            .filter { it.isNotBlank() && it !in koreanBlacklist }
            .distinct()
            .take(10)

        // fallback must still be rich enough
        return if (cleaned.size >= 8) cleaned else {
            (cleaned + listOf("분위기좋은", "목적방문적합", "취향반영가능"))
                .distinct()
                .filter { it !in koreanBlacklist }
                .take(10)
        }
    }
}
