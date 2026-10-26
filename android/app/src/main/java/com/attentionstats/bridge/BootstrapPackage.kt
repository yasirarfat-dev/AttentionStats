package com.attentionstats.bridge

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class BootstrapPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == BootstrapModule.NAME) BootstrapModule(reactContext) else null

    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf(BootstrapModule.NAME to ReactModuleInfo(
            BootstrapModule.NAME, BootstrapModule.NAME,
            false, false, false, true,
        ))
    }
}
