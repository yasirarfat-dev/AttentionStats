package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics
import org.attentionstats.reels.main as verifyReels

/**
 * On-device run of the pure analytics and encounter checks (screen-layout fixtures run on
 * the JVM with `:reels-core:verifyReels`). `adb shell am instrument -w <test package>/<this>`.
 */
class AnalyticsInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            verifyAnalytics(emptyArray())
            verifyReels()
            result.putString("stream", "PASS: analytics and encounter checks")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
