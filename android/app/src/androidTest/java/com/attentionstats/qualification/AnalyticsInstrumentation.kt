package com.attentionstats.qualification

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import org.attentionstats.analytics.main as verifyAnalytics

/** Synthetic APK qualifications; the default mode preserves the original analytics suite. */
class AnalyticsInstrumentation : Instrumentation() {
    private var qualification: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        qualification = arguments?.getString("qualification")
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var probe: SelfUsageProbe? = null
        try {
            when (qualification) {
                null -> {
                    verifyAnalytics(emptyArray())
                    result.putString("stream", "PASS: synthetic analytics APK checks completed\n")
                }
                "usage-self" -> {
                    probe = SelfUsageProbe(this)
                    result.putString("stream", probe.verify())
                }
                else -> error("Unknown qualification mode")
            }
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            probe?.let {
                result.putString("probe_stage", it.stage)
                result.putInt("fixture_resumed", it.resumedFixtures)
                result.putInt("fixture_paused", it.pausedFixtures)
            }
            // Fixtures are synthetic; return only the failure type, never arbitrary runtime messages.
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
