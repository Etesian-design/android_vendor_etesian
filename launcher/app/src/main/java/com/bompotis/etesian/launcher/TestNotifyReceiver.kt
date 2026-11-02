package com.bompotis.etesian.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * For the on-device tests only (not exported; `am broadcast -n` from a root shell): posts silent
 * sample notifications — kind "message" (heads-up, Reply + Mark as read), "progress" (an ongoing
 * live update), "mails" (count=N email notifications), "cancel" removes them; "widget" places a launcher widget; "demo" (on
 * true / false) switches demo mode (Demo.kt). The channel has no sound or vibration.
 */
class TestNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Etesian tests", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)
        })
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        when (intent.getStringExtra("kind") ?: "message") {
            "message" -> {
                val reply = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, TestNotifyReceiver::class.java)
                    .putExtra("kind", "replied"), PendingIntent.FLAG_MUTABLE)
                val read = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, TestNotifyReceiver::class.java)
                    .putExtra("kind", "read"), PendingIntent.FLAG_IMMUTABLE)
                nm.notify(ID_MESSAGE, Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_set_events)
                    .setContentTitle(intent.getStringExtra("title") ?: "Anna Virtanen")
                    .setContentText(intent.getStringExtra("text") ?: "Are we still on for lunch?")
                    .setContentIntent(open).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .addAction(Notification.Action.Builder(null, "Reply", reply)
                        .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()).build())
                    .addAction(Notification.Action.Builder(null, "Mark as read", read).build())
                    .build())
            }
            // A notification about a person (Person uri, e.g. tel:+30…), posted with its own id.
            "person" -> nm.notify(ID_PERSON + intent.getIntExtra("id", 0), Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_set_events)
                .setContentTitle(intent.getStringExtra("title") ?: "Message")
                .setContentText(intent.getStringExtra("text") ?: "")
                .setContentIntent(open).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .apply { intent.getStringExtra("uri")?.let { addPerson(android.app.Person.Builder().setUri(it).setName(intent.getStringExtra("title")).build()) } }
                .build())
            // Tests: a real screen capture by this app (no consent dialog: MANAGE_MEDIA_PROJECTION).
            "project" -> runCatching {
                val sm = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "media_projection")
                val svc = Class.forName("android.media.projection.IMediaProjectionManager\$Stub")
                    .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, sm)!!
                val impl = svc.javaClass.getMethod("createProjection", Int::class.javaPrimitiveType, String::class.java,
                    Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(svc, android.os.Process.myUid(), ctx.packageName, 0, false, 0)!!
                val binder = impl.javaClass.getMethod("asBinder").invoke(impl) as android.os.IBinder
                val data = Intent().putExtra("android.media.projection.extra.EXTRA_MEDIA_PROJECTION", binder)
                projection = ctx.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                    .getMediaProjection(android.app.Activity.RESULT_OK, data)
            }.onFailure { android.util.Log.w("EtesianTest", "project", it) }
            // Tests: lock the private space (quiet mode on), as closing its folder does.
            "lock_private" -> PrivateSpace.profile(ctx)?.let { PrivateSpace.lock(ctx, it) }
            // Tests: a real two-finger gesture through the input pipeline (INJECT_EVENTS).
            // "path" = frames "ax,ay,bx,by;…" (bx<0: one finger), interpolated "steps" times each, "ms" per step.
            "gesture" -> {
                val pending = goAsync()
                val frames = intent.getStringExtra("path").orEmpty().split(';').filter { it.isNotBlank() }
                    .mapNotNull { f -> f.split(',').map { it.trim().toFloatOrNull() ?: return@mapNotNull null } }
                val steps = intent.getIntExtra("steps", 12); val ms = intent.getIntExtra("ms", 16).toLong()
                val hold = intent.getIntExtra("hold", 0).toLong()
                Thread { runCatching { Gesture.play(frames, steps, ms, hold) }.onFailure { android.util.Log.w("EtesianTest", "gesture", it) }; pending.finish() }.start()
            }
            "unproject" -> { runCatching { projection?.stop() }; projection = null }
            "progress" -> nm.notify(ID_PROGRESS, Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_set_events)
                .setContentTitle("Downloading map")
                .setContentText("Lapland · 42%")
                .setProgress(100, 42, false).setOngoing(true).setOnlyAlertOnce(true)
                // A real live update (Android 16 promoted ongoing): the status bar chip must not appear.
                .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .setContentIntent(open).build())
            "replied" -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)
                android.provider.Settings.Global.putString(ctx.contentResolver, "etesian_test_reply", text?.toString())
                nm.cancel(ID_MESSAGE)
            }
            "read" -> {
                android.provider.Settings.Global.putString(ctx.contentResolver, "etesian_test_reply", "read")
                nm.cancel(ID_MESSAGE)
            }
            // Lock-screen stack tests: N separate silent messages ("Stack 1" newest last).
            "stack" -> for (i in 1..intent.getIntExtra("count", 6).coerceIn(1, 20)) {
                nm.notify(ID_STACK + i, Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_set_events)
                    .setContentTitle("Stack $i").setContentText("Test card $i of the lock-screen stack")
                    .setGroup("stack$i").setOnlyAlertOnce(true)
                    .setWhen(System.currentTimeMillis() + i * 1000L).setContentIntent(open).build())
            }
            // Clipboard tests: copy a line of text.
            "copy" -> ctx.getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(android.content.ClipData.newPlainText("test", intent.getStringExtra("text") ?: "Etesian clipboard test"))
            // Places the launcher widget [cls] (e.g. BelleGearClock) on the Widgets view, no picker.
            // Demo mode (Demo.kt): made-up data for screenshots; restart the launcher after switching it.
            "demo" -> Demo.set(ctx, intent.getBooleanExtra("on", true))
            "widget" -> intent.getStringExtra("cls")?.let { WidgetsPage.current?.addForTest(it) }
            // "count" sample mails (CATEGORY_EMAIL, as the mail app posts them): more than the Belle Mail widget shows at once
            "mails" -> for (i in 1..intent.getIntExtra("count", 12)) nm.notify(ID_MAIL + i, Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_set_events)
                .setContentTitle("Sender $i").setContentText("Test mail number $i").setSubText("test@example.org")
                .setWhen(System.currentTimeMillis() - i * 60_000L).setShowWhen(true)
                .setContentIntent(open).setCategory(Notification.CATEGORY_EMAIL).setSilent(true).build())
            "cancel" -> { for (i in 1..30) nm.cancel(ID_MAIL + i); nm.cancel(ID_MESSAGE); nm.cancel(ID_PROGRESS); for (i in 1..20) nm.cancel(ID_STACK + i); for (i in 0..9) nm.cancel(ID_PERSON + i) }
        }
    }

    companion object {
        const val CHANNEL = "etesian_tests"
        const val KEY_REPLY = "reply"
        const val ID_MESSAGE = 7101
        const val ID_PROGRESS = 7102
        const val ID_STACK = 7200
        const val ID_PERSON = 7300
        const val ID_MAIL = 7400
        private var projection: android.media.projection.MediaProjection? = null
    }
}
