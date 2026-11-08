
package com.auratv.launcher

import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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
    private var spotifyController: MediaController? = null
    private lateinit var musicArea: LinearLayout
    private lateinit var musicTitle: TextView
    private lateinit var musicArtist: TextView
    private lateinit var musicArtwork: ImageView
    private lateinit var musicButton: TextView
    private var lastPlaying = false

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 22): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun focusable(
        view: View,
        normal: Int,
        selected: Int,
        radius: Int = 20
    ) {
        view.isFocusable = true
        view.isClickable = true
        view.background = shape(normal, radius)
        view.setOnFocusChangeListener { v, focused ->
            v.background = shape(
                if (focused) selected else normal,
                radius
            )
            v.animate()
                .scaleX(if (focused) 1.045f else 1f)
                .scaleY(if (focused) 1.045f else 1f)
                .setDuration(140)
                .start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this)
        frame.addView(
            ScenicBackground(),
            FrameLayout.LayoutParams(-1, -1)
        )

        val scroll = ScrollView(this).apply {
            fillViewport = true
            clipToPadding = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(30))
        }

        scroll.addView(root)
        frame.addView(scroll)
        setContentView(frame)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        header.addView(
            label("AURA", 27f, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val spotifyShortcut = label("♫  SPOTIFY", 13f, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { openSpotify() }
        }
        focusable(
            spotifyShortcut,
            Color.rgb(27, 43, 53),
            Color.rgb(36, 105, 75)
        )
        header.addView(spotifyShortcut)

        val time = SimpleDateFormat(
            "EEE, MMM d  •  h:mm a",
            Locale.getDefault()
        ).format(Date())

        header.addView(label(time, 14f).apply {
            setPadding(dp(20), 0, 0, 0)
        })

        root.addView(header)

        val spacer = View(this)
        root.addView(
            spacer,
            LinearLayout.LayoutParams(1, dp(54))
        )

        root.addView(label("Your space.", 37f, true))
        root.addView(label("Your entertainment.", 37f, true))

        root.addView(label(
            "Everything you love, beautifully organized.",
            16f
        ).apply {
            alpha = 0.78f
            setPadding(0, dp(8), 0, 0)
        })

        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(30))
        )

        musicArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = shape(Color.rgb(23, 39, 49), 24)
        }

        musicArea.addView(label("NOW PLAYING  •  SPOTIFY", 12f, true))

        val musicRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        musicArtwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.rgb(43, 65, 75), 12)
        }
        musicRow.addView(
            musicArtwork,
            LinearLayout.LayoutParams(dp(66), dp(66))
        )

        val songInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }

        musicTitle = label("Spotify", 20f, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        musicArtist = label("", 14f).apply {
            alpha = 0.7f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        songInfo.addView(musicTitle)
        songInfo.addView(musicArtist)
        musicRow.addView(
            songInfo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val previous = control("❮❮") {
            spotifyController?.transportControls?.skipToPrevious()
        }
        musicRow.addView(previous)

        musicButton = control("Ⅱ") {
            val controls = spotifyController?.transportControls
            if (spotifyController?.playbackState?.state ==
                PlaybackState.STATE_PLAYING) {
                controls?.pause()
            } else {
                controls?.play()
            }
        }
        musicRow.addView(musicButton)

        val next = control("❯❯") {
            spotifyController?.transportControls?.skipToNext()
        }
        musicRow.addView(next)

        musicArea.addView(musicRow)
        root.addView(musicArea)

        val permissionButton = label(
            "Enable music widget access",
            13f,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }
        focusable(
            permissionButton,
            Color.rgb(29, 48, 59),
            Color.rgb(51, 91, 113)
        )

        val permissionRow = LinearLayout(this)
        permissionRow.addView(permissionButton)
        root.addView(permissionRow)

        root.addView(
            label("YOUR APPS", 15f, true).apply {
                setPadding(0, dp(30), 0, dp(15))
            }
        )

        val appScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }

        val appRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(5), dp(9), dp(5), dp(12))
        }

        val appIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(
            appIntent,
            0
        ).filter {
            it.activityInfo.packageName != packageName
        }.distinctBy {
            it.activityInfo.packageName
        }.sortedBy {
            it.loadLabel(packageManager).toString().lowercase()
        }

        for (app in apps) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(16), dp(12), dp(12))
            }

            focusable(
                tile,
                Color.rgb(27, 40, 55),
                Color.rgb(61, 91, 124),
                24
            )

            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                icon,
                LinearLayout.LayoutParams(dp(66), dp(66))
            )

            tile.addView(label(
                app.loadLabel(packageManager).toString(),
                12f,
                true
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(13), 0, 0)
            })

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

            appRow.addView(
                tile,
                LinearLayout.LayoutParams(
                    dp(148),
                    dp(143)
                ).apply {
                    rightMargin = dp(12)
                }
            )
        }

        appScroller.addView(appRow)
        root.addView(appScroller)

        root.addView(label(
            "AURA TV  •  0.3",
            11f
        ).apply {
            alpha = 0.55f
            setPadding(0, dp(22), 0, 0)
        })
    }

    private fun control(
        symbol: String,
        action: () -> Unit
    ): TextView {
        val button = label(symbol, 19f, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }
        focusable(
            button,
            Color.rgb(36, 57, 68),
            Color.rgb(53, 116, 90),
            14
        )
        button.layoutParams =
            LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(6)
            }
        return button
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
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val spotify = packageManager.queryIntentActivities(
            intent, 0
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

            spotifyController = manager.getActiveSessions(
                listener
            ).firstOrNull {
                it.packageName.contains(
                    "spotify",
                    ignoreCase = true
                )
            }

            val controller = spotifyController
            val playing = controller?.playbackState?.state ==
                PlaybackState.STATE_PLAYING

            if (playing != lastPlaying) {
                lastPlaying = playing
                musicArea.visibility =
                    if (playing) View.VISIBLE else View.GONE
            }

            if (playing && controller != null) {
                val metadata = controller.metadata

                musicTitle.text = metadata?.getString(
                    MediaMetadata.METADATA_KEY_TITLE
                ) ?: "Spotify"

                musicArtist.text = metadata?.getString(
                    MediaMetadata.METADATA_KEY_ARTIST
                ) ?: "Now playing"

                val art = metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART
                ) ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ART
                )

                musicArtwork.setImageBitmap(art)
                musicButton.text = "Ⅱ"
            }
        } catch (_: Exception) {
            spotifyController = null
            lastPlaying = false
            musicArea.visibility = View.GONE
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            updateSpotify()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    inner class ScenicBackground : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            paint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(
                    Color.rgb(8, 18, 33),
                    Color.rgb(18, 46, 65),
                    Color.rgb(9, 19, 32)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            paint.shader = android.graphics.RadialGradient(
                w * 0.76f,
                h * 0.52f,
                w * 0.52f,
                intArrayOf(
                    Color.argb(115, 64, 126, 153),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            val distant = Path().apply {
                moveTo(0f, h * 0.89f)
                lineTo(w * 0.16f, h * 0.70f)
                lineTo(w * 0.30f, h * 0.82f)
                lineTo(w * 0.49f, h * 0.60f)
                lineTo(w * 0.66f, h * 0.78f)
                lineTo(w * 0.84f, h * 0.62f)
                lineTo(w, h * 0.77f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            paint.color = Color.rgb(16, 36, 52)
            canvas.drawPath(distant, paint)

            val foreground = Path().apply {
                moveTo(0f, h * 0.95f)
                lineTo(w * 0.24f, h * 0.82f)
                lineTo(w * 0.40f, h * 0.92f)
                lineTo(w * 0.64f, h * 0.78f)
                lineTo(w * 0.82f, h * 0.91f)
                lineTo(w, h * 0.84f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            paint.color = Color.rgb(8, 22, 34)
            canvas.drawPath(foreground, paint)
        }
    }
}
