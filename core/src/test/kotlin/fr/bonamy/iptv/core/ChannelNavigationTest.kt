package fr.bonamy.iptv.core

import kotlin.test.*

class ChannelNavigationTest {
    private fun channel(id: String, available: Boolean = true) = Channel(id, id, "US", emptyList(),
        if (available) listOf(Stream("https://example.com/$id", id)) else emptyList())

    @Test fun `channel keys follow the supplied order and wrap in both directions`() {
        val items = listOf(channel("c"), channel("a"), channel("b"))
        assertSame(items[1], ChannelNavigation.adjacent(items, "c", 1))
        assertSame(items[2], ChannelNavigation.adjacent(items, "c", -1))
        assertSame(items[0], ChannelNavigation.adjacent(items, "b", 1))
        assertEquals(listOf("c", "a", "b"), items.map { it.id })
    }
    @Test fun `unavailable favorites are skipped without leaving the current collection`() {
        val items = listOf(channel("favorite-b"), channel("missing", false), channel("favorite-a"))
        assertEquals("favorite-a", ChannelNavigation.adjacent(items, "favorite-b", 1)?.id)
        assertEquals("favorite-b", ChannelNavigation.adjacent(items, "favorite-a", -1)?.id)
        assertNull(ChannelNavigation.adjacent(items.take(2), "favorite-b", 1))
    }
    @Test fun `empty and single playable lists do not restart the current channel`() {
        assertNull(ChannelNavigation.adjacent(emptyList(), "a", 1))
        assertNull(ChannelNavigation.adjacent(listOf(channel("a")), "a", -1))
        assertNull(ChannelNavigation.adjacent(listOf(channel("missing", false)), "a", 1))
    }
    @Test fun `a channel removed from favorites moves to the first or last remaining item`() {
        val items = listOf(channel("a"), channel("b"))
        assertSame(items[0], ChannelNavigation.adjacent(items, "removed", 1))
        assertSame(items[1], ChannelNavigation.adjacent(items, "removed", -1))
    }
}
