
package com.auratv.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Path
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
import android.provider.Settings
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun background(color: Int, radius: Int): GradientDrawable {
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
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun makeButton(
        button: TextView,
        normalColor: Int,
        focusColor: Int,
        radius: Int = 18
    ) {
        button.isFocusable = true
        button.isClickable = true
        button.background = background(normalColor, radius)

        button.setOnFocusChangeListener { view, focused ->
            view.background = background(
                if (focused) focusColor else normalColor,
                radius
            )
            view.animate()
                .scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f)
                .setDuration(150)
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createHome()
    }

    private fun createHome() {
        val frame = FrameLayout(this)

        frame.addView(
            LandscapeView(),
            FrameLayout.LayoutParams(-1, -1)
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(45), dp(25), dp(45), dp(35))
        }

        scroll.addView(root)
        frame.addView(scroll)
        setContentView(frame)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            text("AURA", 28f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val spotifyButton = text("♫  SPOTIFY", 14f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        makeButton(
            spotifyButton,
            Color.rgb(28, 49, 51),
            Color.rgb(32, 115, 75)
        )

        spotifyButton.setOnClickListener {
            openSpotify()
        }

        header.addView(spotifyButton)

        val clock = SimpleDateFormat(
            "EEE, MMM d  •  h:mm a",
            Locale.getDefault()
        ).format(Date())

        header.addView(text(clock, 14f).apply {
            setPadding(dp(20), 0, 0, 0)
        })

        root.addView(header)

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(55))
        )

        root.addView(text("Your space.", 36f, true))
        root.addView(text("Your entertainment.", 36f, true))

        root.addView(text(
            "Everything you love, beautifully organized.",
            16f
        ).apply {
            alpha = 0.75f
            setPadding(0, dp(10), 0, dp(30))
        })

        createMusicPanel(root)

        val permissionButton = text(
            "Enable Spotify music widget",
            13f,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }

        makeButton(
            permissionButton,
            Color.rgb(29, 49, 60),
            Color.rgb(48, 91, 110)
        )

        permissionButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        root.addView(permissionButton)

        root.addView(text("YOUR APPS", 15f, true).apply {
            setPadding(0, dp(30), 0, dp(15))
        })

        createAppDock(root)

        root.addView(text("AURA TV  •  VERSION 0.3", 11f).apply {
            alpha = 0.5f
            setPadding(0, dp(25), 0, 0)
        })
    }

    private fun createMusicPanel(root: LinearLayout) {
        musicPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = background(Color.rgb(24, 43, 51), 22)
        }

        musicPanel.addView(
            text("NOW PLAYING  •  SPOTIFY", 12f, true)
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }

        albumArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        row.addView(
            albumArt,
            LinearLayout.LayoutParams(dp(64), dp(64))
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(10), 0)
        }

        songTitle = text("Spotify", 19f, true).apply {
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

        row.addView(musicControl("Ⅱ") {
            if (controller?.playbackState?.state ==
                PlaybackState.STATE_PLAYING) {
                controller?.transportControls?.pause()
            } else {
                controller?.transportControls?.play()
            }
        })

        row.addView(musicControl("❯❯") {
            controller?.transportControls?.skipToNext()
        })

        musicPanel.addView(row)
        root.addView(musicPanel)
    }

    private fun musicControl(
        symbol: String,
        action: () -> Unit
    ): TextView {
        val button = text(symbol, 19f, true).apply {
            gravity = Gravity.CENTER
        }

        makeButton(
            button,
            Color.rgb(38, 62, 72),
            Color.rgb(48, 119, 84),
            12
        )

        button.setOnClickListener { action() }

        button.layoutParams =
            LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(6)
            }

        return button
    }

    private fun createAppDock(root: LinearLayout) {
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(12))
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
            it.loadLabel(packageManager).toString()
        }

        for (app in apps) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(15), dp(12), dp(12))
                isFocusable = true
                isClickable = true
                background = background(Color.rgb(27, 43, 57), 22)
            }

            tile.setOnFocusChangeListener { view, focused ->
                view.background = background(
                    if (focused)
                        Color.rgb(61, 98, 128)
                    else
                        Color.rgb(27, 43, 57),
                    22
                )

                view.animate()
                    .scaleX(if (focused) 1.06f else 1f)
                    .scaleY(if (focused) 1.06f else 1f)
                    .setDuration(150)
                    .start()
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                icon,
                LinearLayout.LayoutParams(dp(65), dp(65))
            )

            tile.addView(
                text(
                    app.loadLabel(packageManager).toString(),
                    12f,
                    true
                ).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, dp(12), 0, 0)
                }
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
                    dp(145),
                    dp(140)
                ).apply {
                    rightMargin = dp(12)
                }
            )
        }

        horizontal.addView(row)
        root.addView(horizontal)
    }

    private fun openSpotify() {
        val packages = listOf(
            "com.spotify.tv.android",
            "com.spotify.music"
        )

        for (pkg in packages) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch)
                return
            }
        }

        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val spotify = packageManager.queryIntentActivities(
            query,
            0
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

            val component = ComponentName(
                this,
                AuraNotificationListener::class.java
            )

            val sessions = manager.getActiveSessions(component)

            controller = sessions.firstOrNull {
                it.packageName.contains(
                    "spotify",
                    ignoreCase = true
                )
            }

            val playing = controller?.playbackState?.state ==
                PlaybackState.STATE_PLAYING

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

                val bitmap = metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART
                ) ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ART
                )

                albumArt.setImageBitmap(bitmap)
            }
        } catch (_: Exception) {
            controller = null
            musicPanel.visibility = View.GONE
        }
    }

    private val updater = object : Runnable {
        override fun run() {
            updateMusic()
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

    inner class LandscapeView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            paint.shader = LinearGradient(
                0f,
                0f,
                w,
                h,
                Color.rgb(8, 17, 33),
                Color.rgb(22, 51, 72),
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            val mountain = Path().apply {
                moveTo(0f, h * 0.92f)
                lineTo(w * 0.18f, h * 0.72f)
                lineTo(w * 0.34f, h * 0.85f)
                lineTo(w * 0.52f, h * 0.63f)
                lineTo(w * 0.72f, h * 0.82f)
                lineTo(w * 0.87f, h * 0.69f)
                lineTo(w, h * 0.83f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            paint.color = Color.rgb(13, 32, 46)
            canvas.drawPath(mountain, paint)
        }
    }
}
