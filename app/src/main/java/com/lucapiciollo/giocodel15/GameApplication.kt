package com.lucapiciollo.giocodel15

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.lucapiciollo.giocodel15.audio.GameAudioManager

/** Owns app-wide singletons that must live longer than any single Activity: initializes the
 * audio manager once, and pauses/resumes the background music based on whether any Activity
 * is currently visible (so music stops when the app is backgrounded and resumes when it's
 * brought back to front), without needing per-Activity onPause/onResume boilerplate. */
class GameApplication : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        GameAudioManager.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    GameAudioManager.resumeMusicIfEnabled(activity)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    GameAudioManager.pauseMusic()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
