package fr.bsodium.cron.sensors

import fr.bsodium.cron.session.model.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementClassifierTest {

    @Test
    fun covered_and_dark_is_enclosed() {
        assertEquals(Placement.Enclosed, PlacementClassifier.classify(covered = true, lux = 0f))
    }

    @Test
    fun covered_with_no_lux_reading_is_enclosed() {
        // No light sensor on this device -- proximity alone (covered) is still strong enough evidence.
        assertEquals(Placement.Enclosed, PlacementClassifier.classify(covered = true, lux = null))
    }

    @Test
    fun covered_but_bright_is_open() {
        // A hand cupped over the sensor in a lit room, not actually in a pocket/drawer.
        assertEquals(Placement.Open, PlacementClassifier.classify(covered = true, lux = 200f))
    }

    @Test
    fun uncovered_and_dark_is_open() {
        // A face-up phone on a dark nightstand: dark room, but nothing pressed against the sensor.
        assertEquals(Placement.Open, PlacementClassifier.classify(covered = false, lux = 0f))
    }

    @Test
    fun no_proximity_sensor_is_unknown() {
        assertEquals(Placement.Unknown, PlacementClassifier.classify(covered = null, lux = 0f))
    }
}
