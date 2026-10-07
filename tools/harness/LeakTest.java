import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import brentmaas.buildguide.common.BuildGuide;
import brentmaas.buildguide.common.Config;
import brentmaas.buildguide.common.ILogHandler;
import brentmaas.buildguide.common.TimingLog;
import brentmaas.buildguide.common.screen.AbstractScreenHandler;
import brentmaas.buildguide.common.screen.IScreenWrapper;
import brentmaas.buildguide.common.shape.*;
// Native memory leak fix (2026-10-07): every vertex builder is freed exactly once, after its upload or when
// its generation is discarded. The Fabric ShapeBuffer does not run here; what is pure is: VertexMemory's
// accounting (vanilla's growth rule, release once, no write after release) and Shape's ownership rules, on
// the real generation executor with buffers that keep the same accounts and log every misuse: a cancelled,
// superseded, failed or removed generation is freed once, never while it is still being written.
public class LeakTest {
	static int fails = 0;
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); if(!c) ++fails; }

	// Like the Fabric ShapeBuffer: an account per buffer, end() uploads and releases, close() is the GPU side only
	static final List<FakeBuffer> buffers = Collections.synchronizedList(new ArrayList<>());
	static class FakeBuffer implements IShapeBuffer {
		final VertexMemory.Account memory = VertexMemory.open(28);
		volatile int writesAfterRelease = 0, endAfterRelease = 0, gpuCloses = 0, frees = 0, extraReleases = 0; volatile boolean ended = false;
		FakeBuffer(){ buffers.add(this); }
		public void setColour(int r, int g, int b, int a){}
		public void pushVertex(double x, double y, double z){ if(memory.isReleased()) writesAfterRelease++; memory.write(16); }
		public void end(){ if(memory.isReleased()){ endAfterRelease++; return; } ended = true; releaseVertexData(); }
		public void close(){ gpuCloses++; }
		@Override public void releaseVertexData(){ if(memory.release()) frees++; else extraReleases++; }
	}
	// cubes per generation, a pause after each (so a generation can be caught running), and a cube to fail at
	static class TestShape extends Shape {
		volatile int cubes = 20, failAt = -1; volatile long pauseMillis = 0;
		protected void updateShape(IShapeBuffer buffer) throws Exception {
			for(int i = 0; i < cubes; i++){
				if(i == failAt) throw new RuntimeException("generation failure (test)");
				addShapeCube(buffer, i, 0, 0);
				if(pauseMillis > 0) Thread.sleep(pauseMillis);
			}
		}
	}
	static TestShape shape() throws Exception {
		TestShape s = new TestShape();
		ShapeSet set = OriginScanTest.newSet(new Shape[]{s}, 0, 64, 0);
		Field f = Shape.class.getDeclaredField("shapeSet"); f.setAccessible(true); f.set(s, set);
		return s;
	}
	// Until no generation runs or waits: the lock is free and the shape ready, steadily for 100 ms
	static void settle(Shape s) throws Exception {
		long stable = 0;
		for(int i = 0; i < 1000 && stable < 100; i++){ Thread.sleep(10); stable = !s.lock.isLocked() && !s.lock.hasQueuedThreads() && s.ready ? stable + 10 : 0; }
	}
	// What the render thread does when the gate lets a finished generation into the world
	static void apply(Shape s){
		s.lock.lock(); try { if(s.ready && !s.error && s.buffer != s.shownBuffer){ s.buffer.end(); if(s.shownBuffer != null) s.shownBuffer.close(); s.shownBuffer = s.buffer; } } finally { s.lock.unlock(); }
	}
	static int misuse(){ int n = 0; synchronized(buffers){ for(FakeBuffer b: buffers) n += b.writesAfterRelease + b.endAfterRelease; } return n; }
	static boolean freedOnce(FakeBuffer b){ return b.frees == 1 && b.memory.isReleased(); }

	public static void main(String[] a) throws Exception {
		System.out.println("-- VertexMemory: vanilla's growth rule and the accounts");
		check(VertexMemory.grownCapacity(28, 16) == 28 && VertexMemory.grownCapacity(28, 32) == 56 && VertexMemory.grownCapacity(56, 200) == 200, "28 bytes hold one vertex; the second doubles to 56; a write past double goes straight to what it needs");
		long cap = 28, need = 0; boolean rule = true, steps = true;
		for(int v = 0; v < 2000000; v++){ need += 16; long next = VertexMemory.grownCapacity(cap, need); if(next != cap){ steps &= next - cap == Math.min(cap, VertexMemory.maxGrowth) || next == need; cap = next; } rule &= cap >= need; }
		check(rule && steps && cap - need < VertexMemory.maxGrowth, "2 000 000 vertices (32 MB): always enough room, each step doubles until 2 MiB then adds 2 MiB (" + cap + " for " + need + ")");
		long before = VertexMemory.liveBytes();
		VertexMemory.Account acc = VertexMemory.open(28);
		for(int v = 0; v < 24 * 1000; v++) acc.write(16);
		check(VertexMemory.liveBytes() - before == acc.getCapacity() && acc.getCapacity() >= 24 * 1000 * 16, "an account counts its builder's capacity (" + acc.getCapacity() + " bytes for 1000 cubes)");
		check(acc.release() && VertexMemory.liveBytes() == before, "release gives all of it back");
		check(!acc.release() && VertexMemory.liveBytes() == before, "a second release does nothing (closed exactly once)");
		boolean threw = false; try { acc.write(16); } catch(IllegalStateException e) { threw = true; }
		check(threw && VertexMemory.liveBytes() == before, "a write after the release throws instead of touching freed memory");
		check(VertexMemory.openAccounts() == 0, "no account left open");

		System.out.println("-- Shape: who frees a generation's vertex data, and when");
		BuildGuide.screenHandler = new AbstractScreenHandler(){ public IScreenWrapper createWrapper(Translatable t){ return null; } public void showNone(){} public String translate(String k){ return k; } public String translate(String k, Object... v){ return k; } };
		BuildGuide.logHandler = (ILogHandler) Proxy.newProxyInstance(LeakTest.class.getClassLoader(), new Class<?>[]{ILogHandler.class}, (p, m, x) -> null);
		File dir = Files.createTempDirectory("bgleak").toFile(); BuildGuide.config = new Config(dir);
		BuildGuide.shapeHandler = (IShapeHandler) Proxy.newProxyInstance(LeakTest.class.getClassLoader(), new Class<?>[]{IShapeHandler.class}, (p, m, x) -> m.getName().equals("newBuffer") ? new FakeBuffer() : m.getName().equals("getPlayerPosition") ? new ShapeSet.Origin(0, 64, 0) : null);
		TimingLog.sink = line -> {};
		check(BuildGuide.config.asyncEnabled.value, "generations run on the executor (asyncEnabled, the default)");

		TestShape s = shape();
		s.update(); settle(s);
		FakeBuffer first = buffers.get(buffers.size() - 1);
		check(!first.memory.isReleased() && first.memory.getWritten() == (20 + 1) * 24 * 16, "a finished generation keeps its vertex data until it is sent (20 cubes and the origin cube, " + first.memory.getWritten() + " bytes)");
		apply(s);
		check(freedOnce(first) && first.ended && VertexMemory.liveBytes() == 0, "sent to the world: freed once, by end(); nothing left (native 0)");

		s.update(); settle(s); FakeBuffer superseded = buffers.get(buffers.size() - 1);
		s.update(); settle(s); FakeBuffer newer = buffers.get(buffers.size() - 1);
		check(freedOnce(superseded) && !superseded.ended && !newer.memory.isReleased(), "a finished generation replaced before it reached the world: freed once, when the next one started");
		check(freedOnce(first) && first.gpuCloses == 0, "the buffer the world shows is left alone (its GPU buffer stays until it is replaced)");
		apply(s);
		check(freedOnce(newer) && first.gpuCloses == 1 && VertexMemory.liveBytes() == 0, "the newer one sent: freed by end(), the old world buffer closed once; native 0");

		System.out.println("-- cancelled while running");
		s.cubes = 300; s.pauseMillis = 1; buffers.clear();
		s.update(); Thread.sleep(40); FakeBuffer running = buffers.get(0);
		long writtenBefore = running.memory.getWritten();
		s.update();
		check(writtenBefore > 0 && writtenBefore < 300 * 24 * 16, "a generation caught mid-way (" + writtenBefore / (24 * 16) + " of 300 cubes)");
		settle(s);
		check(freedOnce(running) && running.writesAfterRelease == 0 && !running.ended, "it was freed once, by its own generation when it stopped, never while it was still being written, never sent");
		apply(s); FakeBuffer last = buffers.get(buffers.size() - 1);
		check(freedOnce(last) && VertexMemory.liveBytes() == 0, "the generation that finished went to the world; native 0");

		System.out.println("-- many edits in a row (a held -/+ or typing)");
		s.cubes = 200; s.pauseMillis = 1; buffers.clear();
		for(int i = 0; i < 25; i++){ s.update(); Thread.sleep(i % 3 == 0 ? 15 : 3); if(i % 7 == 6){ settle(s); apply(s); } }
		settle(s); apply(s);
		int freedOnce = 0; synchronized(buffers){ for(FakeBuffer b: buffers) if(freedOnce(b)) freedOnce++; }
		check(freedOnce == buffers.size() && misuse() == 0 && VertexMemory.liveBytes() == 0 && VertexMemory.openAccounts() == 0, "25 edits, " + buffers.size() + " generations started: every one freed exactly once, no write or send after a release, native back to 0");

		System.out.println("-- failure and removal");
		s.cubes = 50; s.pauseMillis = 0; s.failAt = 30; buffers.clear();
		s.update(); settle(s); FakeBuffer failed = buffers.get(0);
		check(s.error && freedOnce(failed) && failed.memory.getWritten() == 30 * 24 * 16, "a generation that fails at cube 30: freed once by its own task (error)");
		s.failAt = -1; s.update(); settle(s); apply(s);
		TestShape gone = shape(); gone.cubes = 400; gone.pauseMillis = 1; buffers.clear();
		gone.update(); settle(gone); apply(gone); FakeBuffer shown = buffers.get(0);
		gone.update(); Thread.sleep(40); FakeBuffer generating = buffers.get(1);
		gone.dispose();
		check(shown.gpuCloses == 1, "a removed shape: the buffer in the world is closed on the render thread");
		settle(gone); Thread.sleep(50);
		check(freedOnce(generating) && generating.writesAfterRelease == 0 && freedOnce(shown), "and the generation that was running is freed once, after it stopped writing");
		TestShape idle = shape(); buffers.clear(); idle.update(); settle(idle); FakeBuffer pending = buffers.get(0);
		idle.dispose();
		check(freedOnce(pending), "a removed shape with nothing running: its pending generation freed at once");
		TestShape one = shape(), two = shape(); ShapeSet set = OriginScanTest.newSet(new Shape[]{one, null, two}, 0, 64, 0); buffers.clear();
		one.update(); two.update(); settle(one); settle(two);
		set.dispose();
		check(buffers.size() == 2 && freedOnce(buffers.get(0)) && freedOnce(buffers.get(1)), "ShapeSet.dispose (State.removeShapeSet): every shape instance of the set frees its buffers");
		check(VertexMemory.liveBytes() == 0 && VertexMemory.openAccounts() == 0, "at the end: native 0, no account open");

		System.out.println("-- the native= field");
		List<String> lines = new ArrayList<>(); TimingLog.sink = lines::add;
		VertexMemory.Account held = VertexMemory.open(3 * 1048576);
		TimingLog.record(TimingLog.PREVIEW_REBUILD, 20, 49568, "generation"); TimingLog.recordWorld("close", 9000000, 0, 0, 49568); TimingLog.record(TimingLog.SCAN, 20, 49568);
		held.release();
		check(lines.size() == 3 && lines.get(0).endsWith(" reason=generation native=3.0") && lines.get(1).endsWith(" native=3.0") && !lines.get(2).contains("native="), "preview-rebuild and world-buffer end with native=<MiB> (3.0 held); other phases do not: " + lines);
		System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
		System.exit(0); // the generation executor's threads would keep the JVM alive for a minute
	}
}
