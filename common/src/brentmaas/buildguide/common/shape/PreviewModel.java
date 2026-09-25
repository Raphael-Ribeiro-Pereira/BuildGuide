package brentmaas.buildguide.common.shape;

import java.util.Collection;
import java.util.List;

import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Immutable snapshot of a shape for the 3D preview: its block positions (local, packed with
 * LocalPos) with their bounding box, and optionally the validation status of each position plus
 * the structure errors around it. The preview never reads the live shape: expectedBlocks is
 * cleared and refilled by the generation executor, so it is copied once, under the shape's lock,
 * and the loader only ever sees these copies.
 *
 * Geometry is frozen when the preview opens; colours are refreshed with withValidation, which
 * returns a new instance sharing the positions. A new instance is what tells the renderer to
 * rebuild its mesh and texture.
 */
public final class PreviewModel {
	public final long[] positions;
	// Inclusive block bounds of positions; meaningless when empty
	public final int minX, minY, minZ, maxX, maxY, maxZ;
	// Status per position (parallel to positions), or null when the shape is not validated: all white
	public final byte[] status;
	// Structure errors (solid blocks deforming the shape), drawn as extra red cubes
	public final long[] errors;
	// ValidationState version the colours were read at; -1 for a geometry-only snapshot
	public final long stateVersion;
	// Shape.getGeneration() the positions were copied at; -1 when not taken from a shape
	public final long generation;

	private PreviewModel(long[] positions, int[] bounds, byte[] status, long[] errors, long stateVersion, long generation) {
		this.positions = positions;
		minX = bounds[0];
		minY = bounds[1];
		minZ = bounds[2];
		maxX = bounds[3];
		maxY = bounds[4];
		maxZ = bounds[5];
		this.status = status;
		this.errors = errors;
		this.stateVersion = stateVersion;
		this.generation = generation;
	}

	public static PreviewModel of(Collection<Long> positions) {
		return of(positions, -1);
	}

	public static PreviewModel of(Collection<Long> positions, long generation) {
		long[] copy = new long[positions.size()];
		int i = 0;
		for(long pos: positions) copy[i++] = pos;
		return new PreviewModel(copy, bounds(copy), null, new long[0], -1, generation);
	}

	/**
	 * Copy of the shape's current blocks, or null while it is generating, after a cancelled or
	 * failed generation (error: expectedBlocks may be partial), or when its lock is busy: try again
	 * next frame. ready, error and the generation are read under the lock, so they are consistent
	 * (see Shape.finishGeneration).
	 */
	public static PreviewModel snapshot(Shape shape) {
		if(!shape.lock.tryLock()) return null;
		try {
			if(!shape.ready || shape.error) return null;
			return of(shape.getExpectedBlocks(), shape.getGeneration());
		}finally {
			shape.lock.unlock();
		}
	}

	/**
	 * Same geometry, colours read from the state now. The version is read first: if the state
	 * changes while the statuses are read, the snapshot is marked older than it is and the next
	 * comparison takes a new one, never the other way round. Excluded positions are untracked,
	 * so they read UNKNOWN (white) even on a validated shape.
	 */
	public PreviewModel withValidation(ValidationState state) {
		long version = state.getVersion();
		if(!state.isValidated()) return new PreviewModel(positions, boundsArray(), null, new long[0], version, generation);
		byte[] s = new byte[positions.length];
		for(int i = 0;i < positions.length;++i) s[i] = state.getStatus(positions[i]);
		List<NearBlock> near = state.getNearBlocks();
		long[] e = new long[near.size()];
		for(int i = 0;i < e.length;++i) e[i] = near.get(i).localPos;
		return new PreviewModel(positions, boundsArray(), s, e, version, generation);
	}

	private static int[] bounds(long[] positions) {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for(long pos: positions) {
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(x < b[0]) b[0] = x;
			if(y < b[1]) b[1] = y;
			if(z < b[2]) b[2] = z;
			if(x > b[3]) b[3] = x;
			if(y > b[4]) b[4] = y;
			if(z > b[5]) b[5] = z;
		}
		return b;
	}

	private int[] boundsArray() {
		return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
	}

	public boolean isEmpty() {
		return positions.length == 0;
	}

	// Colour of position i: its status when validated, white otherwise
	public int colourAt(int i) {
		return PreviewColours.forStatus(status == null ? ValidationState.UNKNOWN : status[i]);
	}

	// Centre of the bounding box, in blocks (a block at x spans x..x+1)
	public double centreX() {
		return (minX + maxX + 1) / 2.0;
	}

	public double centreY() {
		return (minY + maxY + 1) / 2.0;
	}

	public double centreZ() {
		return (minZ + maxZ + 1) / 2.0;
	}

	/**
	 * Framing radius: the sphere around the bounding box expanded by ValidationState.nearRadius on
	 * every side, so structure errors (at most that far out) stay in view and the camera does not
	 * move when one appears or goes away.
	 */
	public double radius() {
		int m = 2 * ValidationState.nearRadius;
		double sx = maxX - minX + 1 + m, sy = maxY - minY + 1 + m, sz = maxZ - minZ + 1 + m;
		return 0.5 * Math.sqrt(sx * sx + sy * sy + sz * sz);
	}
}
