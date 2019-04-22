package org.ice.scene.ocean;

import org.ice.math.Vector2f;

/**
 * The spectrum algorithm interface is used to create the spectrum for the water
 * simulation.
 *
 * @author Daniel Kleebinder
 * @since 1.0.0
 */
public interface SpectrumAlgorithm {

	/**
	 * Calculates the spectrum value on the given location.
	 *
	 * @param water Water.
	 * @param k Index.
	 * @return Spectrum value.
	 */
	float spectrum(Ocean water, Vector2f k);
}
