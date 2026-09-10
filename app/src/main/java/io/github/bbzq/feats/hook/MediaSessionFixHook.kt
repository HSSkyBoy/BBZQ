package io.github.bbzq.feats.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier

class MediaSessionFixHook(env: RoamingEnv) : BaseRoamingHook(env) {

    private var currentMediaKey: String? = null

    companion object {
        var activeCompatSessionRef: java.lang.ref.WeakReference<Any>? = null
        var activeFwkSessionRef: java.lang.ref.WeakReference<android.media.session.MediaSession>? = null
        @Volatile
        var lastValidCoverBitmap: Bitmap? = null
        @Volatile
        var lastCompatMetadataObj: Any? = null
        @Volatile
        var lastFwkMetadataObj: android.media.MediaMetadata? = null

        @Volatile
        var currentActiveMediaKey: String? = null
        @Volatile
        var currentActiveArtUri: String? = null
        @Volatile
        var currentCoverMediaKey: String? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private val coverFetchExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)

        fun createSafeCopy(src: Bitmap, maxDim: Int = 1080): Bitmap? {
            return runCatching {
                if (src.isRecycled) return null
                val w = src.width.coerceAtLeast(1)
                val h = src.height.coerceAtLeast(1)
                val scale = if (w > maxDim || h > maxDim) {
                    maxDim.toFloat() / maxOf(w, h)
                } else {
                    1f
                }
                val dstW = (w * scale).toInt().coerceAtLeast(1)
                val dstH = (h * scale).toInt().coerceAtLeast(1)
                val copy = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(copy)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                val srcRect = Rect(0, 0, w, h)
                val dstRect = Rect(0, 0, dstW, dstH)
                canvas.drawBitmap(src, srcRect, dstRect, paint)
                copy
            }.getOrNull()
        }

        fun updateCoverToSessions(
            targetMediaKey: String?,
            bitmap: Bitmap,
            compatBuilderClass: Class<*>?,
        ) {
            if (bitmap.isRecycled) return
            val activeKey = currentActiveMediaKey
            if (targetMediaKey != null && activeKey != null && targetMediaKey != activeKey) {
                return
            }

            val safeCopy = createSafeCopy(bitmap, 1080) ?: return
            lastValidCoverBitmap = safeCopy
            currentCoverMediaKey = targetMediaKey ?: activeKey

            mainHandler.post {
                runCatching {
                    val currentKey = currentActiveMediaKey
                    if (targetMediaKey != null && currentKey != null && targetMediaKey != currentKey) {
                        return@runCatching
                    }
                    if (safeCopy.isRecycled) return@runCatching

                    val compatSession = activeCompatSessionRef?.get()
                    val compatMeta = lastCompatMetadataObj
                    if (compatSession != null && compatMeta != null && compatBuilderClass != null) {
                        runCatching {
                            val constructor = compatBuilderClass.getConstructor(compatMeta.javaClass)
                            val builder = constructor.newInstance(compatMeta)
                            val putBitmap = compatBuilderClass.getMethod("putBitmap", String::class.java, Bitmap::class.java)
                            val build = compatBuilderClass.getMethod("build")
                            putBitmap.invoke(builder, "android.media.metadata.ALBUM_ART", safeCopy)
                            val newMeta = build.invoke(builder)
                            if (newMeta != null) {
                                lastCompatMetadataObj = newMeta
                                val setMetadataMethod = compatSession.javaClass.methods.firstOrNull {
                                    it.name == "setMetadata" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(newMeta)
                                } ?: compatSession.javaClass.methods.firstOrNull {
                                    it.name == "setMetadata" && it.parameterCount == 1
                                }
                                setMetadataMethod?.invoke(compatSession, newMeta)
                            }
                        }
                    }

                    val fwkSession = activeFwkSessionRef?.get()
                    val fwkMeta = lastFwkMetadataObj
                    if (fwkSession != null && fwkMeta != null) {
                        runCatching {
                            val builder = android.media.MediaMetadata.Builder(fwkMeta)
                            builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, safeCopy)
                            val newFwkMeta = builder.build()
                            lastFwkMetadataObj = newFwkMeta
                            fwkSession.setMetadata(newFwkMeta)
                        }
                    }
                }
            }
        }

        fun fetchCoverAsync(
            targetMediaKey: String,
            uriString: String?,
            classLoader: ClassLoader,
            compatBuilderClass: Class<*>?,
        ) {
            if (uriString.isNullOrBlank()) return

            coverFetchExecutor.execute {
                runCatching {
                    runCatching {
                        val cacheClass = classLoader.findClassOrNull("tv.danmaku.bili.ui.player.notification.MusicCoverImageCache")
                        val getInstanceMethod = cacheClass?.getMethod("getInstance")
                        val cacheInstance = getInstanceMethod?.invoke(null)
                        if (cacheInstance != null) {
                            val getBigImage = cacheClass.getMethod("getBigImage", String::class.java)
                            val cachedBmp = getBigImage.invoke(cacheInstance, uriString) as? Bitmap
                            if (cachedBmp != null && !cachedBmp.isRecycled) {
                                updateCoverToSessions(targetMediaKey, cachedBmp, compatBuilderClass)
                                return@execute
                            }
                        }
                    }

                    if (currentActiveMediaKey != targetMediaKey) return@runCatching

                    val url = java.net.URL(uriString)
                    val conn = url.openConnection() as? java.net.HttpURLConnection ?: return@runCatching
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("User-Agent", "BiliApp/7.44.0")
                    conn.setRequestProperty("Referer", "https://www.bilibili.com")
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                updateCoverToSessions(targetMediaKey, bmp, compatBuilderClass)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installed = 0
        installed += installMediaSessionMetadataHook()
        installed += installCoverImageFetchHook()
        installed += installMediaSessionPlaybackStateHook()

        if (installed > 0) {
            isInstalled = true
            log("startHook: MediaSessionFixHook installed=$installed, initialEnabled=${ModuleSettings.isFixMediaSessionCardEnabled(prefs)}")
        } else {
            log("startHook: MediaSessionFixHook no hook point found")
        }
    }

    private fun installMediaSessionMetadataHook(): Int {
        var count = 0
        val sessionClass = classLoader.findClassOrNull("android.support.v4.media.session.MediaSessionCompat")
            ?: classLoader.findClassOrNull("androidx.media.MediaSessionCompat")
            ?: return 0
        val metadataClass = classLoader.findClassOrNull("android.support.v4.media.MediaMetadataCompat")
            ?: classLoader.findClassOrNull("androidx.media.MediaMetadataCompat")
            ?: return 0

        val builderClass = classLoader.findClassOrNull("android.support.v4.media.MediaMetadataCompat\$Builder")
            ?: classLoader.findClassOrNull("androidx.media.MediaMetadataCompat\$Builder")

        val setMetadataMethod = sessionClass.declaredMethods.firstOrNull {
            it.name == "setMetadata" && it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(metadataClass)
        } ?: return 0

        env.hookBefore(setMetadataMethod) { param ->
            if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
            val session = param.thisObject
            if (session != null) {
                activeCompatSessionRef = java.lang.ref.WeakReference(session)
            }

            val arg0 = param.args.getOrNull(0)
            if (arg0 == null) {
                val fallback = lastCompatMetadataObj
                if (fallback != null) {
                    param.args[0] = fallback
                    log("MediaSessionCompat.setMetadata(null) intercepted: retained previous metadata")
                }
                return@hookBefore
            }
            val metadata = arg0

            val mediaKey = runCatching {
                val getString = metadata.javaClass.getMethod("getString", String::class.java)
                (getString.invoke(metadata, "android.media.metadata.MEDIA_ID")
                    ?: getString.invoke(metadata, "android.media.metadata.TITLE")) as? String
            }.getOrNull()

            val artUri = runCatching {
                val getString = metadata.javaClass.getMethod("getString", String::class.java)
                (getString.invoke(metadata, "android.media.metadata.ALBUM_ART_URI")
                    ?: getString.invoke(metadata, "android.media.metadata.DISPLAY_ICON_URI")) as? String
            }.getOrNull()

            val isNewMedia = mediaKey != null && mediaKey != currentActiveMediaKey
            if (isNewMedia) {
                currentActiveMediaKey = mediaKey
                currentActiveArtUri = artUri
            }

            val coverBitmap = runCatching {
                val getBitmap = metadata.javaClass.getMethod("getBitmap", String::class.java)
                (getBitmap.invoke(metadata, "android.media.metadata.ALBUM_ART")
                    ?: getBitmap.invoke(metadata, "android.media.metadata.ART")) as? Bitmap
            }.getOrNull()

            if (coverBitmap != null && !coverBitmap.isRecycled) {
                currentCoverMediaKey = mediaKey
                val safeCopy = createSafeCopy(coverBitmap, 1080) ?: coverBitmap
                lastValidCoverBitmap = safeCopy
                lastCompatMetadataObj = metadata
            } else {
                val cached = lastValidCoverBitmap
                if (cached != null && !cached.isRecycled && builderClass != null) {
                    runCatching {
                        val constructor = builderClass.getConstructor(metadata.javaClass)
                        val builder = constructor.newInstance(metadata)
                        val putBitmap = builderClass.getMethod("putBitmap", String::class.java, Bitmap::class.java)
                        val build = builderClass.getMethod("build")
                        putBitmap.invoke(builder, "android.media.metadata.ALBUM_ART", cached)
                        val newMetadata = build.invoke(builder)
                        if (newMetadata != null) {
                            param.args[0] = newMetadata
                            lastCompatMetadataObj = newMetadata
                        }
                    }
                } else {
                    lastCompatMetadataObj = metadata
                }

                if (isNewMedia && !artUri.isNullOrBlank()) {
                    fetchCoverAsync(mediaKey, artUri, classLoader, builderClass)
                }
            }
        }
        count++

        runCatching {
            val fwkSessionClass = android.media.session.MediaSession::class.java
            val fwkMetadataClass = android.media.MediaMetadata::class.java
            val fwkSetMetadata = fwkSessionClass.declaredMethods.firstOrNull {
                it.name == "setMetadata" && it.parameterCount == 1 && it.parameterTypes[0] == fwkMetadataClass
            }
            if (fwkSetMetadata != null) {
                env.hookBefore(fwkSetMetadata) { param ->
                    if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
                    val fwkSession = param.thisObject as? android.media.session.MediaSession
                    if (fwkSession != null) {
                        activeFwkSessionRef = java.lang.ref.WeakReference(fwkSession)
                    }
                    val meta = param.args.getOrNull(0) as? android.media.MediaMetadata ?: return@hookBefore
                    lastFwkMetadataObj = meta

                    val hasArt = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                            meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) != null
                    if (!hasArt) {
                        val cached = lastValidCoverBitmap
                        if (cached != null && !cached.isRecycled) {
                            val builder = android.media.MediaMetadata.Builder(meta)
                            builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, cached)
                            val newFwkMeta = builder.build()
                            param.args[0] = newFwkMeta
                            lastFwkMetadataObj = newFwkMeta
                        }
                    }
                }
                count++
            }
        }

        return count
    }

    private fun installCoverImageFetchHook(): Int {
        var count = 0
        val builderClass = classLoader.findClassOrNull("android.support.v4.media.MediaMetadataCompat\$Builder")
            ?: classLoader.findClassOrNull("androidx.media.MediaMetadataCompat\$Builder")

        // 1) 拦截封面具体回调类中的 onFetched(String, Bitmap, Bitmap)
        val listenerImplClasses = listOfNotNull(
            classLoader.findClassOrNull("Su0.a\$a"),
            classLoader.findClassOrNull("tv.danmaku.bili.ui.player.notification.a\$b"),
            classLoader.findClassOrNull("lI.a"),
            classLoader.findClassOrNull("lI.c"),
        )

        for (clazz in listenerImplClasses) {
            for (method in clazz.declaredMethods) {
                if (method.name == "onFetched") {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookAfter
                        val url = param.args.getOrNull(0) as? String
                        val bitmap = (param.args.getOrNull(1) as? Bitmap)
                            ?: (param.args.getOrNull(2) as? Bitmap)
                            ?: return@hookAfter
                        val activeUri = currentActiveArtUri
                        if (url.isNullOrBlank() || activeUri.isNullOrBlank() || url == activeUri || activeUri.contains(url) || url.contains(activeUri)) {
                            updateCoverToSessions(currentActiveMediaKey, bitmap, builderClass)
                        }
                    }
                    count++
                }
            }
        }

        // 2) 拦截 MusicCoverImageCache 缓存写入入口（putLocalBigImage）
        val cacheClass = classLoader.findClassOrNull("tv.danmaku.bili.ui.player.notification.MusicCoverImageCache")
        if (cacheClass != null) {
            for (method in cacheClass.declaredMethods) {
                if (method.name == "putLocalBigImage") {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookAfter
                        val url = param.args.getOrNull(0) as? String
                        val bitmap = (param.args.getOrNull(1) as? Bitmap)
                            ?: (param.args.getOrNull(0) as? Bitmap)
                            ?: return@hookAfter
                        val activeUri = currentActiveArtUri
                        if (url.isNullOrBlank() || activeUri.isNullOrBlank() || url == activeUri || activeUri.contains(url) || url.contains(activeUri)) {
                            updateCoverToSessions(currentActiveMediaKey, bitmap, builderClass)
                        }
                    }
                    count++
                }
            }
        }

        return count
    }

    private fun is15sAction(action: String?, name: String?): Boolean {
        val act = action.orEmpty()
        val nm = name.orEmpty()
        return act.contains("rewind", ignoreCase = true) ||
                act.contains("forward", ignoreCase = true) ||
                nm.contains("15") ||
                nm.contains("快退") ||
                nm.contains("快进")
    }

    /**
     * 3. 动作规范化：
     * - 在 PlaybackStateCompat.Builder 层拦截并剔除 15s 侧边自定义动作（addCustomAction），并确保上一首/下一首动作就位；
     * - 在 Framework 原生 MediaSession 层零反射兜底过滤，确保无论是系统控制中心卡片还是通知栏抽屉，15s 均被彻底净化。
     */
    private fun installMediaSessionPlaybackStateHook(): Int {
        var count = 0
        val actionRewind = 8L
        val actionSkipToPrevious = 16L
        val actionSkipToNext = 32L
        val actionFastForward = 64L

        // A. 拦截 PlaybackStateCompat.Builder 源头：addCustomAction 与 setActions
        runCatching {
            val builderClass = classLoader.findClassOrNull("android.support.v4.media.session.PlaybackStateCompat\$Builder")
                ?: classLoader.findClassOrNull("androidx.media.PlaybackStateCompat\$Builder")
            if (builderClass != null) {
                for (method in builderClass.declaredMethods) {
                    if (method.name == "addCustomAction") {
                        // 拦截 addCustomAction：直接丢弃 15s 快退/快进，不加入构建列表
                        env.hookBefore(method) { param ->
                            if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
                            val is15s = runCatching {
                                when (param.args.size) {
                                    1 -> {
                                        val ca = param.args[0] ?: return@runCatching false
                                        val act = ca.javaClass.methods.firstOrNull { it.name == "getAction" && it.parameterCount == 0 }?.invoke(ca)?.toString()
                                        val name = ca.javaClass.methods.firstOrNull { it.name == "getName" && it.parameterCount == 0 }?.invoke(ca)?.toString()
                                        is15sAction(act, name)
                                    }
                                    3 -> {
                                        val act = param.args[0]?.toString()
                                        val name = param.args[1]?.toString()
                                        is15sAction(act, name)
                                    }
                                    else -> false
                                }
                            }.getOrDefault(false)

                            if (is15s) {
                                // Builder 方法链式返回 this，拦截后直接返回自身，杜绝 15s 动作被添加
                                param.result = param.thisObject
                            }
                        }
                        count++
                    } else if (method.name == "setActions" && method.parameterCount == 1 && method.parameterTypes[0] == Long::class.javaPrimitiveType) {
                        // 拦截 setActions：清除 15s 位，注入上一首/下一首位
                        env.hookBefore(method) { param ->
                            if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
                            val orig = param.args[0] as? Long ?: return@hookBefore
                            val newActions = (orig and (actionRewind or actionFastForward).inv()) or
                                    (actionSkipToPrevious or actionSkipToNext)
                            param.args[0] = newActions
                        }
                        count++
                    }
                }
            }
        }

        // B. Framework 原生 MediaSession 层双保险过滤
        runCatching {
            val fwkSessionClass = android.media.session.MediaSession::class.java
            val fwkStateClass = android.media.session.PlaybackState::class.java
            val fwkSetPlaybackState = fwkSessionClass.declaredMethods.firstOrNull {
                it.name == "setPlaybackState" && it.parameterCount == 1 && it.parameterTypes[0] == fwkStateClass
            }
            if (fwkSetPlaybackState != null) {
                env.hookBefore(fwkSetPlaybackState) { param ->
                    if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
                    val fwkSession = param.thisObject as? android.media.session.MediaSession
                    if (fwkSession != null && activeFwkSessionRef?.get() == null) {
                        activeFwkSessionRef = java.lang.ref.WeakReference(fwkSession)
                    }
                    val stateObj = param.args.getOrNull(0) as? android.media.session.PlaybackState ?: return@hookBefore

                    val origActions = stateObj.actions
                    val has15sAction = (origActions and (actionRewind or actionFastForward)) != 0L
                    val has15sCustom = stateObj.customActions?.any { ca ->
                        ca != null && is15sAction(ca.action, ca.name?.toString())
                    } == true
                    val lacksSkip = (origActions and (actionSkipToPrevious or actionSkipToNext)) != (actionSkipToPrevious or actionSkipToNext)

                    // 如果既没有 15s 动作，也没有 15s 侧边动作，且已具备切集动作，直接放行
                    if (!has15sAction && !has15sCustom && !lacksSkip) {
                        return@hookBefore
                    }

                    runCatching {
                        val newActions = (origActions and (actionRewind or actionFastForward).inv()) or
                                (actionSkipToPrevious or actionSkipToNext)

                        val builder = android.media.session.PlaybackState.Builder()
                        builder.setState(stateObj.state, stateObj.position, stateObj.playbackSpeed, stateObj.lastPositionUpdateTime)
                        builder.setActions(newActions)
                        builder.setBufferedPosition(stateObj.bufferedPosition)
                        builder.setActiveQueueItemId(stateObj.activeQueueItemId)
                        stateObj.extras?.let { builder.setExtras(it) }
                        stateObj.errorMessage?.let { builder.setErrorMessage(it) }

                        stateObj.customActions?.forEach { ca ->
                            if (ca != null && !is15sAction(ca.action, ca.name?.toString())) {
                                builder.addCustomAction(ca)
                            }
                        }

                        param.args[0] = builder.build()
                    }
                }
                count++
            }
        }

        return count
    }
}


