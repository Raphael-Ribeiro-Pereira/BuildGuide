import java.util.*;
import brentmaas.buildguide.common.shape.*;
public class StateTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	public static void main(String[] a) throws Exception {
		ValidationState s = new ValidationState();
		check(!s.isValidated() && s.getTotal()==0, "fresh: not validated, total 0");
		List<Long> exp = new ArrayList<>(); for(int i=0;i<10;i++) exp.add(LocalPos.pack(i,0,0));
		s.beginScan(exp);
		check(!s.isValidated() && s.getTotal()==10 && s.getOk()==0, "beginScan: total 10, not yet validated");
		for(int i=0;i<10;i++) s.setStatus(exp.get(i), i<7?ValidationState.OK:(i<9?ValidationState.MISSING:ValidationState.IGNORED), i==9?"Scaffolding":null);
		s.endScan();
		check(s.isValidated() && s.getOk()==7 && s.getMissing()==3 && s.getIgnored()==1, "scan: 7 ok, 3 missing (1 of them ignored)");
		check(Math.abs(s.getProgress()-0.7)<1e-9, "progress 0.7");
		check("Scaffolding".equals(s.getIgnoredBlockName(exp.get(9))), "ignored block name kept");
		// incremental transitions (2.2): missing -> ok, ok -> missing
		s.setStatus(exp.get(7), ValidationState.OK, null); s.setStatus(exp.get(0), ValidationState.MISSING, null);
		check(s.getOk()==7 && s.getMissing()==3, "transitions keep counters consistent");
		s.setStatus(exp.get(9), ValidationState.OK, null);
		check(s.getIgnored()==0 && s.getIgnoredBlockName(exp.get(9))==null, "ignored -> ok clears name");
		// exclusion (2.3): removes from total, not missing
		s.exclude(exp.get(0)); check(s.getTotal()==9 && s.getMissing()==1, "exclude drops total and its counter");
		s.setStatus(LocalPos.pack(99,99,99), ValidationState.OK, null); check(s.getTotal()==9, "unknown position ignored");
		check(s.getStatus(exp.get(1))==ValidationState.OK && s.getStatus(12345L)==ValidationState.UNKNOWN, "O(1) lookup");
		check(s.getPositions(ValidationState.MISSING).size()==1, "position list by status");
		s.invalidate(); check(!s.isValidated() && s.getTotal()==0 && s.getOk()==0, "invalidate clears everything");
		// Bridge regression: invalidate() call must not change geometry
		BridgeTest.Cfg c = new BridgeTest.Cfg(new int[][]{{0,0,0},{20,0,0},{20,0,20}}); c.width=9; c.railMode="CONTINUOUS"; BridgeTest.run("regression L w9 rails (466)", c);
	}
}
