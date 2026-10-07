package com.darkneslord.fullhdfilmizlesene

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FullhdfilmizlesenePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Fullhdfilmizlesene())
    }
}
