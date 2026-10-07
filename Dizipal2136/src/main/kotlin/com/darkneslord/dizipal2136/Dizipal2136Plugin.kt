package com.darkneslord.dizipal2136

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Dizipal2136Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dizipal2136())
    }
}
