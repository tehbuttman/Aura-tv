
package com.auratv.launcher

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.GridLayout
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private var themeIndex = 0
    private val backgrounds = intArrayOf(
        Color.rgb(8, 15, 33),
        Color.rgb(21, 13, 35),
        Color.rgb(7, 28, 28)
    )

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
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

    private fun buildHome() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 42, 64, 48)
            setBackgroundColor(backgrounds[themeIndex])
        }

        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = text("AURA", 30f, true)
        header.addView(
            logo,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val date = SimpleDateFormat(
            "EEE, MMM d  •  h:mm a",
            Locale.getDefault()
        ).format(Date())

        header.addView(text(date, 17f))
        root.addView(header)

        val subtitle = text(
            "Your entertainment. Beautifully simple.",
            17f
        ).apply {
            alpha = 0.7f
            setPadding(0, 10, 0, 30)
        }
        root.addView(subtitle)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(34, 82, 156),
                    Color.rgb(85, 45, 128)
                )
            ).apply {
                cornerRadius = 28f
            }
        }

        hero.addView(text("WELCOME TO AURA TV", 13f, true))

        hero.addView(text(
            "Everything you love.\nOne beautiful place.",
            28f,
            true
        ).apply {
            setPadding(0, 12, 0, 12)
        })

        hero.addView(text(
            "Your personalized entertainment space",
            15f
        ).apply {
            alpha = 0.8f
        })

        root.addView(hero)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 20)
        }

        val weather = text("☀  Weather coming soon", 15f).apply {
            setPadding(16, 14, 16, 14)
            background = rounded(Color.rgb(35, 44, 65), 16f)
        }

        toolbar.addView(
            weather,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val themeButton = text("CHANGE THEME", 14f, true).apply {
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 14)
            background = rounded(Color.rgb(48, 59, 82), 16f)
            isFocusable = true
            isClickable = true
            setOnClickListener {
                themeIndex = (themeIndex + 1) % backgrounds.size
                buildHome()
            }
        }

        val themeParams = LinearLayout.LayoutParams(-2, -2)
        themeParams.leftMargin = 16
        toolbar.addView(themeButton, themeParams)
        root.addView(toolbar)

        root.addView(text("Your Apps", 23f, true).apply {
            setPadding(0, 8, 0, 20)
        })

        val grid = GridLayout(this).apply {
            columnCount = 5
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        ).filter {
            it.activityInfo.packageName != packageName
        }.distinctBy {
            it.activityInfo.packageName
        }.sortedBy {
            it.loadLabel(packageManager).toString().lowercase()
        }

        val tileWidth = resources.displayMetrics.widthPixels / 6
        val tileHeight = (tileWidth * 0.78f).toInt()

        for (app in apps) {
            val appName = app.loadLabel(packageManager).toString()

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(12, 12, 12, 12)
                background = rounded(Color.rgb(31, 40, 61), 22f)
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { view, focused ->
                    view.scaleX = if (focused) 1.07f else 1f
                    view.scaleY = if (focused) 1.07f else 1f
                    view.elevation = if (focused) 16f else 0f
                    background = rounded(
                        if (focused)
                            Color.rgb(65, 91, 144)
                        else
                            Color.rgb(31, 40, 61),
                        22f
                    )
                }

                setOnClickListener {
                    try {
                        val launch = Intent().apply {
                            component = android.content.ComponentName(
                                app.activityInfo.packageName,
                                app.activityInfo.name
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(launch)
                    } catch (e: Exception) {
                        try {
                            packageManager.getLaunchIntentForPackage(
                                app.activityInfo.packageName
                            )?.let { startActivity(it) }
                        } catch (_: Exception) {}
                    }
                }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            tile.addView(
                icon,
                LinearLayout.LayoutParams(60, 60)
            )

            tile.addView(text(appName, 13f, true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, 12, 0, 0)
            })

            val params = GridLayout.LayoutParams().apply {
                width = tileWidth
                height = tileHeight
                setMargins(7, 7, 7, 7)
            }

            grid.addView(tile, params)
        }

        root.addView(grid)

        root.addView(text(
            "AURA TV  •  Version 0.2",
            12f
        ).apply {
            alpha = 0.45f
            setPadding(0, 32, 0, 0)
        })
    }
}
