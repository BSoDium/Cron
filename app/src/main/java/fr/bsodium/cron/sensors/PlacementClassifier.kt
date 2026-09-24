package fr.bsodium.cron.sensors

import fr.bsodium.cron.session.model.Placement

/**
 * Classifies where the phone physically is from a single proximity+lux sample, taken at
 * screen-off or alarm dismiss (see [ProximityReader]).
 *
 * A pocket or drawer reads *covered* on the proximity sensor and near-zero lux at the same
 * instant. A phone lying face-up on a nightstand or bed also goes dark at night, but is never
 * covered -- proximity only trips when something is pressed right up against the sensor. That
 * distinction is what makes [Placement.Enclosed] detectable without a light-only heuristic
 * mistaking every dark room for a pocket.
 */
object PlacementClassifier {

    /** Pure decision — unit-testable. `covered == null` means no proximity sensor on this device. */
    internal fun classify(covered: Boolean?, lux: Float?): Placement = when {
        covered == null -> Placement.Unknown
        covered && (lux == null || lux < ENCLOSED_LUX_THRESHOLD) -> Placement.Enclosed
        else -> Placement.Open
    }

    /** A pocket/drawer reads darker than even [AmbientLightReader]'s room-level dark gate. */
    private const val ENCLOSED_LUX_THRESHOLD = 5f
}
