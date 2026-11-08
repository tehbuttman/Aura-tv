
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

            notificationArt = (
                extras.getParcelable(Notification.EXTRA_PICTURE)
                    as? Bitmap
                ) ?: notificationArt

        } catch (_: Exception) {}
    }
}

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var backdrop: ImageView
    private lateinit var featuredTitle: TextView
    private lateinit var clock: TextView

    private lateinit var spotifyBox: LinearLayout
    private lateinit var spotifyIdle: TextView
    private lateinit var spotifyDetails: LinearLayout
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView

    private var currentController: MediaController? = null

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun shape(
        color: Int,
        radius: Int = 18,
        stroke: Int = Color.TRANSPARENT
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()

        if (stroke != Color.TRANSPARENT) {
            setStroke(dp(2), stroke)
        }
    }

    private fun label(
        value: String,
        size: Float,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)

        if (bold) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun artworkBitmap(
        drawable: Drawable,
        width: Int,
        height: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)
        val original = Rect(drawable.bounds)

        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = original

        return output
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createHome()
    }

    private fun createHome() {

        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 12, 25))
        }

        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0f
        }

        frame.addView(
            backdrop,
            FrameLayout.LayoutParams(-1, -1)
        )

        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(246, 6, 11, 25),
                    Color.argb(192, 7, 13, 29),
                    Color.argb(165, 7, 13, 28)
                )
            )
        }

        frame.addView(
            shade,
            FrameLayout.LayoutParams(-1, -1)
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(44),
                dp(22),
                dp(44),
                dp(20)
            )
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

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(
            label("AURA", 26f, true).apply {
                letterSpacing = 0.16f
            }
        )

        brand.addView(
            label(
                "YOUR SPACE. YOUR ENTERTAINMENT.",
                9f,
                true
            ).apply {
                alpha = 0.55f
                letterSpacing = 0.08f
            }
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        clock = label("", 12f, true).apply {
            setPadding(dp(18), 0, 0, 0)
            alpha = 0.9f
        }

        header.addView(clock)
        root.addView(header)

        // FEATURED AREA

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(49))
        )

        root.addView(
            label("NOW EXPLORING", 11f, true).apply {
                alpha = 0.68f
                letterSpacing = 0.15f
            }
        )

        featuredTitle = label(
            "Welcome to Aura",
            42f,
            true
        ).apply {
            setPadding(0, dp(7), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        root.addView(featuredTitle)

        root.addView(
            label(
                "All your favorites, one beautiful place.",
                15f
            ).apply {
                alpha = 0.76f
                setPadding(0, dp(7), 0, 0)
            }
        )

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(43))
        )

        root.addView(label("Your Apps", 22f, true))

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(8))
        )

        createAppRow(root)

        root.addView(
            label("AURA TV  •  0.9", 10f).apply {
                alpha = 0.48f
                letterSpacing = 0.12f
                setPadding(0, dp(8), 0, 0)
            }
        )
    }

    private fun createAppRow(root: LinearLayout) {

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(
                dp(10),
                dp(13),
                dp(10),
                dp(18)
            )
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
                .toString()
                .lowercase(Locale.getDefault())
        }

        for (app in apps) {

            val name = app.loadLabel(
                packageManager
            ).toString()

            val drawable = app.activityInfo
                .loadBanner(packageManager)
                ?: app.loadIcon(packageManager)

            val tileArt = artworkBitmap(
                drawable,
                320,
                180
            )

            val backgroundArt = artworkBitmap(
                drawable,
                800,
                450
            )

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )
                background = shape(
                    Color.argb(190, 27, 40, 65),
                    18
                )
                isFocusable = true
                isClickable = true
                elevation = dp(2).toFloat()
            }

            val art = ImageView(this).apply {
                setImageBitmap(tileArt)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                art,
                LinearLayout.LayoutParams(
                    dp(164),
                    dp(92)
                )
            )

            tile.addView(
                label(name, 12f, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(
                        dp(2),
                        dp(9),
                        dp(2),
                        0
                    )
                },
                LinearLayout.LayoutParams(-1, -2)
            )

            tile.setOnFocusChangeListener { view, focused ->

                view.background = if (focused) {
                    shape(
                        Color.argb(245, 43, 73, 117),
                        18,
                        Color.rgb(171, 218, 255)
                    )
                } else {
                    shape(
                        Color.argb(190, 27, 40, 65),
                        18
                    )
                }

                view.animate().cancel()

                view.animate()
                    .scaleX(
                        if (focused) 1.075f else 1f
                    )
                    .scaleY(
                        if (focused) 1.075f else 1f
                    )
                    .translationY(
                        if (focused)
                            -dp(5).toFloat()
                        else
                            0f
                    )
                    .setDuration(210)
                    .start()

                view.elevation =
                    if (focused)
                        dp(14).toFloat()
                    else
                        dp(2).toFloat()

                if (focused) {

                    featuredTitle.animate().cancel()
                    featuredTitle.text = name
                    featuredTitle.alpha = 0.5f

                    featuredTitle.animate()
                        .alpha(1f)
                        .setDuration(230)
                        .start()

                    backdrop.animate().cancel()
                    backdrop.alpha = 0f
                    backdrop.setImageBitmap(backgroundArt)

                    backdrop.animate()
                        .alpha(0.29f)
                        .setDuration(420)
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
                        packageManager
                            .getLaunchIntentForPackage(
                                app.activityInfo.packageName
                            )?.let {
                                startActivity(it)
                            }
                    } catch (_: Exception) {}
                }
            }

            row.addView(
                tile,
                LinearLayout.LayoutParams(
                    dp(184),
                    dp(146)
                ).apply {
                    rightMargin = dp(13)
                }
            )
        }

        scroll.addView(row)
        root.addView(scroll)

        if (row.childCount > 0) {
            row.getChildAt(0).requestFocus()
        }
    }

    private fun createSpotifyHeader(
        header: LinearLayout
    ) {

        spotifyBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(
                Color.rgb(18, 66, 52),
                14
            )
            setPadding(
                dp(11),
                dp(7),
                dp(11),
                dp(7)
            )
            isFocusable = true
            isClickable = true
            setOnClickListener {
                openSpotify()
            }
        }

        spotifyBox.setOnFocusChangeListener { view, focused ->
            view.background = shape(
                if (focused)
                    Color.rgb(35, 131, 89)
                else
                    Color.rgb(18, 66, 52),
                14
            )
        }

        spotifyIdle = label(
            "♫  Spotify",
            13f,
            true
        )

        spotifyBox.addView(spotifyIdle)

        spotifyDetails = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        albumImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(
                Color.rgb(31, 78, 60),
                5
            )
        }

        spotifyDetails.addView(
            albumImage,
            LinearLayout.LayoutParams(
                dp(39),
                dp(39)
            )
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }

        songTitle = label("", 12f, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        songArtist = label("", 11f).apply {
            alpha = 0.78f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        info.addView(songTitle)
        info.addView(songArtist)

        spotifyDetails.addView(
            info,
            LinearLayout.LayoutParams(
                dp(155),
                -2
            )
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
                startActivity(Intent().apply {
                    component = ComponentName(
                        spotify.activityInfo.packageName,
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
                it.packageName.contains(
                    "spotify",
                    true
                ) &&
                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.packageName.contains(
                    "spotify",
                    true
                )
            } ?: AuraNotificationListener
                .notificationController

            val playing =
                currentController?.playbackState?.state ==
                    PlaybackState.STATE_PLAYING

            val metadata = currentController?.metadata

            val title = metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )?.takeIf {
                it.isNotBlank()
            } ?: AuraNotificationListener
                .notificationTitle

            val artist = metadata?.getString(
                MediaMetadata.METADATA_KEY_ARTIST
            )?.takeIf {
                it.isNotBlank()
            } ?: AuraNotificationListener
                .notificationArtist

            val art = metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ALBUM_ART
            ) ?: metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ART
            ) ?: AuraNotificationListener
                .notificationArt

            val show = playing && title.isNotBlank()

            spotifyIdle.visibility =
                if (show)
                    View.GONE
                else
                    View.VISIBLE

            spotifyDetails.visibility =
                if (show)
                    View.VISIBLE
                else
                    View.GONE

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
