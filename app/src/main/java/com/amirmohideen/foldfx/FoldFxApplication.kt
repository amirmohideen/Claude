package com.amirmohideen.foldfx

import android.app.Application

class FoldFxApplication : Application() {
    val runtime: FoldRuntime by lazy { FoldRuntime(this) }
}
