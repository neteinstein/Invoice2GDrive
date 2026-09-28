package org.neteinstein.snap2sheet.data.local

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * Set once from `Snap2SheetApplication.onCreate()`, before Koin starts, so platform code can
 * reach a [Context] — and the foreground activity, which Google's consent screen needs to be
 * launched from — without threading Android types through commonMain/Koin's shared module.
 */
object AndroidAppContext {
    lateinit var instance: Context
        private set

    private var resumedActivity: WeakReference<ComponentActivity>? = null

    /** The activity currently in the foreground, or null while the app is in the background. */
    val currentActivity: ComponentActivity?
        get() = resumedActivity?.get()

    fun init(application: Application) {
        instance = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is ComponentActivity) resumedActivity = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity?.get() === activity) resumedActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Created counts as current until it resumes, so a sign-in started during the
                // first composition still finds an activity to launch from.
                if (activity is ComponentActivity) resumedActivity = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
