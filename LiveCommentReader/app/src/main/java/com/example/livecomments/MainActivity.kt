package com.example.livecomments

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var apiKeyInput: EditText
    private lateinit var videoInput: EditText
    private lateinit var startButton: Button
    private lateinit var speakSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var commentList: RecyclerView

    private val adapter = CommentAdapter()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        videoInput = findViewById(R.id.videoInput)
        startButton = findViewById(R.id.startButton)
        speakSwitch = findViewById(R.id.speakSwitch)
        statusText = findViewById(R.id.statusText)
        commentList = findViewById(R.id.commentList)

        commentList.layoutManager = LinearLayoutManager(this)
        commentList.adapter = adapter

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("apiKey", ""))
        videoInput.setText(prefs.getString("video", ""))

        tts = TextToSpeech(this, this)

        startButton.setOnClickListener {
            if (pollJob?.isActive == true) {
                stopPolling("Stopped.")
            } else {
                prefs.edit()
                    .putString("apiKey", apiKeyInput.text.toString().trim())
                    .putString("video", videoInput.text.toString().trim())
                    .apply()
                startPolling()
            }
        }

        speakSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) tts?.stop()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Falls back to the device default if the device language isn't available
            val result = tts?.setLanguage(Locale.getDefault())
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                ttsReady = tts?.setLanguage(Locale.ENGLISH) == TextToSpeech.SUCCESS
            }
        }
    }

    private fun startPolling() {
        val apiKey = apiKeyInput.text.toString().trim()
        if (apiKey.isEmpty()) {
            setStatus("Please enter your YouTube API key.")
            return
        }
        val client = YouTubeLiveChat(apiKey)
        val videoId = client.extractVideoId(videoInput.text.toString())
        if (videoId == null) {
            setStatus("Couldn't find a video ID in that link.")
            return
        }

        adapter.clear()
        startButton.text = "Stop"
        setStatus("Connecting...")

        pollJob = lifecycleScope.launch {
            try {
                val chatId = withContext(Dispatchers.IO) { client.getLiveChatId(videoId) }
                setStatus("Connected. Waiting for comments...")

                var pageToken: String? = null
                var firstPage = true
                while (isActive) {
                    val page = withContext(Dispatchers.IO) { client.fetchMessages(chatId, pageToken) }
                    pageToken = page.nextPageToken

                    if (page.comments.isNotEmpty()) {
                        adapter.addAll(page.comments)
                        commentList.scrollToPosition(adapter.itemCount - 1)
                        // The first page is chat history: show it, but don't read it all aloud
                        if (!firstPage) speak(page.comments)
                    }
                    firstPage = false
                    setStatus("Live - ${adapter.itemCount} comments")
                    delay(page.pollingIntervalMs)
                }
            } catch (e: YouTubeApiException) {
                val hint = when (e.code) {
                    403 -> " (quota used up, key restricted, or the chat has ended)"
                    400 -> " (check your API key)"
                    else -> ""
                }
                stopPolling("Error: ${e.message}$hint")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                stopPolling("Connection problem: ${e.message}")
            }
        }
    }

    private fun stopPolling(message: String) {
        pollJob?.cancel()
        pollJob = null
        tts?.stop()
        startButton.text = "Start"
        setStatus(message)
    }

    private fun speak(comments: List<Comment>) {
        if (!ttsReady || !speakSwitch.isChecked) return
        // If a burst arrives, read only the latest few so speech doesn't fall behind the chat
        val toRead = comments.takeLast(3)
        for (c in toRead) {
            val cleaned = c.text.replace(Regex("https?://\\S+"), "link").trim()
            if (cleaned.isNotEmpty()) {
                tts?.speak("${c.author} says $cleaned", TextToSpeech.QUEUE_ADD, null, c.id)
            }
        }
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

class CommentAdapter : RecyclerView.Adapter<CommentAdapter.VH>() {
    private val items = ArrayList<Comment>()
    private val maxItems = 500

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val author: TextView = view.findViewById(R.id.authorText)
        val message: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.author.text = c.author
        holder.message.text = c.text
    }

    override fun getItemCount() = items.size

    fun addAll(newItems: List<Comment>) {
        items.addAll(newItems)
        // Keep memory bounded on long streams
        if (items.size > maxItems) {
            items.subList(0, items.size - maxItems).clear()
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(items.size - newItems.size, newItems.size)
        }
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
