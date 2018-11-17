package com.mukoapps.urlloader

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.URL
import java.util.concurrent.Semaphore


class Loader private constructor(){
    //used to throttle simultaneous downloads to a max of 10, in a real world app a thread pool should be used
    //as fixing a max connections number has no point
    private val semaphore = Semaphore(10)
    var cacheSize = 4 * 1024 * 1024 // 4MiB
    private val tasksFlow = Channel<DownloadTask>()
    private val mainJob = Job()


    //centralized cache just to keep it simple without breaking the SOLID design pattern, a better
    // approach would be a per-downloadable type cache to avoid extra instantiation of cached resources
    private val cache = object : LruCache<String, DownloadableContent>(cacheSize) {
        override fun sizeOf(key: String, value: DownloadableContent): Int {
            return value.size
        }
    }

    //List of active download tasks
    private val downloadTasks = mutableListOf<DownloadTask>()

    //Logic and data related to download tasks
    inner class DownloadTask(val url: String, private val downloadables: MutableList<Downloadable<*>>) {
        inner class DownloadTaskResult(var result:DownloadableContent?, var throwable: Throwable?)
        private var taskResult = DownloadTaskResult(null,null)

        //cancel a downloadable from this task, if any downloadables left, cancel this task's job
        fun cancelDownloadable(downloadable: Downloadable<*>)
        {
            Log.d("Loader","cancelling a downloadable from $url")
            downloadables.remove(downloadable)
            if(downloadables.size<1) {
                Log.d("Loader","cancelling a download task because it doesn't have any active downloadable [$url]")
                job.cancel()
            }
        }

        /**
         * add a downloadable to this task, join the task to avoid unsafe operations when the task is being cancelled
         *
         * @return true if downloadable is successfully added to tak or false if task is already cancelled
         */
        fun addDownloadable(downloadable:Downloadable<*>) = CoroutineScope(mainJob).async {
            if(!job.isActive) {
                try{job.join()}
                catch(ignored:java.lang.Exception){}
                return@async false
            }
            downloadables.add(downloadable)
            return@async true

        }

        //This is the job executed by this task, note the lazy start to avoid automatic execution
        private var job = CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
            try {
                Log.d("Loader","Reading contents of $url")
                taskResult.result = URL(url).openStream().use {
                    it.readBytesWithCheck {isActive}
                }
                Log.d("Loader","Finished reading contents of $url")
                cache.put(url,taskResult.result)
            } catch (e: Exception) {
                Log.d("Loader","Error reading $e [$url]")
                taskResult.throwable=e
            }
        }

        //Starts this task's related job
        fun start() {
            job.start()
        }

        //notify attached downloadables about completion, clear references and releases semaphore
        private fun notifyCompletion() = CoroutineScope(Dispatchers.Main).launch{
            downloadables.forEach {
                it.callOnLoad(taskResult.result,taskResult.throwable)
            }
            downloadTasks.remove(this@DownloadTask)
            semaphore.release()
        }

        init{
            job.invokeOnCompletion {
                Log.d("Loader","Job completed with $it error")
                notifyCompletion()
            }
        }
    }

    //resize the cache
    private fun resizeCache(newSize: Int) {
        cache.resize(newSize)
    }

    fun getItemsCacheSize():Int
    {
        return cache.maxSize()
    }

    /**
     * Check if a downloadable is cached, if so, immediately call loaded callback, if not cached,
     * will check if there is some task already downloading the same resource, if so, attach this
     * downloadable to that task, so it gets notified when such task finish loading the needed resource.
     *
     * If not cached and there are no tasks downloading the needed resource, push the url to the
     * "downloader" {@link #tasksFlow} channel, it will download the resource on its turn
     */
    private fun load(downloadable: Downloadable<*>) = CoroutineScope(Dispatchers.Main).launch{
        val fromCache = cache.get(downloadable.url)
        if (fromCache != null) {
            Log.d("Loader","Returning from cached [${downloadable.url}]}")
            downloadable.callOnLoad(fromCache,null)
            return@launch
        }
        val task = downloadTasks.firstOrNull { it.url == downloadable.url }
        if(task!=null && task.addDownloadable(downloadable).await()){
            return@launch
        }
        else {

                val newTask = DownloadTask(downloadable.url, mutableListOf(downloadable))
                downloadTasks.add(newTask)
                tasksFlow.send(newTask)

        }
    }

    /**
     * Tries to cancel this downloadable if already started
     */
    private fun cancel(downloadable: Downloadable<*>) {
        downloadTasks.firstOrNull { it.url == downloadable.url }?.cancelDownloadable(downloadable)
    }

    /**
     * This will pull download tasks from channel and execute tasks throttling them with the semaphore
     */
    private fun mainProcess() = CoroutineScope(mainJob).launch {
        //I know using while(true) is a bad practice in majority of cases, but here it will run
        //during the whole app lifecycle. Other approaches would imply making this class disposable and
        //instantiable, and looping only when needed, but in this particular case, would add extra
        //and unwanted complexity as this is only with illustrative purposes
        while (true) {
            val task = tasksFlow.receive()
            semaphore.acquire()
            //semaphore is used to throttle downloads to a max of 10
            launch {
                //get a DownloadTask from the queue and start it
                task.start()
            }
        }
    }

    //Used to implement singleton pattern, Instantiation would be a better approach
    //but this is used for demonstration purposes
    companion object {
        private var instance: Loader = Loader()

        init {
            instance.mainProcess()
        }

        fun load(downloadable: Downloadable<*>) {
            instance.load(downloadable)
        }

        fun cancel(downloadable: Downloadable<*>) {
            instance.cancel(downloadable)
        }

        fun resizeCache(newSize: Int) {
            instance.resizeCache(newSize)
        }

        fun cacheSize():Int{
            return instance.getItemsCacheSize()
        }
    }
}
