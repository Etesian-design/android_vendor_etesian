package com.bompotis.etesian.launcher.platform

import android.app.Service
import android.app.contextualsearch.ContextualSearchConfig
import android.content.Intent
import android.graphics.Region
import android.os.Bundle
import android.os.IBinder
import android.os.IRemoteCallback
import android.util.Log
import com.android.systemui.shared.recents.ILauncherProxy
import com.android.systemui.shared.recents.ISystemUiProxy
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController
import com.android.wm.shell.recents.IRecentTasks
import com.android.wm.shell.splitscreen.ISplitScreen
import com.bompotis.etesian.launcher.Platform

/**
 * Etesian's side of the Recents connection: SystemUI binds this (as it binds Launcher3's
 * TouchInteractionService) and hands over the shell interfaces that let us drive app windows
 * during gestures. Owning this connection is what makes Etesian the system Recents provider.
 */
class ProxyService : Service() {

    companion object {
        private const val TAG = "EtesianProxy"
        @Volatile var systemUi: ISystemUiProxy? = null
        @Volatile var splitScreen: ISplitScreen? = null
        @Volatile var recentTasks: IRecentTasks? = null
    }

    private var gestures: EdgeGestures? = null
    private val unlockAnimation by lazy { UnlockAnimation(this) }

    /** Starts once the user's storage is unlocked; settings and the app list live there. */
    private val unlocked = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) = startForUser()
    }

    override fun onCreate() {
        super.onCreate()
        Platform.restoreOptions = { RestoreTransition.options() }
        Platform.launchOptions = { onReady -> RestoreTransition.launchOptions(onReady) }
        Platform.splitTasks = { first, second -> MultiWindow.split(first, second) }
        Platform.popupTask = { taskId -> MultiWindow.popup(this, taskId) }
        Platform.splitPair = { MultiWindow.pair() }
        Platform.unsplit = { top -> MultiWindow.unsplit(top) }
        // SystemUI binds us before the user unlocks (hence directBootAware); wait for the unlock.
        if (getSystemService(android.os.UserManager::class.java).isUserUnlocked) startForUser()
        else registerReceiver(unlocked, android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED))
    }

    private fun startForUser() {
        if (gestures != null) return
        runCatching { unregisterReceiver(unlocked) }
        // The bottom-hold peek needs the app list even before home is first shown.
        if (com.bompotis.etesian.launcher.AppRepository.apps.isEmpty()) {
            com.bompotis.etesian.launcher.AppRepository.loadAsync(this) {}
        }
        gestures = EdgeGestures(this).also { it.start() }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        gestures?.stop()
        gestures = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = proxy

    private val proxy = object : ILauncherProxy.Stub() {
        override fun onInitialize(params: Bundle) {
            systemUi = ISystemUiProxy.Stub.asInterface(params.getBinder(ISystemUiProxy.DESCRIPTOR))
            recentTasks = IRecentTasks.Stub.asInterface(params.getBinder(IRecentTasks.DESCRIPTOR))
            splitScreen = ISplitScreen.Stub.asInterface(params.getBinder(ISplitScreen.DESCRIPTOR))
            // N9 unlock: SystemUI drives home's zoom-in when the keyguard goes away over it.
            runCatching {
                ISysuiUnlockAnimationController.Stub.asInterface(
                    params.getBinder(ISysuiUnlockAnimationController.DESCRIPTOR)
                )?.setLauncherUnlockController(
                    com.bompotis.etesian.launcher.HomeActivity::class.java.name, unlockAnimation)
            }.onFailure { Log.w(TAG, "unlock animation controller", it) }
            Log.i(TAG, "connected to SystemUI (recents=${recentTasks != null})")
        }

        // The Recents key and Alt+Tab open Open applications.
        override fun onOverviewToggle() = showSwitcher()
        override fun onOverviewShown(triggeredFromAltTab: Boolean) = showSwitcher()

        override fun onUnbind(reply: IRemoteCallback?) {
            systemUi = null
            recentTasks = null
            runCatching { reply?.sendResult(null) }
        }

        // Nothing else from SystemUI matters to Etesian.
        override fun onActiveNavBarRegionChanges(activeRegion: Region?) {}
        override fun onOverviewHidden(triggeredFromAltTab: Boolean, triggeredFromHomeKey: Boolean) {}
        override fun onAssistantAvailable(available: Boolean, longPressHomeEnabled: Boolean) {}
        override fun onAssistantVisibilityChanged(visibility: Float) {}
        override fun onAssistantOverrideInvoked(invocationType: Int) {}
        override fun onSystemUiStateChanged(stateFlags: Long, displayId: Int) {
            gestures?.systemUiState = stateFlags
        }
        override fun onRotationProposal(rotation: Int, isValid: Boolean) {}
        override fun disable(displayId: Int, state1: Int, state2: Int, animate: Boolean) {}
        override fun onSystemBarAttributesChanged(displayId: Int, behavior: Int) {}
        override fun onTransitionModeUpdated(barMode: Int, checkBarModes: Boolean) {}
        override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) {}
        override fun onNavigationBarLumaSamplingEnabled(displayId: Int, enable: Boolean) {}
        override fun enterStageSplitFromRunningApp(displayId: Int, leftOrTop: Boolean) {}
        override fun onTaskbarToggled() {}
        override fun updateWallpaperVisibility(displayId: Int, visible: Boolean) {}
        override fun checkNavBarModes(displayId: Int) {}
        override fun finishBarAnimations(displayId: Int) {}
        override fun touchAutoDim(displayid: Int, reset: Boolean) {}
        override fun transitionTo(displayId: Int, barMode: Int, animate: Boolean) {}
        override fun appTransitionPending(pending: Boolean) {}
        override fun onDisplayAddSystemDecorations(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayRemoveSystemDecorations(displayId: Int) {}
        override fun onActionCornerActivated(action: Int, displayId: Int) {}
        override fun invokeContextualSearch(entryPoint: Int, config: ContextualSearchConfig?) {}
    }

    private fun showSwitcher() {
        startActivity(com.bompotis.etesian.launcher.HomeActivity.switcherIntent(this))
    }
}
