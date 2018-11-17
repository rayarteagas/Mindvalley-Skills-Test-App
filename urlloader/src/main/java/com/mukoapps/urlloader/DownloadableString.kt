package com.mukoapps.urlloader

class DownloadableString(url: String) : Downloadable<String>(url) {
    //converts ByteArray to string
    override fun transform(content: DownloadableContent): String {
        return content.toString(Charsets.UTF_8)
    }
}