package io.github.bbzq.feats

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.symbol.BiliSymbolResolver
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object SymbolScanRefreshRequestHandler {
    private val installedProcesses = ConcurrentHashMap.newKeySet<String>()
    private val running = AtomicBoolean(false)
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "BBZQ-RefreshWatch").apply { isDaemon = true }
    }

    fun install(
        env: RoamingEnv,
        xposed: XposedInterface,
        classLoader: ClassLoader,
    ) {
        val processKey = "${env.packageName}/${env.processName}"
        if (!installedProcesses.add(processKey)) return

        val remotePrefs = runCatching {
            xposed.getRemotePreferences(ModuleSettings.PREFS_NAME)
        }.getOrElse {
            env.log("Symbol scan refresh request listener unavailable", it)
            return
        }
        val appContext = env.hostContext.applicationContext ?: env.hostContext
        val handledPrefs = appContext.getSharedPreferences(BiliSymbolResolver.CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val lastHandled = AtomicReference(
            runCatching {
                handledPrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_HANDLED_ID, null)
            }.getOrNull(),
        )

        fun handlePendingRequest(trigger: String) {
            val requestId = runCatching {
                remotePrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID, null)
            }.getOrElse {
                env.log("Symbol scan refresh request read failed trigger=$trigger", it)
                return
            }
                ?.takeIf { it.isNotBlank() }
                ?: return
            if (requestId == lastHandled.get()) return
            if (!running.compareAndSet(false, true)) {
                env.log("Symbol scan refresh request ignored while scan is running trigger=$trigger")
                return
            }
            lastHandled.set(requestId)
            thread(name = "BBZQ-SymbolRefresh", isDaemon = true) {
                try {
                    env.log("Symbol scan refresh request accepted id=$requestId trigger=$trigger")
                    val markerPersisted = markRequestHandled(env, handledPrefs, requestId)
                    env.symbols = BiliSymbolResolver.forceRefresh(
                        hostContext = env.hostContext,
                        classLoader = classLoader,
                        log = env::log,
                    )
                    env.log("Symbol scan refresh request completed id=$requestId")
                    if (markerPersisted) {
                        restartHostApp(env, requestId)
                    } else {
                        env.log("Symbol scan refresh restart skipped id=$requestId: handled marker not persisted")
                    }
                } catch (throwable: Throwable) {
                    env.log("Symbol scan refresh request failed id=$requestId", throwable)
                } finally {
                    running.set(false)
                }
            }
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID) {
                watchExecutor.execute { handlePendingRequest("remote-pref") }
            }
        }
        synchronized(listeners) {
            listeners += listener
        }
        remotePrefs.registerOnSharedPreferenceChangeListener(listener)
        watchExecutor.execute { handlePendingRequest("install") }
        installForegroundPolling(appContext, ::handlePendingRequest)
        env.log("Symbol scan refresh request listener installed")
    }

    private fun installForegroundPolling(
        appContext: Context,
        handlePendingRequest: (String) -> Unit,
    ) {
        val application = appContext as? Application
        if (application == null) {
            watchExecutor.scheduleWithFixedDelay(
                { runCatching { handlePendingRequest("poll-fallback") } },
                FOREGROUND_POLL_MS,
                FOREGROUND_POLL_MS,
                TimeUnit.MILLISECONDS,
            )
            return
        }

        val startedActivities = AtomicInteger(0)
        val pollLock = Any()
        var pollFuture: ScheduledFuture<*>? = null

        fun startPollingIfNeeded(trigger: String) {
            synchronized(pollLock) {
                if (pollFuture == null || pollFuture?.isCancelled == true) {
                    pollFuture = watchExecutor.scheduleWithFixedDelay(
                        { runCatching { handlePendingRequest("poll") } },
                        FOREGROUND_POLL_MS,
                        FOREGROUND_POLL_MS,
                        TimeUnit.MILLISECONDS,
                    )
                }
            }
            watchExecutor.execute { handlePendingRequest(trigger) }
        }

        fun stopPolling() {
            synchronized(pollLock) {
                pollFuture?.cancel(false)
                pollFuture = null
            }
        }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities.incrementAndGet() == 1) {
                    startPollingIfNeeded("foreground-start")
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (startedActivities.get() <= 0) {
                    startedActivities.set(1)
                }
                startPollingIfNeeded("foreground-resume")
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
                if (remaining == 0) {
                    stopPolling()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun markRequestHandled(
        env: RoamingEnv,
        prefs: SharedPreferences,
        requestId: String,
    ): Boolean {
        return runCatching {
            prefs.edit()
                .putString(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_HANDLED_ID, requestId)
                .commit()
        }.getOrElse {
            env.log("Symbol scan refresh handled marker write failed id=$requestId", it)
            false
        }
    }

    private fun restartHostApp(env: RoamingEnv, requestId: String) {
        mainHandler.postDelayed(
            {
                try {
                    val restartIntent = env.hostContext.packageManager
                        .getLaunchIntentForPackage(env.packageName)
                        ?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    if (restartIntent == null) {
                        env.log("Symbol scan refresh restart skipped id=$requestId: launch intent unavailable")
                        return@postDelayed
                    }
                    env.log("Symbol scan refresh restarting host id=$requestId")
                    env.hostContext.startActivity(restartIntent)
                } catch (throwable: Throwable) {
                    env.log("Symbol scan refresh restart launch failed id=$requestId", throwable)
                    return@postDelayed
                }

                try {
                    android.os.Process.killProcess(android.os.Process.myPid())
                } catch (throwable: Throwable) {
                    env.log("Symbol scan refresh kill process failed id=$requestId", throwable)
                }
                try {
                    exitProcess(0)
                } catch (throwable: Throwable) {
                    env.log("Symbol scan refresh exit process failed id=$requestId", throwable)
                }
            },
            HOST_RESTART_DELAY_MS,
        )
    }

    private const val HOST_RESTART_DELAY_MS = 1_000L
    private const val FOREGROUND_POLL_MS = 10_000L
}
