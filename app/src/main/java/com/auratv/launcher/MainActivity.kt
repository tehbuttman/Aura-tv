
package com.auratv.launcher

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var clock: TextView
    private var themeIndex = 0

    private val themes = listOf(
        intArrayOf(0xFF101B36.toInt(), 0xFF080C18.toInt()),
        intArrayOf(0xFF34204C.toInt(), 0xFF100B1F.toInt()),
        intArrayOf(0xFF123B39.toInt(), 0xFF071C1B.toInt())
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeIndex = getPreferences(MODE_PRIVATE)
            .getInt("theme", 0)
        createHome()
    }

    private fun createHome() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 28, 44, 32)
        }
        applyTheme()
        scroll.addView(root)
        setContentView(scroll)

        val heading = TextView(this).apply {
            text = "AURA TV"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(heading)

        clock = TextView(this).apply {
            textSize = 19f
            setTextColor(0xFFCDD8EC.toInt())
        }
        root.addView(clock)
        updateClock()

        val welcome = TextView(this).apply {
            text = "Your space. Your entertainment."
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(0, 30, 0, 20)
        }
        root.addView(welcome)

        val weather = TextView(this).apply {
            text = "☀  Weather\nSet up your local forecast"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            background = panelBackground()
        }
        root.addView(weather)

        val settings = Button(this).apply {
            text = "Change Theme"
            setOnClickListener {
                themeIndex = (themeIndex + 1) % themes.size
                getPreferences(MODE_PRIVATE).edit()
                    .putInt("theme", themeIndex).apply()
                applyTheme()
            }
        }
        root.addView(settings)

        val title = TextView(this).apply {
            text = "Your Apps"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 12)
        }
        root.addView(title)

        val grid = GridLayout(this).apply {
            columnCount = 4
        }
        root.addView(grid)

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter {
                it.activityInfo.packageName != packageName
            }
            .sortedBy {
                it.loadLabel(packageManager).toString()
            }

        if (apps.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No TV apps found"
                setTextColor(Color.WHITE)
            }
            root.addView(empty)
        }

        apps.forEach { app ->
            val button = Button(this).apply {
                text = app.loadLabel(packageManager).toString()
                isAllCaps = false
                textSize = 14f
                isFocusable = true
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 220
                    height = 110
                    setMargins(6, 6, 6, 6)
                }
                setOnClickListener {
                    val launch = Intent().apply {
                        setClassName(
                            app.activityInfo.packageName,
                            app.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(launch)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Unable to open app",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            grid.addView(button)
        }
    }

    private fun updateClock() {
        val formatter = SimpleDateFormat(
            "EEEE, MMMM d  •  h:mm a",
            Locale.getDefault()
        )
        clock.text = formatter.format(Date())
        clock.postDelayed({ updateClock() }, 30000)
    }

    private fun applyTheme() {
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            themes[themeIndex]
        )
    }

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(0xFF26344F.toInt())
            cornerRadius = 24f
        }
    }
}
