package brentmaas.buildguide.common.shape;

import java.util.Arrays;
import java.util.List;
import java.util.function.LongPredicate;

import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Builds the world overlay of a shape's validation problems into an IShapeBuffer: red on
 * structure errors (solid blocks deforming the shape, ValidationState.NearBlock), yellow on
 * IGNORED positions, white on the position highlighted in the error list. Colours are per
 * vertex, so one buffer (one draw call) holds them all. MISSING gets nothing: the shape's own
 * silhouette already shows what is absent.
 *
 * Two cube kinds. IGNORED positions hold a see-through block (scaffolding), so a small inner
 * cube shows through it. Structure errors are solid and opaque: an inner cube would be hidden
 * by the block itself under the depth test, so they get a shell slightly larger than the block
 * that paints its surface (the same idea as the vanilla selection outline).
 *
 * At most maxCubes cubes are drawn; beyond that the nearest ones to the player are kept (the
 * error list still shows the real totals). Cube geometry is CubeMesh, local coordinates.
 */
public class ValidationOverlay {
	public static final int maxCubes = 4000;
	// The position picked in the error list: white, the strongest alpha of the overlay (also the faces of the
	// area 3 target outline, TargetOutline)
	public static final int highlightR = 255, highlightG = 255, highlightB = 255, highlightA = 200;
	// Slightly larger than the shape cubes so the colour reads through the guide
	private static final double cubeSize = 0.7;
	// Shell around a solid block: this far outside each face. Raise it if the faces z-fight at a distance
	private static final double shellInset = 0.01;

	public static void build(IShapeBuffer buffer, ValidationState state, ShapeSet.Origin playerLocal) {
		build(buffer, state, playerLocal, null);
	}

	/**
	 * Build focus: inFocus (null = every position) limits the states that belong to the shape (wrong layer,
	 * wrong block) to the blocks the world shows. Structure errors near the shape and ignored positions are
	 * always drawn, and so is the highlighted position.
	 */
	public static void build(IShapeBuffer buffer, ValidationState state, ShapeSet.Origin playerLocal, LongPredicate inFocus) {
		// The entries as two primitive arrays (position, colour index), in this order: ignored, structure errors,
		// wrong blocks, wrong layer. Palette per layer (4D): wrong blocks on the shape are red like structure
		// errors, another layer's block orange; both are solid blocks, so they get a shell
		List<Long> ignored = state.getPositions(ValidationState.IGNORED);
		List<NearBlock> near = state.getNearBlocks();
		List<Long> wrongBlock = state.getPositions(ValidationState.WRONG_BLOCK), wrongLayer = state.getPositions(ValidationState.WRONG_LAYER);
		int capacity = ignored.size() + near.size() + wrongBlock.size() + wrongLayer.size(), n = 0;
		long[] positions = new long[capacity];
		byte[] kinds = new byte[capacity];
		for(long pos: ignored) {
			positions[n] = pos;
			kinds[n++] = 1;
		}
		for(NearBlock nb: near) {
			positions[n] = nb.localPos;
			kinds[n++] = 2;
		}
		for(long pos: wrongBlock) if(inFocus == null || inFocus.test(pos)) {
			positions[n] = pos;
			kinds[n++] = 2;
		}
		for(long pos: wrongLayer) if(inFocus == null || inFocus.test(pos)) {
			positions[n] = pos;
			kinds[n++] = 3;
		}

		int[] drawn = nearestFirst(positions, n, playerLocal);
		long highlighted = state.getHighlightedPos();
		for(int e: drawn) {
			long pos = positions[e];
			if(pos == highlighted) continue; // drawn last, on top
			if(kinds[e] == 1) {
				buffer.setColour(255, 220, 40, 160);
				pushCube(buffer, pos);
			}else if(kinds[e] == 3) {
				buffer.setColour(255, 140, 26, 160);
				pushShell(buffer, pos);
			}else {
				buffer.setColour(255, 60, 60, 160);
				pushShell(buffer, pos);
			}
		}
		if(highlighted != -1) {
			buffer.setColour(highlightR, highlightG, highlightB, highlightA);
			// Untracked position = structure error (solid): shell. Tracked ones are the shape's own positions
			byte s = state.getStatus(highlighted);
			if(s == ValidationState.UNKNOWN || s == ValidationState.WRONG_LAYER || s == ValidationState.WRONG_BLOCK) pushShell(buffer, highlighted);
			else pushCube(buffer, highlighted);
		}
	}

	/**
	 * The entries to draw, as indices into positions: all of them in their order when they fit (or without a
	 * player), else the maxCubes nearest to the player, nearest first, ties by position. Performance step 4: the
	 * same choice and order as sorting every entry with a comparator, which took 5 to 30 ms on the render thread
	 * with 20 000 structure errors; now one primitive sort of the distances finds the cut, and only the entries
	 * kept are put in order.
	 */
	static int[] nearestFirst(long[] positions, int n, ShapeSet.Origin playerLocal) {
		if(n <= maxCubes || playerLocal == null) {
			int[] all = new int[n];
			for(int e = 0;e < n;++e) all[e] = e;
			return all;
		}
		int px = playerLocal.x, py = playerLocal.y, pz = playerLocal.z;
		long[] d2 = new long[n];
		for(int e = 0;e < n;++e) d2[e] = distanceSquared(positions[e], px, py, pz);
		long[] sorted = Arrays.copyOf(d2, n);
		Arrays.sort(sorted);
		long cut = sorted[maxCubes - 1];
		// Everything nearer than the cut, then the nearest of the ties at the cut by position
		int below = 0;
		for(int e = 0;e < n;++e) if(d2[e] < cut) ++below;
		int ties = 0;
		for(int e = 0;e < n;++e) if(d2[e] == cut) ++ties;
		long[] tied = new long[ties];
		ties = 0;
		for(int e = 0;e < n;++e) if(d2[e] == cut) tied[ties++] = positions[e];
		Arrays.sort(tied);
		long lastTied = tied[maxCubes - below - 1];
		Integer[] kept = new Integer[maxCubes];
		int k = 0;
		for(int e = 0;e < n && k < maxCubes;++e) if(d2[e] < cut || (d2[e] == cut && positions[e] <= lastTied)) kept[k++] = e;
		Arrays.sort(kept, (a, b) -> d2[a] != d2[b] ? Long.compare(d2[a], d2[b]) : Long.compare(positions[a], positions[b]));
		int[] out = new int[k];
		for(int e = 0;e < k;++e) out[e] = kept[e];
		return out;
	}

	private static void pushShell(IShapeBuffer buffer, long pos) {
		CubeMesh.push(buffer, LocalPos.unpackX(pos) - shellInset, LocalPos.unpackY(pos) - shellInset, LocalPos.unpackZ(pos) - shellInset, 1 + 2 * shellInset);
	}

	private static void pushCube(IShapeBuffer buffer, long pos) {
		double x = LocalPos.unpackX(pos) + 0.5 - cubeSize / 2;
		double y = LocalPos.unpackY(pos) + 0.5 - cubeSize / 2;
		double z = LocalPos.unpackZ(pos) + 0.5 - cubeSize / 2;
		CubeMesh.push(buffer, x, y, z, cubeSize);
	}

	private static long distanceSquared(long pos, int px, int py, int pz) {
		long dx = LocalPos.unpackX(pos) - px, dy = LocalPos.unpackY(pos) - py, dz = LocalPos.unpackZ(pos) - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	// True when there is anything to draw
	public static boolean hasContent(ValidationState state) {
		return state.isValidated() && (state.getIgnored() > 0 || state.getNearCount() > 0 || state.getWrongLayer() > 0 || state.getWrongBlock() > 0 || state.getHighlightedPos() != -1);
	}
}
