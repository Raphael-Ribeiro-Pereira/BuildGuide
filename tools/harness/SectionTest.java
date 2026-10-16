import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.LongSupplier;
import brentmaas.buildguide.common.AbstractRenderHandler;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.RenderStats;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.WorldUpdateGate.Mode;
import brentmaas.buildguide.common.WorldUpload;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
// The world buffer in 16-block sections (2026-10-16, wip/perf): every quad goes whole to one section and the
// sections together hold exactly what a single buffer holds, colours and order within a section included; the
// sections' bounds; endPart's budget (at least one section, then until the deadline); close and release reach
// every section; live apply sends a sectioned buffer over several frames and swaps it in only when all of it is
// sent, without asking the gate again, and a newer generation or focus drops the partial one; nothing leaks;
// the timing lines (world-buffer with sections=, frames=, maxframe=, gpu=; the periodic phase=render).
public class SectionTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }
	static void busy(double ms){ long t = System.nanoTime(); while(System.nanoTime() - t < ms * 1e6); }

	// Every vertex with the colour it was pushed with, in order
	static class ColourBuffer implements IShapeBuffer {
		final List<double[]> vertices = new ArrayList<>(); int r = 255, g = 255, b = 255, a = 255; int colourCalls = 0;
		int ends = 0, closes = 0, releases = 0;
		public void setColour(int r, int g, int b, int a){ this.r = r; this.g = g; this.b = b; this.a = a; colourCalls++; }
		public void pushVertex(double x, double y, double z){ vertices.add(new double[]{x, y, z, r, g, b, a}); }
		public void end(){ ends++; } public void close(){ closes++; }
		@Override public void releaseVertexData(){ releases++; }
	}
	static String key(double[] v){ return v[0] + "," + v[1] + "," + v[2] + "," + (int) v[3] + "," + (int) v[4] + "," + (int) v[5] + "," + (int) v[6]; }
	static int sec(double c){ return (int) Math.floor(c / SectionedBuffer.SIZE); }

	// The same pushes into a single buffer and into a sectioned one; true when the sections hold exactly the single
	// buffer's vertices, each section the single buffer's subsequence of its quads, every quad whole in the section
	// of its first vertex, the bounds exactly those of the section's vertices
	static String compare(ColourBuffer single, SectionedBuffer sectioned){
		Map<String, List<String>> want = new HashMap<>();
		for(int q = 0; q < single.vertices.size(); q += 4){
			double[] f = single.vertices.get(q); String s = sec(f[0]) + "/" + sec(f[1]) + "/" + sec(f[2]);
			List<String> l = want.computeIfAbsent(s, k -> new ArrayList<>()); for(int i = 0; i < 4; i++) l.add(key(single.vertices.get(q + i)));
		}
		if(want.size() != sectioned.getSectionCount()) return "sections " + sectioned.getSectionCount() + " != " + want.size();
		long total = 0;
		for(SectionedBuffer.Section s: sectioned.getSections()){
			ColourBuffer in = (ColourBuffer) s.getBuffer(); List<String> got = new ArrayList<>();
			double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
			for(double[] v: in.vertices){ got.add(key(v)); minX = Math.min(minX, v[0]); minY = Math.min(minY, v[1]); minZ = Math.min(minZ, v[2]); maxX = Math.max(maxX, v[0]); maxY = Math.max(maxY, v[1]); maxZ = Math.max(maxZ, v[2]); }
			if(!got.equals(want.get(s.sx + "/" + s.sy + "/" + s.sz))) return "section " + s.sx + "/" + s.sy + "/" + s.sz + " differs";
			if(in.vertices.size() % 4 != 0 || s.getVertexCount() != in.vertices.size()) return "a split quad in " + s.sx + "/" + s.sy + "/" + s.sz;
			for(int q = 0; q < in.vertices.size(); q += 4){ double[] f = in.vertices.get(q); if(sec(f[0]) != s.sx || sec(f[1]) != s.sy || sec(f[2]) != s.sz) return "a quad outside its section"; }
			if(minX != s.minX() || minY != s.minY() || minZ != s.minZ() || maxX != s.maxX() || maxY != s.maxY() || maxZ != s.maxZ()) return "bounds of " + s.sx + "/" + s.sy + "/" + s.sz;
			total += in.vertices.size();
		}
		return total == single.vertices.size() ? "" : "vertices " + total + " != " + single.vertices.size();
	}
	// Both buffers get the same calls
	static void both(IShapeBuffer x, IShapeBuffer y, java.util.function.Consumer<IShapeBuffer> pushes){ pushes.accept(x); pushes.accept(y); }

	// Like the Fabric ShapeBuffer: an account each, end() sends and frees, close() is the GPU side only (counted)
	static final List<SectionBuffer> made = Collections.synchronizedList(new ArrayList<>());
	static class SectionBuffer implements IShapeBuffer {
		final VertexMemory.Account memory = VertexMemory.open(28); final double endMs;
		volatile int writesAfterRelease = 0, endAfterRelease = 0, closes = 0, frees = 0; volatile boolean sent = false;
		SectionBuffer(double endMs){ this.endMs = endMs; made.add(this); }
		public void setColour(int r, int g, int b, int a){}
		public void pushVertex(double x, double y, double z){ if(memory.isReleased()) writesAfterRelease++; memory.write(16); }
		public void end(){ if(memory.isReleased()){ endAfterRelease++; return; } busy(endMs); sent = true; releaseVertexData(); }
		public void close(){ closes++; }
		@Override public void releaseVertexData(){ if(memory.release()) frees++; }
	}
	// A sectioned buffer with n sections (one cube in each, along X), as a finished generation leaves it
	static SectionedBuffer sectioned(int n, double endMs){
		SectionedBuffer b = new SectionedBuffer(() -> new SectionBuffer(endMs));
		for(int i = 0; i < n; i++) CubeMesh.push(b, i * 16 + 4, 4, 4, 0.6);
		return b;
	}
	static class Handler extends AbstractRenderHandler {
		Mode mode = Mode.LIVE; boolean menuOpen = false;
		public void register(){}
		public void renderShapeBuffer(Shape shape){}
		protected void setupRenderingShapeSet(ShapeSet shape){}
		protected void endRenderingShapeSet(){}
		protected void pushProfiler(String key){}
		protected void popProfiler(){}
		@Override protected boolean deferredWorldUpdates(){ return true; }
		@Override protected boolean isMenuOpen(){ return menuOpen; }
		@Override protected Mode worldUpdateMode(){ return mode; }
		void frame(ShapeSet s){ renderShapeSet(s); }
	}
	static List<String> lines = new ArrayList<>();
	static void resetLog() throws Exception { lines.clear(); TimingLog.sink = lines::add; Method r = TimingLog.class.getDeclaredMethod("reset"); r.setAccessible(true); r.invoke(null); }
	// The cube size without ShapeSet.setCubeSize, which would start a generation of its own
	static void cubeSize(ShapeSet set, double s) throws Exception { Field f = ShapeSet.class.getDeclaredField("shapeCubeSize"); f.setAccessible(true); f.setDouble(set, s); }
	static double part(String line, String key){ for(String w: line.split(" ")) if(w.startsWith(key + "=")) return Double.parseDouble(w.substring(key.length() + 1)); return -1; }
	static String word(String line, String key){ for(String w: line.split(" ")) if(w.startsWith(key + "=")) return w.substring(key.length() + 1); return ""; }

	public static void main(String[] a) throws Exception {
		System.out.println("-- routing: the sections hold exactly what one buffer holds");
		{
			ColourBuffer single = new ColourBuffer(); SectionedBuffer sb = new SectionedBuffer(ColourBuffer::new);
			// A 40 x 40 x 40 block of cubes from -8 (blocks -8..31: sections -1, 0, 1 on each axis, 27), colour by height
			both(single, sb, b -> { for(int x = -8; x < 32; x++) for(int y = -8; y < 32; y++) for(int z = -8; z < 32; z++){ b.setColour(y * 5 & 255, 100, 200, 128); CubeMesh.push(b, x + 0.2, y + 0.2, z + 0.2, 0.6); } });
			String diff = compare(single, sb);
			check(diff.isEmpty() && sb.getSectionCount() == 27, "40^3 cubes from -8 (colour by height): 27 sections, the same vertices and colours, in order within each section, no split quad, exact bounds " + diff);
			SectionedBuffer.Section s0 = null; for(SectionedBuffer.Section s: sb.getSections()) if(s.sx == -1 && s.sy == -1 && s.sz == -1) s0 = s;
			check(s0 != null && s0.getVertexCount() == 8 * 8 * 8 * 24 && s0.minX() == -7.8 && Math.abs(s0.maxX() - (-0.2)) < 1e-9, "negative coordinates: section -1 holds blocks -8..-1 (8^3 cubes), bounds -7.8 .. -0.2");
			int maxCubes = 0; for(SectionedBuffer.Section s: sb.getSections()) maxCubes = Math.max(maxCubes, s.getVertexCount() / 24);
			check(maxCubes == 4096, "a full section holds 16^3 = 4096 cubes");
		}
		{
			// Cube size 1 at y = z = 15: the +Y and +Z faces start on 16 and go to the sections above and behind; the +X face of block 15 to the next one
			ColourBuffer single = new ColourBuffer(); SectionedBuffer sb = new SectionedBuffer(ColourBuffer::new);
			both(single, sb, b -> { for(int x = 14; x < 18; x++) CubeMesh.push(b, x, 15, 15, 1.0); });
			String diff = compare(single, sb);
			check(diff.isEmpty() && sb.getSectionCount() == 6, "cube size 1 across section edges: whole quads only, each in the section of its first vertex (x 0 or 1, times base, +Y, +Z: 6 sections) " + diff);
		}
		{
			// Colour changes between quads, before a section exists, and when a section is visited again
			ColourBuffer single = new ColourBuffer(); SectionedBuffer sb = new SectionedBuffer(ColourBuffer::new);
			FaceShadedBuffer fs1 = new FaceShadedBuffer(single), fs2 = new FaceShadedBuffer(sb);
			both(fs1, fs2, b -> { for(int i = 0; i < 6; i++){ b.setColour(10 * i, 20, 30, 255); CubeMesh.push(b, (i % 2) * 20, 0, 0, 0.6); } });
			String diff = compare(single, sb);
			int calls = 0; for(SectionedBuffer.Section s: sb.getSections()) calls += ((ColourBuffer) s.getBuffer()).colourCalls;
			check(diff.isEmpty() && sb.getSectionCount() == 2, "face shading (a colour per face) and sections visited in turn: the same colours " + diff);
			check(calls <= single.colourCalls, "a section's colour is set only when it changes (" + calls + " calls, the single buffer " + single.colourCalls + ")");
		}

		System.out.println("-- endPart, end, close, release");
		{
			SectionedBuffer sb = new SectionedBuffer(ColourBuffer::new);
			for(int i = 0; i < 10; i++) CubeMesh.push(sb, i * 16 + 1, 1, 1, 0.6);
			LongSupplier late = () -> 100; // always past the deadline: one section per call
			boolean first = sb.endPart(late, 0);
			check(!first && sb.getSentCount() == 1, "past the deadline: still one section per call");
			int calls = 1; while(!sb.endPart(late, 0)) calls++;
			check(calls + 1 == 10 && sb.getSentCount() == 10, "ten sections, one per call: done on the tenth (" + (calls + 1) + ")");
			check(sb.endPart(late, 0), "nothing left: done at once");
			boolean once = true; for(SectionedBuffer.Section s: sb.getSections()) once &= ((ColourBuffer) s.getBuffer()).ends == 1 && s.isSent();
			check(once, "each section sent exactly once");
			SectionedBuffer sb2 = new SectionedBuffer(ColourBuffer::new); for(int i = 0; i < 10; i++) CubeMesh.push(sb2, i * 16 + 1, 1, 1, 0.6);
			long[] t = {0}; LongSupplier ticking = () -> t[0]++; // 0, 1, 2, ...: deadline 3 lets four sections through
			check(!sb2.endPart(ticking, 3) && sb2.getSentCount() == 4, "a clock reaching the deadline after three reads: four sections (" + sb2.getSentCount() + ")");
			check(sb2.endPart(() -> 0, Long.MAX_VALUE) && sb2.getSentCount() == 10, "a deadline never reached: the rest in one call");
			SectionedBuffer sb3 = new SectionedBuffer(ColourBuffer::new); for(int i = 0; i < 5; i++) CubeMesh.push(sb3, i * 16 + 1, 1, 1, 0.6);
			sb3.endPart(late, 0); sb3.end();
			once = true; for(SectionedBuffer.Section s: sb3.getSections()) once &= ((ColourBuffer) s.getBuffer()).ends == 1;
			check(once && sb3.getSentCount() == 5, "end() after a part: the rest, none twice");
			sb3.close(); sb3.releaseVertexData();
			boolean all = true; for(SectionedBuffer.Section s: sb3.getSections()){ ColourBuffer c = (ColourBuffer) s.getBuffer(); all &= c.closes == 1 && c.releases == 1; }
			check(all, "close() and releaseVertexData() reach every section");
			boolean threw = false; int before = sb3.getSectionCount(); try { sb3.pushVertex(500, 0, 0); } catch(IllegalStateException e){ threw = true; }
			check(threw && sb3.getSectionCount() == before, "a write after the release throws and makes no section (its memory would never be freed)");
			SectionedBuffer empty = new SectionedBuffer(ColourBuffer::new);
			check(empty.endPart(late, 0) && empty.getSectionCount() == 0, "an empty buffer: done at once, no section");
			IShapeBuffer plain = new ColourBuffer();
			check(plain.endPart(late, 0) && ((ColourBuffer) plain).ends == 1, "any other buffer: endPart is end(), done in one call");
		}
		{
			SectionedBuffer sb = new SectionedBuffer(ColourBuffer::new); CubeMesh.push(sb, 2, 0, 2, 0.6); // bounds x, z: 2 .. 2.6
			SectionedBuffer.Section s = sb.getSections().get(0);
			check(s.horizontalDistanceSq(2.3, 2.3, 0, 0) == 0 && s.horizontalDistanceSq(2.3, 2.3, 0, 0) == s.horizontalDistanceSq(2.3, 2.3, 0, 0), "a point above the box: distance 0");
			check(Math.abs(s.horizontalDistanceSq(5.6, 2.3, 0, 0) - 9) < 1e-9 && Math.abs(s.horizontalDistanceSq(105.6, 106.6, 100, 100) - 25) < 1e-9, "outside: squared distance to the box, with the origin added (3 -> 9; 3, 4 -> 25)");
		}

		System.out.println("-- live apply over several frames");
		BuildGuide.widgetHandler = new PresetTest.FakeWidgets();
		Handler h = new Handler();
		{
			Shape shape = new ShapeCuboid(); ShapeSet set = OriginScanTest.newSet(new Shape[]{shape}, 0, 64, 0);
			IShapeBuffer old = new ColourBuffer(); shape.buffer = old; shape.ready = true; shape.worldGate.request(); h.frame(set);
			check(shape.shownBuffer == old && !shape.worldGate.isPending(), "a plain buffer: applied in one frame, as before");
			SectionedBuffer next = sectioned(10, 1.0); shape.buffer = next; shape.worldGate.request(); resetLog();
			h.frame(set);
			int afterOne = next.getSentCount();
			check(shape.shownBuffer == old && afterOne >= 1 && afterOne < 10 && shape.worldGate.isPending() && shape.upload.isSending(next), "10 sections of 1 ms, 3 ms a frame: after one frame the world still shows the old buffer (" + afterOne + " sent)");
			h.menuOpen = true; h.mode = Mode.ON_CLOSE; // the gate would say no now: the sending goes on anyway
			int frames = 1; while(shape.shownBuffer != next && frames < 50){ h.frame(set); frames++; }
			check(shape.shownBuffer == next && ((ColourBuffer) old).closes == 1 && !shape.worldGate.isPending() && !shape.upload.isSending(next), "swapped in only when the last section is sent, without asking the gate again; the old buffer closed (" + frames + " frames)");
			check(frames >= 3 && frames <= 6, "about 3 sections a frame: 4 frames, give or take the clock (" + frames + ")");
			String line = lines.size() == 1 ? lines.get(0) : "";
			check(line.contains(" sections=10 frames=" + frames + " ") && line.contains(" reason=close "), "one world-buffer line, with sections= and frames=, and the reason it started with: " + line);
			check(part(line, "world-end") >= 10 && part(line, "maxframe") >= 3 && part(line, "maxframe") < 6 && !word(line, "gpu").isEmpty(), "world-end: the sending over all frames (" + part(line, "world-end") + " ms); maxframe: the longest one (" + part(line, "maxframe") + " ms); gpu=");
			h.frame(set);
			check(lines.size() == 1, "the frames after it: nothing");

			// A newer generation while a buffer is being sent: the partial one is dropped, never shown
			h.menuOpen = false; h.mode = Mode.LIVE;
			SectionedBuffer partial = sectioned(10, 1.0); shape.buffer = partial; shape.worldGate.request(); h.frame(set);
			check(shape.shownBuffer == next && partial.getSentCount() < 10, "a new buffer half sent (" + partial.getSentCount() + ")");
			SectionedBuffer newer = sectioned(2, 0); shape.buffer = newer; shape.worldGate.request(); // what doUpdate does under the lock
			h.frame(set);
			boolean closed = true; for(SectionedBuffer.Section s: partial.getSections()) closed &= ((SectionBuffer) s.getBuffer()).closes == 1;
			check(closed && shape.shownBuffer == newer && !shape.upload.isSending(partial), "the next frame: the half-sent buffer closed on the GPU, the newer one sent and shown");
			// A frame without the lock (a generation running) leaves the sending alone
			SectionedBuffer waiting = sectioned(10, 1.0); shape.buffer = waiting; shape.worldGate.request(); h.frame(set);
			int sent = waiting.getSentCount();
			Thread holder = new Thread(() -> { shape.lock.lock(); try { busy(30); } finally { shape.lock.unlock(); } }); holder.start(); busy(5);
			h.frame(set); h.frame(set);
			check(waiting.getSentCount() == sent && shape.shownBuffer == newer, "the lock taken by a generation: nothing sent, the world keeps what it shows");
			holder.join();
			int n = 0; while(shape.shownBuffer != waiting && n < 50){ h.frame(set); n++; }
			check(shape.shownBuffer == waiting, "the lock free again: the sending goes on to the end");
			// The shape is refocusing (its new world buffer not made yet): nothing is sent, nothing dropped
			shape.buffer = sectioned(3, 0); shape.worldGate.request();
			Field asked = Shape.class.getDeclaredField("refocusAsked"); asked.setAccessible(true); asked.setLong(shape, asked.getLong(shape) + 1);
			h.frame(set);
			check(((SectionedBuffer) shape.buffer).getSentCount() == 0 && shape.shownBuffer == waiting, "a focus change still making its buffer: nothing sent");
			Field done = Shape.class.getDeclaredField("refocusDone"); done.setAccessible(true); done.setLong(shape, asked.getLong(shape));
			h.frame(set);
			check(((SectionedBuffer) shape.buffer).getSentCount() == 3 && shape.shownBuffer == shape.buffer, "made: sent and shown");
		}

		System.out.println("-- through Shape, on the real executor: the same blocks, nothing leaks");
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(SectionTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgsection").toFile(); BuildGuide.config = new Config(dir);
		TimingLog.sink = line -> {};
		BuildGuide.config.focusFrom.value = 0;
		final boolean[] sectionedMode = {false};
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(SectionTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? (sectionedMode[0] ? new SectionedBuffer(FocusWorldTest.RecBuffer::new) : new FocusWorldTest.RecBuffer()) : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
		{
			ShapeIsland island = FocusWorldTest.place(new ShapeIsland());
			Field setField = Shape.class.getDeclaredField("shapeSet"); setField.setAccessible(true); cubeSize((ShapeSet) setField.get(island), 0.6);
			FocusWorldTest.setProp(island, "propertySpikes", 10); FocusWorldTest.setProp(island, "propertyLayers", true);
			FocusWorldTest.setProp(island, "propertyWidthX", 81); FocusWorldTest.setProp(island, "propertyWidthZ", 81); FocusWorldTest.setProp(island, "propertyDepth", 48);
			long t0 = System.nanoTime(); island.update(); FocusWorldTest.settle(island); long plainMs = (System.nanoTime() - t0) / 1000000;
			List<Long> plain = new ArrayList<>(((FocusWorldTest.RecBuffer) island.buffer).cubes); Collections.sort(plain);
			sectionedMode[0] = true;
			t0 = System.nanoTime(); island.update(); FocusWorldTest.settle(island); long sectionedMs = (System.nanoTime() - t0) / 1000000;
			SectionedBuffer sb = (SectionedBuffer) island.buffer; List<Long> joined = new ArrayList<>();
			for(SectionedBuffer.Section s: sb.getSections()) joined.addAll(((FocusWorldTest.RecBuffer) s.getBuffer()).cubes);
			Collections.sort(joined);
			check(joined.equals(plain) && plain.size() == island.getNumberOfBlocks() + 1, "island " + island.getNumberOfBlocks() + " blocks with layers and spikes: the sections hold the same cubes (origin cube included) in " + sb.getSectionCount() + " sections");
			System.out.println("     generation (with settling): one buffer " + plainMs + " ms, in sections " + sectionedMs + " ms");
			island.setFocus(FocusFilter.Part.CAP, 0); FocusWorldTest.settle(island);
			SectionedBuffer cap = (SectionedBuffer) island.buffer; Set<Long> capCubes = new HashSet<>();
			for(SectionedBuffer.Section s: cap.getSections()) capCubes.addAll(((FocusWorldTest.RecBuffer) s.getBuffer()).cubes);
			Set<Long> want = FocusWorldTest.select(island, new FocusFilter(FocusFilter.Part.CAP, 0)); want.add(LocalPos.pack(0, 0, 0));
			check(cap != sb && capCubes.equals(want), "a focus change: a new sectioned buffer with only the cap (" + (capCubes.size() - 1) + " blocks)");
		}
		{
			// Ownership with sections: a cycle of generations, a focus change, sending in parts, a generation and a
			// focus change while a buffer is half sent, removal. Every section's memory freed once, never used after
			made.clear(); long baseline = VertexMemory.liveBytes(), accounts = VertexMemory.openAccounts();
			BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(SectionTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new SectionedBuffer(() -> new SectionBuffer(0.3)) : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : m.isDefault() ? InvocationHandler.invokeDefault(p, m, x) : null);
			ShapeIsland island = FocusWorldTest.place(new ShapeIsland());
			Field setField = Shape.class.getDeclaredField("shapeSet"); setField.setAccessible(true); ShapeSet set = (ShapeSet) setField.get(island); cubeSize(set, 0.6);
			FocusWorldTest.setProp(island, "propertyLayers", true);
			h.menuOpen = false; h.mode = Mode.LIVE;
			for(int i = 0; i < 4; i++){ island.update(); FocusWorldTest.settle(island); for(int f = 0; f < 200 && island.shownBuffer != island.buffer; f++) h.frame(set); }
			check(island.shownBuffer == island.buffer, "four generations, each sent in parts and shown");
			island.update(); FocusWorldTest.settle(island); h.frame(set); // half sent
			island.update(); FocusWorldTest.settle(island); h.frame(set); // a newer one: the half-sent one dropped
			island.setFocus(FocusFilter.Part.BODY, 0); FocusWorldTest.settle(island); h.frame(set); // a focus change while one is half sent
			island.setFocus(FocusFilter.Part.ALL, 0); FocusWorldTest.settle(island);
			for(int f = 0; f < 200 && island.shownBuffer != island.buffer; f++) h.frame(set);
			check(island.shownBuffer == island.buffer, "after them: the last one sent and shown");
			island.dispose(); Thread.sleep(100);
			int misuse = 0, twice = 0, unfreed = 0, sentNotClosed = 0;
			synchronized(made){ for(SectionBuffer b: made){ misuse += b.writesAfterRelease + b.endAfterRelease; if(b.frees > 1) twice++; if(!b.memory.isReleased()) unfreed++; if(b.sent && b.closes == 0) sentNotClosed++; } }
			check(misuse == 0 && twice == 0, "no section written or sent after its release, none freed twice (" + made.size() + " sections made)");
			check(unfreed == 0 && VertexMemory.liveBytes() == baseline && VertexMemory.openAccounts() == accounts, "every section's native memory freed: back to the baseline (" + unfreed + " left)");
			check(sentNotClosed == 0, "every section that reached the GPU closed there (dropped, replaced or removed)");
		}

		System.out.println("-- the timing lines");
		resetLog();
		TimingLog.recordWorld("close", 5200000, 300000, 1100000, 49568, -1, 0, 1, 5200000);
		check(lines.equals(Arrays.asList("[Build Guide] timing: phase=world-buffer ms=7 blocks=49568 reason=close world-end=5.2 world-close=0.3 world-other=1.1 native=0.0")), "without sections: the line as before: " + lines);
		resetLog();
		GpuMemory.allocated(3 * 1048576); GpuMemory.allocated(1048576 / 2);
		TimingLog.recordWorld("apply", 12300000, 400000, 900000, 107518, 50000, 260, 5, 3100000);
		String w = lines.get(0);
		check(w.endsWith(" focus=50000/107518 sections=260 frames=5 maxframe=3.1 gpu=" + GpuMemory.liveMegabytes()) && w.contains(" ms=14 ") && w.contains(" world-end=12.3 "), "with sections: sections=, frames=, maxframe=, gpu= at the end: " + w);
		GpuMemory.freed(3 * 1048576); GpuMemory.freed(1048576 / 2);
		check(GpuMemory.liveBuffers() == 0 && GpuMemory.liveBytes() == 0, "GpuMemory: what was allocated is freed");
		resetLog();
		long[] now = {1000}; RenderStats.clock = () -> now[0];
		Method rr = RenderStats.class.getDeclaredMethod("reset"); rr.setAccessible(true); rr.invoke(null);
		for(int f = 0; f < 100; f++){ RenderStats.sections(30, 260); RenderStats.frame(f == 50 ? 4100000 : 400000); now[0] += 100; }
		check(lines.isEmpty(), "10 s of frames: nothing yet (every 30 s)");
		for(int f = 0; f < 201; f++){ RenderStats.sections(30, 260); RenderStats.frame(400000); now[0] += 100; } // the 301st frame is at 30 s
		String r = lines.size() == 1 ? lines.get(0) : "";
		check(r.startsWith("[Build Guide] timing: phase=render frames=") && r.contains(" sections=30/260 gpu=") && r.contains(" native="), "at 30 s: one render line: " + r);
		check(part(r, "frames") == 301, "frames: those of the period (" + word(r, "frames") + ")");
		check(word(r, "max").equals("4.1") && Math.abs(part(r, "avg") - 0.41) < 0.02, "avg with two decimals (" + word(r, "avg") + "), max the longest frame (" + word(r, "max") + ")");
		for(int f = 0; f < 299; f++){ RenderStats.sections(10, 260); RenderStats.frame(200000); now[0] += 100; }
		check(lines.size() == 1, "a new period starts after the line");
		RenderStats.sections(10, 260); RenderStats.frame(200000); now[0] += 100;
		RenderStats.sections(10, 260); RenderStats.frame(200000);
		check(lines.size() == 2 && lines.get(1).contains(" sections=10/260 ") && word(lines.get(1), "max").equals("0.2"), "the next period on its own numbers: " + lines.get(lines.size() - 1));
		resetLog();
		for(int i = 0; i < TimingLog.maxRenderLines + 10; i++) TimingLog.recordRender(1, 1, 1, 0, 0);
		check(lines.size() == TimingLog.maxRenderLines && lines.get(lines.size() - 1).endsWith("(limit of 120 render lines reached, no more this session)"), "at most 120 render lines (an hour), the last one says so");
		TimingLog.record(TimingLog.GENERATION, 50, 10);
		check(lines.size() == TimingLog.maxRenderLines + 1, "and they never use up the other phases' 200 lines");
		check(WorldUpload.budgetNanos == 3000000, "the budget: 3 ms of sending per frame");

		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
