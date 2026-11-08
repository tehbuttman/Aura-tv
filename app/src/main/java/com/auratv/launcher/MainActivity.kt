
package com.auratv.launcher

import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuraNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile var connected = false
        @Volatile var notificationTitle = ""
        @Volatile var notificationArtist = ""
        @Volatile var notificationArt: Bitmap? = null
        @Volatile var notificationController: MediaController? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true

        try {
            activeNotifications?.forEach {
                readNotification(it)
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        connected = false
        notificationController = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        readNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName.contains("spotify", true)) {
            notificationTitle = ""
            notificationArtist = ""
            notificationArt = null
            notificationController = null
        }
    }

    private fun readNotification(sbn: StatusBarNotification) {
        if (!sbn.packageName.contains("spotify", true)) return

        try {
            val extras = sbn.notification.extras

            val token = extras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION
            ) as? MediaSession.Token

            if (token != null) {
                notificationController = MediaController(this, token)
            }

            extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { notificationTitle = it }

            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { notificationArtist = it }

            notificationArt =
                (extras.getParcelable(
                    Notification.EXTRA_PICTURE
                ) as? Bitmap) ?: notificationArt

        } catch (_: Exception) {}
    }
}

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private val preferences by lazy {
        getSharedPreferences("aura_recent_apps", MODE_PRIVATE)
    }

    private val white = Color.WHITE
    private val muted = Color.rgb(175, 193, 215)
    private val accent = Color.rgb(165, 217, 255)

    private val cardColor = Color.argb(195, 19, 33, 52)
    private val selectedColor = Color.argb(245, 37, 65, 98)

    private lateinit var mainContent: LinearLayout
    private lateinit var backgroundLayer: View

    private lateinit var featuredTitle: TextView
    private lateinit var featuredSubtitle: TextView

    private lateinit var clock: TextView
    private lateinit var weatherTemperature: TextView
    private lateinit var weatherCondition: TextView

    private lateinit var spotifyPanel: LinearLayout
    private lateinit var spotifySummary: LinearLayout
    private lateinit var spotifyControls: LinearLayout

    private lateinit var spotifyIdle: TextView
    private lateinit var spotifyDetails: LinearLayout
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var playPauseButton: TextView

    private var currentController: MediaController? = null

    private var selectedPackage = ""
    private var launchedPackage = ""
    private var homeBuilt = false
    private var spotifyExpanded = false

    private var lastRebindAttempt = 0L
    private var lastWeatherFetch = 0L
    private var weatherLoading = false

    private var cachedTemperature = "--°"
    private var cachedCondition = "Bridgeville, PA"

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun rounded(
        color: Int,
        radius: Int,
        border: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()

            if (border != Color.TRANSPARENT) {
                setStroke(dp(2), border)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(white)
            includeFontPadding = false

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun bitmapFrom(
        drawable: Drawable,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        val oldBounds = Rect(drawable.bounds)

        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = oldBounds

        return bitmap
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        createHome()
        requestSpotifyReconnect()
        fetchWeather()
    }

    // -----------------------------
    // SPOTIFY CONNECTION
    // -----------------------------

    private fun listenerComponent(): ComponentName {
        return ComponentName(
            this,
            AuraNotificationListener::class.java
        )
    }

    private fun hasNotificationAccess(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            ) ?: ""

            enabled.split(":").any {
                ComponentName.unflattenFromString(it) ==
                    listenerComponent()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun requestSpotifyReconnect() {
        if (AuraNotificationListener.connected) return
        if (!hasNotificationAccess()) return

        val now = System.currentTimeMillis()

        if (now - lastRebindAttempt < 30000L) return

        lastRebindAttempt = now

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(
                    listenerComponent()
                )
            }
        } catch (_: Exception) {}
    }

    private fun getSpotifyController(): MediaController? {
        return try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val sessions = try {
                manager.getActiveSessions(listenerComponent())
            } catch (_: Exception) {
                emptyList<MediaController>()
            }

            sessions.firstOrNull {
                it.packageName.contains("spotify", true) &&
                    it.playbackState?.state ==
                        PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.packageName.contains("spotify", true)
            } ?: AuraNotificationListener.notificationController

        } catch (_: Exception) {
            AuraNotificationListener.notificationController
        }
    }

    private fun controlSpotify(action: String) {
        val controller = getSpotifyController() ?: return

        try {
            val controls = controller.transportControls

            when (action) {
                "previous" -> controls.skipToPrevious()

                "next" -> controls.skipToNext()

                "toggle" -> {
                    if (
                        controller.playbackState?.state ==
                        PlaybackState.STATE_PLAYING
                    ) {
                        controls.pause()
                    } else {
                        controls.play()
                    }
                }
            }

            handler.postDelayed({
                updateSpotify()
            }, 350)

        } catch (_: Exception) {}
    }

    // -----------------------------
    // LIVE BRIDGEVILLE WEATHER
    // -----------------------------

    private fun weatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorms"
            else -> "Current weather"
        }
    }

    private fun weatherIcon(code: Int): String {
        return when (code) {
            0, 1 -> "☀"
            2 -> "⛅"
            3, 45, 48 -> "☁"
            51, 53, 55, 56, 57 -> "☂"
            61, 63, 65, 66, 67, 80, 81, 82 -> "☂"
            71, 73, 75, 77, 85, 86 -> "❄"
            95, 96, 99 -> "⚡"
            else -> "☁"
        }
    }

    private fun fetchWeather() {
        val now = System.currentTimeMillis()

        if (weatherLoading) return

        if (
            lastWeatherFetch != 0L &&
            now - lastWeatherFetch < 15 * 60 * 1000L
        ) return

        weatherLoading = true

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val address =
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=40.3562" +
                    "&longitude=-80.1106" +
                    "&current=temperature_2m,weather_code" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=America%2FNew_York"

                connection = URL(address)
                    .openConnection() as HttpURLConnection

                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"

                if (connection.responseCode != 200) {
                    throw Exception("Weather unavailable")
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val current = JSONObject(response)
                    .getJSONObject("current")

                val temperature = current.getDouble(
                    "temperature_2m"
                ).toInt()

                val code = current.getInt("weather_code")

                val temperatureText =
                    "${weatherIcon(code)}  ${temperature}°F"

                val conditionText = weatherDescription(code)

                handler.post {
                    cachedTemperature = temperatureText
                    cachedCondition = conditionText

                    weatherTemperature.text = cachedTemperature
                    weatherCondition.text = cachedCondition

                    lastWeatherFetch = System.currentTimeMillis()
                    weatherLoading = false
                }

            } catch (_: Exception) {
                handler.post {
                    weatherLoading = false
                    lastWeatherFetch = System.currentTimeMillis()
                    weatherTemperature.text = cachedTemperature
                    weatherCondition.text = cachedCondition
                }

            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    // -----------------------------
    // RECENT APPS
    // -----------------------------

    private fun rememberApp(pkg: String) {
        preferences.edit()
            .putLong(pkg, System.currentTimeMillis())
            .apply()
    }

    private fun sortedApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        return packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )
            .filter {
                it.activityInfo.packageName != packageName
            }
            .distinctBy {
                it.activityInfo.packageName
            }
            .sortedWith(
                compareByDescending<ResolveInfo> {
                    preferences.getLong(
                        it.activityInfo.packageName,
                        0L
                    )
                }.thenBy {
                    it.loadLabel(packageManager)
                        .toString()
                        .lowercase(Locale.getDefault())
                }
            )
    }

    // -----------------------------
    // BACKGROUNDS
    // -----------------------------

    private fun backgroundFor(pkg: String): GradientDrawable {
        val palettes = arrayOf(
            intArrayOf(20, 49, 92),
            intArrayOf(47, 37, 88),
            intArrayOf(14, 65, 82),
            intArrayOf(79, 37, 69),
            intArrayOf(66, 51, 38),
            intArrayOf(21, 60, 74)
        )

        val index = (
            (pkg.hashCode().toLong() and 0x7fffffffL) %
                palettes.size
        ).toInt()

        val c = palettes[index]

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(c[0], c[1], c[2]),
                Color.rgb(
                    c[0] / 2 + 5,
                    c[1] / 2 + 6,
                    c[2] / 2 + 12
                ),
                Color.rgb(4, 8, 18)
            )
        )
    }

    // -----------------------------
    // HOME SCREEN
    // -----------------------------

    private fun createHome() {
        homeBuilt = false
        spotifyExpanded = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(4, 8, 18))
        }

        backgroundLayer = View(this).apply {
            background = backgroundFor("aura")
        }

        root.addView(
            backgroundLayer,
            FrameLayout.LayoutParams(-1, -1)
        )

        val overlay = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(35, 0, 0, 0),
                    Color.argb(10, 0, 0, 0),
                    Color.argb(165, 0, 0, 0)
                )
            )
        }

        root.addView(
            overlay,
            FrameLayout.LayoutParams(-1, -1)
        )

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(44),
                dp(23),
                dp(44),
                dp(14)
            )
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            mainContent,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(root)

        createHeader()

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(42))
        )

        featuredTitle = text(
            "Welcome to Aura",
            40f,
            true
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }

        mainContent.addView(featuredTitle)

        featuredSubtitle = text(
            "Everything you love, all in one place",
            13f
        ).apply {
            setTextColor(muted)
            setPadding(0, dp(9), 0, 0)
        }

        mainContent.addView(featuredSubtitle)

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(29))
        )

        val appsHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        appsHeader.addView(
            text("Your Apps", 20f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        appsHeader.addView(
            text("RECENTLY OPENED FIRST", 9f, true).apply {
                letterSpacing = 0.12f
                setTextColor(muted)
            }
        )

        mainContent.addView(appsHeader)

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(7))
        )

        createAppGrid()

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(
            text("AURA TV", 10f, true).apply {
                letterSpacing = 0.16f
                setTextColor(muted)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        footer.addView(
            text("1.4", 10f, true).apply {
                setTextColor(accent)
            }
        )

        mainContent.addView(footer)

        homeBuilt = true
    }

    // -----------------------------
    // TOP HEADER
    // -----------------------------

    private fun createHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val brand = text("AURA", 31f, true).apply {
            letterSpacing = 0.2f
        }

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        val weatherBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(13),
                dp(7),
                dp(13),
                dp(7)
            )
            background = rounded(cardColor, 15)
        }

        weatherTemperature = text(
            cachedTemperature,
            16f,
            true
        ).apply {
            gravity = Gravity.CENTER
        }

        weatherCondition = text(
            cachedCondition,
            10f
        ).apply {
            setTextColor(muted)
            gravity = Gravity.CENTER
            maxLines = 1
        }

        weatherBox.addView(weatherTemperature)
        weatherBox.addView(weatherCondition)

        header.addView(
            weatherBox,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(12)
            }
        )

        clock = text("", 12f, true).apply {
            setPadding(dp(15), 0, 0, 0)
        }

        header.addView(clock)

        val settingsButton = text(
            "⚙  Settings",
            12f,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
            )
            background = rounded(cardColor, 14)
            isFocusable = true
            isClickable = true
        }

        settingsButton.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused) selectedColor else cardColor,
                14,
                if (focused) accent else Color.TRANSPARENT
            )

            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.06f else 1f)
                .scaleY(if (focused) 1.06f else 1f)
                .setDuration(180)
                .start()
        }

        settingsButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                    )
                } catch (_: Exception) {}
            }
        }

        header.addView(
            settingsButton,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(12)
            }
        )

        mainContent.addView(header)
    }

    // -----------------------------
    // EXPANDABLE SPOTIFY WIDGET
    // -----------------------------

    private fun createSpotifyHeader(header: LinearLayout) {
        spotifyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 17)
            setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
            )
            isFocusable = true
            isClickable = true
            clipChildren = false
            clipToPadding = false
        }

        spotifySummary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        spotifyIdle = text("♫  Spotify", 13f, true)

        spotifySummary.addView(spotifyIdle)

        spotifyDetails = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        albumImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(
                Color.rgb(25, 55, 65),
                8
            )
        }

        spotifyDetails.addView(
            albumImage,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        info.addView(
            text("NOW PLAYING", 9f, true).apply {
                setTextColor(Color.rgb(137, 226, 183))
                letterSpacing = 0.09f
                setPadding(0, 0, 0, dp(4))
            }
        )

        songTitle = text("", 13f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        songArtist = text("", 11f).apply {
            setTextColor(muted)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }

        info.addView(songTitle)
        info.addView(songArtist)

        spotifyDetails.addView(
            info,
            LinearLayout.LayoutParams(dp(140), -2)
        )

        spotifySummary.addView(spotifyDetails)
        spotifyPanel.addView(spotifySummary)

        spotifyControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(10), 0, dp(2))
        }

        val previousButton = musicButton("❮❮", "previous")
        playPauseButton = musicButton("▶", "toggle")
        val nextButton = musicButton("❯❯", "next")

        spotifyControls.addView(
            previousButton,
            LinearLayout.LayoutParams(
                dp(65),
                dp(38)
            )
        )

        spotifyControls.addView(
            playPauseButton,
            LinearLayout.LayoutParams(
                dp(65),
                dp(38)
            ).apply {
                leftMargin = dp(7)
                rightMargin = dp(7)
            }
        )

        spotifyControls.addView(
            nextButton,
            LinearLayout.LayoutParams(
                dp(65),
                dp(38)
            )
        )

        spotifyPanel.addView(spotifyControls)

        spotifyPanel.setOnClickListener {
            if (!spotifyExpanded) {
                expandSpotify()
            } else {
                playPauseButton.requestFocus()
            }
        }

        spotifyPanel.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused) selectedColor else cardColor,
                17,
                if (focused) accent else Color.TRANSPARENT
            )

            if (focused) {
                expandSpotify()
            } else {
                scheduleSpotifyCollapse()
            }
        }

        header.addView(
            spotifyPanel,
            LinearLayout.LayoutParams(-2, -2)
        )
    }

    private fun musicButton(
        symbol: String,
        action: String
    ): TextView {
        val button = text(symbol, 17f, true).apply {
            gravity = Gravity.CENTER
            background = rounded(
                Color.argb(190, 45, 69, 96),
                12
            )
            isFocusable = true
            isClickable = true
        }

        button.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused)
                    Color.rgb(101, 154, 205)
                else
                    Color.argb(190, 45, 69, 96),
                12,
                if (focused) white else Color.TRANSPARENT
            )

            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.09f else 1f)
                .scaleY(if (focused) 1.09f else 1f)
                .setDuration(160)
                .start()

            if (!focused) {
                scheduleSpotifyCollapse()
            }
        }

        button.setOnClickListener {
            controlSpotify(action)
        }

        return button
    }

    private fun expandSpotify() {
        if (spotifyExpanded) return

        spotifyExpanded = true
        spotifyControls.visibility = View.VISIBLE
        spotifyControls.alpha = 0f
        spotifyControls.translationY = -dp(6).toFloat()

        spotifyControls.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(210)
            .start()
    }

    private fun scheduleSpotifyCollapse() {
        handler.postDelayed({
            if (
                ::spotifyPanel.isInitialized &&
                !spotifyPanel.hasFocus()
            ) {
                collapseSpotify()
            }
        }, 100)
    }

    private fun collapseSpotify() {
        if (!spotifyExpanded) return

        spotifyExpanded = false

        spotifyControls.animate().cancel()
        spotifyControls.visibility = View.GONE

        spotifyPanel.background = rounded(
            cardColor,
            17
        )
    }

    private fun updateSpotify() {
        if (!AuraNotificationListener.connected) {
            requestSpotifyReconnect()
        }

        try {
            currentController = getSpotifyController()

            val playing =
                currentController?.playbackState?.state ==
                    PlaybackState.STATE_PLAYING

            val metadata = currentController?.metadata

            val title = metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )?.takeIf {
                it.isNotBlank()
            } ?: AuraNotificationListener.notificationTitle

            val artist = metadata?.getString(
                MediaMetadata.METADATA_KEY_ARTIST
            )?.takeIf {
                it.isNotBlank()
            } ?: AuraNotificationListener.notificationArtist

            val art = metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ALBUM_ART
            ) ?: metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ART
            ) ?: AuraNotificationListener.notificationArt

            val show = title.isNotBlank() &&
                currentController != null

            spotifyIdle.visibility =
                if (show) View.GONE else View.VISIBLE

            spotifyDetails.visibility =
                if (show) View.VISIBLE else View.GONE

            if (show) {
                songTitle.text = title
                songArtist.text = artist
                albumImage.setImageBitmap(art)
            }

            playPauseButton.text =
                if (playing) "Ⅱ" else "▶"

        } catch (_: Exception) {
            spotifyIdle.visibility = View.VISIBLE
            spotifyDetails.visibility = View.GONE
        }
    }

    // -----------------------------
    // TWO-ROW APP GRID
    // -----------------------------

    private fun createAppGrid() {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFillViewport = true
        }

        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(
                dp(12),
                dp(13),
                dp(12),
                dp(13)
            )
        }

        val apps = sortedApps()
        val columnCount = (apps.size + 1) / 2

        var firstTile: View? = null

        for (columnIndex in 0 until columnCount) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            for (rowIndex in 0..1) {
                val appIndex = columnIndex * 2 + rowIndex

                if (appIndex >= apps.size) continue

                val app = apps[appIndex]
                val name = app.loadLabel(packageManager).toString()
                val pkg = app.activityInfo.packageName

                val drawable = app.activityInfo
                    .loadBanner(packageManager)
                    ?: app.loadIcon(packageManager)

                val image = bitmapFrom(drawable, 320, 180)

                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(9),
                        dp(8),
                        dp(9),
                        dp(9)
                    )
                    background = rounded(cardColor, 19)
                    isFocusable = true
                    isClickable = true
                    elevation = dp(2).toFloat()
                }

                tile.addView(
                    ImageView(this).apply {
                        setImageBitmap(image)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    LinearLayout.LayoutParams(
                        dp(145),
                        dp(74)
                    )
                )

                tile.addView(
                    text(name, 11f, true).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(
                            dp(3),
                            dp(8),
                            dp(3),
                            0
                        )
                    },
                    LinearLayout.LayoutParams(-1, -2)
                )

                tile.setOnFocusChangeListener { view, focused ->
                    view.animate().cancel()

                    view.background = rounded(
                        if (focused) selectedColor else cardColor,
                        19,
                        if (focused) accent else Color.TRANSPARENT
                    )

                    view.animate()
                        .scaleX(if (focused) 1.075f else 1f)
                        .scaleY(if (focused) 1.075f else 1f)
                        .translationY(
                            if (focused) -dp(4).toFloat() else 0f
                        )
                        .setDuration(220)
                        .start()

                    view.elevation =
                        dp(if (focused) 14 else 2).toFloat()

                    if (focused && selectedPackage != pkg) {
                        selectedPackage = pkg

                        featuredTitle.animate().cancel()
                        featuredTitle.text = name
                        featuredTitle.alpha = 0.45f

                        featuredTitle.animate()
                            .alpha(1f)
                            .setDuration(260)
                            .start()

                        featuredSubtitle.text =
                            "Press OK to open $name"

                        backgroundLayer.animate().cancel()
                        backgroundLayer.alpha = 0.45f
                        backgroundLayer.background = backgroundFor(pkg)

                        backgroundLayer.animate()
                            .alpha(1f)
                            .setDuration(450)
                            .start()
                    }
                }

                tile.setOnClickListener {
                    rememberApp(pkg)
                    launchedPackage = pkg

                    try {
                        startActivity(Intent().apply {
                            component = ComponentName(
                                pkg,
                                app.activityInfo.name
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {
                        try {
                            packageManager
                                .getLaunchIntentForPackage(pkg)
                                ?.let { startActivity(it) }
                        } catch (_: Exception) {}
                    }
                }

                column.addView(
                    tile,
                    LinearLayout.LayoutParams(
                        dp(165),
                        dp(118)
                    ).apply {
                        bottomMargin = dp(13)
                    }
                )

                if (firstTile == null) {
                    firstTile = tile
                }
            }

            columns.addView(
                column,
                LinearLayout.LayoutParams(
                    dp(165),
                    -2
                ).apply {
                    rightMargin = dp(14)
                }
            )
        }

        scroll.addView(columns)

        mainContent.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                dp(283)
            )
        )

        firstTile?.requestFocus()
    }

    // -----------------------------
    // CLOCK AND REFRESH
    // -----------------------------

    private fun updateClock() {
        clock.text = SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        ).format(Date())
    }

    private val updater = object : Runnable {
        override fun run() {
            updateClock()
            updateSpotify()
            fetchWeather()

            handler.postDelayed(this, 2000)
        }
    }

    override fun onResume() {
        super.onResume()

        if (homeBuilt && launchedPackage.isNotEmpty()) {
            launchedPackage = ""
            selectedPackage = ""
            createHome()
        }

        requestSpotifyReconnect()

        handler.removeCallbacks(updater)
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }
}
