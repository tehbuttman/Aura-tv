
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
import android.widget.Toast
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

    private val prefs by lazy {
        getSharedPreferences("aura_recent_apps", MODE_PRIVATE)
    }

    private val white = Color.WHITE
    private val muted = Color.rgb(174, 193, 215)
    private val accent = Color.rgb(177, 223, 255)
    private val panelColor = Color.argb(220, 14, 25, 44)

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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun shape(
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

    private fun label(
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

    private fun render(
        drawable: Drawable,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val previousBounds = Rect(drawable.bounds)

        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = previousBounds

        return bitmap
    }

    private fun palette(packageName: String): GradientDrawable {
        val colors = arrayOf(
            intArrayOf(22, 57, 107),
            intArrayOf(58, 37, 96),
            intArrayOf(13, 78, 91),
            intArrayOf(94, 40, 76),
            intArrayOf(75, 57, 40)
        )

        val index = (
            (packageName.hashCode().toLong() and 0x7fffffffL) %
                colors.size
        ).toInt()

        val color = colors[index]

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(color[0], color[1], color[2]),
                Color.rgb(
                    color[0] / 2 + 4,
                    color[1] / 2 + 6,
                    color[2] / 2 + 12
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

    private fun remember(packageName: String) {
        prefs.edit()
            .putLong(packageName, System.currentTimeMillis())
            .apply()
    }

    private fun hiddenApps(): Set<String> {
        return prefs.getStringSet(
            "aura_hidden_apps",
            emptySet()
        )?.toSet() ?: emptySet()
    }

    private fun hideApp(packageName: String, name: String) {
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
                        hiddenApps() + packageName
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
                val packageName =
                    available[index].activityInfo.packageName

                prefs.edit()
                    .putStringSet(
                        "aura_hidden_apps",
                        hiddenApps() - packageName
                    )
                    .apply()

                selectedPackage = ""
                buildHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Keep Aura TV open")
    override fun onBackPressed() {
        // Back does not close the launcher.
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

        val overlay = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(28, 0, 0, 0),
                    Color.TRANSPARENT,
                    Color.argb(145, 0, 0, 0)
                )
            )
        }

        root.addView(
            overlay,
            FrameLayout.LayoutParams(-1, -1)
        )

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(46),
                dp(18),
                dp(46),
                dp(12)
            )

            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            content,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(root)

        createHeader()

        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(27))
        )

        feature = label(
            "Your entertainment",
            34f,
            true
        ).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        content.addView(feature)

        subtitle = label(
            "Pick up where you left off",
            13f
        ).apply {
            setTextColor(muted)
            setPadding(0, dp(9), 0, 0)
        }

        content.addView(subtitle)

        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(23))
        )

        val heading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        heading.addView(
            label("APPS", 14f, true).apply {
                letterSpacing = 0.18f
                setTextColor(muted)
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val restore = label(
            "HIDDEN APPS",
            10f,
            true
        ).apply {
            setTextColor(muted)

            setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
            )

            background = shape(panelColor, 12)

            isFocusable = true
            isClickable = true

            setOnClickListener {
                restoreApps()
            }

            setOnFocusChangeListener { view, focused ->
                view.background = shape(
                    if (focused)
                        Color.rgb(40, 66, 98)
                    else
                        panelColor,
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

        createAppGrid()

        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        footer.addView(
            label("AURA TV", 10f, true).apply {
                setTextColor(muted)
                letterSpacing = 0.12f
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        footer.addView(
            label("1.7", 10f, true).apply {
                setTextColor(accent)
            }
        )

        content.addView(footer)

        built = true
        refreshWeather()
    }

    private fun createHeader() {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        bar.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        createSpotify(bar)

        val weather = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(panelColor, 14)

            setPadding(
                dp(14),
                dp(9),
                dp(14),
                dp(9)
            )
        }

        weatherTemp = label("--°F", 16f, true)

        weatherDetail = label(
            "Bridgeville · Loading",
            10f
        ).apply {
            setTextColor(muted)
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
            background = shape(panelColor, 14)
            isFocusable = true
            isClickable = true
        }

        settings.setOnFocusChangeListener { view, focused ->
            view.background = shape(
                if (focused)
                    Color.rgb(40, 66, 98)
                else
                    panelColor,
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
                dp(48),
                dp(45)
            ).apply {
                leftMargin = dp(12)
            }
        )

        content.addView(bar)
    }

    private fun createSpotify(bar: LinearLayout) {
        musicPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(panelColor, 16)

            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )

            isFocusable = true
            isClickable = true
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        musicIdle = label(
            "♫  SPOTIFY · Press OK to open",
            12f,
            true
        ).apply {
            setTextColor(Color.rgb(169, 239, 195))
        }

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
                dp(49),
                dp(49)
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
            setTextColor(muted)
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
            visibility = View.VISIBLE
            setPadding(0, dp(9), 0, 0)
        }

        val previous = createControl("⏮") {
            transport {
                skipToPrevious()
            }
        }

        playButton = createControl("⏯") {
            val active = controller
                ?: AuraNotificationListener.notificationController

            transport {
                if (
                    active?.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
                ) {
                    pause()
                } else {
                    play()
                }
            }
        }

        val next = createControl("⏭") {
            transport {
                skipToNext()
            }
        }

        musicControls.addView(previous)

        musicControls.addView(
            playButton,
            LinearLayout.LayoutParams(
                dp(49),
                dp(36)
            ).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
            }
        )

        musicControls.addView(next)
        musicPanel.addView(musicControls)

        musicStatus = label("", 9f).apply {
            setTextColor(muted)
            visibility = View.GONE
        }

        musicPanel.addView(musicStatus)

        musicPanel.setOnFocusChangeListener { view, focused ->
            view.background = shape(
                if (focused)
                    Color.rgb(35, 58, 87)
                else
                    panelColor,
                16,
                if (focused)
                    accent
                else
                    Color.TRANSPARENT
            )

            musicControls.visibility = View.VISIBLE

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

            view.animate().cancel()

            view.animate()
                .scaleX(if (focused) 1.025f else 1f)
                .scaleY(if (focused) 1.025f else 1f)
                .setDuration(180)
                .start()
        }

        musicPanel.setOnClickListener {
            openSpotify()
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

        bar.addView(
            musicPanel,
            LinearLayout.LayoutParams(dp(292), -2)
        )
    }

    private fun openSpotify() {
        try {
            val launch =
                packageManager.getLaunchIntentForPackage(
                    "com.spotify.tv.android"
                ) ?: packageManager.getLaunchIntentForPackage(
                    "com.spotify.music"
                )

            if (launch != null) {
                launchedPackage = "spotify"
                startActivity(launch)
            } else {
                Toast.makeText(
                    this,
                    "Spotify app not found",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Could not open Spotify",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createControl(
        symbol: String,
        action: () -> Unit
    ): TextView {
        return label(symbol, 22f, true).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true

            background = shape(
                Color.argb(110, 67, 98, 126),
                10
            )

            setOnFocusChangeListener { view, focused ->
                view.background = shape(
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

            setOnKeyListener { _, keyCode, event ->
                if (
                    event.action ==
                        android.view.KeyEvent.ACTION_DOWN &&
                    keyCode ==
                        android.view.KeyEvent.KEYCODE_BACK
                ) {
                    musicPanel.requestFocus()
                    true
                } else {
                    false
                }
            }

            layoutParams = LinearLayout.LayoutParams(
                dp(43),
                dp(36)
            )
        }
    }

    private fun transport(
        action:
            MediaController.TransportControls.() -> Unit
    ) {
        try {
            val active = controller
                ?: AuraNotificationListener.notificationController

            if (active == null) {
                Toast.makeText(
                    this,
                    "Start playing Spotify first",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            active.transportControls.action()
        } catch (_: Exception) {}
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

            setPadding(
                dp(12),
                dp(13),
                dp(12),
                dp(13)
            )
        }

        val availableApps = apps()
        var first: View? = null

        for (
            columnIndex in
            0 until (availableApps.size + 1) / 2
        ) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            for (rowIndex in 0..1) {
                val index = columnIndex * 2 + rowIndex

                if (index >= availableApps.size) {
                    continue
                }

                val app = availableApps[index]
                val appPackage = app.activityInfo.packageName
                val name = app.loadLabel(
                    packageManager
                ).toString()

                val drawable =
                    if (iconCache.containsKey(appPackage)) {
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
                        dp(9),
                        dp(8),
                        dp(9),
                        dp(9)
                    )

                    background = shape(panelColor, 18)
                    isFocusable = true
                    isClickable = true
                    elevation = dp(2).toFloat()
                }

                val artwork = ImageView(this).apply {
                    setImageBitmap(
                        iconCache.getOrPut(appPackage) {
                            render(drawable!!, 240, 135)
                        }
                    )

                    scaleType =
                        ImageView.ScaleType.FIT_CENTER
                }

                tile.addView(
                    artwork,
                    LinearLayout.LayoutParams(
                        dp(160),
                        dp(85)
                    )
                )

                val appName = label(
                    name,
                    11f,
                    true
                ).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize =
                        TextUtils.TruncateAt.END

                    setPadding(
                        dp(3),
                        dp(8),
                        dp(3),
                        0
                    )
                }

                tile.addView(
                    appName,
                    LinearLayout.LayoutParams(-1, -2)
                )

                tile.setOnFocusChangeListener { view, focused ->
                    view.background = shape(
                        if (focused)
                            Color.argb(245, 38, 66, 100)
                        else
                            panelColor,
                        18,
                        if (focused)
                            accent
                        else
                            Color.TRANSPARENT
                    )

                    view.animate().cancel()

                    view.animate()
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

                    view.elevation = dp(
                        if (focused) 5 else 1
                    ).toFloat()

                    if (
                        focused &&
                        selectedPackage != appPackage
                    ) {
                        selectedPackage = appPackage
                        feature.text = name
                        subtitle.text =
                            "Press OK to open $name"

                        backdrop.background =
                            palette(appPackage)
                    }
                }

                tile.setOnLongClickListener {
                    hideApp(appPackage, name)
                    true
                }

                tile.setOnClickListener {
                    remember(appPackage)
                    launchedPackage = appPackage

                    try {
                        startActivity(
                            Intent().apply {
                                component = ComponentName(
                                    appPackage,
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
                                .getLaunchIntentForPackage(
                                    appPackage
                                )
                                ?.let {
                                    startActivity(it)
                                }
                        } catch (_: Exception) {}
                    }
                }

                column.addView(
                    tile,
                    LinearLayout.LayoutParams(
                        dp(183),
                        dp(135)
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
                    dp(183),
                    -2
                ).apply {
                    rightMargin = dp(14)
                }
            )
        }

        scroll.addView(columns)

        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                dp(311)
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
                if (show)
                    View.GONE
                else
                    View.VISIBLE

            musicDetails.visibility =
                if (show)
                    View.VISIBLE
                else
                    View.GONE

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
    ): Pair<String, String> {
        return when (code) {
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
    }

    private fun refreshWeather() {
        val now = System.currentTimeMillis()

        if (
            weatherLoading ||
            now - lastWeather < 15 * 60 * 1000L
        ) {
            return
        }

        weatherLoading = true

        thread(name = "aura-weather") {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=40.3562" +
                        "&longitude=-80.1101" +
                        "&current=temperature_2m,weather_code" +
                        "&temperature_unit=fahrenheit" +
                        "&timezone=America%2FNew_York"
                )

                connection = (
                    url.openConnection() as HttpURLConnection
                ).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }

                val json = JSONObject(
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                )

                val current =
                    json.getJSONObject("current")

                val temperature = current.getDouble(
                    "temperature_2m"
                ).toInt()

                val (symbol, condition) =
                    weatherDescription(
                        current.getInt("weather_code")
                    )

                handler.post {
                    if (!isFinishing && !isDestroyed) {
                        weatherTemp.text =
                            "$symbol  $temperature°F"

                        weatherDetail.text =
                            "Bridgeville · $condition"
                    }
                }

                lastWeather =
                    System.currentTimeMillis()

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
                connection?.disconnect()
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
