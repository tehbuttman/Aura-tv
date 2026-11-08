
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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    private lateinit var mainContent: LinearLayout
    private lateinit var backgroundLayer: View

    private lateinit var featuredTitle: TextView
    private lateinit var featuredSubtitle: TextView
    private lateinit var clock: TextView

    private lateinit var spotifyIdle: TextView
    private lateinit var spotifyDetails: LinearLayout
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var connectionStatus: TextView

    private var currentController: MediaController? = null

    private var selectedPackage = ""
    private var launchedPackage = ""
    private var homeBuilt = false

    private var lastRebindAttempt = 0L
    private var firstSpotifyCheck = true

    private val white = Color.WHITE
    private val muted = Color.rgb(169, 187, 208)
    private val accent = Color.rgb(153, 211, 255)
    private val darkCard = Color.argb(185, 21, 35, 55)
    private val focusCard = Color.argb(245, 36, 64, 96)

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
    }

    // SPOTIFY AUTOMATIC RECONNECT

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

            val component = listenerComponent()

            enabled.split(":").any {
                val parsed = ComponentName.unflattenFromString(it)
                parsed == component
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun requestSpotifyReconnect() {
        if (AuraNotificationListener.connected) return
        if (!hasNotificationAccess()) return

        val now = System.currentTimeMillis()

        // Avoid repeatedly requesting a rebind.
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

    private fun backgroundFor(pkg: String): GradientDrawable {
        val palettes = arrayOf(
            intArrayOf(22, 55, 100),
            intArrayOf(47, 38, 91),
            intArrayOf(15, 70, 86),
            intArrayOf(83, 37, 72),
            intArrayOf(70, 55, 42),
            intArrayOf(24, 66, 79)
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
                    c[0] / 2 + 4,
                    c[1] / 2 + 6,
                    c[2] / 2 + 12
                ),
                Color.rgb(5, 9, 20)
            )
        )
    }

    private fun createHome() {
        homeBuilt = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 20))
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
                    Color.argb(18, 0, 0, 0),
                    Color.argb(155, 0, 0, 0)
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
                dp(24),
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
            LinearLayout.LayoutParams(1, dp(31))
        )

        mainContent.addView(
            text("DISCOVER YOUR SPACE", 10f, true).apply {
                letterSpacing = 0.2f
                setTextColor(accent)
            }
        )

        featuredTitle = text(
            "Welcome to Aura",
            39f,
            true
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(9), 0, 0)
        }

        mainContent.addView(featuredTitle)

        featuredSubtitle = text(
            "Your entertainment. Beautifully simple.",
            13f
        ).apply {
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        }

        mainContent.addView(featuredSubtitle)

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(28))
        )

        val appsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(
            text("AURA TV  •  MADE FOR YOUR SCREEN", 10f, true).apply {
                setTextColor(muted)
                alpha = 0.75f
                letterSpacing = 0.08f
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        footer.addView(
            text("1.3", 10f, true).apply {
                setTextColor(accent)
            }
        )

        mainContent.addView(footer)

        homeBuilt = true
    }

    private fun createHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(
            text("AURA", 30f, true).apply {
                letterSpacing = 0.2f
            }
        )

        brand.addView(
            text("YOUR TV. REIMAGINED.", 9f, true).apply {
                letterSpacing = 0.13f
                setTextColor(muted)
                setPadding(0, dp(5), 0, 0)
            }
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        clock = text("", 12f, true).apply {
            setPadding(dp(17), 0, 0, 0)
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
            background = rounded(darkCard, 14)
            isFocusable = true
            isClickable = true
        }

        settingsButton.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused) focusCard else darkCard,
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
                leftMargin = dp(16)
            }
        )

        mainContent.addView(header)
    }

    private fun createSpotifyHeader(header: LinearLayout) {
        val spotifyBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(darkCard, 16)
            setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
            )
            isFocusable = true
            isClickable = true
            setOnClickListener { openSpotify() }
        }

        spotifyBox.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused) focusCard else darkCard,
                16,
                if (focused) accent else Color.TRANSPARENT
            )

            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.025f else 1f)
                .scaleY(if (focused) 1.025f else 1f)
                .setDuration(180)
                .start()
        }

        spotifyIdle = text("♫  Spotify", 13f, true)
        spotifyBox.addView(spotifyIdle)

        spotifyDetails = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        albumImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(
                Color.rgb(28, 60, 69),
                8
            )
        }

        spotifyDetails.addView(
            albumImage,
            LinearLayout.LayoutParams(
                dp(54),
                dp(54)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }

        info.addView(
            text("NOW PLAYING", 9f, true).apply {
                setTextColor(Color.rgb(137, 226, 183))
                letterSpacing = 0.1f
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
            LinearLayout.LayoutParams(dp(168), -2)
        )

        spotifyBox.addView(spotifyDetails)

        val musicArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        musicArea.addView(spotifyBox)

        connectionStatus = text(
            "",
            9f
        ).apply {
            setTextColor(muted)
            gravity = Gravity.END
            setPadding(0, dp(4), dp(4), 0)
            visibility = View.GONE
        }

        musicArea.addView(connectionStatus)

        header.addView(musicArea)
    }

    private fun createAppGrid() {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFillViewport = true
            descendantFocusability =
                android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
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
                    background = rounded(darkCard, 18)
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
                        if (focused) focusCard else darkCard,
                        18,
                        if (focused) accent else Color.TRANSPARENT
                    )

                    view.animate()
                        .scaleX(if (focused) 1.07f else 1f)
                        .scaleY(if (focused) 1.07f else 1f)
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

    private fun openSpotify() {
        for (pkg in listOf(
            "com.spotify.tv.android",
            "com.spotify.music"
        )) {
            try {
                val launch = packageManager
                    .getLaunchIntentForPackage(pkg)

                if (launch != null) {
                    rememberApp(pkg)
                    launchedPackage = pkg
                    startActivity(launch)
                    return
                }
            } catch (_: Exception) {}
        }

        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val spotify = packageManager
            .queryIntentActivities(query, 0)
            .firstOrNull {
                it.activityInfo.packageName
                    .contains("spotify", true)
            }

        if (spotify != null) {
            try {
                val pkg = spotify.activityInfo.packageName

                rememberApp(pkg)
                launchedPackage = pkg

                startActivity(Intent().apply {
                    component = ComponentName(
                        pkg,
                        spotify.activityInfo.name
                    )
                })
            } catch (_: Exception) {}
        }
    }

    private fun updateSpotify() {
        if (!AuraNotificationListener.connected) {
            requestSpotifyReconnect()
        }

        val accessGranted = hasNotificationAccess()

        connectionStatus.visibility =
            if (accessGranted &&
                !AuraNotificationListener.connected
            ) View.VISIBLE else View.GONE

        connectionStatus.text = "Connecting music…"

        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val sessions = try {
                manager.getActiveSessions(
                    listenerComponent()
                )
            } catch (_: Exception) {
                emptyList<MediaController>()
            }

            currentController = sessions.firstOrNull {
                it.packageName.contains("spotify", true) &&
                    it.playbackState?.state ==
                        PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.packageName.contains("spotify", true)
            } ?: AuraNotificationListener.notificationController

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

            val show = playing && title.isNotBlank()

            spotifyIdle.visibility =
                if (show) View.GONE else View.VISIBLE

            spotifyDetails.visibility =
                if (show) View.VISIBLE else View.GONE

            if (show) {
                songTitle.text = title
                songArtist.text = artist
                albumImage.setImageBitmap(art)
                connectionStatus.visibility = View.GONE
            }

        } catch (_: Exception) {
            spotifyIdle.visibility = View.VISIBLE
            spotifyDetails.visibility = View.GONE
        }

        firstSpotifyCheck = false
    }

    private fun updateClock() {
        clock.text = SimpleDateFormat(
            "EEE, MMM d  •  h:mm a",
            Locale.getDefault()
        ).format(Date())
    }

    private val updater = object : Runnable {
        override fun run() {
            updateClock()
            updateSpotify()
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
