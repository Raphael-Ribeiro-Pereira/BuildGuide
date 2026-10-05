import brentmaas.buildguide.common.shape.LocalPos;
import brentmaas.buildguide.common.shape.ValidationState;
import java.util.*;
// Ghost near-block investigation (not part of the suite: it reports, it does not assert).
// The real ValidationState with the same conversion IncrementalValidator does (isInRange, then
// updateBlock) is driven by a model of the 1.21.11 client block-change code, written from the
// decompiled ClientLevel / BlockStatePredictionHandler / MultiPlayerGameMode:
//  - ClientLevel.setBlock (the mixin hook, fires when it returns true), retaining the old server-
//    verified state while a prediction is active;
//  - setServerVerifiedBlockState: updateKnownServerState absorbs the update when an entry exists,
//    otherwise Level.setBlock through invokespecial (super), which does NOT reach the hook;
//  - handleBlockChangedAck -> endPredictionsUpTo -> syncBlockState (virtual setBlock: hooked).
// IncrementalValidator itself needs net.minecraft types, so its 3 lines are repeated here.
public class GhostTest {
	static final int AIR = 0, STONE = 1;
	static final long P = LocalPos.pack(2, 1, 0);
	// The second injection (MixinClientLevel.setServerVerifiedBlockState): after every server update the hook
	// re-reads the world at that position. Off = the code before the ghost fix
	static boolean second = false;

	// ---- client model
	static class Entry { int seq; int state; Entry(int s, int st){ seq = s; state = st; } }
	static class Client {
		int world = AIR; Entry entry = null; int seqNr = 0; boolean predicting = false;
		ValidationState vs = new ValidationState();
		Client(){
			List<Long> exp = new ArrayList<>(); for(int x = 0; x < 6; x++) exp.add(LocalPos.pack(x, 0, 0));
			vs.beginScan(exp); for(long p: exp) vs.setStatus(p, ValidationState.OK, null); vs.endScan();
		}
		void hook(int state){ // MixinClientLevel + IncrementalValidator.onBlockChanged
			if(!vs.isInRange(2, 1, 0)) return;
			boolean air = state == AIR, solid = !air;
			vs.updateBlock(P, air, solid, false, air ? null : "Stone");
		}
		boolean levelSetBlock(int state){ if(world == state) return false; world = state; return true; } // Level.setBlock
		boolean clientSetBlock(int state){ // ClientLevel.setBlock (hooked)
			boolean r;
			if(predicting){ int old = world; r = levelSetBlock(state); if(r){ if(entry != null) entry.seq = seqNr; else entry = new Entry(seqNr, old); } }
			else r = levelSetBlock(state);
			if(r) hook(state);
			return r;
		}
		void action(int state){ ++seqNr; predicting = true; clientSetBlock(state); predicting = false; } // startPrediction + setBlock
		void serverUpdate(int state){ // setServerVerifiedBlockState
			if(entry != null) entry.state = state; // absorbed: the world is unchanged
			else levelSetBlock(state); // super.setBlock: no hook of its own
			if(second) hook(world); // MixinClientLevel.buildguide$onServerVerified
		}
		void ack(int n){ // endPredictionsUpTo + syncBlockState
			if(entry != null && entry.seq <= n){ int st = entry.state; entry = null; if(world != st) clientSetBlock(st); }
		}
		boolean ghost(){ return world == AIR && vs.getNearCount() > 0; }
		boolean missing(){ return world == STONE && vs.getNearCount() == 0; }
	}

	// ---- scenario: client steps C = [place, break]; server messages S (in order) with the client step each answers
	static class Msg { final String name; final int after; final Runnable[] run = new Runnable[1]; Msg(String n, int a){ name = n; after = a; } }
	interface Make { Msg[] make(Client c); }

	static int total, ghosts, missings;
	static List<String> ghostList = new ArrayList<>(), missingList = new ArrayList<>();

	// interleave: client step i (0 place, 1 break) with server messages in order; a message needs its client step done
	static void run(String variant, Make make){
		total = ghosts = missings = 0; ghostList.clear(); missingList.clear();
		Client probe = new Client(); int n = make.make(probe).length;
		// order encoded as a sequence of 'p', 'b' (client steps, p before b) and 's' (next server message)
		gen(variant, make, new StringBuilder(), 0, 0, n);
		System.out.println(String.format("%-52s orderings %3d   ghost marker %3d   missing marker %3d", variant, total, ghosts, missings));
		for(String s: ghostList) System.out.println("      GHOST   " + s);
		for(String s: missingList) System.out.println("      MISSING " + s);
	}
	static void gen(String variant, Make make, StringBuilder sb, int stepsDone, int msgsDone, int nMsgs){
		if(stepsDone == 2 && msgsDone == nMsgs){ play(make, sb.toString()); return; }
		if(stepsDone < 2){ sb.append(stepsDone == 0 ? 'p' : 'b'); gen(variant, make, sb, stepsDone + 1, msgsDone, nMsgs); sb.setLength(sb.length() - 1); }
		if(msgsDone < nMsgs){
			Client probe = new Client(); Msg m = make.make(probe)[msgsDone];
			if(stepsDone > m.after){ sb.append('s'); gen(variant, make, sb, stepsDone, msgsDone + 1, nMsgs); sb.setLength(sb.length() - 1); }
		}
	}
	static void play(Make make, String order){
		Client c = new Client(); Msg[] ms = make.make(c); int mi = 0; StringBuilder log = new StringBuilder();
		for(char ch: order.toCharArray()){
			if(ch == 'p'){ c.action(STONE); log.append("place "); }
			else if(ch == 'b'){ c.action(AIR); log.append("break "); }
			else { ms[mi].run[0].run(); log.append(ms[mi].name).append(' '); mi++; }
		}
		total++;
		String line = log.toString().trim() + "   -> world " + (c.world == AIR ? "AIR" : "STONE") + ", errors " + c.vs.getNearCount();
		if(c.ghost()){ ghosts++; ghostList.add(line); }
		if(c.missing()){ missings++; missingList.add(line); }
	}
	static Msg U(Client c, String nm, int after, int st){ Msg m = new Msg(nm, after); m.run[0] = () -> c.serverUpdate(st); return m; }
	static Msg A(Client c, String nm, int after, int n){ Msg m = new Msg(nm, after); m.run[0] = () -> c.ack(n); return m; }

	public static void main(String[] a){
		System.out.println("Model of the 1.21.11 client; real ValidationState. 'p' place STONE at P (a near block), 'b' break it (client steps),");
		System.out.println("server messages U(state)=BlockUpdate, A(n)=BlockChangedAck. 'U(x)#1' answers the place, '#2' the break.\n");
		System.out.println("== A. vanilla server order: update before its ack, one answer per action");
		run("A1 U(STONE) A1 U(AIR) A2", c -> new Msg[]{ U(c,"U(STONE)#1",0,STONE), A(c,"A1",0,1), U(c,"U(AIR)#2",1,AIR), A(c,"A2",1,2) });
		System.out.println("\n== B. place and break processed in one server tick: only the final state is sent, one coalesced ack");
		run("B  U(AIR) A2", c -> new Msg[]{ U(c,"U(AIR)#12",1,AIR), A(c,"A2",1,2) });
		run("B' A2 only (state already matches)", c -> new Msg[]{ A(c,"A2",1,2) });
		System.out.println("\n== C. the server removes the block by itself (no client action behind it): falling block, other player, explosion, decay");
		runServerRemoval();
		System.out.println("\n== D. stress, NOT vanilla order: ack may overtake its update");
		run("D  A1 before U(STONE), then break answers", c -> new Msg[]{ A(c,"A1",0,1), U(c,"U(STONE)#1",0,STONE), A(c,"A2",1,2), U(c,"U(AIR)#2",1,AIR) });
	}

	// C: place is answered normally, then a server-side removal arrives with no prediction entry
	static boolean runServerRemoval(){
		Client c = new Client();
		c.action(STONE); c.serverUpdate(STONE); c.ack(1);
		System.out.println("      after place + its answers: world " + (c.world == STONE ? "STONE" : "AIR") + ", errors " + c.vs.getNearCount() + ", entry " + (c.entry == null ? "none" : "kept"));
		c.serverUpdate(AIR); // BlockUpdate(AIR) with no entry -> Level.setBlock via super: no hook
		System.out.println(String.format("%-52s world %s, errors %d   %s", "C  server sets AIR, no entry", c.world == AIR ? "AIR" : "STONE", c.vs.getNearCount(), c.ghost() ? "GHOST MARKER" : "ok"));
		return c.ghost();
	}
}
