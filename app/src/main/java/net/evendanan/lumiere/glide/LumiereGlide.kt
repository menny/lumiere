package net.evendanan.lumiere.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import net.evendanan.lumiere.okhttp.lumiereOkHttp
import java.io.InputStream

@GlideModule
class LumiereGlide : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        glide.registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(lumiereOkHttp))
    }
}