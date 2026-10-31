package com.bompotis.etesian.store.ui

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bompotis.etesian.store.ChangeBus
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.StoreApplication
import com.bompotis.etesian.store.data.FdroidApp
import com.bompotis.etesian.store.data.RepoIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bompotis.etesian.fastscroll.EtesianFastScroller
import com.bompotis.etesian.toolbar.EtesianToolbar

/** Home / Categories / Search / Updates, switched from the bottom toolbar. */
class MainActivity : Activity() {
    companion object {
        private const val TAB_HOME = 0
        private const val TAB_CATEGORIES = 1
        private const val TAB_SEARCH = 2
        private const val TAB_UPDATES = 3
        private const val KEY_TAB = "tab"
    }

    private val scope = MainScope()
    private val app by lazy { StoreApplication.get(this) }
    private lateinit var adapter: AppAdapter
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var updateAll: TextView
    private lateinit var list: RecyclerView
    private var scroller: EtesianFastScroller? = null
    private val tabs = ArrayList<TextView>()
    private var tab = TAB_HOME
    private var shownIndex: RepoIndex? = null
    private var shownTab = -1
    private var searchJob: Job? = null
    private var updatesJob: Job? = null
    private var updates: List<FdroidApp> = emptyList()

    private val listener: (Int) -> Unit = { kind ->
        when (kind) {
            ChangeBus.INDEX -> render(force = false)
            ChangeBus.PACKAGES -> {
                if (tab == TAB_UPDATES) loadUpdates() else adapter.notifyDataSetChanged()
            }
            else -> adapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt(KEY_TAB, TAB_HOME) ?: TAB_HOME
        val d = { v: Int -> Ui.dp(this, v) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.WINDOW)
        }
        val header = Ui.Header(this, getString(R.string.app_name))
        header.action(getString(R.string.refresh)) { app.repo.refresh(force = true) }
        root.addView(header.view)

        status = Ui.text(this, 14f, Ui.SECONDARY).apply {
            setPadding(d(16), d(8), d(16), d(8))
            visibility = View.GONE
        }
        root.addView(status)

        search = EditText(this).apply {
            setHint(R.string.search_hint)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SECONDARY)
            setBackgroundResource(R.drawable.search_bg)
            setPadding(d(12), d(10), d(12), d(10))
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { runSearch(debounce = true) }
            })
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(d(12), d(10), d(12), d(6))
        })

        updateAll = Ui.button(this).apply {
            setText(R.string.update_all)
            setBackgroundResource(R.drawable.btn_blue)
            visibility = View.GONE
            setOnClickListener { updates.forEach { app.installer.install(it) } }
        }
        root.addView(updateAll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            setMargins(d(12), d(10), d(12), d(6))
        })

        adapter = AppAdapter(this)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            itemAnimator = null
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.LIST)
        }
        listOf(R.string.tab_home, R.string.tab_categories, R.string.tab_search, R.string.tab_updates)
            .forEachIndexed { i, label ->
                val t = Ui.text(this, 14f).apply {
                    setText(label)
                    gravity = Gravity.CENTER
                    minHeight = d(52)
                    setBackgroundResource(R.drawable.row_bg)
                    setOnClickListener { selectTab(i) }
                }
                tabs += t
                toolbar.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        // N9: the views are the system toolbar's tabs (icons); our own row only without it.
        val systemBar = EtesianToolbar.isAvailable(this)
        if (!systemBar) {
            root.addView(Ui.separator(this))
            root.addView(toolbar)
        }

        // The window's decor view exists only after setContentView.
        setContentView(root)
        Ui.setupWindow(this, root, header.view, if (systemBar) list else toolbar)
        list.clipToPadding = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab)
    }

    override fun onStart() {
        super.onStart()
        ChangeBus.add(listener)
        render(force = true)
    }

    override fun onStop() {
        ChangeBus.remove(listener)
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Back from another tab returns to Home (registered only while not on Home).
    private val backToHome = OnBackInvokedCallback { selectTab(TAB_HOME) }
    private var backRegistered = false

    private fun updateBack() {
        val want = tab != TAB_HOME
        if (want == backRegistered) return
        if (want) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToHome)
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backToHome)
        }
        backRegistered = want
    }

    private val tabIds = listOf("home", "categories", "search", "updates")
    private val tabIcons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_categories,
        R.drawable.ic_tab_search, R.drawable.ic_tab_updates)
    private val tabLabels = listOf(R.string.tab_home, R.string.tab_categories, R.string.tab_search, R.string.tab_updates)

    /** The system toolbar: Back off Home, and the four views as tabs, the current one selected. */
    private fun updateSystemBar() {
        if (!EtesianToolbar.isAvailable(this)) return
        val items = tabIds.indices.map { i ->
            EtesianToolbar.Item.tab(tabIds[i], tabIcons[i], getString(tabLabels[i]), i == tab)
        }
        EtesianToolbar.of(this).setBack(tab != TAB_HOME)
            .setItems(items) { id -> tabIds.indexOf(id).takeIf { it >= 0 }?.let(::selectTab) }
    }

    private fun selectTab(i: Int) {
        if (i == tab) return
        tab = i
        val imm = getSystemService(InputMethodManager::class.java)
        if (i == TAB_SEARCH) {
            search.requestFocus()
            imm?.showSoftInput(search, 0)
        } else {
            imm?.hideSoftInputFromWindow(search.windowToken, 0)
        }
        render(force = true)
    }

    private fun render(force: Boolean) {
        val repo = app.repo
        val idx = repo.index
        val err = repo.error
        status.visibility = View.VISIBLE
        status.text = when {
            repo.busy -> getString(R.string.updating)
            err != null -> getString(R.string.update_failed, err)
            idx == null -> getString(R.string.no_catalogue)
            else -> {
                status.visibility = View.GONE
                ""
            }
        }
        updateBack()
        tabs.forEachIndexed { i, t -> t.setTextColor(if (i == tab) Ui.BLUE else Ui.TEXT) }
        updateSystemBar()
        search.visibility = if (tab == TAB_SEARCH) View.VISIBLE else View.GONE
        updateAll.visibility = if (tab == TAB_UPDATES && updates.size > 1) View.VISIBLE else View.GONE

        if (!force && idx === shownIndex && tab == shownTab) {
            adapter.notifyDataSetChanged()
            return
        }
        val tabChanged = tab != shownTab
        shownIndex = idx
        shownTab = tab
        if (idx == null) {
            adapter.items = emptyList()
            return
        }
        when (tab) {
            TAB_HOME -> adapter.items = buildList<Item> {
                add(Item.Section(getString(R.string.section_new)))
                idx.byAdded.take(10).forEach { add(Item.App(it)) }
                add(Item.Section(getString(R.string.section_updated)))
                idx.byUpdated.take(40).forEach { add(Item.App(it)) }
            }
            TAB_CATEGORIES -> adapter.items = idx.categories.map {
                Item.Category(it.id, it.name, idx.categoryCounts[it.id] ?: 0)
            }
            TAB_SEARCH -> runSearch(debounce = false)
            TAB_UPDATES -> loadUpdates()
        }
        if (tabChanged) {
            list.scrollToPosition(0)
            updateScroller()
        }
    }

    // Letter jump only on the name-sorted lists (Categories, Updates).
    private fun updateScroller() {
        val want = tab == TAB_CATEGORIES || tab == TAB_UPDATES
        if (want == (scroller != null)) return
        scroller?.detach()
        // attach() adds a scroll listener that detach() leaves behind; the list has no others.
        list.clearOnScrollListeners()
        scroller = if (want) EtesianFastScroller.attach(list) { adapter.sectionFor(it) } else null
    }

    private fun runSearch(debounce: Boolean) {
        val idx = app.repo.index ?: return
        val q = search.text.toString()
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounce) delay(250)
            val results = withContext(Dispatchers.Default) { idx.search(q) }
            if (tab != TAB_SEARCH) return@launch
            adapter.items = when {
                q.isBlank() -> emptyList()
                results.isEmpty() -> listOf(Item.Note(getString(R.string.no_results)))
                else -> results.take(200).map { Item.App(it) }
            }
        }
    }

    private fun loadUpdates() {
        val idx = app.repo.index ?: return
        updatesJob?.cancel()
        updatesJob = scope.launch {
            val found = withContext(Dispatchers.IO) { findUpdates(idx) }
            updates = found
            if (tab != TAB_UPDATES) return@launch
            updateAll.visibility = if (found.size > 1) View.VISIBLE else View.GONE
            adapter.items = if (found.isEmpty()) listOf(Item.Note(getString(R.string.no_updates)))
            else found.map { Item.App(it) }
        }
    }

    private fun findUpdates(idx: RepoIndex): List<FdroidApp> {
        val pm = packageManager
        val installed = pm.getInstalledPackages(
            android.content.pm.PackageManager.PackageInfoFlags.of(
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
            ),
        )
        return installed.mapNotNull { info ->
            val a = idx.apps[info.packageName] ?: return@mapNotNull null
            if (AppActions.action(this, a, info) == Action.UPDATE) a else null
        }.sortedBy { it.name.lowercase() }
    }
}
