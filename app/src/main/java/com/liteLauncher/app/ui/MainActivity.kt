package com.liteLauncher.app.ui

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
    private lateinit var drawerAdapter: AppListAdapter
    private lateinit var homeAdapter: AppListAdapter
    private var showingHiddenApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository(applicationContext)

        setSystemWallpaperAsBackground()
        applyStatusBarTransparency()
        setupHomeAppsGrid()
        setupAppDrawer()
        setupHomeScreenControls()
        loadApps()
        loadHomeApps()
    }

    override fun onResume() {
        super.onResume()
        setSystemWallpaperAsBackground()
        applyStatusBarTransparency()
        if (binding.appDrawerContainer.visibility != View.VISIBLE) {
            loadApps()
        }
        loadHomeApps()
    }

    private fun setupHomeAppsGrid() {
        homeAdapter = AppListAdapter(
            repository = repository,
            badgeSymbol = "×",
            onAppClick = { app -> repository.launchApp(app) },
            onBadgeClick = { app, _ ->
                repository.unpinApp(app.packageName)
                loadHomeApps()
            }
        )
        binding.homeAppsGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = homeAdapter
        }
    }

    private fun loadHomeApps() {
        homeAdapter.submitList(repository.loadPinnedApps())
    }

    private fun applyStatusBarTransparency() {
        if (repository.isStatusBarTransparent()) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = Color.BLACK
        }
    }

    private fun setupHomeScreenControls() {
        binding.openDrawerHandle.setOnClickListener { openAppDrawer() }

        binding.homeRoot.setOnLongClickListener {
            showHomeScreenMenu(it)
            true
        }

        binding.drawerMenuButton.setOnClickListener { showDrawerOverflowMenu(it) }
    }

    private fun showHomeScreenMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Change wallpaper")
        popup.menu.add(
            if (repository.isStatusBarTransparent()) "Disable transparent status bar"
            else "Enable transparent status bar"
        )
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Change wallpaper" -> {
                    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    startActivity(Intent.createChooser(intent, "Set wallpaper"))
                    true
                }
                "Enable transparent status bar" -> {
                    repository.setStatusBarTransparent(true)
                    applyStatusBarTransparency()
                    true
                }
                "Disable transparent status bar" -> {
                    repository.setStatusBarTransparent(false)
                    applyStatusBarTransparency()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDrawerOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(if (showingHiddenApps) "Show all apps" else "Manage hidden apps")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Manage hidden apps" -> {
                    showingHiddenApps = true
                    binding.searchInput.setText("")
                    lifecycleScope.launch {
                        drawerAdapter.submitList(repository.loadHiddenApps())
                    }
                    true
                }
                "Show all apps" -> {
                    showingHiddenApps = false
                    lifecycleScope.launch {
                        drawerAdapter.submitList(repository.loadInstalledApps())
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setSystemWallpaperAsBackground() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            binding.homeRoot.background = wallpaperManager.drawable
        } catch (e: SecurityException) {
        }
    }

    private fun setupAppDrawer() {
        drawerAdapter = AppListAdapter(
            repository = repository,
            badgeSymbol = "⋮",
            onAppClick = { app ->
                repository.launchApp(app)
                closeAppDrawer()
            },
            onBadgeClick = { app, view -> showAppOptionsMenu(app, view) }
        )
        binding.appDrawerRecycler.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = drawerAdapter
            setHasFixedSize(true)
        }

        binding.searchInput.addTextChangedListener { text ->
            drawerAdapter.filter(text?.toString().orEmpty())
        }
    }

    private fun openAppDrawer() {
        binding.appDrawerContainer.visibility = View.VISIBLE
    }

    private fun closeAppDrawer() {
        binding.appDrawerContainer.visibility = View.GONE
        binding.searchInput.setText("")
        if (showingHiddenApps) {
            showingHiddenApps = false
            loadApps()
        }
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
            drawerAdapter.submitList(apps)
        }
    }

    private fun showAppOptionsMenu(app: AppInfo, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("App info")
        popup.menu.add(if (showingHiddenApps) "Unhide" else "Hide")
        popup.menu.add(if (repository.isPinned(app.packageName)) "Remove from home screen" else "Add to home screen")
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
                "Hide" -> {
                    repository.hideApp(app.packageName)
                    lifecycleScope.launch { drawerAdapter.submitList(repository.loadInstalledApps()) }
                    true
                }
                "Unhide" -> {
                    repository.unhideApp(app.packageName)
                    lifecycleScope.launch { drawerAdapter.submitList(repository.loadHiddenApps()) }
                    true
                }
                "Add to home screen" -> {
                    repository.pinApp(app)
                    loadHomeApps()
                    true
                }
                "Remove from home screen" -> {
                    repository.unpinApp(app.packageName)
                    loadHomeApps()
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
