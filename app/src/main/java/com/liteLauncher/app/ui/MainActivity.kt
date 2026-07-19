package com.liteLauncher.app.ui

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.liteLauncher.app.data.AppInfo
import com.liteLauncher.app.data.AppRepository
import com.liteLauncher.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository(applicationContext)

        setSystemWallpaperAsBackground()
        setupAppDrawer()
        setupSwipeGesture()
        loadApps()
    }

    private fun setSystemWallpaperAsBackground() {
        // Use the device's actual wallpaper instead of shipping our own image asset -> saves APK size
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            binding.homeRoot.background = wallpaperManager.drawable
        } catch (e: SecurityException) {
            // no wallpaper permission on some OEM builds; fall back to theme background color
        }
    }

    private fun setupAppDrawer() {
        adapter = AppListAdapter(
            repository = repository,
            onAppClick = { app -> repository.launchApp(app) },
            onAppLongClick = { app, view -> showAppOptionsMenu(app, view) }
        )
        binding.appDrawerRecycler.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true) // perf: item sizes don't change -> skip extra layout passes
        }

        binding.searchInput.addTextChangedListener { text ->
            adapter.filter(text?.toString().orEmpty())
        }
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val deltaY = e1.y - e2.y
                if (deltaY > 150 && velocityY < -300) {
                    openAppDrawer()
                    return true
                }
                return false
            }
        })
        binding.homeRoot.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun openAppDrawer() {
        binding.appDrawerContainer.visibility = View.VISIBLE
    }

    private fun closeAppDrawer() {
        binding.appDrawerContainer.visibility = View.GONE
        binding.searchInput.setText("")
    }

    override fun onBackPressed() {
        if (binding.appDrawerContainer.visibility == View.VISIBLE) {
            closeAppDrawer()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = repository.loadInstalledApps()
            adapter.submitList(apps)
        }
    }

    private fun showAppOptionsMenu(app: AppInfo, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("App info")
        popup.menu.add("Uninstall")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "App info" -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                "Uninstall" -> {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
