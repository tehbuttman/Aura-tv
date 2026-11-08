
package com.auratv.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuraNotificationListener : NotificationListenerService()

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private lateinit var musicPanel: LinearLayout
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var albumArt: ImageView
    private lateinit var playButton: TextView
    private lateinit var clockText: TextView

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun shape(
        color: Int,
        radius: Int = 22
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun focusEffect(
        view: View,
        normal: Int,
        focusedColor: Int,
        radius: Int = 22
    ) {
        view.isFocusable = true
        view.isClickable = true
        view.background = shape(normal, radius)

        view.setOnFocusChangeListener { v, focused ->
            v.background = shape(
                if (focused) focusedColor else normal,
                radius
            )
            v.animate()
                .scaleX(if (focused) 1.065f else 1f)
                .scaleY(if (focused) 1.065f else 1f)
                .setDuration(180)
                .start()
            v.elevation = if (focused) dp(12).toFloat() else 0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        val frame = FrameLayout(this)

        frame.addView(
            CinematicBackground(),
            FrameLayout.LayoutParams(-1, -1)
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(26), dp(48), dp(30))
            clipChildren = false
            clipToPadding = false
        }

        scroll.addView(root)
        frame.addView(scroll)
        setContentView(frame)

        // TOP NAVIGATION

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = text("AURA", 27f, true)
        top.addView(
            logo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val spotify = text("♫  Spotify", 14f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { openSpotify() }
        }

        focusEffect(
            spotify,
            Color.rgb(22, 53, 43),
            Color.rgb(29, 135, 82),
            25
        )

        top.addView(spotify)

        clockText = text("", 14f, true).apply {
            setPadding(dp(24), 0, 0, 0)
        }
        updateClock()
        top.addView(clockText)

        root.addView(top)

        // MAIN HEADING

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(55))
        )

        root.addView(text("Welcome home.", 39f, true))

        root.addView(text(
            "Your favorite entertainment, all in one place.",
            16f
        ).apply {
            alpha = 0.7f
            setPadding(0, dp(9), 0, 0)
        })

        // SPOTIFY NOW PLAYING

        createMusicPanel(root)

        // APP SECTION

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(42))
        )

        val appsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        appsHeader.addView(
            text("Your Apps", 23f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        appsHeader.addView(
            text("Explore  •  Select to open", 12f).apply {
                alpha = 0.65f
            }
        )

        root.addView(appsHeader)

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(16))
        )

        createAppDock(root)

        root.addView(text(
            "AURA TV 0.4   •   MADE FOR YOUR TV",
            11f
        ).apply {
            alpha = 0.45f
            setPadding(0, dp(35), 0, 0)
        })
    }

    private fun createAppDock(root: LinearLayout) {
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(10), dp(12), dp(10), dp(15))
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

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(17), dp(15), dp(12))
            }

            focusEffect(
                tile,
                Color.rgb(29, 39, 58),
                Color.rgb(67, 91, 137),
                24
            )

            val banner = app.activityInfo.loadBanner(packageManager)

            if (banner != null) {
                val bannerImage = ImageView(this).apply {
                    setImageDrawable(banner)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                tile.addView(
                    bannerImage,
                    LinearLayout.LayoutParams(
                        dp(176),
                        dp(100)
                    )
                )
            } else {
                val icon = ImageView(this).apply {
                    setImageDrawable(
                        app.loadIcon(packageManager)
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                tile.addView(
                    icon,
                    LinearLayout.LayoutParams(
                        dp(100),
                        dp(100)
                    )
                )
            }

            val appLabel = text(name, 14f, true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize =
                    android.text.TextUtils.TruncateAt.END
                setPadding(dp(4), dp(15), dp(4), 0)
            }

            tile.addView(
                appLabel,
                LinearLayout.LayoutParams(-1, -2)
            )

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
                    dp(215),
                    dp(176)
                ).apply {
                    rightMargin = dp(18)
                }
            )
        }

        scroller.addView(row)
        root.addView(scroller)
    }

    private fun createMusicPanel(root: LinearLayout) {
        musicPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = shape(Color.rgb(20, 45, 46), 24)
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.topMargin = dp(24)

        root.addView(musicPanel, params)

        musicPanel.addView(
            text("♫  NOW PLAYING ON SPOTIFY", 12f, true)
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        albumArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.rgb(42, 71, 69), 12)
        }

        row.addView(
            albumArt,
            LinearLayout.LayoutParams(dp(72), dp(72))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(12), 0)
        }

        songTitle = text("Spotify", 21f, true).apply {
            maxLines = 1
        }

        songArtist = text("Now playing", 14f).apply {
            alpha = 0.7f
            maxLines = 1
        }

        info.addView(songTitle)
        info.addView(songArtist)

        row.addView(
            info,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        row.addView(musicControl("❮❮") {
            controller?.transportControls?.skipToPrevious()
        })

        playButton = musicControl("Ⅱ") {
            val state = controller?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                controller?.transportControls?.pause()
            } else {
                controller?.transportControls?.play()
            }
            handler.postDelayed({ updateMusic() }, 350)
        }
        row.addView(playButton)

        row.addView(musicControl("❯❯") {
            controller?.transportControls?.skipToNext()
        })

        musicPanel.addView(row)
    }

    private fun musicControl(
        symbol: String,
        action: () -> Unit
    ): TextView {
        val button = text(symbol, 18f, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }

        focusEffect(
            button,
            Color.rgb(35, 69, 68),
            Color.rgb(40, 139, 93),
            14
        )

        button.layoutParams =
            LinearLayout.LayoutParams(
                dp(52),
                dp(52)
            ).apply {
                leftMargin = dp(8)
            }

        return button
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

    private fun updateMusic() {
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

            val state = controller?.playbackState?.state

            val playing =
                state == PlaybackState.STATE_PLAYING

            musicPanel.visibility =
                if (playing) View.VISIBLE else View.GONE

            if (playing) {
                val metadata = controller?.metadata

                songTitle.text = metadata?.getString(
                    MediaMetadata.METADATA_KEY_TITLE
                ) ?: "Spotify"

                songArtist.text = metadata?.getString(
                    MediaMetadata.METADATA_KEY_ARTIST
                ) ?: "Now playing"

                val art = metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART
                ) ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ART
                )

                albumArt.setImageBitmap(art)
                playButton.text = "Ⅱ"
            }
        } catch (_: Exception) {
            controller = null
            musicPanel.visibility = View.GONE
        }
    }

    private fun updateClock() {
        clockText.text = SimpleDateFormat(
            "EEE, MMM d  •  h:mm a",
            Locale.getDefault()
        ).format(Date())
    }

    private val updater = object : Runnable {
        override fun run() {
            updateMusic()
            updateClock()
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

    inner class CinematicBackground :
        View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            paint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.rgb(8, 13, 26),
                    Color.rgb(15, 29, 49),
                    Color.rgb(7, 13, 25)
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(0f, 0f, w, h, paint)

            paint.shader = RadialGradient(
                w * 0.80f,
                h * 0.25f,
                w * 0.65f,
                intArrayOf(
                    Color.argb(130, 40, 81, 133),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(0f, 0f, w, h, paint)

            paint.shader = RadialGradient(
                w * 0.18f,
                h * 0.90f,
                w * 0.55f,
                intArrayOf(
                    Color.argb(85, 41, 91, 91),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }
    }
}
