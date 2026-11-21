package io.github.gasserma.quadranttwodo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.gasserma.quadranttwodo.AppContainer
import io.github.gasserma.quadranttwodo.appContainer
import io.github.gasserma.quadranttwodo.core.DUE_TIME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Keeps the widget current, three ways (no special permissions needed):
 *  1. on every change to tasks, the log or the settings while the app process is alive, and
 *     whenever the app goes to the background;
 *  2. a WorkManager job every 4 hours, since urgency drifts;
 *  3. a one-off job just after 06:00 each day, when tasks become due. It re-arms itself.
 */
object WidgetRefresher {
    private const val PERIODIC = "widget-refresh-periodic"
    private const val DAILY = "widget-refresh-daily"

    suspend fun refresh(context: Context) = QuadrantTwoDoWidget().updateAll(context)

    /**
     * Redraws straight away on every change (a log change arrives as one emission, since it's one
     * transaction), and again whenever the app goes to the background, so the widget is current by
     * the time the home screen shows. Call on the main thread.
     */
    fun observeChanges(context: Context, container: AppContainer, scope: CoroutineScope) {
        scope.launch {
            combine(container.repository.observeTasks(), container.settings.settings, ::Pair)
                .distinctUntilChanged()
                .conflate()
                .collect { refresh(context) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    scope.launch { refresh(context) }
                }
            },
        )
    }

    fun schedule(context: Context, clock: Clock) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(4, TimeUnit.HOURS).build(),
        )
        scheduleDaily(context, clock)
    }

    /** Arms the one-off refresh for the next 06:00 (plus a minute of slack). */
    fun scheduleDaily(context: Context, clock: Clock) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayUntilNextDueTime(LocalDateTime.now(clock)).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(DAILY)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DAILY, ExistingWorkPolicy.REPLACE, request)
    }

    fun delayUntilNextDueTime(now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(DUE_TIME).plusMinutes(1)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    internal fun isDaily(tags: Set<String>) = DAILY in tags
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.refresh(applicationContext)
        if (WidgetRefresher.isDaily(tags)) {
            WidgetRefresher.scheduleDaily(applicationContext, applicationContext.appContainer.clock)
        }
        return Result.success()
    }
}
