package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The world buffer in sections of 16 x 16 x 16 blocks, like the world's own chunk sections, so the render
 * handler draws only the sections in view and a new shape reaches the GPU a few sections per frame (endPart).
 * Each quad goes whole to the section its first vertex is in; within a section the vertices and their colours
 * are exactly the ones a single buffer would get, in the same order. Sections are made on the generation
 * thread as quads arrive (one loader buffer each, from the factory); the render thread reads them after the
 * generation, under the shape's lock. The rules of IShapeBuffer hold for the whole: end() sends and frees every
 * section, releaseVertexData() frees every section's native copy, close() is the GPU side only.
 */
public class SectionedBuffer implements IShapeBuffer {
	public static final int SIZE = 16;

	public static final class Section {
		// Section coordinates (the block coordinate divided by SIZE, rounded down), local to the shape's origin
		public final int sx, sy, sz;
		private final IShapeBuffer buffer;
		// Bounds of the vertices pushed, local coordinates; empty while no vertex is in
		private double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		private int vertices = 0;
		// The colour last given to the buffer (-1: none yet), so it is set only when it changes
		private int r = -1, g = -1, b = -1, a = -1;
		private boolean sent = false;

		private Section(int sx, int sy, int sz, IShapeBuffer buffer) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.buffer = buffer;
		}

		private void push(double x, double y, double z, int r, int g, int b, int a) {
			if(r != this.r || g != this.g || b != this.b || a != this.a) {
				buffer.setColour(r, g, b, a);
				this.r = r;
				this.g = g;
				this.b = b;
				this.a = a;
			}
			buffer.pushVertex(x, y, z);
			++vertices;
			if(x < minX) minX = x;
			if(x > maxX) maxX = x;
			if(y < minY) minY = y;
			if(y > maxY) maxY = y;
			if(z < minZ) minZ = z;
			if(z > maxZ) maxZ = z;
		}

		private void send() {
			buffer.end();
			sent = true;
		}

		public IShapeBuffer getBuffer() {
			return buffer;
		}

		public int getVertexCount() {
			return vertices;
		}

		public boolean isSent() {
			return sent;
		}

		public double minX() {
			return minX;
		}

		public double minY() {
			return minY;
		}

		public double minZ() {
			return minZ;
		}

		public double maxX() {
			return maxX;
		}

		public double maxY() {
			return maxY;
		}

		public double maxZ() {
			return maxZ;
		}

		// Horizontal distance from (x, z) to the section's box with the shape's origin at (originX, originZ), squared; 0 above or inside it
		public double horizontalDistanceSq(double x, double z, double originX, double originZ) {
			double dx = Math.max(0, Math.max(originX + minX - x, x - originX - maxX));
			double dz = Math.max(0, Math.max(originZ + minZ - z, z - originZ - maxZ));
			return dx * dx + dz * dz;
		}
	}

	private final Supplier<IShapeBuffer> factory;
	private final List<Section> sections = new ArrayList<Section>();
	private final Map<Long, Section> byKey = new HashMap<Long, Section>();
	// The section of the quad being written and its key (consecutive quads mostly stay in one section)
	private Section current = null;
	private long currentKey;
	private int r = 255, g = 255, b = 255, a = 255;
	private long vertices = 0;
	// Sections are sent in the order they were made; the next one endPart sends
	private int nextToSend = 0;
	private volatile boolean released = false;

	public SectionedBuffer(Supplier<IShapeBuffer> factory) {
		this.factory = factory;
	}

	public void setColour(int r, int g, int b, int a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	public void pushVertex(double x, double y, double z) {
		// A released buffer makes no new section: its native memory would never be freed
		if(released) throw new IllegalStateException("vertex data written after it was released");
		if(vertices++ % 4 == 0) {
			int sx = (int) Math.floor(x / SIZE), sy = (int) Math.floor(y / SIZE), sz = (int) Math.floor(z / SIZE);
			long key = key(sx, sy, sz);
			if(current == null || key != currentKey) {
				Section section = byKey.get(key);
				if(section == null) {
					section = new Section(sx, sy, sz, factory.get());
					byKey.put(key, section);
					sections.add(section);
				}
				current = section;
				currentKey = key;
			}
		}
		current.push(x, y, z, r, g, b, a);
	}

	private static long key(int sx, int sy, int sz) {
		return ((long) (sx & 0x1FFFFF) << 42) | ((long) (sy & 0x1FFFFF) << 21) | (sz & 0x1FFFFF);
	}

	// Every section not sent yet, at once (each one's end(): to the GPU, and its native copy freed)
	public void end() {
		while(nextToSend < sections.size()) sections.get(nextToSend++).send();
	}

	/**
	 * Sends sections in order until the clock reaches the deadline, at least one per call, and says whether
	 * every section is sent. Render thread, under the shape's lock, after the generation finished.
	 */
	@Override
	public boolean endPart(LongSupplier clock, long deadline) {
		if(nextToSend < sections.size()) {
			do {
				sections.get(nextToSend++).send();
			}while(nextToSend < sections.size() && clock.getAsLong() < deadline);
		}
		return nextToSend >= sections.size();
	}

	// The GPU side of every section that has one (render thread); the native copies are not touched
	public void close() {
		for(Section section: sections) section.buffer.close();
	}

	@Override
	public void releaseVertexData() {
		released = true;
		for(Section section: sections) section.buffer.releaseVertexData();
	}

	public List<Section> getSections() {
		return Collections.unmodifiableList(sections);
	}

	public int getSectionCount() {
		return sections.size();
	}

	public int getSentCount() {
		return nextToSend;
	}
}
