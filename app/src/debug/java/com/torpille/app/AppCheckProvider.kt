package com.torpille.app
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
internal object AppCheckProvider { fun factory() = DebugAppCheckProviderFactory.getInstance() }
