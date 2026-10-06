package brentmaas.buildguide.common.shape;

/**
 * Island: seeded 2D gradient noise. A pure function of (seed, x, z): no state, no tables, so the
 * result never depends on call order or thread. Values are roughly in [-1, 1].
 *
 * Only primitives and java.*: no net.minecraft.
 */
public final class IslandNoise {
	private IslandNoise() {}

	// SplitMix64 finaliser: a well mixed 64-bit hash
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// Gradient of lattice point (ix, iz) dotted with the offset (dx, dz)
	private static double grad(long seed, int ix, int iz, double dx, double dz) {
		long h = mix(seed ^ (ix * 0x9E3779B97F4A7C15L) ^ (iz * 0xC2B2AE3D27D4EB4FL));
		double angle = (h >>> 11) * 0x1.0p-53 * 2 * Math.PI;
		return Math.cos(angle) * dx + Math.sin(angle) * dz;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	// One octave of gradient noise, scaled to about [-1, 1]
	public static double noise(long seed, double x, double z) {
		int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
		double fx = x - ix, fz = z - iz;
		double u = fade(fx), v = fade(fz);
		double a = grad(seed, ix, iz, fx, fz), b = grad(seed, ix + 1, iz, fx - 1, fz);
		double c = grad(seed, ix, iz + 1, fx, fz - 1), d = grad(seed, ix + 1, iz + 1, fx - 1, fz - 1);
		double ab = a + (b - a) * u, cd = c + (d - c) * u;
		return Math.max(-1.0, Math.min(1.0, (ab + (cd - ab) * v) * 1.4142135623730951));
	}

	// Three octaves (frequency x2, amplitude x0.5), normalised back to about [-1, 1]
	public static double fbm(long seed, double x, double z) {
		double sum = 0, amp = 1, norm = 0;
		for(int o = 0;o < 3;++o) {
			sum += amp * noise(mix(seed + o), x, z);
			norm += amp;
			amp *= 0.5;
			x *= 2;
			z *= 2;
		}
		return sum / norm;
	}
}
