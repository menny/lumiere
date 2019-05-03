package net.evendanan.lumiere.okhttp

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val lumiereOkHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
}