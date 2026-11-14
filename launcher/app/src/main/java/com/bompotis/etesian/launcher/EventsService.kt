package com.bompotis.etesian.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class EventItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
    val smallIcon: android.graphics.drawable.Icon?,
    /** Android notification channel, so the user can sort e.g. a chat app's messages apart from its promotions. */
    val channelId: String = "",
    val channelName: String = "",
    /** Notification.CATEGORY_*, set by the app. */
    val category: String? = null,
    /** NotificationManager.IMPORTANCE_*, as the user/system ranks this channel. */
    val importance: Int = 3,
    /** The notification's own actions (Reply, Mark as read...), for the Android extras sheet. */
    val actions: List<Notification.Action> = emptyList(),
    /** Live updates (ongoing with progress): max 0 = none; [progress] of [progressMax]. */
    val progressMax: Int = 0,
    val progress: Int = 0,
    val indeterminate: Boolean = false,
    val color: Int = 0,
    /** The people it is about (Person uris: tel:, mailto:, contact lookup), for priority people. */
    val people: List<String> = emptyList(),
) {
    val replyAction get() = actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    val markReadAction get() = actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
        ?: actions.firstOrNull { it.title?.toString()?.contains("read", ignoreCase = true) == true && it.remoteInputs.isNullOrEmpty() }
}

/** Shared notification state for the Events view. */
object EventsStore {
    var items: List<EventItem> = emptyList()
        private set
    /** Ongoing notifications with progress (Android extras → Live updates in Events). */
    var live: List<EventItem> = emptyList()
        private set
    var service: EventsService? = null
    private val listeners = mutableSetOf<() -> Unit>()

    /** Something else shown in Events changed (kept dismissed notifications). */
    fun changed() = listeners.toList().forEach { it() }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun update(sbns: Array<StatusBarNotification>?, ranking: NotificationListenerService.RankingMap? = null) {
        val all = sbns.orEmpty()
        live = all.filter { it.isOngoing && isLive(it) }.mapNotNull { toItem(it, ranking, live = true) }
            .sortedByDescending { it.time }
        items = all.filter { !it.isOngoing || (isSystem(it) && !isLive(it)) }.mapNotNull { toItem(it, ranking) }
            .distinctBy { it.key }.sortedByDescending { it.time }
        listeners.toList().forEach { it() }
    }

    /** An ongoing notification with a progress bar, or one Android promotes as a live update. */
    private fun isLive(sbn: StatusBarNotification): Boolean {
        val e = sbn.notification.extras
        return e.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) ||
            (android.os.Build.VERSION.SDK_INT >= 36 && sbn.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
    }

    /**
     * Posted by Android itself (system, phone, System UI: uids below the first app uid). Its notices, such as No service
     * or USB debugging, are ongoing but are events to the user, so Events lists them; apps' ongoing ones stay out.
     */
    private fun isSystem(sbn: StatusBarNotification) = sbn.uid < android.os.Process.FIRST_APPLICATION_UID

    private fun toItem(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?, live: Boolean = false): EventItem? {
        val n = sbn.notification
        if ((sbn.isOngoing && !live && !isSystem(sbn)) || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        val rank = NotificationListenerService.Ranking()
        val ranked = ranking?.getRanking(sbn.key, rank) == true
        val channel = if (ranked) rank.channel else null
        return EventItem(sbn.key, sbn.packageName, title, text, sbn.postTime, n.contentIntent,
            n.flags and Notification.FLAG_AUTO_CANCEL != 0, n.smallIcon,
            channelId = n.channelId.orEmpty(),
            channelName = channel?.name?.toString().orEmpty(),
            category = n.category,
            importance = if (ranked) rank.importance else 3,
            actions = n.actions?.toList().orEmpty(),
            progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progress = extras.getInt(Notification.EXTRA_PROGRESS, 0),
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
            color = n.color,
            people = peopleOf(n))
    }

    /** Person uris from EXTRA_PEOPLE_LIST and the MessagingStyle senders. */
    private fun peopleOf(n: Notification): List<String> {
        val e = n.extras
        val out = mutableListOf<String>()
        runCatching {
            @Suppress("DEPRECATION")
            e.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)?.forEach { p -> p.uri?.let { out += it } }
        }
        runCatching {
            e.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { b ->
                ((b as? android.os.Bundle)?.get("sender_person") as? android.app.Person)?.uri?.let { out += it }
            }
        }
        return out.distinct()
    }

    fun isEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }
}

class EventsService : NotificationListenerService() {
    override fun onListenerConnected() {
        EventsStore.service = this
        BelleMusicWidget.watch(applicationContext)
        refresh()
    }

    override fun onListenerDisconnected() {
        EventsStore.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        // Android extras → Keep dismissed notifications: the ones the user dismissed (or tapped) linger.
        if (sbn != null && (reason == REASON_CANCEL || reason == REASON_CLICK || reason == REASON_LISTENER_CANCEL)) {
            EventsStore.items.firstOrNull { it.key == sbn.key }?.let { Ghosts.add(applicationContext, it) }
        }
        refresh()
    }

    private fun refresh() = EventsStore.update(runCatching { activeNotifications }.getOrNull(),
        runCatching { currentRanking }.getOrNull())
}
