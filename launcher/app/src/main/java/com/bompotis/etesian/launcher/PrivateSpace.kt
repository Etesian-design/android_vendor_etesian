package com.bompotis.etesian.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Android extras → Private space (story Phone v2, state private-space): Android's private space
 * profile as a locked folder at the end of the Applications grid. Opening it asks for the
 * security code (N9 code screen); closing the folder locks it again.
 */
object PrivateSpace {
    const val TYPE = "android.os.usertype.profile.PRIVATE"
    const val FOLDER_ID = "private"

    fun on(ctx: Context) = Extras.on(ctx, Extras.PRIVATE)

    fun profile(ctx: Context): UserHandle? = runCatching {
        val um = ctx.getSystemService(UserManager::class.java)
        val la = ctx.getSystemService(LauncherApps::class.java)
        um.userProfiles.firstOrNull { it != android.os.Process.myUserHandle() && la.getLauncherUserInfo(it)?.userType == TYPE }
    }.getOrNull()

    fun locked(ctx: Context, u: UserHandle) = ctx.getSystemService(UserManager::class.java).isQuietModeEnabled(u)

    fun lock(ctx: Context, u: UserHandle) { runCatching { ctx.getSystemService(UserManager::class.java).requestQuietModeEnabled(true, u) } }
    fun unlock(ctx: Context, u: UserHandle) = runCatching { ctx.getSystemService(UserManager::class.java).requestQuietModeEnabled(false, u) }.getOrDefault(false)

    private fun lpu(ctx: Context): Any = Class.forName("com.android.internal.widget.LockPatternUtils").getConstructor(Context::class.java).newInstance(ctx)

    /** The user whose code opens the space: its own when it has a separate one, else the device's. */
    fun codeUser(ctx: Context, u: UserHandle): Int {
        val id = u.getIdentifier()
        return runCatching {
            val l = lpu(ctx)
            if (l.javaClass.getMethod("isSeparateProfileChallengeEnabled", Int::class.javaPrimitiveType).invoke(l, id) as Boolean) id
            else ctx.getSystemService(UserManager::class.java).let { um ->
                um.javaClass.getMethod("getProfileParent", UserHandle::class.java).invoke(um, u) as UserHandle? }?.getIdentifier() ?: 0
        }.getOrDefault(0)
    }

    fun hasCode(ctx: Context, user: Int) = runCatching {
        val l = lpu(ctx); l.javaClass.getMethod("isSecure", Int::class.javaPrimitiveType).invoke(l, user) as Boolean
    }.getOrDefault(false)

    /** Checks the security code (PIN, or password when it has letters). */
    fun check(ctx: Context, user: Int, code: String): Boolean = runCatching {
        val l = lpu(ctx)
        val lc = Class.forName("com.android.internal.widget.LockscreenCredential")
        val cred = lc.getMethod(if (code.all { it.isDigit() }) "createPin" else "createPassword", CharSequence::class.java).invoke(null, code)
        val cb = Class.forName("com.android.internal.widget.LockPatternUtils\$CheckCredentialProgressCallback")
        l.javaClass.getMethod("checkCredential", lc, Int::class.javaPrimitiveType, cb).invoke(l, cred, user, null) as Boolean
    }.getOrDefault(false)

    fun open(ctx: Context) = ctx.startActivity(Intent(ctx, PrivateCodeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "Private · Enter the security code · ●●○○" (N9 query screen); Done unlocks the space. */
class PrivateCodeActivity : Activity() {
    private lateinit var dots: LinearLayout
    private lateinit var input: EditText
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val u = PrivateSpace.profile(this) ?: run { finish(); return }
        val codeUser = PrivateSpace.codeUser(this, u)
        if (!PrivateSpace.locked(this, u)) { done(); return }
        val root = FrameLayout(this).apply { setBackgroundColor(0xF2000000.toInt()); setOnClickListener { finish() } }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; isClickable = true }
        col.addView(TextView(this).apply { text = "Private"; setTextColor(N9.TEXT); textSize = 32f; typeface = N9.FONT_LIGHT; gravity = Gravity.CENTER })
        hint = TextView(this).apply {
            text = "Enter the security code"; setTextColor(0xFFC8C8C8.toInt()); textSize = 20f; typeface = N9.FONT_LIGHT; gravity = Gravity.CENTER
            setPadding(0, dp(18f), 0, dp(18f))
        }
        col.addView(hint)
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER; contentDescription = "Code" }
        col.addView(dots)
        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            alpha = 0f; isCursorVisible = false
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { drawDots() }
            })
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_DONE || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(u, codeUser); true } else false
            }
        }
        col.addView(input, LinearLayout.LayoutParams(1, 1))
        col.addView(N9.editButton(this, "Done") { submit(u, codeUser) }.apply { contentDescription = "Done" },
            LinearLayout.LayoutParams(dp(232f), dp(44f)).apply { topMargin = dp(36f) })
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)
        drawDots()
        input.requestFocus()
        input.postDelayed({ getSystemService(InputMethodManager::class.java).showSoftInput(input, 0) }, 200)
    }

    private fun drawDots() {
        dots.removeAllViews()
        val n = input.text.length
        repeat(maxOf(4, n)) { i ->
            dots.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (i < n) setColor(N9.TEXT) else { setColor(0); setStroke(dp(2f), 0xFF8A8A8A.toInt()) }
                }
            }, LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginStart = dp(5f); marginEnd = dp(5f) })
        }
        dots.contentDescription = "Code: $n digits"
    }

    private fun submit(u: UserHandle, codeUser: Int) {
        val code = input.text.toString()
        val ok = !PrivateSpace.hasCode(this, codeUser) || (code.isNotEmpty() && PrivateSpace.check(this, codeUser, code))
        if (!ok) { hint.text = "Wrong code, try again"; input.setText(""); return }
        PrivateSpace.unlock(this, u)
        done()
    }

    private fun done() {
        setResult(RESULT_OK)
        sendBroadcast(Intent(ACTION_OPEN).setPackage(packageName))
        finish()
    }

    companion object { const val ACTION_OPEN = "com.bompotis.etesian.launcher.PRIVATE_OPEN" }
}
