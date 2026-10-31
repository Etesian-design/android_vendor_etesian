package com.bompotis.etesian.launcher

import android.app.Dialog
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Android extras → Inline reply (mock 05): the message being answered on top, a one-line field
 * with the service's squircle icon, rising with the keyboard; the keyboard's white N9 strip carries
 * Send (IME action send). With the Pro1-X keyboard out only the strip shows.
 */
object EventsReply {
    fun show(ctx: Context, e: EventItem, icon: Bitmap?) {
        val action = e.replyAction ?: return
        val input = action.remoteInputs.first { it.allowFreeFormInput }
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val d = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        val field = EditText(ctx).apply {
            hint = "Reply"
            setTextColor(0xFF1A1A1A.toInt()); setHintTextColor(0xFF8C8C8C.toInt())
            textSize = 18f; typeface = N9.FONT
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
            contentDescription = "Reply field"
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A1A.toInt())
        }
        fun send() {
            val text = field.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(arrayOf(input), fill, Bundle().apply { putCharSequence(input.resultKey, text) })
            runCatching { action.actionIntent.send(ctx, 0, fill) }
            d.dismiss()
        }
        field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        val quoted = TextView(ctx).apply {
            val who = e.title.ifEmpty { "" }
            text = android.text.SpannableStringBuilder().apply {
                append(who, android.text.style.ForegroundColorSpan(0xFF5A5A5A.toInt()), 0)
                if (who.isNotEmpty() && e.text.isNotEmpty()) append(" · ")
                append(e.text, android.text.style.ForegroundColorSpan(0xFF8C8C8C.toInt()), 0)
            }
            textSize = 15f; typeface = N9.FONT; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(px(16f), px(12f), px(16f), px(6f))
            contentDescription = "Inline reply"
            isClickable = true
            addView(quoted)
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(ctx).apply { setImageBitmap(icon) }, LinearLayout.LayoutParams(px(22f), px(22f)).apply { marginEnd = px(12f) })
                addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        val root = FrameLayout(ctx).apply {
            setOnClickListener { d.dismiss() }
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        // Rise with the keyboard (or sit on the white strip alone with the hardware keyboard).
        root.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(android.view.WindowInsets.Type.ime() or android.view.WindowInsets.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom); ins
        }
        d.setContentView(root)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setDecorFitsSystemWindows(false)
        }
        d.show()
        field.requestFocus()
    }
}
