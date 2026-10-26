package com.attentionstats.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule

/** Read-only scaffold handshake; no history, grants, settings, jobs or source reads. */
@ReactModule(name = BootstrapModule.NAME)
class BootstrapModule(context: ReactApplicationContext) : NativeBootstrapSpec(context) {
    override fun getName() = NAME

    override fun getBootstrapStatus(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putInt("contractVersion", 1)
            putString("sourceState", "not_implemented")
        })
    }

    companion object { const val NAME = "NativeBootstrap" }
}
