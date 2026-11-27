package com.example.livecomments

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Comment(val id: String, val author: String, val text: String)

data class ChatPage(
    val comments: List<Comment>,
    val nextPageToken: String?,
    val pollingIntervalMs: Long
)

class YouTubeApiException(val code: Int, message: String) : Exception(message)

/** Minimal client for the YouTube Data API v3 live chat endpoints. */
class YouTubeLiveChat(private val apiKey: String) {

    private val base = "https://www.googleapis.com/youtube/v3"

    /** Accepts a full YouTube link (watch, youtu.be, live, embed) or a bare 11-char video ID. */
    fun extractVideoId(input: String): String? {
        val s = input.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) return s
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("/(?:live|embed|shorts)/([A-Za-z0-9_-]{11})")
        )
        for (p in patterns) p.find(s)?.let { return it.groupValues[1] }
        return null
    }

    /** Returns the activeLiveChatId for a live video, or throws if it is not live. */
    fun getLiveChatId(videoId: String): String {
        val url = "$base/videos?part=liveStreamingDetails&id=${enc(videoId)}&key=${enc(apiKey)}"
        val json = get(url)
        val items = json.optJSONArray("items")
        if (items == null || items.length() == 0) {
            throw YouTubeApiException(404, "Video not found.")
        }
        val details = items.getJSONObject(0).optJSONObject("liveStreamingDetails")
        val chatId = details?.optString("activeLiveChatId", "").orEmpty()
        if (chatId.isEmpty()) {
            throw YouTubeApiException(404, "This video has no active live chat (is it live right now?).")
        }
        return chatId
    }

    fun fetchMessages(liveChatId: String, pageToken: String?): ChatPage {
        val sb = StringBuilder("$base/liveChat/messages?liveChatId=${enc(liveChatId)}")
        sb.append("&part=snippet,authorDetails&maxResults=200&key=${enc(apiKey)}")
        if (!pageToken.isNullOrEmpty()) sb.append("&pageToken=${enc(pageToken)}")
        val json = get(sb.toString())

        val out = ArrayList<Comment>()
        val items = json.optJSONArray("items")
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val snippet = item.optJSONObject("snippet") ?: continue
                // Only plain chat messages carry displayMessage text we want to read
                if (snippet.optString("type") != "textMessageEvent") continue
                val text = snippet.optString("displayMessage")
                if (text.isBlank()) continue
                val author = item.optJSONObject("authorDetails")?.optString("displayName").orEmpty()
                out.add(Comment(item.optString("id"), author, text))
            }
        }
        return ChatPage(
            comments = out,
            nextPageToken = json.optString("nextPageToken").ifEmpty { null },
            pollingIntervalMs = json.optLong("pollingIntervalMillis", 5000L).coerceAtLeast(2000L)
        )
    }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(body).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP $code"
                }
                throw YouTubeApiException(code, msg)
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
