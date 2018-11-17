package com.mukoapps.urlloader

abstract class Downloadable<T>(var url: String) {
    var cancelled = false
    private var used = false
    private lateinit var onLoad: (T?, throwable: Throwable?) -> Unit
    abstract fun transform(content: DownloadableContent): T
    fun load(onLoad: (T?, Throwable?) -> Unit) {
        if (used)
            //Avoid unnecessary chaining complexity as this is not the main goal of this sample
            throw IllegalStateException("This Downloadable is already loading")
        if(cancelled)
            throw IllegalStateException("This Downloadable has already been cancelled")
        used = true
        this.onLoad = onLoad
        Loader.load(this)
    }

    fun callOnLoad(content: DownloadableContent?, throwable: Throwable?) {
        if (cancelled)
            return
        onLoad( if(content!=null) transform(content) else null, throwable)
    }

    fun cancel() {
        cancelled = true
        Loader.cancel(this)
    }
}