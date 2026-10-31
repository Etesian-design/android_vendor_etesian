package com.bompotis.etesian.store.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bompotis.etesian.store.ChangeBus
import com.bompotis.etesian.store.R
import com.bompotis.etesian.store.StoreApplication
import com.bompotis.etesian.store.data.RepoIndex
import com.bompotis.etesian.fastscroll.EtesianFastScroller

/** Apps of one category, most recently updated first or A–Z (with the letter-jump strip). */
class CategoryActivity : Activity() {
    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val KEY_BY_NAME = "byName"

        fun start(ctx: Context, id: String, name: String) {
            ctx.startActivity(Intent(ctx, CategoryActivity::class.java)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name))
        }
    }

    private val adapter by lazy { AppAdapter(this) }
    private var shown: RepoIndex? = null
    private var byName = false
    private lateinit var list: RecyclerView
    private lateinit var sortButton: TextView
    private var scroller: EtesianFastScroller? = null
    private val listener: (Int) -> Unit = { kind ->
        if (kind == ChangeBus.INDEX) render() else adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME) ?: id
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.WINDOW)
        }
        byName = savedInstanceState?.getBoolean(KEY_BY_NAME) ?: false
        val header = Ui.Header(this, name)
        sortButton = header.action("") {
            byName = !byName
            shown = null
            render()
            list.scrollToPosition(0)
        }
        root.addView(header.view)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@CategoryActivity)
            adapter = this@CategoryActivity.adapter
            itemAnimator = null
            clipToPadding = false
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // The window's decor view exists only after setContentView.
        setContentView(root)
        Ui.setupWindow(this, root, header.view, list)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_BY_NAME, byName)
    }

    override fun onStart() {
        super.onStart()
        ChangeBus.add(listener)
        render()
    }

    override fun onStop() {
        ChangeBus.remove(listener)
        super.onStop()
    }

    private fun render() {
        sortButton.setText(if (byName) R.string.sort_recent else R.string.sort_name)
        if (byName != (scroller != null)) {
            scroller?.detach()
            // attach() adds a scroll listener that detach() leaves behind; the list has no others.
            list.clearOnScrollListeners()
            scroller = if (byName) EtesianFastScroller.attach(list) { adapter.sectionFor(it) } else null
        }
        val idx = StoreApplication.get(this).repo.index
        if (idx === shown) {
            adapter.notifyDataSetChanged()
            return
        }
        shown = idx
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val apps = idx?.inCategory(id) ?: emptyList()
        adapter.items = (if (byName) apps.sortedBy { it.name.lowercase() } else apps).map { Item.App(it) }
    }
}
