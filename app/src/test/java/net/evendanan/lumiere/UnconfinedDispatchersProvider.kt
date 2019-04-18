package net.evendanan.lumiere

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

class UnconfinedDispatchersProvider : DispatchersProvider {
    override val main: CoroutineContext by lazy { Dispatchers.Unconfined }
    override val immediateMain: CoroutineContext by lazy { Dispatchers.Unconfined }
    override val background: CoroutineContext by lazy { Dispatchers.Unconfined }
}