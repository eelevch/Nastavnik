package app.nastavnik.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndMascotTest {

    @Test
    fun `tablet tab bar defaults to LEFT and supports side and bottom positions`() {
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet(null))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("unknown"))
        assertEquals(TabBarPosition.BOTTOM, TabBarPosition.forTablet("BOTTOM"))
        assertEquals(TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.forTablet("BOTTOM_CAPSULE"))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("LEFT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("RIGHT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("right"))
        assertEquals(TabBarPosition.BOTH, TabBarPosition.forTablet("BOTH"))
        assertEquals(TabBarPosition.BOTH, TabBarPosition.forTablet("both"))
        assertEquals(
            listOf(TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.LEFT, TabBarPosition.RIGHT, TabBarPosition.BOTH),
            TabBarPosition.TABLET_OPTIONS,
        )
        assertTrue(TabBarPosition.LEFT.isSide())
        assertTrue(TabBarPosition.RIGHT.isSide())
        assertTrue(TabBarPosition.BOTH.isSide())
        assertTrue(TabBarPosition.BOTH.hasLeftEdge())
        assertTrue(TabBarPosition.BOTH.hasRightEdge())
        assertTrue(TabBarPosition.LEFT.hasLeftEdge())
        assertFalse(TabBarPosition.LEFT.hasRightEdge())
        assertTrue(TabBarPosition.RIGHT.hasRightEdge())
        assertFalse(TabBarPosition.RIGHT.hasLeftEdge())
        assertFalse(TabBarPosition.BOTTOM.isSide())
        assertFalse(TabBarPosition.BOTTOM_CAPSULE.isSide())
    }

    @Test
    fun `phone tab bar parses all five variants with safe fallback`() {
        assertEquals(TabBarPosition.BOTTOM, TabBarPosition.forPhone(null))
        assertEquals(TabBarPosition.BOTTOM, TabBarPosition.forPhone("unknown"))
        assertEquals(TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.forPhone("BOTTOM_CAPSULE"))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forPhone("LEFT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forPhone("RIGHT"))
        assertEquals(TabBarPosition.BOTH, TabBarPosition.forPhone("BOTH"))
        assertEquals(
            listOf(TabBarPosition.BOTTOM, TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.LEFT, TabBarPosition.RIGHT, TabBarPosition.BOTH),
            TabBarPosition.PHONE_OPTIONS,
        )
    }

    @Test
    fun `mascot color presets parse safely and theme resolves distinct stops`() {
        assertEquals(MascotColor.SUNSET, MascotColor.fromId(null))
        assertEquals(MascotColor.SUNSET, MascotColor.fromId(""))
        assertEquals(MascotColor.SUNSET, MascotColor.fromId("unknown"))

        assertEquals(8, MascotColor.entries.size)
        assertEquals(8, MascotColor.entries.map { it.id }.toSet().size)
        assertEquals(8, MascotColor.entries.map { it.title }.toSet().size)

        MascotColor.entries.forEach { preset ->
            assertEquals(preset, MascotColor.fromId(preset.id))
            assertEquals(preset, MascotColor.fromId(preset.id.lowercase()))
        }

        val themeStops = MascotColor.THEME.resolveStops(0xFF0E9A73.toInt())
        assertFalse(themeStops.coreArgb == themeStops.midArgb)
        assertFalse(themeStops.midArgb == themeStops.rimArgb)
        assertEquals(0xFF000000.toInt(), themeStops.coreArgb and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), themeStops.midArgb and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), themeStops.rimArgb and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), themeStops.sparkArgb and 0xFF000000.toInt())
    }
}
