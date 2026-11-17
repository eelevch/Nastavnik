package app.nastavnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun highResTabletResolutionIsScaledOptimally() {
        // Стандартный 2.5K планшет: 2560x1600 @ 280 dpi
        val res = DeviceProfile.optimalTabletResolution(2560, 1600, 280, isWeak = false)
        assertEquals(1500, maxOf(res.width, res.height))
        // Соотношение сторон 16:10 сохраняется
        assertEquals(1500, res.width)
        assertEquals(938, res.height)
        assertTrue("Ширина должна быть чётной", res.width % 2 == 0)
        assertTrue("Высота должна быть чётной", res.height % 2 == 0)
        assertTrue("DPI должен масштабироваться", res.dpi in 160..170)
        assertTrue("Scale меньше 1", res.scale < 1.0f)
    }

    @Test
    fun weakTabletResolutionIsScaledTo1200() {
        val res = DeviceProfile.optimalTabletResolution(2560, 1600, 280, isWeak = true)
        assertEquals(1200, maxOf(res.width, res.height))
        assertEquals(1200, res.width)
        assertEquals(750, res.height)
        assertTrue(res.width % 2 == 0)
        assertTrue(res.height % 2 == 0)
    }

    @Test
    fun standardResolutionIsPreserved() {
        // Full HD планшет 1920x1200 -> чуть выше 1500, масштабируется мягко
        val res = DeviceProfile.optimalTabletResolution(1280, 800, 160, isWeak = false)
        assertEquals(1280, res.width)
        assertEquals(800, res.height)
        assertEquals(160, res.dpi)
        assertEquals(1.0f, res.scale, 1e-4f)
    }

    @Test
    fun cacheMemoryWithinLimits() {
        val bytes = DeviceProfile.maxBackgroundCacheBytes()
        assertTrue(bytes >= 16 * 1024 * 1024)
        assertTrue(bytes <= 48 * 1024 * 1024)
    }
}
