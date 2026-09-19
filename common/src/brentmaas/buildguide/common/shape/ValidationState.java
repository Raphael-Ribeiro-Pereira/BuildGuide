package brentmaas.buildguide.common.shape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live validation state of an IValidatable shape: the status of every expected block
 * (local position packed with LocalPos) plus derived counters. Filled by the loader's render
 * handler, read by the GUI. Mutable on purpose: incremental validation adjusts single
 * positions many times per second, so counters are maintained on each transition instead
 * of being recomputed. All methods are synchronized because writers (scan, later block
 * events) and readers (GUI, render) are on different threads.
 *
 * Only primitives, Strings and collections: no net.minecraft.
 */
public class ValidationState {
	public static final byte UNKNOWN = 0;
	public static final byte OK = 1;
	public static final byte MISSING = 2;
	public static final byte WRONG = 3;

	// A solid block near the shape that is not part of it, likely misplaced
	public static class NearBlock {
		public final long localPos;
		public final String blockName;
		public final float distance;

		public NearBlock(long localPos, String blockName, float distance) {
			this.localPos = localPos;
			this.blockName = blockName;
			this.distance = distance;
		}
	}

	private final Map<Long, Byte> status = new HashMap<Long, Byte>();
	private final Map<Long, String> wrongBlockNames = new HashMap<Long, String>();
	// Near blocks keyed by local position so incremental updates can add and remove them
	private Map<Long, NearBlock> nearBlocks = new LinkedHashMap<Long, NearBlock>();
	// Local bounding box of the expected positions, expanded by nearRadius; cheap pre-filter for block events
	private int minX, minY, minZ, maxX, maxY, maxZ;
	public static final int nearRadius = 2;
	private int ok = 0, missing = 0, wrong = 0;
	private boolean validated = false;
	// A full scan was requested by the shape itself (after regeneration); the render handler
	// runs it once the shape has been idle for a moment and its chunks are loaded
	private boolean scanRequested = false;

	// The shape regenerated: everything known so far is stale
	public synchronized void invalidate() {
		status.clear();
		wrongBlockNames.clear();
		nearBlocks.clear();
		ok = missing = wrong = 0;
		validated = false;
		minX = minY = minZ = Integer.MAX_VALUE;
		maxX = maxY = maxZ = Integer.MIN_VALUE;
	}

	// Start a full scan over these expected positions (all UNKNOWN until set)
	public synchronized void beginScan(Collection<Long> expected) {
		invalidate();
		for(long pos: expected) {
			status.put(pos, UNKNOWN);
			int x = LocalPos.unpackX(pos), y = LocalPos.unpackY(pos), z = LocalPos.unpackZ(pos);
			if(x < minX) minX = x;
			if(x > maxX) maxX = x;
			if(y < minY) minY = y;
			if(y > maxY) maxY = y;
			if(z < minZ) minZ = z;
			if(z > maxZ) maxZ = z;
		}
	}

	public synchronized void endScan() {
		validated = true;
	}
	
	public synchronized void requestScan() {
		scanRequested = true;
	}
	
	public synchronized boolean isScanRequested() {
		return scanRequested;
	}
	
	// Clears the request; returns whether one was pending
	public synchronized boolean consumeScanRequest() {
		boolean was = scanRequested;
		scanRequested = false;
		return was;
	}

	// Set one position's status, adjusting the counters by the transition. Unknown positions are ignored
	public synchronized void setStatus(long pos, byte newStatus, String blockName) {
		Byte old = status.get(pos);
		if(old == null) return;
		adjust(old, -1);
		adjust(newStatus, 1);
		status.put(pos, newStatus);
		if(newStatus == WRONG && blockName != null) wrongBlockNames.put(pos, blockName);
		else wrongBlockNames.remove(pos);
	}

	// Remove a position from the expected set (exclusion rules): it leaves the total, it does not become missing
	public synchronized void exclude(long pos) {
		Byte old = status.remove(pos);
		if(old == null) return;
		adjust(old, -1);
		wrongBlockNames.remove(pos);
	}

	private void adjust(byte s, int delta) {
		switch(s) {
		case OK:
			ok += delta;
			break;
		case MISSING:
			missing += delta;
			break;
		case WRONG:
			wrong += delta;
			break;
		default:
			break;
		}
	}

	public synchronized void setNearBlocks(List<NearBlock> near) {
		nearBlocks.clear();
		if(near != null) for(NearBlock nb: near) nearBlocks.put(nb.localPos, nb);
	}
	
	// True if a local position could affect this state: inside the expected bounding box expanded by nearRadius
	public synchronized boolean isInRange(int x, int y, int z) {
		return validated && x >= minX - nearRadius && x <= maxX + nearRadius && y >= minY - nearRadius && y <= maxY + nearRadius && z >= minZ - nearRadius && z <= maxZ + nearRadius;
	}
	
	/**
	 * Incremental update for one changed block (same rules as the full scan). Expected
	 * position: air -> MISSING, solid -> OK, otherwise WRONG. Other positions: a solid block
	 * within nearRadius of an expected one becomes a near block; anything else removes one.
	 */
	public synchronized void updateBlock(long local, boolean air, boolean solid, String blockName) {
		if(!validated) return;
		if(status.containsKey(local)) {
			if(air) setStatus(local, MISSING, null);
			else if(solid) setStatus(local, OK, null);
			else setStatus(local, WRONG, blockName);
			return;
		}
		if(air || !solid) {
			nearBlocks.remove(local);
			return;
		}
		int x = LocalPos.unpackX(local), y = LocalPos.unpackY(local), z = LocalPos.unpackZ(local);
		double best = Double.MAX_VALUE;
		for(int dx = -nearRadius;dx <= nearRadius;++dx) {
			for(int dy = -nearRadius;dy <= nearRadius;++dy) {
				for(int dz = -nearRadius;dz <= nearRadius;++dz) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if(d2 == 0 || d2 > nearRadius * nearRadius) continue;
					if(status.containsKey(LocalPos.pack(x + dx, y + dy, z + dz))) {
						double d = Math.sqrt(d2);
						if(d < best) best = d;
					}
				}
			}
		}
		if(best <= nearRadius) nearBlocks.put(local, new NearBlock(local, blockName, (float) best));
		else nearBlocks.remove(local);
	}

	public synchronized boolean isValidated() {
		return validated;
	}

	public synchronized int getOk() {
		return ok;
	}

	public synchronized int getMissing() {
		return missing;
	}

	public synchronized int getWrong() {
		return wrong;
	}

	// Number of expected positions currently tracked (after exclusions)
	public synchronized int getTotal() {
		return status.size();
	}

	// ok / total in [0, 1]; 0 when nothing is expected
	public synchronized double getProgress() {
		return status.isEmpty() ? 0.0 : (double) ok / status.size();
	}

	// O(1) lookup for renderers; UNKNOWN for positions not tracked
	public synchronized byte getStatus(long pos) {
		Byte s = status.get(pos);
		return s == null ? UNKNOWN : s;
	}

	// Snapshot of the positions with the given status (for error lists)
	public synchronized List<Long> getPositions(byte wanted) {
		List<Long> result = new ArrayList<Long>();
		for(Map.Entry<Long, Byte> e: status.entrySet()) if(e.getValue() == wanted) result.add(e.getKey());
		return result;
	}

	public synchronized String getWrongBlockName(long pos) {
		return wrongBlockNames.get(pos);
	}

	public synchronized List<NearBlock> getNearBlocks() {
		return new ArrayList<NearBlock>(nearBlocks.values());
	}
	
	public synchronized int getNearCount() {
		return nearBlocks.size();
	}
}
