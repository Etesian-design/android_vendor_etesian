/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bompotis.etesian.toolbar;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Puts an activity's own actions on the Etesian (N9) system toolbar at the bottom of the
 * screen, in place of the user's custom buttons, while that activity is in front.
 *
 * <pre>
 * EtesianToolbar.of(activity)
 *         .setBack(false)
 *         .setItems(Arrays.asList(
 *                 EtesianToolbar.Item.action("add", R.drawable.ic_add, "New event"),
 *                 EtesianToolbar.Item.tab("month", R.drawable.ic_month, "Month", true),
 *                 EtesianToolbar.Item.tab("week", R.drawable.ic_week, "Week", false)),
 *                 id -> onToolbarItem(id));
 * </pre>
 *
 * Call it again whenever the items change (e.g. another tab is selected). The toolbar follows
 * the activity: it is shown while the activity is in front and re-sent when it resumes. If the
 * system toolbar is unavailable ({@link #isAvailable}), draw your own bar instead.
 */
public final class EtesianToolbar {

    /** Broadcast to SystemUI: show these items for a task's top activity. */
    public static final String ACTION_SET = "com.bompotis.etesian.toolbar.SET";
    /** Broadcast to SystemUI: the activity no longer provides items. */
    public static final String ACTION_CLEAR = "com.bompotis.etesian.toolbar.CLEAR";

    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_COMPONENT = "component";
    public static final String EXTRA_BACK = "back";
    public static final String EXTRA_ITEMS = "items";
    public static final String EXTRA_CALLBACK = "callback";
    /** Filled into the callback by SystemUI: the id of the tapped item. */
    public static final String EXTRA_ID = "id";

    public static final String KEY_ID = "id";
    public static final String KEY_LABEL = "label";
    public static final String KEY_ICON = "icon";
    public static final String KEY_TAB = "tab";
    public static final String KEY_SELECTED = "selected";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_BADGE = "badge";

    private static final String SYSTEMUI = "com.android.systemui";
    private static final WeakHashMap<Activity, EtesianToolbar> sInstances = new WeakHashMap<>();

    /** Receives taps on this toolbar's items. */
    public interface Listener {
        void onItemSelected(String id);
    }

    /** One button: a tab (shown selected or not) or a plain action. */
    public static final class Item {
        final String mId;
        final int mIconRes;
        final Icon mIcon;
        final String mLabel;
        final boolean mTab;
        boolean mSelected;
        boolean mEnabled = true;
        boolean mTint = true;
        String mBadge;
        boolean mStart;

        private Item(String id, int iconRes, Icon icon, String label, boolean tab, boolean selected) {
            mId = id;
            mIconRes = iconRes;
            mIcon = icon;
            mLabel = label;
            mTab = tab;
            mSelected = selected;
        }

        /** A button that runs an action. The icon is drawn monochrome in the bar's colour. */
        public static Item action(String id, int iconRes, String label) {
            return new Item(id, iconRes, null, label, false, false);
        }

        public static Item action(String id, Icon icon, String label) {
            return new Item(id, 0, icon, label, false, false);
        }

        /** One of a set of views; the selected one is drawn as the N9's lighter block. */
        public static Item tab(String id, int iconRes, String label, boolean selected) {
            return new Item(id, iconRes, null, label, true, selected);
        }

        public Item setEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }

        /**
         * Whether the icon is drawn monochrome in the bar's colour (the default), or in its own
         * colours (the N9's app icons, like the orange feed icon in Feeds).
         */
        public Item setTinted(boolean tint) {
            mTint = tint;
            return this;
        }

        /** A small count or mark on the button, or null for none. */
        public Item setBadge(String badge) {
            mBadge = badge;
            return this;
        }

        /**
         * Where a lone button sits: left (true) or right (false, the default), by its action.
         * Several buttons always share the bar in equal slots, so this only matters for one.
         */
        public Item setAlignStart(boolean start) {
            mStart = start;
            return this;
        }

        Bundle toBundle(Context context) {
            final Bundle b = new Bundle();
            b.putString("align", mStart ? "start" : "end");
            b.putString(KEY_ID, mId);
            b.putString(KEY_LABEL, mLabel);
            b.putParcelable(KEY_ICON, mIcon != null ? mIcon
                    : Icon.createWithResource(context.getPackageName(), mIconRes));
            b.putBoolean(KEY_TAB, mTab);
            b.putBoolean(KEY_SELECTED, mSelected);
            b.putBoolean(KEY_ENABLED, mEnabled);
            b.putBoolean("tint", mTint);
            b.putString(KEY_BADGE, mBadge);
            return b;
        }
    }

    /**
     * Whether the system toolbar is showing (Etesian mode with the bottom bar on). When it
     * isn't, the app should draw its own bar.
     */
    public static boolean isAvailable(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), "etesian_gestures", 0) == 1
                && Settings.Secure.getInt(context.getContentResolver(), "etesian_bottom_bar", 1)
                        == 1;
    }

    /** The toolbar for [activity] (one per activity). */
    public static EtesianToolbar of(Activity activity) {
        EtesianToolbar t = sInstances.get(activity);
        if (t == null) {
            t = new EtesianToolbar(activity);
            sInstances.put(activity, t);
        }
        return t;
    }

    private final Activity mActivity;
    private final String mClickAction;
    private boolean mBack = true;
    private String mStyle;
    private boolean mHidden;
    private List<Item> mItems;
    private Listener mListener;
    private boolean mRegistered;

    private final BroadcastReceiver mClicks = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String id = intent.getStringExtra(EXTRA_ID);
            if (id != null && mListener != null) {
                mListener.onItemSelected(id);
            }
        }
    };

    private EtesianToolbar(Activity activity) {
        mActivity = activity;
        mClickAction = "com.bompotis.etesian.toolbar.CLICK." + activity.getPackageName() + "."
                + System.identityHashCode(activity);
        activity.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                if (mItems != null) send();
            }

            @Override
            public void onActivityDestroyed(Activity a) {
                clear();
                sInstances.remove(a);
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
    }

    /** Whether the system Back button is shown with the items (main screens usually omit it). */
    public EtesianToolbar setBack(boolean back) {
        mBack = back;
        if (mItems != null) send();
        return this;
    }

    /** The bar follows the user's theme (the default). */
    public static final int STYLE_DEFAULT = 0;
    /** A black bar whatever the theme, as in the N9's Gallery and other black screens. */
    public static final int STYLE_DARK = 1;
    /** The N9's light grey bar whatever the theme. */
    public static final int STYLE_LIGHT = 2;

    /** Asks for a dark or light bar for this activity (STYLE_*). */
    public EtesianToolbar setStyle(int style) {
        mStyle = style == STYLE_DARK ? "dark" : style == STYLE_LIGHT ? "light" : null;
        if (mItems != null) send();
        return this;
    }

    /**
     * No toolbar at all for this activity, as on the N9's main Settings page (sub pages have
     * one). The app is shown to the bottom edge.
     */
    public EtesianToolbar setHidden(boolean hidden) {
        mHidden = hidden;
        if (mItems != null) send();
        return this;
    }

    /** Shows [items] on the toolbar; [listener] receives taps. */
    public EtesianToolbar setItems(List<Item> items, Listener listener) {
        mItems = new ArrayList<>(items);
        mListener = listener;
        send();
        return this;
    }

    public EtesianToolbar setItems(Listener listener, Item... items) {
        return setItems(Arrays.asList(items), listener);
    }

    /** Hands the toolbar back to the user's own buttons. */
    public void clear() {
        mItems = null;
        if (mRegistered) {
            try {
                mActivity.unregisterReceiver(mClicks);
            } catch (IllegalArgumentException ignored) {
            }
            mRegistered = false;
        }
        mActivity.sendBroadcast(new Intent(ACTION_CLEAR).setPackage(SYSTEMUI)
                .putExtra(EXTRA_TASK, mActivity.getTaskId())
                .putExtra(EXTRA_COMPONENT, mActivity.getComponentName().flattenToString()));
    }

    private void send() {
        if (!mRegistered) {
            mActivity.registerReceiver(mClicks, new IntentFilter(mClickAction),
                    Context.RECEIVER_NOT_EXPORTED);
            mRegistered = true;
        }
        final ArrayList<Bundle> items = new ArrayList<>();
        for (Item i : mItems) items.add(i.toBundle(mActivity));
        // Runs as this app when SystemUI sends it, so the receiver needn't be exported.
        final PendingIntent callback = PendingIntent.getBroadcast(mActivity,
                System.identityHashCode(mActivity),
                new Intent(mClickAction).setPackage(mActivity.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        mActivity.sendBroadcast(new Intent(ACTION_SET).setPackage(SYSTEMUI)
                .putExtra(EXTRA_TASK, mActivity.getTaskId())
                .putExtra(EXTRA_COMPONENT, mActivity.getComponentName().flattenToString())
                .putExtra(EXTRA_BACK, mBack)
                .putExtra("style", mStyle)
                .putExtra("hidden", mHidden)
                .putParcelableArrayListExtra(EXTRA_ITEMS, items)
                .putExtra(EXTRA_CALLBACK, callback));
    }
}
