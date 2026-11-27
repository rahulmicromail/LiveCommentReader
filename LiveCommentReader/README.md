# Live Comment Reader (Android)

Shows the live chat comments of a YouTube live stream in a scrolling list and reads new ones aloud.

## 1. Get a YouTube API key (free)
1. Go to https://console.cloud.google.com and create a project.
2. Open **APIs & Services > Library**, search **YouTube Data API v3**, click **Enable**.
3. Open **APIs & Services > Credentials > Create credentials > API key**. Copy it.

The free quota is 10,000 units/day. Each chat poll costs a few units, so a long stream can use up the day's quota. If you see a "quota" error, wait for the next day's reset or use a second project's key.

## 2. Build the APK
1. Install Android Studio (https://developer.android.com/studio).
2. **File > Open** and choose this `LiveCommentReader` folder. Let Gradle sync finish (first sync downloads dependencies).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and install it (allow "install unknown apps" when asked).

You can also plug in your phone with USB debugging on and press the green Run button.

## 3. Use it
1. Open the app, paste your API key and the live stream link (or the 11-character video ID).
2. Tap **Start**. Comments appear as they arrive; keep **Read aloud** on to hear them.
3. Tap **Stop** to end.

## Notes
- The video must be live right now with chat enabled.
- On connecting, recent chat history is shown but not read aloud; only new comments are spoken.
- If a burst of comments arrives, only the latest 3 of that batch are read so the voice does not fall behind.
- To read in another language, install that language's voice in your phone's Text-to-speech settings.
