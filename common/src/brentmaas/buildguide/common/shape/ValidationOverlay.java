package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
	// Slightly larger than the shape cubes so the colour reads through the guide
	private static final double cubeSize = 0.7;
	// Shell around a solid block: this far outside each face. Raise it if the faces z-fight at a distance
	private static final double shellInset = 0.01;

	public static void build(IShapeBuffer buffer, ValidationState state, ShapeSet.Origin playerLocal) {
		List<long[]> entries = new ArrayList<long[]>(); // {pos, colourIndex}
		for(long pos: state.getPositions(ValidationState.IGNORED)) entries.add(new long[] {pos, 1});
		for(NearBlock nb: state.getNearBlocks()) entries.add(new long[] {nb.localPos, 2});

		if(entries.size() > maxCubes && playerLocal != null) {
			// Only when over the cap: keep the errors closest to the player (deterministic tie-break by position)
			final int px = playerLocal.x, py = playerLocal.y, pz = playerLocal.z;
			entries.sort(Comparator.<long[]>comparingLong(e -> distanceSquared(e[0], px, py, pz)).thenComparingLong(e -> e[0]));
			entries = entries.subList(0, maxCubes);
		}

		long highlighted = state.getHighlightedPos();
		for(long[] e: entries) {
			long pos = e[0];
			if(pos == highlighted) continue; // drawn last, on top
			if(e[1] == 1) {
				buffer.setColour(255, 220, 40, 160);
				pushCube(buffer, pos);
			}else {
				buffer.setColour(255, 60, 60, 160);
				pushShell(buffer, pos);
			}
		}
		if(highlighted != -1) {
			buffer.setColour(255, 255, 255, 200);
			// Untracked position = structure error (solid): shell. Tracked ones are the shape's own positions
			if(state.getStatus(highlighted) == ValidationState.UNKNOWN) pushShell(buffer, highlighted);
			else pushCube(buffer, highlighted);
		}
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
		return state.isValidated() && (state.getIgnored() > 0 || state.getNearCount() > 0 || state.getHighlightedPos() != -1);
	}
}
