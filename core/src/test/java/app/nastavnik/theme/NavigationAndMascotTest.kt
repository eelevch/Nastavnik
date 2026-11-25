package app.nastavnik.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndMascotTest {

    @Test
    fun `tablet tab bar defaults to LEFT and only allows side positions`() {
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet(null))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("BOTTOM"))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("LEFT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("RIGHT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("right"))
        assertEquals(listOf(TabBarPosition.LEFT, TabBarPosition.RIGHT), TabBarPosition.TABLET_OPTIONS)
        assertTrue(TabBarPosition.LEFT.isSide())
        assertTrue(TabBarPosition.RIGHT.isSide())
        assertFalse(TabBarPosition.BOTTOM.isSide())
        assertFalse(TabBarPosition.BOTTOM_CAPSULE.isSide())
    }

    @Test
    fun `phone tab bar parses all four variants with safe fallback`() {
        assertEquals(TabBarPosition.BOTTOM, TabBarPosition.forPhone(null))
        assertEquals(TabBarPosition.BOTTOM, TabBarPosition.forPhone("unknown"))
        assertEquals(TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.forPhone("BOTTOM_CAPSULE"))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forPhone("LEFT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forPhone("RIGHT"))
        assertEquals(
            listOf(TabBarPosition.BOTTOM, TabBarPosition.BOTTOM_CAPSULE, TabBarPosition.LEFT, TabBarPosition.RIGHT),
            TabBarPosition.PHONE_OPTIONS,
        )
    }

    @Test
    fun `mascot variants expose all four options and parse case insensitively`() {
        assertEquals(4, MascotVariant.entries.size)
        assertEquals(MascotVariant.CLASSIC, MascotVariant.fromId(null))
        assertEquals(MascotVariant.CLASSIC, MascotVariant.fromId("nope"))
        assertEquals(MascotVariant.DUAL_ORBIT, MascotVariant.fromId("dual_orbit"))
        assertEquals(MascotVariant.PARALLEL_ORBIT, MascotVariant.fromId("PARALLEL_ORBIT"))
        assertEquals(MascotVariant.ECLIPSE_DUO, MascotVariant.fromId("ECLIPSE_DUO"))
    }
}
