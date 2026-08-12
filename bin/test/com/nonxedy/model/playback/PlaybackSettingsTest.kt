package com.nonxedy.model.playback

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlaybackSettingsTest {

    @Test
    fun `default settings are valid`() {
        val settings = PlaybackSettings()
        assertEquals(60, settings.updateRate)
        assertEquals(InterpolationType.CATMULL_ROM, settings.interpolation)
        assertTrue(settings.smoothRotation)
        assertTrue(settings.bakePath)
        assertEquals(16L, settings.updateIntervalMs)
    }

    @Test
    fun `update rate clamped to minimum 20`() {
        val settings = PlaybackSettings(updateRate = 10)
        assertEquals(20, settings.updateRate)
    }

    @Test
    fun `update rate clamped to maximum 240`() {
        val settings = PlaybackSettings(updateRate = 300)
        assertEquals(240, settings.updateRate)
    }

    @Test
    fun `update interval ms calculated correctly`() {
        val settings = PlaybackSettings(updateRate = 120)
        assertEquals(8L, settings.updateIntervalMs)
    }
}
