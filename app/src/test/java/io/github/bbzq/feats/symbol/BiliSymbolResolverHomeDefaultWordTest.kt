package io.github.bbzq.feats.symbol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliSymbolResolverHomeDefaultWordTest {

    private interface RelocatedDefaultWord {
        fun getDefaultWordGoto(): String
        fun getDefaultWordType(): Int
        fun getShowFront(): Int
        fun getFlashScreenQueryId(): Long
        fun getWord(): String
    }

    private interface PartialDefaultWord {
        fun getDefaultWordGoto(): String
        fun getWord(): String
    }

    private abstract class DefaultWordImplementation : RelocatedDefaultWord

    @Test
    fun acceptsInterfaceWithDefaultWordAccessors() {
        assertTrue(BiliSymbolResolver.isHomeDefaultSearchWordType(RelocatedDefaultWord::class.java))
    }

    @Test
    fun rejectsPartialInterfacesAndConcreteTypes() {
        assertFalse(BiliSymbolResolver.isHomeDefaultSearchWordType(PartialDefaultWord::class.java))
        assertFalse(BiliSymbolResolver.isHomeDefaultSearchWordType(DefaultWordImplementation::class.java))
        assertFalse(BiliSymbolResolver.isHomeDefaultSearchWordType(String::class.java))
    }
}
