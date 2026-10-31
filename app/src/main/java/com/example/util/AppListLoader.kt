package com.example.util

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object AppListLoader {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    fun getCachedIcon(packageName: String): ImageBitmap? = iconCache[packageName]

    fun putCachedIcon(packageName: String, imageBitmap: ImageBitmap) {
        iconCache[packageName] = imageBitmap
    }

    fun clearCache() {
        iconCache.clear()
    }

    suspend fun getInstalledLaunchableApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val selfPkg = context.packageName

        resolveInfos
            .filter { it.activityInfo.packageName != selfPkg }
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                // Pre-rasterize icon to ImageBitmap on Dispatchers.IO to prevent Main-thread rasterization
                if (icon != null && !iconCache.containsKey(pkgName)) {
                    try {
                        val bmp = ImageUtil.drawableToBitmap(icon, 96)
                        iconCache[pkgName] = bmp.asImageBitmap()
                    } catch (e: Exception) {
                        // Suppress icon generation errors
                    }
                }

                AppInfo(
                    packageName = pkgName,
                    appName = label,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }
}
