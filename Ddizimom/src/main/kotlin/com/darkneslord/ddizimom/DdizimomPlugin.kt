package com.darkneslord.ddizimom

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DdizimomPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Ddizimom())
    }
}
