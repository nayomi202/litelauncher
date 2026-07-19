package com.liteLauncher.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
)

/**
 * Loads installed apps and caches their icons in memory (LruCache) so scrolling
 * the app drawer never re-reads icons from disk/PackageManager after the first load.
 * Icon cache size is capped relative to available memory -> safe on low-RAM devices.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private val iconCache: LruCache<String, Drawable> by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = maxMemoryKb / 16 // use ~1/16th of available heap for icons
        object : LruCache<String, Drawable>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Drawable): Int {
                // rough estimate: bitmap-ish size in KB
                return (value.intrinsicWidth * value.intrinsicHeight * 4) / 1024 + 1
            }
        }
    }

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps = packageManager.queryIntentActivities(mainIntent, 0)

        resolvedApps
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name
                )
            }
            .distinctBy { it.packageName + it.activityName }
            .sortedBy { it.label.lowercase() }
    }

    fun getIcon(app: AppInfo): Drawable {
        iconCache.get(app.packageName)?.let { return it }

        val icon = try {
            packageManager.getActivityIcon(
                android.content.ComponentName(app.packageName, app.activityName)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            packageManager.defaultActivityIcon
        }

        iconCache.put(app.packageName, icon)
        return icon
    }

    fun launchApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = android.content.ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getAppInfoForSettings(app: AppInfo): ApplicationInfo? =
        try {
            packageManager.getApplicationInfo(app.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
