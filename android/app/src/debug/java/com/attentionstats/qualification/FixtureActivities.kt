package com.attentionstats.qualification

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.attentionstats.R

/** Native debug-only fixtures: no React runtime, source grants or history store. */
open class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            setText(R.string.synthetic_collection_fixture)
            textSize = 24f
        })
    }
}

class FirstFixtureActivity : FixtureActivity()
class SecondFixtureActivity : FixtureActivity()
