package com.bompotis.etesian.toolbar;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * The N9 toolbar menu (the ≡ item): a light card that rises from the bottom over the toolbar,
 * inset from the screen edges with rounded corners, the page behind dimmed. Items are bold,
 * with thin dividers; a choice item shows its current value under its title.
 *
 * Any app can show its options menu this way: {@code EtesianMenu.show(activity, R.menu.x, ...)}.
 */
public final class EtesianMenu {

    /** Picks which items show (and their titles) before the menu opens. */
    public interface Preparer {
        void prepare(Menu menu);
    }

    /** A tap on an item. */
    public interface Listener {
        boolean onItemClick(MenuItem item);
    }

    private static final int CARD_TOP = 0xFFF7F6F8;
    private static final int CARD_BOTTOM = 0xFFECEBEE;
    private static final int TEXT = 0xFF282828;
    private static final int SUBTEXT = 0xFF6E6E73;
    private static final int DIVIDER = 0x1F000000;
    private static final int DIM = 0x8C6E6E6E;

    private EtesianMenu() {
    }

    /** Shows the visible items of [menuRes] as the N9 menu; returns the dialog. */
    public static Dialog show(Activity activity, int menuRes, Preparer preparer, Listener listener) {
        // PopupMenu only to inflate a real Menu (titles, visibility, ids) without a host view.
        PopupMenu holder = new PopupMenu(activity, activity.getWindow().getDecorView());
        holder.getMenuInflater().inflate(menuRes, holder.getMenu());
        if (preparer != null) preparer.prepare(holder.getMenu());
        return show(activity, holder.getMenu(), listener);
    }

    /** Shows the visible items of [menu] as the N9 menu; returns the dialog. */
    public static Dialog show(Activity activity, Menu menu, Listener listener) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM});
        bg.setCornerRadius(dp(activity, 14));
        bg.setStroke(Math.max(1, dp(activity, 1) / 2), 0x14000000);
        card.setBackground(bg);
        card.setClipToOutline(true);

        boolean first = true;
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            if (!first) {
                View line = new View(activity);
                line.setBackgroundColor(DIVIDER);
                card.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1) / 2)));
            }
            first = false;
            card.addView(row(activity, item, () -> {
                dialog.dismiss();
                if (listener != null) listener.onItemClick(item);
            }));
        }

        FrameLayout frame = new FrameLayout(activity);
        int inset = dp(activity, 8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.setMargins(inset, 0, inset, inset);
        frame.addView(card, lp);
        // A tap on the dimmed page closes the menu.
        frame.setOnClickListener(v -> dialog.cancel());
        card.setClickable(true);
        dialog.setContentView(frame);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setWindowAnimations(0);
            frame.setBackgroundColor(DIM);
        }
        // The system toolbar steps aside while the menu covers it.
        EtesianToolbar bar = EtesianToolbar.isAvailable(activity)
                ? EtesianToolbar.of(activity) : null;
        if (bar != null) bar.setHidden(true);
        dialog.setOnDismissListener(d -> { if (bar != null) bar.setHidden(false); });
        dialog.show();

        // Rises from the bottom, as on the N9.
        card.setTranslationY(dp(activity, 400));
        frame.setAlpha(0f);
        ObjectAnimator.ofFloat(frame, View.ALPHA, 0f, 1f).setDuration(150).start();
        ObjectAnimator up = ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(activity, 400), 0f);
        up.setDuration(220);
        up.setInterpolator(new DecelerateInterpolator(1.6f));
        up.start();
        return dialog;
    }

    private static View row(Activity activity, MenuItem item, Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(activity, 56));
        int h = dp(activity, 16);
        row.setPadding(h, dp(activity, 10), h, dp(activity, 10));
        TypedValue ripple = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        if (ripple.resourceId != 0) row.setForeground(activity.getDrawable(ripple.resourceId));
        row.setEnabled(item.isEnabled());
        row.setAlpha(item.isEnabled() ? 1f : 0.4f);
        row.setOnClickListener(v -> onClick.run());

        TextView title = new TextView(activity);
        title.setText(item.getTitle());
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setSingleLine(true);
        row.addView(title);

        // A choice item (checkable, or with a condensed title as its value) shows it below.
        CharSequence value = item.getTitleCondensed();
        if (value != null && !value.toString().contentEquals(item.getTitle())) {
            TextView sub = new TextView(activity);
            sub.setText(value);
            sub.setTextColor(SUBTEXT);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.addView(sub);
        }
        row.setContentDescription(item.getTitle());
        return row;
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
