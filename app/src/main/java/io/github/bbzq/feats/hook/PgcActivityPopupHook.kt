package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class PgcActivityPopupHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var filteredLogged = false

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockPgcActivityPopupEnabled(prefs)) {
            log("startHook: PgcActivityPopup disabled, zero hooks installed")
            return
        }

        val model = classLoader.findClassOrNull(MODEL_CLASS)
        val popup = classLoader.findClassOrNull(POPUP_CLASS)
        val descriptor = classLoader.findClassOrNull(DESCRIPTOR_CLASS)
        val property = classLoader.findClassOrNull(PROPERTY_CLASS)
        if (model == null || popup == null || descriptor == null || property == null) {
            log("startHook: PgcActivityPopup OgvActivityVo structure not found")
            return
        }

        val parameters = model.declaredConstructors
            .filter { !it.isSynthetic && it.parameterTypes.count { type -> type == popup } == 1 }
            .singleOrNull()
            ?.parameterTypes
            ?.toList()
        if (parameters == null) {
            log("startHook: PgcActivityPopup constructor not matched")
            return
        }
        val popupIndex = parameters.indexOf(popup)

        val construct = descriptor.declaredMethods.singleOrNull {
            it.name == "constructWith" &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers) &&
                it.returnType == Any::class.java &&
                it.parameterTypes.contentEquals(arrayOf(Array<Any?>::class.java))
        }
        if (construct == null) {
            log("startHook: PgcActivityPopup constructWith not found")
            return
        }

        if (!isNullableHalfContainer(descriptor, property, parameters, popup, popupIndex)) {
            log("startHook: PgcActivityPopup descriptor does not match a nullable half screen slot")
            return
        }

        env.hookBefore(construct) { param ->
            if (!ModuleSettings.isBlockPgcActivityPopupEnabled(prefs)) return@hookBefore
            val values = param.args.firstOrNull() as? Array<*> ?: return@hookBefore
            if (values.size != parameters.size) return@hookBefore
            val current = values[popupIndex] ?: return@hookBefore
            if (!popup.isInstance(current)) return@hookBefore
            val copy = arrayOfNulls<Any?>(values.size)
            values.forEachIndexed { index, value -> copy[index] = value }
            copy[popupIndex] = null
            param.args[0] = copy
            if (!filteredLogged) {
                filteredLogged = true
                log("PgcActivityPopup removed auto half screen popup")
            }
        }
        isInstalled = true
        log("startHook: PgcActivityPopup installed")
    }

    private fun isNullableHalfContainer(
        descriptor: Class<*>,
        property: Class<*>,
        parameters: List<Class<*>>,
        popup: Class<*>,
        popupIndex: Int,
    ): Boolean = runCatching {
        val field = descriptor.declaredFields.singleOrNull {
            Modifier.isStatic(it.modifiers) && it.type.isArray && it.type.componentType == property
        } ?: return@runCatching false
        field.isAccessible = true
        val properties = field.get(null) as? Array<*> ?: return@runCatching false
        if (properties.size != parameters.size) return@runCatching false
        val keys = properties.map { it?.callMethod("getKeyName") as? String }
        if (keys.count { it == HALF_KEY } != 1 || keys[popupIndex] != HALF_KEY) return@runCatching false
        val half = properties[popupIndex] ?: return@runCatching false
        if (half.callMethod("getType") != popup) return@runCatching false
        if (half.callMethod("getNonNull") != false) return@runCatching false
        properties.indices.all { index ->
            val type = properties[index]?.callMethod("getType") as? Type ?: return@all false
            val raw = if (type is ParameterizedType) type.rawType else type
            raw == parameters[index]
        }
    }.getOrDefault(false)

    private companion object {
        const val MODEL_CLASS = "com.bilibili.ship.theseus.ogv.activity.OgvActivityVo"
        const val POPUP_CLASS = "com.bilibili.ship.theseus.ogv.activity.OgvActivityHalfScreenPopup"
        const val DESCRIPTOR_CLASS = "${MODEL_CLASS}_JsonDescriptor"
        const val PROPERTY_CLASS = "com.bilibili.bson.common.PojoPropertyDescriptor"
        const val HALF_KEY = "play_half_container"
    }
}
