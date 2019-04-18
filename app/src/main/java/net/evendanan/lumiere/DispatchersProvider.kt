package net.evendanan.lumiere

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.Main
import kotlin.coroutines.CoroutineContext

interface DispatchersProvider {
    val main: CoroutineContext
    val immediateMain: CoroutineContext
    val background: CoroutineContext
}


object AndroidDispatchersProvider : DispatchersProvider {
    override val main: CoroutineContext by lazy { Dispatchers.Main }

    override val immediateMain: CoroutineContext by lazy { Dispatchers.Main.immediate }

    override val background: CoroutineContext by lazy { Dispatchers.Default }
}