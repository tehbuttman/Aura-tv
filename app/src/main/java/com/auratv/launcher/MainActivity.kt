
package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

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
            val e = sbn.notification.extras
            val token = e.getParcelable(
                Notification.EXTRA_MEDIA_SESSION
            ) as? MediaSession.Token

            if (token != null) {
                notificationController = MediaController(this, token)
            }

            e.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { notificationTitle = it }

            e.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { notificationArtist = it }

            notificationArt =
                (e.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                    ?: notificationArt
        } catch (_: Exception) {}
    }
}

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("aura_recent_apps", MODE_PRIVATE)
    }

    private val ink = Color.WHITE
    private val dim = Color.rgb(174, 193, 215)
    private val accent = Color.rgb(177, 223, 255)
    private val cardColor = Color.argb(202, 18, 32, 53)

    private lateinit var content: LinearLayout
    private lateinit var backdrop: View
    private lateinit var feature: TextView
    private lateinit var subtitle: TextView
    private lateinit var clock: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDetail: TextView

    private lateinit var musicPanel: LinearLayout
    private lateinit var musicDetails: LinearLayout
    private lateinit var musicIdle: TextView
    private lateinit var musicArt: ImageView
    private lateinit var musicTitle: TextView
    private lateinit var musicArtist: TextView
    private lateinit var musicControls: LinearLayout
    private lateinit var playButton: TextView
    private lateinit var musicStatus: TextView

    private var controller: MediaController? = null
    private var selectedPackage = ""
    private var launchedPackage = ""
    private var built = false
    private var lastWeather = 0L
    private var weatherLoading = false

    private val iconCache = HashMap<String, Bitmap>()
    private var lastArt: Bitmap? = null
    private var lastTitle = ""
    private var lastArtist = ""

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun shape(
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

    private fun label(
        s: String,
        size: Float,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(ink)
        includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun render(
        drawable: Drawable,
        w: Int,
        h: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(
            w, h, Bitmap.Config.ARGB_8888
        )
        val old = Rect(drawable.bounds)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bmp))
        drawable.bounds = old
        return bmp
    }

    private fun palette(pkg: String): GradientDrawable {
        val colors = arrayOf(
            intArrayOf(22, 57, 107),
            intArrayOf(58, 37, 96),
            intArrayOf(13, 78, 91),
            intArrayOf(94, 40, 76),
            intArrayOf(75, 57, 40)
        )

        val index = (
            (pkg.hashCode().toLong() and 0x7fffffffL) %
                colors.size
        ).toInt()

        val p = colors[index]

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(p[0], p[1], p[2]),
                Color.rgb(
                    p[0] / 2 + 4,
                    p[1] / 2 + 6,
                    p[2] / 2 + 12
                ),
                Color.rgb(4, 8, 19)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        buildHome()
    }

    private fun remember(pkg: String) {
        prefs.edit()
            .putLong(pkg, System.currentTimeMillis())
            .apply()
    }

    private fun hiddenApps(): Set<String> =
        prefs.getStringSet(
            "aura_hidden_apps",
            emptySet()
        )?.toSet() ?: emptySet()

    private fun hideApp(pkg: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Hide $name?")
            .setMessage(
                "This removes the app from Aura TV, " +
                "but does not uninstall it."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Hide") { _, _ ->
                prefs.edit()
                    .putStringSet(
                        "aura_hidden_apps",
                        hiddenApps() + pkg
                    )
                    .apply()

                selectedPackage = ""
                buildHome()
            }
            .show()
    }

    private fun restoreApps() {
        val hidden = hiddenApps()

        val available = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(
                Intent.CATEGORY_LEANBACK_LAUNCHER
            ),
            PackageManager.MATCH_ALL
        )
            .filter {
                it.activityInfo.packageName in hidden
            }
            .distinctBy {
                it.activityInfo.packageName
            }

        if (available.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Hidden apps")
                .setMessage("No hidden apps to restore.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = available.map {
            it.loadLabel(packageManager).toString()
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Restore an app")
            .setItems(names) { _, index ->
                val pkg =
                    available[index].activityInfo.packageName

                prefs.edit()
                    .putStringSet(
                        "aura_hidden_apps",
                        hiddenApps() - pkg
                    )
                    .apply()

                selectedPackage = ""
                buildHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Aura TV stays open when Back is pressed")
    override fun onBackPressed() {
        // Stay on the Aura TV home screen.
    }

    private fun apps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        return packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )
            .filter {
                it.activityInfo.packageName != packageName &&
                    it.activityInfo.packageName !in hiddenApps()
            }
            .distinctBy {
                it.activityInfo.packageName
            }
            .sortedWith(
                compareByDescending<ResolveInfo> {
                    prefs.getLong(
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

    private fun buildHome() {
        built = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(4, 8, 19))
        }

        backdrop = View(this).apply {
            background = palette("aura")
        }

        root.addView(
            backdrop,
            FrameLayout.LayoutParams(-1, -1)
        )

        root.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(28, 0, 0, 0),
                        Color.TRANSPARENT,
                        Color.argb(145, 0, 0, 0)
                    )
                )
            },
            FrameLayout.LayoutParams(-1, -1)
        )

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(42), dp(24), dp(42), dp(12)
            )
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            content,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(root)
        header()

        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(43))
        )

        feature = label(
            "Welcome to Aura",
            39f,
            true
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        content.addView(feature)

        subtitle = label(
            "Everything you love, all in one place",
            13f
        ).apply {
            setTextColor(dim)
            setPadding(0, dp(9), 0, 0)
        }

        content.addView(subtitle)

        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(30))
        )

        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        heading.addView(
            label("Your Apps", 20f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val restore = label(
            "HIDDEN APPS",
            10f,
            true
        ).apply {
            setTextColor(dim)
            setPadding(
                dp(12), dp(9), dp(12), dp(9)
            )
            background = shape(cardColor, 12)
            isFocusable = true
            isClickable = true

            setOnClickListener {
                restoreApps()
            }

            setOnFocusChangeListener { v, focused ->
                v.background = shape(
                    if (focused)
                        Color.rgb(40, 66, 98)
                    else
                        cardColor,
                    12,
                    if (focused)
                        accent
                    else
                        Color.TRANSPARENT
                )
            }
        }

        heading.addView(restore)
        content.addView(heading)

        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(7))
        )

        appGrid()

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(
            label("AURA TV", 10f, true).apply {
                setTextColor(dim)
                letterSpacing = 0.12f
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        footer.addView(
            label("1.6", 10f, true).apply {
                setTextColor(accent)
            }
        )

        content.addView(footer)

        built = true
        refreshWeather()
    }

    private fun header() {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        bar.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        spotify(bar)

        val weather = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cardColor, 14)
            setPadding(
                dp(14), dp(9), dp(14), dp(9)
            )
        }

        weatherTemp = label("--°F", 16f, true)

        weatherDetail = label(
            "Bridgeville · Loading",
            10f
        ).apply {
            setTextColor(dim)
        }

        weather.addView(weatherTemp)
        weather.addView(weatherDetail)

        bar.addView(
            weather,
            LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(12)
            }
        )

        clock = label("", 12f, true).apply {
            setPadding(dp(14), 0, 0, 0)
        }

        bar.addView(clock)

        val settings = label("⚙", 22f, true).apply {
            gravity = Gravity.CENTER
            background = shape(cardColor, 14)
            isFocusable = true
            isClickable = true
        }

        settings.setOnFocusChangeListener { v, focused ->
            v.background = shape(
                if (focused)
                    Color.rgb(40, 66, 98)
                else
                    cardColor,
                14,
                if (focused)
                    accent
                else
                    Color.TRANSPARENT
            )
        }

        settings.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                )
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                    )
                } catch (_: Exception) {}
            }
        }

        bar.addView(
            settings,
            LinearLayout.LayoutParams(
                dp(48), dp(45)
            ).apply {
                leftMargin = dp(12)
            }
        )

        content.addView(bar)
    }

    private fun spotify(bar: LinearLayout) {
        musicPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cardColor, 16)
            setPadding(
                dp(12), dp(8), dp(12), dp(8)
            )
            isFocusable = true
            isClickable = true
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        musicIdle = label("♫  Spotify", 13f, true)
        top.addView(musicIdle)

        musicDetails = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        musicArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(
                Color.rgb(29, 57, 70),
                7
            )
        }

        musicDetails.addView(
            musicArt,
            LinearLayout.LayoutParams(
                dp(49), dp(49)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        info.addView(
            label("NOW PLAYING", 9f, true).apply {
                setTextColor(
                    Color.rgb(143, 231, 189)
                )
                letterSpacing = 0.1f
            }
        )

        musicTitle = label("", 12f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }

        musicArtist = label("", 10f).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(dim)
            setPadding(0, dp(3), 0, 0)
        }

        info.addView(musicTitle)
        info.addView(musicArtist)

        musicDetails.addView(
            info,
            LinearLayout.LayoutParams(dp(138), -2)
        )

        top.addView(musicDetails)
        musicPanel.addView(top)

        musicControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(9), 0, 0)
        }

        val prev = control("⏮") {
            transport {
                skipToPrevious()
            }
        }

        playButton = control("⏯") {
            transport {
                if (
                    controller?.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
                ) {
                    pause()
                } else {
                    play()
                }
            }
        }

        val next = control("⏭") {
            transport {
                skipToNext()
            }
        }

        musicControls.addView(prev)

        musicControls.addView(
            playButton,
            LinearLayout.LayoutParams(
                dp(49), dp(36)
            ).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
            }
        )

        musicControls.addView(next)
        musicPanel.addView(musicControls)

        musicStatus = label("", 9f).apply {
            setTextColor(dim)
            visibility = View.GONE
        }

        musicPanel.addView(musicStatus)

        musicPanel.setOnFocusChangeListener { v, focused ->
            v.background = shape(
                if (focused)
                    Color.rgb(35, 58, 87)
                else
                    cardColor,
                16,
                if (focused)
                    accent
                else
                    Color.TRANSPARENT
            )

            if (focused) {
                musicControls.visibility = View.VISIBLE
            } else {
                v.post {
                    if (!musicControls.hasFocus()) {
                        musicControls.visibility = View.GONE
                    }
                }
            }

            musicStatus.visibility =
                if (
                    focused &&
                    !AuraNotificationListener.connected
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            musicStatus.text = "Music access unavailable"

            v.animate().cancel()
            v.animate()
                .scaleX(if (focused) 1.025f else 1f)
                .scaleY(if (focused) 1.025f else 1f)
                .setDuration(180)
                .start()
        }

        musicPanel.setOnClickListener {
            playButton.requestFocus()
        }

        musicPanel.setOnKeyListener { _, keyCode, event ->
            if (
                event.action ==
                    android.view.KeyEvent.ACTION_DOWN &&
                keyCode ==
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                playButton.requestFocus()
                true
            } else {
                false
            }
        }

        bar.addView(musicPanel)
    }

    private fun control(
        symbol: String,
        action: () -> Unit
    ): TextView = label(symbol, 22f, true).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true

        background = shape(
            Color.argb(110, 67, 98, 126),
            10
        )

        setOnFocusChangeListener { v, focused ->
            v.background = shape(
                if (focused)
                    Color.rgb(72, 115, 153)
                else
                    Color.argb(110, 67, 98, 126),
                10,
                if (focused)
                    accent
                else
                    Color.TRANSPARENT
            )

            if (focused) {
                musicControls.visibility = View.VISIBLE
            }
        }

        setOnClickListener {
            action()
        }

        layoutParams = LinearLayout.LayoutParams(
            dp(43), dp(36)
        )
    }

    private fun transport(
        action:
            MediaController.TransportControls.() -> Unit
    ) {
        try {
            val c = controller
                ?: AuraNotificationListener.notificationController

            c?.transportControls?.action()
        } catch (_: Exception) {}
    }

    private fun appGrid() {
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
                dp(12), dp(13), dp(12), dp(13)
            )
        }

        val list = apps()
        var first: View? = null

        for (colIndex in 0 until (list.size + 1) / 2) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            for (rowIndex in 0..1) {
                val index = colIndex * 2 + rowIndex
                if (index >= list.size) continue

                val app = list[index]
                val pkg = app.activityInfo.packageName
                val name = app.loadLabel(
                    packageManager
                ).toString()

                val drawable =
                    if (iconCache.containsKey(pkg)) {
                        null
                    } else {
                        app.activityInfo.loadBanner(
                            packageManager
                        ) ?: app.loadIcon(packageManager)
                    }

                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(
                        dp(9), dp(8), dp(9), dp(9)
                    )
                    background = shape(cardColor, 18)
                    isFocusable = true
                    isClickable = true
                    elevation = dp(2).toFloat()
                }

                tile.addView(
                    ImageView(this).apply {
                        setImageBitmap(
                            iconCache.getOrPut(pkg) {
                                render(drawable!!, 240, 135)
                            }
                        )
                        scaleType =
                            ImageView.ScaleType.FIT_CENTER
                    },
                    LinearLayout.LayoutParams(
                        dp(145), dp(74)
                    )
                )

                tile.addView(
                    label(name, 11f, true).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize =
                            TextUtils.TruncateAt.END
                        setPadding(
                            dp(3), dp(8), dp(3), 0
                        )
                    },
                    LinearLayout.LayoutParams(-1, -2)
                )

                tile.setOnFocusChangeListener { v, focused ->
                    v.background = shape(
                        if (focused)
                            Color.argb(245, 38, 66, 100)
                        else
                            cardColor,
                        18,
                        if (focused)
                            accent
                        else
                            Color.TRANSPARENT
                    )

                    v.animate().cancel()

                    v.animate()
                        .scaleX(
                            if (focused) 1.065f else 1f
                        )
                        .scaleY(
                            if (focused) 1.065f else 1f
                        )
                        .translationY(
                            if (focused)
                                -dp(3).toFloat()
                            else
                                0f
                        )
                        .setDuration(120)
                        .start()

                    v.elevation = dp(
                        if (focused) 5 else 1
                    ).toFloat()

                    if (
                        focused &&
                        selectedPackage != pkg
                    ) {
                        selectedPackage = pkg
                        feature.text = name
                        subtitle.text =
                            "Press OK to open $name"
                        backdrop.background = palette(pkg)
                    }
                }

                tile.setOnLongClickListener {
                    hideApp(pkg, name)
                    true
                }

                tile.setOnClickListener {
                    remember(pkg)
                    launchedPackage = pkg

                    try {
                        startActivity(
                            Intent().apply {
                                component = ComponentName(
                                    pkg,
                                    app.activityInfo.name
                                )
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            }
                        )
                    } catch (_: Exception) {
                        try {
                            packageManager
                                .getLaunchIntentForPackage(pkg)
                                ?.let {
                                    startActivity(it)
                                }
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

                if (first == null) {
                    first = tile
                }
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

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1, dp(283)
            )
        )

        first?.requestFocus()
    }

    private fun updateMusic() {
        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val sessions = try {
                manager.getActiveSessions(
                    ComponentName(
                        this,
                        AuraNotificationListener::class.java
                    )
                )
            } catch (_: Exception) {
                emptyList<MediaController>()
            }

            controller = sessions.firstOrNull {
                it.packageName.contains(
                    "spotify",
                    true
                ) &&
                    it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.packageName.contains("spotify", true)
            } ?: AuraNotificationListener.notificationController

            val metadata = controller?.metadata

            val playing =
                controller?.playbackState?.state ==
                    PlaybackState.STATE_PLAYING

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

            val show =
                title.isNotBlank() &&
                    (
                        playing ||
                        controller?.playbackState?.state ==
                        PlaybackState.STATE_PAUSED
                    )

            musicIdle.visibility =
                if (show) View.GONE else View.VISIBLE

            musicDetails.visibility =
                if (show) View.VISIBLE else View.GONE

            if (show) {
                if (title != lastTitle) {
                    musicTitle.text = title
                    lastTitle = title
                }

                if (artist != lastArtist) {
                    musicArtist.text = artist
                    lastArtist = artist
                }

                if (art !== lastArt) {
                    musicArt.setImageBitmap(art)
                    lastArt = art
                }

                playButton.text =
                    if (playing) "⏸" else "▶"
            }
        } catch (_: Exception) {}
    }

    private fun weatherDescription(
        code: Int
    ): Pair<String, String> = when (code) {
        0 -> "☀" to "Clear"
        1, 2 -> "⛅" to "Partly cloudy"
        3 -> "☁" to "Cloudy"
        45, 48 -> "☁" to "Fog"
        in 51..57 -> "☂" to "Drizzle"
        in 61..67, in 80..82 -> "☂" to "Rain"
        in 71..77, 85, 86 -> "❄" to "Snow"
        in 95..99 -> "⚡" to "Thunderstorms"
        else -> "☁" to "Weather"
    }

    private fun refreshWeather() {
        val now = System.currentTimeMillis()

        if (
            weatherLoading ||
            now - lastWeather < 15 * 60 * 1000L
        ) return

        weatherLoading = true

        thread(name = "aura-weather") {
            var conn: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=40.3562" +
                    "&longitude=-80.1101" +
                    "&current=temperature_2m,weather_code" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=America%2FNew_York"
                )

                conn = (
                    url.openConnection() as HttpURLConnection
                ).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }

                val json = JSONObject(
                    conn.inputStream.bufferedReader().use {
                        it.readText()
                    }
                )

                val current = json.getJSONObject("current")

                val temp = current.getDouble(
                    "temperature_2m"
                ).toInt()

                val (icon, condition) =
                    weatherDescription(
                        current.getInt("weather_code")
                    )

                handler.post {
                    if (!isFinishing && !isDestroyed) {
                        weatherTemp.text = "$icon  $temp°F"
                        weatherDetail.text =
                            "Bridgeville · $condition"
                    }
                }

                lastWeather = System.currentTimeMillis()

            } catch (_: Exception) {
                lastWeather =
                    System.currentTimeMillis() -
                    10 * 60 * 1000L

                handler.post {
                    if (!isFinishing && !isDestroyed) {
                        weatherDetail.text =
                            "Bridgeville · Unavailable"
                    }
                }
            } finally {
                conn?.disconnect()
                weatherLoading = false
            }
        }
    }

    private val updater = object : Runnable {
        override fun run() {
            if (built) {
                clock.text = SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(Date())

                updateMusic()
                refreshWeather()
            }

            handler.postDelayed(this, 5000)
        }
    }

    override fun onResume() {
        super.onResume()

        if (
            built &&
            launchedPackage.isNotEmpty()
        ) {
            launchedPackage = ""
            selectedPackage = ""
            buildHome()
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
