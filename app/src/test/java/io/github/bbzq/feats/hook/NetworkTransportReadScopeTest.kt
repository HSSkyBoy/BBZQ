package io.github.bbzq.feats.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkTransportReadScopeTest {
    @Test fun scopeIsRestoredAfterRead() {
        assertFalse(NetworkTransportReadScope.isActive)
        NetworkTransportReadScope.read { assertTrue(NetworkTransportReadScope.isActive) }
        assertFalse(NetworkTransportReadScope.isActive)
    }

    @Test fun nestedFailureDoesNotLeakOrClearTheOuterScope() {
        NetworkTransportReadScope.read {
            runCatching { NetworkTransportReadScope.read<Unit> { error("test") } }
            assertTrue(NetworkTransportReadScope.isActive)
        }
        assertFalse(NetworkTransportReadScope.isActive)
    }

    @Test fun anotherThreadStillSeesNormalWifiHookBehaviour() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            NetworkTransportReadScope.read {
                assertTrue(NetworkTransportReadScope.isActive)
                assertFalse(executor.submit<Boolean> { NetworkTransportReadScope.isActive }.get(2, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
