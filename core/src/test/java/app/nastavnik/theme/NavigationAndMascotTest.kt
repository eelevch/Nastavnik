package app.nastavnik.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndMascotTest {

    @Test
    fun `tablet tab bar defaults to LEFT and supports side positions including BOTH`() {
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet(null))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("BOTTOM"))
        assertEquals(TabBarPosition.LEFT, TabBarPosition.forTablet("LEFT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("RIGHT"))
        assertEquals(TabBarPosition.RIGHT, TabBarPosition.forTablet("right"))
        assertEquals(TabBarPosition.BOTH, TabBarPosition.forTablet("BOTH"))
        assertEquals(TabBarPosition.BOTH, TabBarPosition.forTablet("both"))
        assertEquals(listOf(TabBarPosition.LEFT, TabBarPosition.RIGHT, TabBarPosition.BOTH), TabBarPosition.TABLET_OPTIONS)
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
}
