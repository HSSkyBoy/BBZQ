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
        var activeSessionRef: java.lang.ref.WeakReference<Any>? = null
        @Volatile
        var lastValidCoverBitmap: Bitmap? = null
        @Volatile
        var lastValidMetadataObj: Any? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * 创建安全、独立的位图副本，保持 1080px 超清画质与 ARGB_8888 全彩通道。
         * 杜绝二次模糊与色彩阶梯色带，保证控制中心背景丝滑细腻。
         * 并在切集时平滑过渡，杜绝 Bitmap.recycle() 引起的空指针。
         */
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

    /**
     * 1. 拦截 MediaSessionCompat.setMetadata 与 Framework 原生 MediaSession.setMetadata：
     * - 针对换集瞬间：如果新视频封面尚在异步加载中，平滑保留当前有效封面，绝对不让控制中心背景塌陷为纯色；
     * - 当新视频高清封面加载到达后，平滑置换为新封面；
     * - 仅注入一份高质量 ALBUM_ART，杜绝在同一 Parcel 中重复放入多份 Bitmap 导致 Binder 溢出。
     */
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
                activeSessionRef = java.lang.ref.WeakReference(session)
            }

            val arg0 = param.args.getOrNull(0)
            if (arg0 == null) {
                val fallback = lastValidMetadataObj
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

            val coverBitmap = runCatching {
                val getBitmap = metadata.javaClass.getMethod("getBitmap", String::class.java)
                (getBitmap.invoke(metadata, "android.media.metadata.ALBUM_ART")
                    ?: getBitmap.invoke(metadata, "android.media.metadata.ART")) as? Bitmap
            }.getOrNull()

            if (coverBitmap != null && !coverBitmap.isRecycled) {
                // 收到携带封面的完整元数据，直接采用新封面
                currentMediaKey = mediaKey
                val safeCopy = createSafeCopy(coverBitmap, 1080) ?: coverBitmap
                lastValidCoverBitmap = safeCopy
                lastValidMetadataObj = metadata
            } else {
                // 收到暂无封面的过渡元数据（如切集瞬间），复用当前有效封面进行平滑过渡，杜绝纯色卡片塌陷
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
                            lastValidMetadataObj = newMetadata
                        }
                    }
                } else {
                    lastValidMetadataObj = metadata
                }
            }
        }
        count++

        // 双保险：Framework 原生 MediaSession.setMetadata
        runCatching {
            val fwkSessionClass = android.media.session.MediaSession::class.java
            val fwkMetadataClass = android.media.MediaMetadata::class.java
            val fwkSetMetadata = fwkSessionClass.declaredMethods.firstOrNull {
                it.name == "setMetadata" && it.parameterCount == 1 && it.parameterTypes[0] == fwkMetadataClass
            }
            if (fwkSetMetadata != null) {
                env.hookBefore(fwkSetMetadata) { param ->
                    if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookBefore
                    val fwkSession = param.thisObject
                    if (fwkSession != null && activeSessionRef?.get() == null) {
                        activeSessionRef = java.lang.ref.WeakReference(fwkSession)
                    }
                    val meta = param.args.getOrNull(0) as? android.media.MediaMetadata ?: return@hookBefore
                    val hasArt = meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
                            meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) != null
                    if (!hasArt) {
                        val cached = lastValidCoverBitmap
                        if (cached != null && !cached.isRecycled) {
                            val builder = android.media.MediaMetadata.Builder(meta)
                            builder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, cached)
                            param.args[0] = builder.build()
                        }
                    }
                }
                count++
            }
        }

        return count
    }

    /**
     * 2. 拦截 B 站官方封面加载完成回调与大图缓存入口：
     * 精准拦截具体实现类（Su0.a$a 与 a$b）的 onFetched(url, bigBitmap, iconBitmap)，
     * 以及 MusicCoverImageCache 的 putLocalBigImage，一旦高清封面就位，立即平滑推入 MediaSession。
     */
    private fun installCoverImageFetchHook(): Int {
        var count = 0
        val builderClass = classLoader.findClassOrNull("android.support.v4.media.MediaMetadataCompat\$Builder")
            ?: classLoader.findClassOrNull("androidx.media.MediaMetadataCompat\$Builder")

        fun dispatchCoverUpdate(bitmap: Bitmap) {
            if (bitmap.isRecycled) return
            val safeCopy = createSafeCopy(bitmap, 1080) ?: return
            lastValidCoverBitmap = safeCopy

            mainHandler.post {
                runCatching {
                    if (safeCopy.isRecycled) return@runCatching
                    val session = activeSessionRef?.get()
                    val meta = lastValidMetadataObj
                    if (session != null && meta != null && builderClass != null) {
                        val constructor = builderClass.getConstructor(meta.javaClass)
                        val builder = constructor.newInstance(meta)
                        val putBitmap = builderClass.getMethod("putBitmap", String::class.java, Bitmap::class.java)
                        val build = builderClass.getMethod("build")
                        putBitmap.invoke(builder, "android.media.metadata.ALBUM_ART", safeCopy)
                        val newMeta = build.invoke(builder)
                        if (newMeta != null) {
                            lastValidMetadataObj = newMeta
                            val setMetadataMethod = session.javaClass.methods.firstOrNull {
                                it.name == "setMetadata" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(newMeta)
                            } ?: session.javaClass.methods.firstOrNull {
                                it.name == "setMetadata" && it.parameterCount == 1
                            }
                            setMetadataMethod?.invoke(session, newMeta)
                        }
                    }
                }
            }
        }

        // 1) 拦截封面具体回调类中的 onFetched(String, Bitmap, Bitmap)
        val listenerImplClasses = listOfNotNull(
            classLoader.findClassOrNull("Su0.a\$a"),
            classLoader.findClassOrNull("tv.danmaku.bili.ui.player.notification.a\$b"),
            classLoader.findClassOrNull("lI.a"),
            classLoader.findClassOrNull("lI.c")
        )

        for (clazz in listenerImplClasses) {
            for (method in clazz.declaredMethods) {
                if (method.name == "onFetched") {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isFixMediaSessionCardEnabled(prefs)) return@hookAfter
                        val bitmap = (param.args.getOrNull(1) as? Bitmap)
                            ?: (param.args.getOrNull(2) as? Bitmap)
                            ?: return@hookAfter
                        dispatchCoverUpdate(bitmap)
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
                        val bitmap = (param.args.getOrNull(1) as? Bitmap)
                            ?: (param.args.getOrNull(0) as? Bitmap)
                            ?: return@hookAfter
                        dispatchCoverUpdate(bitmap)
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
                    val fwkSession = param.thisObject
                    if (fwkSession != null && activeSessionRef?.get() == null) {
                        activeSessionRef = java.lang.ref.WeakReference(fwkSession)
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


