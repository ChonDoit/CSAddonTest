package com.pelisplushd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Pelisplushd_1Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pelisplushd())
    }
}