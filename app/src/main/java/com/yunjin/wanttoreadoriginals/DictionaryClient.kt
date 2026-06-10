package com.yunjin.wanttoreadoriginals

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class DictionaryClient {
    private val client = OkHttpClient()

    fun lookup(raw: String): String {
        val clean = raw.trim()
            .replace(Regex("^[^A-Za-z]+|[^A-Za-z-]+$"), "")
            .lowercase()
        if (clean.isBlank()) return "검색할 영어 단어가 비어 있음"

        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(clean, "UTF-8")}" 
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return "뜻을 못 찾음: $clean"
            val body = res.body?.string().orEmpty()
            val root = JSONArray(body)
            val first = root.getJSONObject(0)
            val phonetic = first.optString("phonetic", "")
            val meanings = first.optJSONArray("meanings") ?: return clean
            val lines = mutableListOf<String>()
            lines.add(clean + if (phonetic.isNotBlank()) "  $phonetic" else "")
            for (i in 0 until minOf(4, meanings.length())) {
                val m = meanings.getJSONObject(i)
                val part = m.optString("partOfSpeech", "")
                val defs = m.optJSONArray("definitions")
                if (defs != null && defs.length() > 0) {
                    lines.add("[$part] " + defs.getJSONObject(0).optString("definition"))
                }
            }
            return lines.joinToString("\n")
        }
    }
}
