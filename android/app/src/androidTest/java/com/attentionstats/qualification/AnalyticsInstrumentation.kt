package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics

/** Runs the existing synthetic suite through the application's D8/desugaring pipeline. */
class AnalyticsInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            verifyAnalytics(emptyArray())
            result.putString("stream", "PASS: synthetic analytics APK checks completed\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            // Fixtures are synthetic; return only the failure type, never arbitrary runtime messages.
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
