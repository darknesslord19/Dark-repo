package com.darkrepo

import android.content.Context
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class RepoItem(val name: String, val url: String)

@CloudstreamPlugin
class DarkRepoPlugin : Plugin() {

    // !!! KENDI GITHUB ADRESINLE DEGISTIR !!!
    private val listUrl =
        "https://raw.githubusercontent.com/darknesslord19/Dark-repo/main/repos.json"

    override fun load(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = app.get(listUrl).text
                val list = parseJson<List<RepoItem>>(json)
                val existing = RepositoryManager.getRepositories().map { it.url }.toSet()
                list.filter { it.url !in existing }.forEach {
                    // Eski CloudStream surumlerinde: RepositoryData(it.name, it.url)
                    RepositoryManager.addRepository(
                        RepositoryData(iconUrl = null, name = it.name, url = it.url)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
