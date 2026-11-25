package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.media.EsosaImageCache

class EsosaApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = EsosaImageCache.createImageLoader(this)
}
