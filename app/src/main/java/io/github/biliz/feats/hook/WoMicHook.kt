package io.github.biliz.feats.hook

import android.content.Context
import android.view.View
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.findClassOrNull
import io.github.biliz.feats.getObjectField
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookAfterAllConstructors
import io.github.biliz.feats.hookBeforeAllMethods
import io.github.biliz.feats.methodOrNull
import io.github.biliz.feats.setBooleanField
import io.github.biliz.feats.setIntField
import io.github.biliz.feats.setObjectField

class WoMicHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) {
            log("WoMicHook: skip non-main process (${env.processName})")
            return
        }
        log("WoMicHook: starting for ${env.packageName}...")

        var count = 0
        count += hookSubscription()  // 伪造订阅付费状态
        count += hookVolumeSeekbar()  // 拖拽音量条时强制解锁高级音量
        count += hookAds()  // 拦截AdMob广告加载展示

        log("WoMicHook: installed $count hook(s)")
    }

    // 订阅伪造
    private fun hookSubscription(): Int {
        var count = 0
        val cls = classLoader.findClassOrNull(SUBSCRIPTION_CLASS) ?: return logSkip("O2.a missing")

        // 实例构造时直接覆写付费字段
        count += env.hookAfterAllConstructors(cls) { param ->
            patchSubscriptionFields(param.thisObject)
            logOnce("sub_ctor", "purchaseState → $SUBSCRIBED")
        }

        // 拦截静态获取单例方法，每次取出实例强制重写订阅字段
        val getter = cls.methodOrNull("a", Context::class.java)
        getter?.let {
            env.hookAfter(it) { param ->
                patchSubscriptionFields(param.result)
            }
            count++
            logOnce("sub_getter", "Singleton getter patched")
        } ?: log("sub_getter: static a(Context) not found")

        log("Subscription: $count hook(s)")
        return count
    }

    // 统一修改订阅管理类付费相关字段
    private fun patchSubscriptionFields(instance: Any?) {
        instance ?: return
        instance.setIntField(PURCHASE_STATE_FIELD, SUBSCRIBED)
        instance.setObjectField(PRODUCT_FIELD, FAKE_PRODUCT_ID)
    }

    // 拖拽全程（含实时 onProgressChanged）强制解锁付费标记，避免拖动中途被实时校验重置回默认音量
    private fun hookVolumeSeekbar(): Int {
        val listenerCls = classLoader.findClassOrNull(SEEKBAR_LISTENER)
            ?: return logSkip("Z2.d missing")

        // 只按名字 hook Z2.d 自身声明的 SeekBar 回调（onProgressChanged 是写入音量处，受 MainFragment.r0 门控）。
        // 绝不能传 methodName=null：allMethods() 会向上遍历到 java.lang.Object，null 时不过滤名字，
        // 会把继承的 hashCode/equals/toString 也一并 hook，等同对全 App 对象做全局 hook，拖垮宿主导致页面无法加载。
        var count = 0
        for (methodName in SEEKBAR_CALLBACKS) {
            count += env.hookBeforeAllMethods(listenerCls, methodName) { param ->
                val self = param.thisObject ?: return@hookBeforeAllMethods
                val selector = self.getObjectField("a") as? Int
                // 仅处理音量调节滑块
                if (selector != VOLUME_SELECTOR) return@hookBeforeAllMethods

                val mainFragment = self.getObjectField("b") ?: return@hookBeforeAllMethods
                // 强制开启高级音量权限标记
                mainFragment.setBooleanField("r0", true)
                logOnce("volume", "r0=true on seekbar callback")
            }
        }

        log("Volume seekbar: $count hook(s)")
        return count
    }

    // 广告拦截
    private fun hookAds(): Int {
        val bannerCls = classLoader.findClassOrNull(BANNER_ADVIEW_CLASS)
            ?: return logSkip("$BANNER_ADVIEW_CLASS missing")

        // 拦截混淆后的 loadAd：跳过原方法阻止广告请求，并隐藏空的广告视图
        val count = env.hookBeforeAllMethods(bannerCls, BANNER_LOAD_METHOD) { param ->
            (param.thisObject as? View)?.visibility = View.GONE
            param.result = null
            logOnce("banner", "Blocked banner loadAd, AdView hidden")
        }

        log("Ads: $count hook(s)")
        return count
    }

    private val logged = hashSetOf<String>()
    private fun logOnce(key: String, msg: String) {
        if (logged.add(key)) log(msg)
    }

    private fun logSkip(reason: String): Int {
        log("skip: $reason")
        return 0
    }

    private companion object {
        // O2.a 订阅状态
        private const val SUBSCRIPTION_CLASS = "O2.a"
        private const val PURCHASE_STATE_FIELD = "f"
        private const val PRODUCT_FIELD = "g" // string 商品ID
        private const val SUBSCRIBED = 1
        private const val FAKE_PRODUCT_ID = "biliz_unlocked"
        // Z2.d 音量滑块
        private const val SEEKBAR_LISTENER = "Z2.d"
        private const val VOLUME_SELECTOR = 0
        private val SEEKBAR_CALLBACKS = listOf(
            "onProgressChanged",
            "onStartTrackingTouch",
            "onStopTrackingTouch",
        )
        // l1.C1836f 混淆后的 AdMob 横幅 AdView，loadAd 混淆为 b(l1.C1834d) —— WO Mic 5.3
        private const val BANNER_ADVIEW_CLASS = "l1.C1836f"
        private const val BANNER_LOAD_METHOD = "b"
    }
}