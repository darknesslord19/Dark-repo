package com.darkneslord.sinematv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SinematvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sinematv())
    }
}
