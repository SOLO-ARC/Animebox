package com.lagradost.cloudstream3.ui.animebox

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AnimeScreenshotEvents {
    private val _refreshFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFlow = _refreshFlow.asSharedFlow()

    fun notifyScreenshotCaptured() {
        _refreshFlow.tryEmit(Unit)
    }
}
