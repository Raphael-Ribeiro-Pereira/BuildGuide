package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import brentmaas.buildguide.common.shape.ValidationState.NearBlock;

/**
 * Builds the world overlay of a shape's validation problems into an IShapeBuffer: red cubes on
 * WRONG positions, yellow on IGNORED, orange on near blocks, white on the position highlighted
 * in the error list. Colours are per vertex, so one buffer (one draw call) holds them all.
 * MISSING gets nothing: the shape's own silhouette already shows what is absent.
 *
 * At most maxCubes cubes are drawn; beyond that the nearest ones to the player are kept (the
 * error list still shows the real totals). Cube geometry is CubeMesh, local coordinates.
 */
public class ValidationOverlay {
	public static final int maxCubes = 4000;
	// Slightly larger than the shape cubes so the colour reads through the guide
	private static final double cubeSize = 0.7;

	public static void build(IShapeBuffer buffer, ValidationState state, ShapeSet.Origin playerLocal) {
		List<long[]> entries = new ArrayList<long[]>(); // {pos, colourIndex}
		for(long pos: state.getPositions(ValidationState.WRONG)) entries.add(new long[] {pos, 0});
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
			switch((int) e[1]) {
			case 0:
				buffer.setColour(255, 60, 60, 160);
				break;
			case 1:
				buffer.setColour(255, 220, 40, 160);
				break;
			default:
				buffer.setColour(255, 140, 30, 140);
				break;
			}
			pushCube(buffer, pos);
		}
		if(highlighted != -1) {
			buffer.setColour(255, 255, 255, 200);
			pushCube(buffer, highlighted);
		}
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
		return state.isValidated() && (state.getWrong() > 0 || state.getIgnored() > 0 || state.getNearCount() > 0 || state.getHighlightedPos() != -1);
	}
}
