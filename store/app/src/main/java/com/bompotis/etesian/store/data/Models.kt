package com.bompotis.etesian.store.data

class Version(
    val versionName: String,
    val versionCode: Long,
    /** Path relative to the repo, starting with '/'. */
    val apkName: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    val permissions: List<String>,
    /** Lower-case hex SHA-256 of the APK signing certificate(s). */
    val signers: List<String>,
    val added: Long,
)

class FdroidApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val author: String?,
    val license: String?,
    val webSite: String?,
    val icon: String?,
    val categories: List<String>,
    val screenshots: List<String>,
    val added: Long,
    val lastUpdated: Long,
    val suggested: Version,
)

class Category(val id: String, val name: String)

class RepoIndex(val timestamp: Long, val apps: Map<String, FdroidApp>, categoryList: List<Category>) {
    val byUpdated: List<FdroidApp> = apps.values.sortedByDescending { it.lastUpdated }
    val byAdded: List<FdroidApp> = apps.values.sortedByDescending { it.added }
    val categoryCounts: Map<String, Int> =
        apps.values.flatMap { it.categories }.groupingBy { it }.eachCount()
    val categories: List<Category> = run {
        val known = categoryList.associateBy { it.id }
        // Include categories used by apps but missing from repo metadata.
        (categoryCounts.keys.map { known[it] ?: Category(it, it) })
            .sortedBy { it.name.lowercase() }
    }

    fun inCategory(id: String) = byUpdated.filter { id in it.categories }

    fun search(query: String): List<FdroidApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return apps.values.mapNotNull { a ->
            val n = a.name.lowercase()
            val score = when {
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                a.summary.lowercase().contains(q) -> 3
                a.packageName.contains(q) -> 4
                else -> return@mapNotNull null
            }
            score to a
        }.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() })).map { it.second }
    }
}
