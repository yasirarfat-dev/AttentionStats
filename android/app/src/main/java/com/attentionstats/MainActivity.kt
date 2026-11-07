package com.attentionstats

import android.os.Bundle
import android.content.Intent

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
    super.onCreate(savedInstanceState)
  }

  override fun onNewIntent(intent: Intent) {
    // Retain links received while React is starting, before its URL listener is attached.
    setIntent(intent)
    super.onNewIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    // A backup failure must never prevent opening the app or reviewing a pending restore.
    try { com.attentionstats.backup.SystemBackupState.initialize(this) }
    catch (_: RuntimeException) { android.util.Log.w("AttentionStats", "Backup initialization deferred") }
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "AttentionStats"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * for React Native's New Architecture.
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName)
}
