
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
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
        @Volatile var title = ""
        @Volatile var artist = ""
        @Volatile var artwork: Bitmap? = null
        @Volatile var controller: MediaController? = null
        @Volatile var notificationSeen = false
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        try {
            activeNotifications?.forEach { readSpotify(it) }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        connected = false
        controller = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        readSpotify(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName.contains("spotify", true)) {
            notificationSeen = false
            title = ""
            artist = ""
            artwork = null
            controller = null
        }
    }

    private fun readSpotify(sbn: StatusBarNotification) {
        if (!sbn.packageName.contains("spotify", true)) return
        notificationSeen = true

        try {
            val extras = sbn.notification.extras

            val token = extras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION
            ) as? MediaSession.Token

            if (token != null) {
                controller = MediaController(this, token)
            }

            val newTitle = extras.getCharSequence(
                Notification.EXTRA_TITLE
            )?.toString().orEmpty()

            val newArtist = extras.getCharSequence(
                Notification.EXTRA_TEXT
            )?.toString().orEmpty()

            if (newTitle.isNotBlank()) title = newTitle
            if (newArtist.isNotBlank()) artist = newArtist

            val newArt = extras.getParcelable(
                Notification.EXTRA_PICTURE
            ) as? Bitmap

            if (newArt != null) artwork = newArt

        } catch (_: Exception) {}
    }
}

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var backgroundImage: ImageView
    private lateinit var featuredTitle: TextView
    private lateinit var clock: TextView
    private lateinit var spotifyBox: LinearLayout
    private lateinit var spotifyStatus: TextView
    private lateinit var spotifyDetails: LinearLayout
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var accessButton: TextView

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        value: String,
        size: Float,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun bitmapFromDrawable(
        drawable: Drawable,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
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
        buildHome()
    }

    private fun buildHome() {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.rgb(8, 13, 26))

        backgroundImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.22f
        }

        frame.addView(
            backgroundImage,
            FrameLayout.LayoutParams(-1, -1)
        )

        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(160, 5, 10, 23),
                    Color.argb(135, 6, 12, 27),
                    Color.argb(245, 4, 8, 19)
                )
            )
        }

        frame.addView(
            shade,
            FrameLayout.LayoutParams(-1, -1)
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(44), dp(22), dp(44), dp(22))
            clipChildren = false
            clipToPadding = false
        }

        frame.addView(root)
        setContentView(frame)

        // HEADER

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            label("AURA", 26f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        clock = label("", 13f, true).apply {
            setPadding(dp(18), 0, 0, 0)
        }

        header.addView(clock)
        root.addView(header)

        // MUSIC ACCESS BUTTON

        accessButton = label(
            "Music Access Settings", 12f, true
        ).apply {
            gravity = Gravity.CENTER
            background = shape(
                Color.rgb(42, 58, 82), 12
            )
            setPadding(
                dp(12), dp(8), dp(12), dp(8)
            )
            isFocusable = true
            isClickable = true
            setOnClickListener {
                openMusicAccessSettings()
            }
            setOnFocusChangeListener { view, focused ->
                view.background = shape(
                    if (focused)
                        Color.rgb(65, 106, 158)
                    else
                        Color.rgb(42, 58, 82),
                    12
                )
            }
        }

        val accessRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }

        accessRow.addView(accessButton)
        root.addView(accessRow)

        // FEATURED SECTION

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(28))
        )

        root.addView(
            label("YOUR ENTERTAINMENT", 12f, true).apply {
                alpha = 0.75f
            }
        )

        featuredTitle = label(
            "Welcome to Aura", 39f, true
        ).apply {
            setPadding(0, dp(10), 0, 0)
            maxLines = 2
        }

        root.addView(featuredTitle)

        root.addView(
            label(
                "Everything you love, beautifully organized.",
                15f
            ).apply {
                alpha = 0.75f
                setPadding(0, dp(8), 0, 0)
            }
        )

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(35))
        )

        root.addView(label("Your Apps", 23f, true))

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(12))
        )

        createAppRow(root)

        root.addView(
            label("AURA TV  •  0.8", 11f).apply {
                alpha = 0.5f
                setPadding(0, dp(10), 0, 0)
            }
        )
    }

    private fun createAppRow(root: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(10), dp(12), dp(10), dp(18))
        }

        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(
            query,
            PackageManager.MATCH_ALL
        ).filter {
            it.activityInfo.packageName != packageName
        }.distinctBy {
            it.activityInfo.packageName
        }.sortedBy {
            it.loadLabel(packageManager)
                .toString().lowercase()
        }

        for (app in apps) {
            val name = app.loadLabel(
                packageManager
            ).toString()

            val drawable = app.activityInfo
                .loadBanner(packageManager)
                ?: app.loadIcon(packageManager)

            // Separate copies protect tile images.
            val tileBitmap = bitmapFromDrawable(
                drawable, 320, 180
            )

            val backgroundBitmap = bitmapFromDrawable(
                drawable, 640, 360
            )

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = shape(
                    Color.argb(130, 32, 45, 65), 20
                )
                isFocusable = true
                isClickable = true
            }

            val image = ImageView(this).apply {
                setImageBitmap(tileBitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                image,
                LinearLayout.LayoutParams(
                    dp(163), dp(92)
                )
            )

            tile.addView(
                label(name, 13f, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize =
                        android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(10), 0, 0)
                },
                LinearLayout.LayoutParams(-1, -2)
            )

            tile.setOnFocusChangeListener { view, focused ->
                view.background = shape(
                    if (focused)
                        Color.rgb(60, 102, 151)
                    else
                        Color.argb(130, 32, 45, 65),
                    20
                )

                view.animate()
                    .scaleX(if (focused) 1.07f else 1f)
                    .scaleY(if (focused) 1.07f else 1f)
                    .setDuration(180)
                    .start()

                if (focused) {
                    featuredTitle.text = name

                    backgroundImage.animate().cancel()
                    backgroundImage.alpha = 0f
                    backgroundImage.setImageBitmap(
                        backgroundBitmap
                    )
                    backgroundImage.animate()
                        .alpha(0.22f)
                        .setDuration(320)
                        .start()
                }
            }

            tile.setOnClickListener {
                try {
                    startActivity(Intent().apply {
                        component = ComponentName(
                            app.activityInfo.packageName,
                            app.activityInfo.name
                        )
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    })
                } catch (_: Exception) {
                    try {
                        packageManager.getLaunchIntentForPackage(
                            app.activityInfo.packageName
                        )?.let { startActivity(it) }
                    } catch (_: Exception) {}
                }
            }

            row.addView(
                tile,
                LinearLayout.LayoutParams(
                    dp(187), dp(150)
                ).apply {
                    rightMargin = dp(12)
                }
            )
        }

        scroller.addView(row)
        root.addView(scroller)

        if (row.childCount > 0) {
            row.getChildAt(0).requestFocus()
        }
    }

    private fun createSpotifyHeader(header: LinearLayout) {
        spotifyBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(
                Color.rgb(22, 58, 46), 15
            )
            setPadding(
                dp(12), dp(8), dp(12), dp(8)
            )
            isFocusable = true
            isClickable = true
            setOnClickListener { openSpotify() }
        }

        spotifyBox.setOnFocusChangeListener { view, focused ->
            view.background = shape(
                if (focused)
                    Color.rgb(34, 135, 83)
                else
                    Color.rgb(22, 58, 46),
                15
            )
        }

        spotifyStatus = label(
            "♫ Spotify: Checking", 13f, true
        )
        spotifyBox.addView(spotifyStatus)

        spotifyDetails = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        albumImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(
                Color.rgb(36, 76, 59), 6
            )
        }

        spotifyDetails.addView(
            albumImage,
            LinearLayout.LayoutParams(
                dp(46), dp(46)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        songTitle = label("", 14f, true).apply {
            maxLines = 1
            ellipsize =
                android.text.TextUtils.TruncateAt.END
        }

        songArtist = label("", 12f).apply {
            alpha = 0.75f
            maxLines = 1
            ellipsize =
                android.text.TextUtils.TruncateAt.END
        }

        info.addView(songTitle)
        info.addView(songArtist)

        spotifyDetails.addView(
            info,
            LinearLayout.LayoutParams(dp(155), -2)
        )

        spotifyBox.addView(spotifyDetails)
        header.addView(spotifyBox)
    }

    private fun openMusicAccessSettings() {
        val actions = listOf(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            Settings.ACTION_SETTINGS
        )

        for (action in actions) {
            try {
                startActivity(Intent(action))
                return
            } catch (_: Exception) {}
        }

        spotifyStatus.text = "♫ Settings unavailable"
    }

    private fun hasMusicAccess(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()

            enabled.split(":").any {
                val component = ComponentName
                    .unflattenFromString(it)

                component?.packageName == packageName &&
                    component.className ==
                        AuraNotificationListener::class.java.name
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openSpotify() {
        for (pkg in listOf(
            "com.spotify.tv.android",
            "com.spotify.music"
        )) {
            val launch = packageManager
                .getLaunchIntentForPackage(pkg)

            if (launch != null) {
                startActivity(launch)
                return
            }
        }

        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val spotify = packageManager.queryIntentActivities(
            query, 0
        ).firstOrNull {
            it.activityInfo.packageName
                .contains("spotify", true)
        }

        if (spotify != null) {
            startActivity(Intent().apply {
                component = ComponentName(
                    spotify.activityInfo.packageName,
                    spotify.activityInfo.name
                )
            })
        }
    }

    private fun updateSpotify() {
        val access = hasMusicAccess()

        if (!access) {
            spotifyStatus.text = "♫ Music access not enabled"
            spotifyStatus.visibility = View.VISIBLE
            spotifyDetails.visibility = View.GONE
            return
        }

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
            } catch (_: SecurityException) {
                spotifyStatus.text =
                    "♫ Access granted • Session blocked"
                spotifyDetails.visibility = View.GONE
                return
            }

            val spotifySessions = sessions.filter {
                it.packageName.contains("spotify", true)
            }

            val session = spotifySessions.firstOrNull {
                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            } ?: spotifySessions.firstOrNull()
                ?: AuraNotificationListener.controller

            val state = session?.playbackState?.state
            val metadata = session?.metadata

            val title = metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )?.takeIf { it.isNotBlank() }
                ?: AuraNotificationListener.title

            val artist = metadata?.getString(
                MediaMetadata.METADATA_KEY_ARTIST
            )?.takeIf { it.isNotBlank() }
                ?: AuraNotificationListener.artist

            val art = metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ALBUM_ART
            ) ?: metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ART
            ) ?: AuraNotificationListener.artwork

            val status = when {
                !AuraNotificationListener.connected ->
                    "Granted • Listener disconnected"

                session == null &&
                    AuraNotificationListener.notificationSeen ->
                    "Notification detected"

                session == null ->
                    "Granted • No Spotify session"

                state == PlaybackState.STATE_PLAYING &&
                    title.isBlank() ->
                    "Playing • No title"

                state == PlaybackState.STATE_PLAYING ->
                    "Playing"

                state == PlaybackState.STATE_PAUSED ->
                    "Paused"

                state == null ->
                    "State unknown"

                else ->
                    "Playback state $state"
            }

            spotifyStatus.text = "♫ $status"

            val showDetails =
                state == PlaybackState.STATE_PLAYING &&
                title.isNotBlank()

            spotifyDetails.visibility =
                if (showDetails) View.VISIBLE else View.GONE

            if (showDetails) {
                songTitle.text = title
                songArtist.text = artist.ifBlank {
                    "Unknown artist"
                }
                albumImage.setImageBitmap(art)
            }

        } catch (e: Exception) {
            spotifyStatus.text =
                "♫ Error: ${e.javaClass.simpleName}"
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
