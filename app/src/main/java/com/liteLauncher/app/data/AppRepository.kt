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

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("lite_launcher_prefs", Context.MODE_PRIVATE)

    private fun getHiddenSet(): MutableSet<String> =
        HashSet(prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet())

    fun hideApp(packageName: String) {
        val hidden = getHiddenSet()
        hidden.add(packageName)
        prefs.edit().putStringSet("hidden_packages", hidden).apply()
    }

    fun unhideApp(packageName: String) {
        val hidden = getHiddenSet()
        hidden.remove(packageName)
        prefs.edit().putStringSet("hidden_packages", hidden).apply()
    }

    fun isHidden(packageName: String): Boolean = getHiddenSet().contains(packageName)

    private val iconCache: LruCache<String, Drawable> by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = maxMemoryKb / 16
        object : LruCache<String, Drawable>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Drawable): Int {
                return (value.intrinsicWidth * value.intrinsicHeight * 4) / 1024 + 1
            }
        }
    }

    private suspend fun loadAllApps(): List<AppInfo> = withContext(Dispatchers.Default) {
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

    suspend fun loadInstalledApps(): List<AppInfo> {
        val hidden = getHiddenSet()
        return loadAllApps().filterNot { hidden.contains(it.packageName) }
    }

    suspend fun loadHiddenApps(): List<AppInfo> {
        val hidden = getHiddenSet()
        return loadAllApps().filter { hidden.contains(it.packageName) }
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
