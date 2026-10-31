package com.bompotis.etesian.launcher

import android.content.Context
import android.media.MediaRouter
import android.media.projection.MediaProjectionManager

/**
 * Ongoing interactive statuses besides the screen recorder (approved "Recording" state): casting
 * the screen to another device and an app capturing the screen. MANAGE_MEDIA_PROJECTION is a
 * plain signature permission (the launcher is platform-signed: no allowlist entry needed).
 */
object Ongoing {
    class Status(val kind: String, val color: Int, val glyph: String, val title: String, val detail: String, val pkg: String?)

    fun current(ctx: Context): List<Status> {
        val out = mutableListOf<Status>()
        val cast = castRoute(ctx)
        if (cast != null) out += Status("cast", 0xFF0067BC.toInt(), "cast", "Casting the screen", cast.getName(ctx).toString(), null)
        val pkg = projectionPackage(ctx)
        // SystemUI's own recorder has its own row.
        if (pkg != null && !(pkg == "com.android.systemui" && (Recorder.on(ctx) || cast != null))) {
            out += Status("share", 0xFFC7302A.toInt(), "record", "Sharing the screen", label(ctx, pkg), pkg)
        }
        return out
    }

    fun stop(ctx: Context, s: Status) {
        when (s.kind) {
            "cast" -> runCatching {
                val r = ctx.getSystemService(MediaRouter::class.java)
                r.selectRoute(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY, r.defaultRoute)
            }
            "share" -> runCatching {
                val m = ctx.getSystemService(MediaProjectionManager::class.java)
                m.javaClass.getMethod("stopActiveProjection", Int::class.javaPrimitiveType).invoke(m, 0)
            }.recoverCatching {
                val m = ctx.getSystemService(MediaProjectionManager::class.java)
                m.javaClass.getMethod("stopActiveProjection").invoke(m)
            }
        }
    }

    private fun castRoute(ctx: Context): MediaRouter.RouteInfo? = runCatching {
        val r = ctx.getSystemService(MediaRouter::class.java)
        r.getSelectedRoute(MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY).takeIf {
            it != r.defaultRoute && (it.supportedTypes and MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY) != 0
        }
    }.getOrNull()

    private fun projectionPackage(ctx: Context): String? = runCatching {
        val m = ctx.getSystemService(MediaProjectionManager::class.java)
        val info = m.javaClass.getMethod("getActiveProjectionInfo").invoke(m) ?: return null
        info.javaClass.getMethod("getPackageName").invoke(info) as String
    }.getOrNull()

    private fun label(ctx: Context, pkg: String) = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
