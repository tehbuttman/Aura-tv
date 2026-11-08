
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

// Keep the same notification-listener component: existing TV access depends on its name.
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
        try { activeNotifications?.forEach { readNotification(it) } } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        connected = false
        notificationController = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { readNotification(sbn) }

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
            val token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
            if (token != null) notificationController = MediaController(this, token)
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { notificationTitle = it }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }?.let { notificationArtist = it }
            notificationArt = (extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                ?: notificationArt
        } catch (_: Exception) {}
    }
}

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var background: View
    private lateinit var ambientGlow: View
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
    private var selectedIndex = -1

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, border: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != Color.TRANSPARENT) setStroke(dp(2), border)
        }

    private fun text(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

    private fun artBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val original = Rect(drawable.bounds)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = original
        return bitmap
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        createHome()
    }

    private fun createHome() {
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 9, 19)) }
        background = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(16, 43, 77), Color.rgb(9, 19, 39), Color.rgb(5, 9, 19))
            )
        }
        frame.addView(background, FrameLayout.LayoutParams(-1, -1))

        ambientGlow = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.argb(100, 42, 125, 198), Color.TRANSPARENT, Color.TRANSPARENT)
            )
            alpha = 0.65f
        }
        frame.addView(ambientGlow, FrameLayout.LayoutParams(-1, -1))

        val dim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(38, 0, 0, 0), Color.TRANSPARENT, Color.argb(125, 1, 4, 12))
            )
        }
        frame.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(27), dp(48), dp(20))
            clipChildren = false
            clipToPadding = false
        }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("AURA", 30f, true).apply { letterSpacing = 0.19f })
        brand.addView(text("A BETTER WAY TO WATCH", 10f, true).apply {
            alpha = 0.60f
            letterSpacing = 0.14f
            setPadding(0, dp(5), 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        createSpotifyHeader(header)
        clock = text("", 13f, true).apply {
            setPadding(dp(22), 0, 0, 0)
            alpha = 0.91f
        }
        header.addView(clock)
        root.addView(header)

        root.addView(View(this), LinearLayout.LayoutParams(1, dp(55)))
        root.addView(text("✦  DISCOVER YOUR WORLD", 11f, true).apply {
            letterSpacing = 0.18f
            setTextColor(Color.rgb(166, 211, 255))
        })
        featuredTitle = text("Welcome to Aura", 47f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(13), 0, 0)
        }
        root.addView(featuredTitle)
        featuredSubtitle = text("Your entertainment starts here", 15f).apply {
            alpha = 0.77f
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(featuredSubtitle)

        root.addView(View(this), LinearLayout.LayoutParams(1, dp(42)))
        val appsHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        appsHeading.addView(text("Your Apps", 21f, true), LinearLayout.LayoutParams(0, -2, 1f))
        appsHeading.addView(text("PRESS OK TO OPEN", 10f, true).apply {
            letterSpacing = 0.11f
            alpha = 0.58f
        })
        root.addView(appsHeading)
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(9)))
        createAppRow(root)

        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(text("AURA TV  /  CINEMATIC EDITION", 10f, true).apply {
            alpha = 0.55f
            letterSpacing = 0.10f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(text("1.0", 11f, true).apply { alpha = 0.65f })
        root.addView(footer)
    }

    private fun createAppRow(root: LinearLayout) {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(13), dp(18), dp(13), dp(20))
        }
        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }

        val palettes = arrayOf(
            intArrayOf(20, 67, 113), intArrayOf(65, 37, 109),
            intArrayOf(13, 98, 102), intArrayOf(103, 46, 73),
            intArrayOf(91, 66, 31), intArrayOf(37, 75, 102)
        )
        apps.forEachIndexed { index, app ->
            val name = app.loadLabel(packageManager).toString()
            val drawable = app.activityInfo.loadBanner(packageManager)
                ?: app.loadIcon(packageManager)
            val bitmap = artBitmap(drawable, 320, 180)
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(12))
                background = rounded(Color.argb(155, 29, 41, 62), 20)
                isFocusable = true
                isClickable = true
                elevation = dp(3).toFloat()
            }
            tile.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(166), dp(91)))
            tile.addView(text(name, 12f, true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(12), dp(2), 0)
            }, LinearLayout.LayoutParams(-1, -2))

            tile.setOnFocusChangeListener { view, focused ->
                view.background = if (focused) rounded(
                    Color.argb(230, 43, 63, 93), 20, Color.rgb(198, 232, 255)
                ) else rounded(Color.argb(155, 29, 41, 62), 20)
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.09f else 1f)
                    .scaleY(if (focused) 1.09f else 1f)
                    .translationY(if (focused) -dp(7).toFloat() else 0f)
                    .setDuration(220).start()
                view.elevation = dp(if (focused) 16 else 3).toFloat()
                if (focused && selectedIndex != index) {
                    selectedIndex = index
                    featuredTitle.animate().cancel()
                    featuredTitle.text = name
                    featuredTitle.alpha = 0.45f
                    featuredTitle.animate().alpha(1f).setDuration(280).start()
                    featuredSubtitle.text = "Press OK to launch $name"
                    val rgb = palettes[index % palettes.size]
                    background.background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                            Color.rgb(rgb[0], rgb[1], rgb[2]),
                            Color.rgb(rgb[0] / 3 + 4, rgb[1] / 3 + 8, rgb[2] / 3 + 13),
                            Color.rgb(5, 9, 19)
                        )
                    )
                    ambientGlow.animate().cancel()
                    ambientGlow.alpha = 0.1f
                    ambientGlow.animate().alpha(0.65f).setDuration(420).start()
                }
            }
            tile.setOnClickListener {
                try {
                    startActivity(Intent().apply {
                        component = ComponentName(app.activityInfo.packageName, app.activityInfo.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {
                    try {
                        packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                            ?.let { startActivity(it) }
                    } catch (_: Exception) {}
                }
            }
            row.addView(tile, LinearLayout.LayoutParams(dp(190), dp(150)).apply {
                rightMargin = dp(15)
            })
        }
        scroll.addView(row)
        root.addView(scroll)
        if (row.childCount > 0) row.getChildAt(0).requestFocus()
    }

    private fun createSpotifyHeader(header: LinearLayout) {
        spotifyBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(178, 21, 37, 51), 16)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isFocusable = true
            isClickable = true
            setOnClickListener { openSpotify() }
        }
        spotifyBox.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused) Color.argb(235, 38, 87, 92) else Color.argb(178, 21, 37, 51),
                16, if (focused) Color.rgb(172, 241, 224) else Color.TRANSPARENT
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
            background = rounded(Color.rgb(30, 61, 64), 6)
        }
        spotifyDetails.addView(albumImage, LinearLayout.LayoutParams(dp(43), dp(43)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        songTitle = text("", 12f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        songArtist = text("", 11f).apply {
            alpha = 0.77f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }
        info.addView(songTitle)
        info.addView(songArtist)
        spotifyDetails.addView(info, LinearLayout.LayoutParams(dp(158), -2))
        spotifyBox.addView(spotifyDetails)
        header.addView(spotifyBox)
    }

    private fun openSpotify() {
        for (pkg in listOf("com.spotify.tv.android", "com.spotify.music")) {
            try {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) { startActivity(launch); return }
            } catch (_: Exception) {}
        }
        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val spotify = packageManager.queryIntentActivities(query, 0).firstOrNull {
            it.activityInfo.packageName.contains("spotify", true)
        }
        if (spotify != null) {
            try {
                startActivity(Intent().apply {
                    component = ComponentName(spotify.activityInfo.packageName, spotify.activityInfo.name)
                })
            } catch (_: Exception) {}
        }
    }

    // This detection is intentionally unchanged from the working 0.9 release.
    private fun updateSpotify() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(this, AuraNotificationListener::class.java)
            val sessions = try { manager.getActiveSessions(listener) }
                catch (_: Exception) { emptyList<MediaController>() }
            currentController = sessions.firstOrNull {
                it.packageName.contains("spotify", true) &&
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.packageName.contains("spotify", true)
            } ?: AuraNotificationListener.notificationController
            val playing = currentController?.playbackState?.state == PlaybackState.STATE_PLAYING
            val metadata = currentController?.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: AuraNotificationListener.notificationTitle
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: AuraNotificationListener.notificationArtist
            val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: AuraNotificationListener.notificationArt
            val show = playing && title.isNotBlank()
            spotifyIdle.visibility = if (show) View.GONE else View.VISIBLE
            spotifyDetails.visibility = if (show) View.VISIBLE else View.GONE
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
        clock.text = SimpleDateFormat("EEE, MMM d  •  h:mm a", Locale.getDefault()).format(Date())
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
