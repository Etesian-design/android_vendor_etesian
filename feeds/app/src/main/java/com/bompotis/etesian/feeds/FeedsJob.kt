package com.bompotis.etesian.feeds

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Refreshes the feeds in the background, about hourly, when online. */
class FeedsJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { Feeds.refreshNow(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 4711

        /** Schedules (or, with no feeds, cancels) the periodic refresh. */
        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (Feeds.feeds(ctx).isEmpty()) { js.cancel(JOB_ID); return }
            if (js.getPendingJob(JOB_ID) != null) return
            // Never let scheduling take the launcher down; Events still refreshes when shown.
            runCatching {
                js.schedule(JobInfo.Builder(JOB_ID, ComponentName(ctx, FeedsJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(60 * 60 * 1000L)
                    .setPersisted(true)
                    .build())
            }
        }
    }
}
