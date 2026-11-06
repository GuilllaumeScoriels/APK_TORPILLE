package com.torpille.app
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
internal object AppCheckProvider { fun factory() = PlayIntegrityAppCheckProviderFactory.getInstance() }
