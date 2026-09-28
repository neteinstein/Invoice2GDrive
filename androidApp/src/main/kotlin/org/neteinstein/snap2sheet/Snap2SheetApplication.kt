package org.neteinstein.snap2sheet

import android.app.Application
import org.neteinstein.snap2sheet.data.local.AndroidAppContext
import org.neteinstein.snap2sheet.di.initKoin

class Snap2SheetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.init(this)
        initKoin()
    }
}
