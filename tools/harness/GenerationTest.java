import java.lang.reflect.*;
import java.util.*;
import brentmaas.buildguide.common.shape.*;
// E1: Shape.generation advances only on a successful generation; PreviewModel.snapshot refuses a
// generation that ended with error (partial expectedBlocks) and records the generation it copied.
// update() itself needs the config and the executor, so the harness drives finishGeneration(), the
// method update()'s finally calls with the lock held.
public class GenerationTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	@SuppressWarnings("unchecked")
	public static void main(String[] a) throws Exception {
		ShapeBridge s=new ShapeBridge();
		Method finish=Shape.class.getDeclaredMethod("finishGeneration"); finish.setAccessible(true);
		Field fe=Shape.class.getDeclaredField("expectedBlocks"); fe.setAccessible(true);
		Set<Long> expected=(Set<Long>)fe.get(s);
		check(s.getGeneration()==0 && !s.ready, "new shape: generation 0, not ready");
		// Successful generation
		s.lock.lock(); s.error=false; expected.add(LocalPos.pack(1,2,3)); finish.invoke(s); s.lock.unlock();
		check(s.getGeneration()==1 && s.ready, "successful generation: generation 1, ready");
		PreviewModel m=PreviewModel.snapshot(s);
		check(m!=null && m.generation==1 && m.positions.length==1, "snapshot of a successful generation records generation 1");
		// Cancelled / failed generation: expectedBlocks partial, error set
		s.lock.lock(); s.ready=false; s.error=true; expected.add(LocalPos.pack(4,5,6)); finish.invoke(s); s.lock.unlock();
		check(s.getGeneration()==1 && s.ready && s.error, "cancelled/failed generation: ready again, generation stays 1");
		check(PreviewModel.snapshot(s)==null, "snapshot refused after an error (F6: expectedBlocks may be partial)");
		// Generating: not ready
		s.lock.lock(); s.ready=false; s.error=false; s.lock.unlock();
		check(PreviewModel.snapshot(s)==null, "snapshot refused while not ready");
		// Next successful generation
		s.lock.lock(); finish.invoke(s); s.lock.unlock();
		PreviewModel m2=PreviewModel.snapshot(s);
		check(s.getGeneration()==2 && m2!=null && m2.generation==2 && m2.positions.length==2, "next success: generation 2, snapshot carries it");
		// Lock held by the generation thread: tryLock fails, no blocking
		Thread gen=new Thread(()->{ s.lock.lock(); try{ Thread.sleep(300);}catch(InterruptedException e){} s.lock.unlock(); });
		gen.start(); Thread.sleep(50); long t0=System.nanoTime(); PreviewModel busy=PreviewModel.snapshot(s); double ms=(System.nanoTime()-t0)/1e6; gen.join();
		check(busy==null && ms<20, "lock busy: snapshot returns null at once ("+String.format("%.2f",ms)+" ms), try next frame");
		// withValidation keeps the generation
		check(m2.withValidation(new ValidationState()).generation==2, "withValidation keeps the generation");
		check(PreviewModel.of(new ArrayList<Long>()).generation==-1, "of() without a shape: generation -1");
	}
}
