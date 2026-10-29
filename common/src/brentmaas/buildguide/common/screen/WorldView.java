package brentmaas.buildguide.common.screen;

import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.Shape;
import brentmaas.buildguide.common.shape.ShapeSet;

// "See in the world" (the Oficina prototype, 2026-10-28): the loaded ground around the shape seen from the top in
// map colours (IShapeHandler.topColours), with the shape's footprint over it in the accent. Our own view, without
// any other mod; the map is read again when the shape or its origin changes, and every two seconds
public class WorldView {
	// The most columns read on a side (a larger area is sampled every few columns)
	public static final int maxSide = 256;
	public static final long refreshMillis = 2000;

	private int[] colours = null;
	private boolean[] footprint = null;
	private int columns, rows, step;
	private long builtAt = 0;
	private Object builtShape = null;
	private int builtX, builtZ, builtBlocks;

	// Blocks per drawn square in the last draw, for the note under the map
	public int getStep() {
		return step;
	}

	public void draw(BaseScreen screen, int x1, int y1, int x2, int y2, Shape shape, ShapeSet set) {
		if(shape == null || set == null || !shape.ready) return;
		int ox = set.getOriginX(), oz = set.getOriginZ(), blocks = shape.getExpectedBlocks().size();
		long now = System.currentTimeMillis();
		if(colours == null || builtShape != shape || builtX != ox || builtZ != oz || builtBlocks != blocks || now - builtAt > refreshMillis) {
			build(shape, ox, oz, x2 - x1, y2 - y1);
			builtShape = shape;
			builtX = ox;
			builtZ = oz;
			builtBlocks = blocks;
			builtAt = now;
		}
		if(colours == null) return;
		int tile = Math.max(1, Math.min((x2 - x1) / columns, (y2 - y1) / rows));
		int left = x1 + (x2 - x1 - columns * tile) / 2, top = y1 + (y2 - y1 - rows * tile) / 2;
		for(int r = 0;r < rows;++r) {
			// Runs of one colour as one rectangle
			int start = 0;
			for(int c = 1;c <= columns;++c) {
				if(c < columns && colours[r * columns + c] == colours[r * columns + start]) continue;
				int colour = colours[r * columns + start];
				screen.fillRect(left + start * tile, top + r * tile, left + c * tile, top + (r + 1) * tile, colour == 0 ? Theme.FIELD : colour);
				start = c;
			}
			for(int c = 0;c < columns;++c) {
				if(footprint[r * columns + c]) screen.fillRect(left + c * tile, top + r * tile, left + (c + 1) * tile, top + (r + 1) * tile, Theme.FOOTPRINT);
			}
		}
	}

	// The area: the shape's footprint and a margin of a quarter of its size (8 blocks at least) on each side
	private void build(Shape shape, int ox, int oz, int width, int height) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for(long pos: shape.getExpectedBlocks()) {
			int x = LocalPos.unpackX(pos), z = LocalPos.unpackZ(pos);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		if(minX > maxX) {
			colours = null;
			return;
		}
		int margin = Math.max(8, Math.max(maxX - minX, maxZ - minZ) / 4);
		int x0 = ox + minX - margin, z0 = oz + minZ - margin, spanX = maxX - minX + 1 + 2 * margin, spanZ = maxZ - minZ + 1 + 2 * margin;
		// Every step-th column, so that the area fits the view at one pixel a square at least and maxSide a side
		step = Math.max(1, Math.max((int) Math.ceil(spanX / (double) Math.min(width, maxSide)), (int) Math.ceil(spanZ / (double) Math.min(height, maxSide))));
		columns = Math.max(1, spanX / step);
		rows = Math.max(1, spanZ / step);
		int[] full = BuildGuide.shapeHandler.topColours(x0, z0, columns, rows, step);
		if(full == null) {
			colours = null;
			return;
		}
		colours = full;
		footprint = new boolean[columns * rows];
		for(long pos: shape.getExpectedBlocks()) {
			int c = (ox + LocalPos.unpackX(pos) - x0) / step, r = (oz + LocalPos.unpackZ(pos) - z0) / step;
			if(c >= 0 && c < columns && r >= 0 && r < rows) footprint[r * columns + c] = true;
		}
	}
}
