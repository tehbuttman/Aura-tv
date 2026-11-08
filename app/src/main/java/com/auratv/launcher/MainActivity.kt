
package com.auratv.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class AuraNotificationListener :
    android.service.notification.NotificationListenerService()

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private lateinit var backgroundLayer: FrameLayout
    private lateinit var backgroundArt: ImageView
    private lateinit var backgroundShade: View
    private lateinit var clock: TextView
    private lateinit var spotifyArea: LinearLayout
    private lateinit var spotifyArtwork: ImageView
    private lateinit var spotifyTitle: TextView
    private lateinit var spotifyArtist: TextView
    private lateinit var spotifyIdle: TextView
    private lateinit var spotifyPlaying: LinearLayout
    private lateinit var featuredTitle: TextView

    private var lastMusicKey = ""
    private var currentBackgroundKey = ""

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 20
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun gradient(color: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                darken(color, 0.50f),
                darken(color, 0.20f),
                Color.rgb(5, 9, 19)
            )
        )

    private fun darken(color: Int, amount: Float): Int =
        Color.rgb(
            (Color.red(color) * amount).toInt(),
            (Color.green(color) * amount).toInt(),
            (Color.blue(color) * amount).toInt()
        )

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        val frame = FrameLayout(this)

        backgroundLayer = FrameLayout(this)
        backgroundLayer.background =
            gradient(Color.rgb(35, 55, 100))

        backgroundArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.14f
        }

        backgroundLayer.addView(
            backgroundArt,
            FrameLayout.LayoutParams(-1, -1)
        )

        backgroundShade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(155, 2, 5, 14),
                    Color.argb(115, 3, 7, 18),
                    Color.argb(240, 3, 7, 17)
                )
            )
        }

        backgroundLayer.addView(
            backgroundShade,
            FrameLayout.LayoutParams(-1, -1)
        )

        frame.addView(backgroundLayer)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(22), dp(48), dp(24))
            clipChildren = false
            clipToPadding = false
        }

        frame.addView(root)
        setContentView(frame)

        // TOP BAR

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            text("AURA", 26f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        createSpotifyHeader(header)

        clock = text("", 14f, true).apply {
            setPadding(dp(22), 0, 0, 0)
        }
        updateClock()
        header.addView(clock)

        root.addView(header)

        // FEATURED AREA

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(54))
        )

        root.addView(text("YOUR ENTERTAINMENT", 12f, true).apply {
            alpha = 0.7f
        })

        featuredTitle = text("Welcome to Aura.", 39f, true).apply {
            maxLines = 2
            setPadding(0, dp(12), 0, 0)
        }

        root.addView(featuredTitle)

        root.addView(text(
            "Choose an app and enjoy.",
            16f
        ).apply {
            alpha = 0.72f
            setPadding(0, dp(10), 0, 0)
        })

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(60))
        )

        // APP DOCK

        root.addView(text("Your Apps", 23f, true))

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(12))
        )

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            descendantFocusability =
                android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(10), dp(14), dp(10), dp(18))
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
            it.loadLabel(packageManager).toString().lowercase()
        }

        for (app in apps) {
            val name = app.loadLabel(packageManager).toString()
            val artwork = app.activityInfo.loadBanner(packageManager)
                ?: app.loadIcon(packageManager)

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(
                    Color.argb(105, 35, 46, 65),
                    22
                )
                isFocusable = true
                isClickable = true
            }

            val image = ImageView(this).apply {
                setImageDrawable(artwork)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                image,
                LinearLayout.LayoutParams(
                    dp(164),
                    dp(93)
                )
            )

            tile.addView(
                text(name, 13f, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize =
                        android.text.TextUtils.TruncateAt.END
                    setPadding(dp(4), dp(10), dp(4), 0)
                },
                LinearLayout.LayoutParams(-1, -2)
            )

            tile.setOnFocusChangeListener { view, focused ->
                view.background = rounded(
                    if (focused)
                        Color.argb(220, 85, 107, 145)
                    else
                        Color.argb(105, 35, 46, 65),
                    22
                )

                view.animate()
                    .scaleX(if (focused) 1.075f else 1f)
                    .scaleY(if (focused) 1.075f else 1f)
                    .setDuration(180)
                    .start()

                if (focused) {
                    featuredTitle.text = name
                    changeBackground(
                        app.activityInfo.packageName,
                        artwork
                    )
                }
            }

            tile.setOnClickListener {
                try {
                    startActivity(Intent().apply {
                        component = ComponentName(
                            app.activityInfo.packageName,
                            app.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                    dp(188),
                    dp(151)
                ).apply {
                    rightMargin = dp(12)
                }
            )
        }

        scroller.addView(row)
        root.addView(scroller)

        root.addView(text("AURA TV  •  0.5", 11f).apply {
            alpha = 0.45f
            setPadding(0, dp(14), 0, 0)
        })

        if (row.childCount > 0) {
            row.getChildAt(0).requestFocus()
        }
    }

    private fun createSpotifyHeader(header: LinearLayout) {
        spotifyArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(12), dp(7))
            isFocusable = true
            isClickable = true
            background = rounded(
                Color.argb(160, 19, 54, 43),
                17
            )
            setOnClickListener { openSpotify() }
        }

        spotifyArea.setOnFocusChangeListener { view, focused ->
            view.background = rounded(
                if (focused)
                    Color.rgb(32, 135, 83)
                else
                    Color.argb(160, 19, 54, 43),
                17
            )
        }

        spotifyIdle = text("♫  Spotify", 14f, true)
        spotifyArea.addView(spotifyIdle)

        spotifyPlaying = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        spotifyArtwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(33, 70, 57), 7)
        }

        spotifyPlaying.addView(
            spotifyArtwork,
            LinearLayout.LayoutParams(dp(43), dp(43))
        )

        val songInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }

        spotifyTitle = text("Spotify", 14f, true).apply {
            maxLines = 1
            ellipsize =
                android.text.TextUtils.TruncateAt.END
        }

        spotifyArtist = text("", 12f).apply {
            alpha = 0.75f
            maxLines = 1
            ellipsize =
                android.text.TextUtils.TruncateAt.END
        }

        songInfo.addView(spotifyTitle)
        songInfo.addView(spotifyArtist)

        spotifyPlaying.addView(
            songInfo,
            LinearLayout.LayoutParams(dp(145), -2)
        )

        spotifyArea.addView(spotifyPlaying)
        header.addView(spotifyArea)
    }

    private fun changeBackground(
        key: String,
        drawable: Drawable
    ) {
        if (currentBackgroundKey == key) return
        currentBackgroundKey = key

        val color = extractColor(drawable)

        backgroundLayer.background = gradient(color)

        backgroundArt.animate().cancel()
        backgroundArt.alpha = 0f
        backgroundArt.setImageDrawable(
            drawable.constantState?.newDrawable()?.mutate()
                ?: drawable
        )
        backgroundArt.animate()
            .alpha(0.18f)
            .setDuration(350)
            .start()
    }

    private fun extractColor(drawable: Drawable): Int {
        return try {
            val bitmap = Bitmap.createBitmap(
                24, 24, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val oldBounds = drawable.bounds
            drawable.setBounds(0, 0, 24, 24)
            drawable.draw(canvas)
            drawable.bounds = oldBounds

            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L

            for (y in 0 until 24 step 2) {
                for (x in 0 until 24 step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 80) {
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                        count++
                    }
                }
            }

            bitmap.recycle()

            if (count == 0L) {
                Color.rgb(45, 65, 105)
            } else {
                Color.rgb(
                    ((red / count).toInt() + 35).coerceAtMost(255),
                    ((green / count).toInt() + 35).coerceAtMost(255),
                    ((blue / count).toInt() + 35).coerceAtMost(255)
                )
            }
        } catch (_: Exception) {
            Color.rgb(45, 65, 105)
        }
    }

    private fun openSpotify() {
        val packages = listOf(
            "com.spotify.tv.android",
            "com.spotify.music"
        )

        for (pkg in packages) {
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
            it.activityInfo.packageName.contains(
                "spotify",
                ignoreCase = true
            )
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
        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val listener = ComponentName(
                this,
                AuraNotificationListener::class.java
            )

            controller = manager.getActiveSessions(
                listener
            ).firstOrNull {
                it.packageName.contains(
                    "spotify",
                    ignoreCase = true
                )
            }

            val playing = controller?.playbackState?.state ==
                PlaybackState.STATE_PLAYING

            spotifyIdle.visibility =
                if (playing) View.GONE else View.VISIBLE

            spotifyPlaying.visibility =
                if (playing) View.VISIBLE else View.GONE

            if (playing) {
                val metadata = controller?.metadata

                val title = metadata?.getString(
                    MediaMetadata.METADATA_KEY_TITLE
                ) ?: "Spotify"

                val artist = metadata?.getString(
                    MediaMetadata.METADATA_KEY_ARTIST
                ) ?: "Now playing"

                val key = "$title|$artist"

                if (key != lastMusicKey) {
                    lastMusicKey = key

                    spotifyTitle.text = title
                    spotifyArtist.text = artist

                    val artwork = metadata?.getBitmap(
                        MediaMetadata.METADATA_KEY_ALBUM_ART
                    ) ?: metadata?.getBitmap(
                        MediaMetadata.METADATA_KEY_ART
                    )

                    spotifyArtwork.setImageBitmap(artwork)
                }
            } else {
                lastMusicKey = ""
            }
        } catch (_: Exception) {
            controller = null
            spotifyIdle.visibility = View.VISIBLE
            spotifyPlaying.visibility = View.GONE
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
