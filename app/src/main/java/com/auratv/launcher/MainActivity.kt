
package com.auratv.launcher

import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            activeNotifications?.forEach { readNotification(it) }
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
                (extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                    ?: notificationArt
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

    private lateinit var spotifyBox: LinearLayout
    private lateinit var spotifyIdle: TextView
    private lateinit var spotifyDetails: LinearLayout
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView

    private var currentController: MediaController? = null
    private var selectedPackage = ""
    private var launchedPackage = ""
    private var homeBuilt = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int,
        border: Int = Color.TRANSPARENT
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border != Color.TRANSPARENT) {
            setStroke(dp(2), border)
        }
    }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(Color.WHITE)
        includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun bitmapFrom(
        drawable: Drawable,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val old = Rect(drawable.bounds)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = old
        return bitmap
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        createHome()
    }

    private fun rememberApp(pkg: String) {
        preferences.edit()
            .putLong(pkg, System.currentTimeMillis())
            .apply()
    }

    private fun sortedApps(): List<android.content.pm.ResolveInfo> {
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
                compareByDescending<android.content.pm.ResolveInfo> {
                    preferences.getLong(
                        it.activityInfo.packageName, 0L
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
            intArrayOf(19, 55, 105),
            intArrayOf(54, 34, 103),
            intArrayOf(12, 83, 91),
            intArrayOf(94, 35, 65),
            intArrayOf(86, 57, 26),
            intArrayOf(22, 65, 79)
        )

        val colors = palettes[
            (pkg.hashCode().toLong() and 0x7fffffffL).toInt() %
                palettes.size
        ]

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(colors[0], colors[1], colors[2]),
                Color.rgb(
                    colors[0] / 3 + 6,
                    colors[1] / 3 + 9,
                    colors[2] / 3 + 15
                ),
                Color.rgb(5, 9, 19)
            )
        )
    }

    private fun createHome() {
        homeBuilt = false

        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 9, 19))
        }

        backgroundLayer = View(this).apply {
            background = backgroundFor("aura")
        }

        rootFrame.addView(
            backgroundLayer,
            FrameLayout.LayoutParams(-1, -1)
        )

        val overlay = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(25, 0, 0, 0),
                    Color.TRANSPARENT,
                    Color.argb(125, 0, 0, 0)
                )
            )
        }

        rootFrame.addView(
            overlay,
            FrameLayout.LayoutParams(-1, -1)
        )

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(44), dp(25), dp(44), dp(16)
            )
            clipChildren = false
            clipToPadding = false
        }

        rootFrame.addView(
            mainContent,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(rootFrame)

        createHeader()

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(35))
        )

        mainContent.addView(
            text("✦  YOUR ENTERTAINMENT", 10f, true).apply {
                letterSpacing = 0.16f
                setTextColor(Color.rgb(177, 218, 255))
            }
        )

        featuredTitle = text("Welcome to Aura", 37f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        }

        mainContent.addView(featuredTitle)

        featuredSubtitle = text(
            "Everything you love, all in one place",
            13f
        ).apply {
            alpha = 0.75f
            setPadding(0, dp(8), 0, 0)
        }

        mainContent.addView(featuredSubtitle)

        mainContent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(28))
        )

        val appsHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        appsHeader.addView(
            text("Your Apps", 20f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        appsHeader.addView(
            text("SORTED BY RECENTLY OPENED", 9f, true).apply {
                alpha = 0.58f
                letterSpacing = 0.1f
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
            text("AURA TV  /  YOUR SPACE", 10f, true).apply {
                alpha = 0.5f
                letterSpacing = 0.1f
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        footer.addView(
            text("1.2", 10f, true).apply {
                alpha = 0.6f
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
            text("AURA", 29f, true).apply {
                letterSpacing = 0.18f
            }
        )

        brand.addView(
            text("A BETTER WAY TO WATCH", 9f, true).apply {
                letterSpacing = 0.13f
                alpha = 0.6f
                setPadding(0, dp(5), 0, 0)
            }
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        clock = text("", 12f, true).apply {
            alpha = 0.9f
            setPadding(dp(17), 0, 0, 0)
        }

        header.addView(clock)

        // SETTINGS BUTTON
        val settingsButton = text("⚙  Settings", 12f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(
                Color.argb(205, 22, 41, 56),
                14
            )
            isFocusable = true
            isClickable = true
        }

        settingsButton.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused)
                    Color.argb(240, 43, 65, 96)
                else
                    Color.argb(205, 22, 41, 56),
                14,
                if (focused)
                    Color.rgb(207, 237, 255)
                else
                    Color.TRANSPARENT
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
                startActivity(
                    Intent("android.settings.SETTINGS")
                )
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent("android.settings.DEVICE_INFO_SETTINGS")
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
            setPadding(dp(12), dp(13), dp(12), dp(13))
        }

        val apps = sortedApps()
        val columnCount = (apps.size + 1) / 2
        val tileViews = mutableListOf<View>()

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
                    setPadding(dp(9), dp(8), dp(9), dp(9))
                    background = rounded(
                        Color.argb(158, 29, 43, 65), 17
                    )
                    isFocusable = true
                    isClickable = true
                    elevation = dp(2).toFloat()
                }

                tile.addView(
                    ImageView(this).apply {
                        setImageBitmap(image)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    LinearLayout.LayoutParams(dp(145), dp(74))
                )

                tile.addView(
                    text(name, 11f, true).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(3), dp(8), dp(3), 0)
                    },
                    LinearLayout.LayoutParams(-1, -2)
                )

                tile.setOnFocusChangeListener { view, focused ->
                    view.animate().cancel()

                    view.background = if (focused) {
                        rounded(
                            Color.argb(235, 43, 65, 96),
                            17,
                            Color.rgb(207, 237, 255)
                        )
                    } else {
                        rounded(
                            Color.argb(158, 29, 43, 65),
                            17
                        )
                    }

                    view.animate()
                        .scaleX(if (focused) 1.065f else 1f)
                        .scaleY(if (focused) 1.065f else 1f)
                        .translationY(
                            if (focused) -dp(3).toFloat() else 0f
                        )
                        .setDuration(190)
                        .start()

                    view.elevation = dp(
                        if (focused) 13 else 2
                    ).toFloat()

                    if (focused && selectedPackage != pkg) {
                        selectedPackage = pkg

                        featuredTitle.animate().cancel()
                        featuredTitle.text = name
                        featuredTitle.alpha = 0.5f
                        featuredTitle.animate()
                            .alpha(1f)
                            .setDuration(220)
                            .start()

                        featuredSubtitle.text =
                            "Press OK to open $name"

                        backgroundLayer.animate().cancel()
                        backgroundLayer.alpha = 0.45f
                        backgroundLayer.background = backgroundFor(pkg)
                        backgroundLayer.animate()
                            .alpha(1f)
                            .setDuration(380)
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
                        dp(165), dp(118)
                    ).apply {
                        bottomMargin = dp(13)
                    }
                )

                tileViews.add(tile)
            }

            columns.addView(
                column,
                LinearLayout.LayoutParams(
                    dp(165), -2
                ).apply {
                    rightMargin = dp(14)
                }
            )
        }

        scroll.addView(columns)

        mainContent.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1, dp(283)
            )
        )

        if (tileViews.isNotEmpty()) {
            tileViews[0].requestFocus()
        }
    }

    private fun createSpotifyHeader(header: LinearLayout) {
        spotifyBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(
                Color.argb(205, 22, 41, 56), 15
            )
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isFocusable = true
            isClickable = true
            setOnClickListener { openSpotify() }
        }

        spotifyBox.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused)
                    Color.argb(240, 33, 91, 88)
                else
                    Color.argb(205, 22, 41, 56),
                15,
                if (focused)
                    Color.rgb(166, 236, 218)
                else
                    Color.TRANSPARENT
            )
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
                Color.rgb(28, 67, 64), 6
            )
        }

        spotifyDetails.addView(
            albumImage,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }

        info.addView(
            text("NOW PLAYING", 9f, true).apply {
                setTextColor(Color.rgb(143, 225, 188))
                letterSpacing = 0.08f
                setPadding(0, 0, 0, dp(4))
            }
        )

        songTitle = text("", 13f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        songArtist = text("", 11f).apply {
            alpha = 0.78f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }

        info.addView(songTitle)
        info.addView(songArtist)

        spotifyDetails.addView(
            info,
            LinearLayout.LayoutParams(dp(170), -2)
        )

        spotifyBox.addView(spotifyDetails)
        header.addView(spotifyBox)
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
        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val listener = ComponentName(
                this,
                AuraNotificationListener::class.java
            )

            val sessions = try {
                manager.getActiveSessions(listener)
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
            }

        } catch (_: Exception) {
            spotifyIdle.visibility = View.VISIBLE
            spotifyDetails.visibility = View.GONE
        }
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
