import java.util.*;
import brentmaas.buildguide.common.shape.*;
import brentmaas.buildguide.common.shape.ValidationState.NearBlock;
// Etapa 2.5 Part 3: new error concept. Expected position: solid -> OK, ignored type -> IGNORED, anything
// else (air, torch, water) -> MISSING (no WRONG). Structure error = solid within 2 of the shape, not part
// of it, including inside the cavity of a hollow shape.
public class ClassifyTest {
	static void check(boolean c, String m){ System.out.println((c?"OK   ":"FAIL ")+m); }
	static ValidationState scanned(List<Long> exp){ ValidationState s=new ValidationState(); s.beginScan(exp); for(long e: exp) s.setStatus(e, ValidationState.MISSING, null); s.endScan(); return s; }

	public static void main(String[] a) throws Exception {
		System.out.println("-- expected positions (updateBlock: air, solid, ignoredType, name)");
		List<Long> wall=NearDiagTest.wall(); long p=LocalPos.pack(4,2,0);
		ValidationState s=scanned(wall);
		s.updateBlock(p,false,true,false,"Stone"); check(s.getStatus(p)==ValidationState.OK && s.getOk()==1, "stone on the guideline -> OK");
		s.updateBlock(p,false,true,false,"Dirt"); check(s.getStatus(p)==ValidationState.OK, "dirt -> OK");
		s.updateBlock(p,false,true,false,"Bricks"); check(s.getStatus(p)==ValidationState.OK, "bricks -> OK");
		s.updateBlock(p,false,false,false,"Torch"); check(s.getStatus(p)==ValidationState.MISSING && s.getOk()==0 && s.getMissing()==50 && s.getNearCount()==0 && s.getIgnoredBlockName(p)==null, "torch -> MISSING, no name, no error");
		s.updateBlock(p,false,false,false,"Water"); check(s.getStatus(p)==ValidationState.MISSING, "water -> MISSING");
		s.updateBlock(p,false,false,true,"Scaffolding"); check(s.getStatus(p)==ValidationState.IGNORED && s.getIgnored()==1 && s.getMissing()==50 && "Scaffolding".equals(s.getIgnoredBlockName(p)), "scaffolding (ignored) -> IGNORED, still missing");
		s.updateBlock(p,true,false,false,null); check(s.getStatus(p)==ValidationState.MISSING && s.getIgnored()==0, "air -> MISSING");
		boolean anyReserved=false; for(long e: wall) if(s.getStatus(e)==3) anyReserved=true;
		check(!anyReserved, "status 3 (was WRONG) never produced");
		System.out.println("-- around the shape");
		s.updateBlock(LocalPos.pack(4,2,1),false,true,true,"Scaffolding"); check(s.getNearCount()==0, "ignored type next to the shape: not an error");
		s.updateBlock(LocalPos.pack(4,2,1),false,false,false,"Torch"); check(s.getNearCount()==0, "non-solid next to the shape: not an error");
		s.updateBlock(LocalPos.pack(4,2,1),false,true,false,"Stone"); check(s.getNearCount()==1 && "Stone".equals(s.getNearBlocks().get(0).blockName), "solid next to the shape: error, named");
		s.updateBlock(LocalPos.pack(4,2,1),true,false,false,null); check(s.getNearCount()==0, "error broken -> gone");

		System.out.println("-- cavity of hollow shapes: every interior cell filled with stone");
		Set<Long> sphere=new LinkedHashSet<>(); int r=6;
		for(int x=-r;x<=r;x++) for(int y=-r;y<=r;y++) for(int z=-r;z<=r;z++){ double d=Math.sqrt(x*x+y*y+z*z); if(d<r && d>=r-1) sphere.add(LocalPos.pack(x,y,z)); }
		Set<Long> sphereInside=new LinkedHashSet<>();
		for(int x=-r;x<=r;x++) for(int y=-r;y<=r;y++) for(int z=-r;z<=r;z++){ long q=LocalPos.pack(x,y,z); if(Math.sqrt(x*x+y*y+z*z)<r-1 && !sphere.contains(q)) sphereInside.add(q); }
		cavity("hollow sphere r=6 (simulated shell r-1 <= d < r)", sphere, sphereInside);

		Set<Long> cone=new LinkedHashSet<>();
		ShapeCone.enumerate(ShapeCone.direction.Y, 6f, 8f, false, 0f, ShapeCone.Mode.HOLLOW, 1f, 1, (x,y,z)->cone.add(LocalPos.pack(x,y,z)));
		// Interior of the real hollow cone: per layer, cells strictly between the wall cells of that layer (row scan in x)
		Set<Long> coneInside=new LinkedHashSet<>();
		for(int y=-1;y<=9;y++) for(int z=-7;z<=7;z++){
			int lo=Integer.MAX_VALUE, hi=Integer.MIN_VALUE; for(int x=-7;x<=7;x++) if(cone.contains(LocalPos.pack(x,y,z))){ lo=Math.min(lo,x); hi=Math.max(hi,x); }
			for(int x=lo+1;x<hi;x++){ long q=LocalPos.pack(x,y,z); if(cone.contains(q)) continue; int zlo=Integer.MAX_VALUE,zhi=Integer.MIN_VALUE; for(int zz=-7;zz<=7;zz++) if(cone.contains(LocalPos.pack(x,y,zz))){ zlo=Math.min(zlo,zz); zhi=Math.max(zhi,zz);} if(z>zlo && z<zhi) coneInside.add(q); }
		}
		cavity("hollow cone base 6 height 8 (real ShapeCone.enumerate)", cone, coneInside);
	}

	// Fill every interior cell with stone: the flagged set (scan mirror and incremental) must equal the cells
	// whose brute-force distance to the shape is <= 2; touching the wall must be flagged
	static void cavity(String label, Set<Long> shape, Set<Long> inside){
		List<Long> exp=new ArrayList<>(shape);
		Set<Long> within2=new HashSet<>(); int touching=0, deep=0;
		for(long q: inside){ double best=Double.MAX_VALUE; for(long e: shape){ double dx=LocalPos.unpackX(q)-LocalPos.unpackX(e), dy=LocalPos.unpackY(q)-LocalPos.unpackY(e), dz=LocalPos.unpackZ(q)-LocalPos.unpackZ(e); best=Math.min(best, Math.sqrt(dx*dx+dy*dy+dz*dz)); } if(best<=2) within2.add(q); else deep++; if(best==1) touching++; }
		Set<Long> solid=new HashSet<>(shape); solid.addAll(inside);
		Set<Long> byScan=new HashSet<>(); for(NearBlock nb: NearDiagTest.scanNear(new HashSet<>(shape), solid)) if(inside.contains(nb.localPos)) byScan.add(nb.localPos);
		ValidationState s=scanned(exp); for(long q: inside) s.updateBlock(q,false,true,false,"Stone");
		Set<Long> byInc=new HashSet<>(); for(NearBlock nb: s.getNearBlocks()) byInc.add(nb.localPos);
		System.out.printf("   %s: %d wall blocks, %d interior cells (%d touching the wall, %d within 2, %d deeper)%n", label, shape.size(), inside.size(), touching, within2.size(), deep);
		check(touching>0 && within2.size()>0, label+": has interior cells near the wall");
		check(byScan.equals(within2), label+": scan flags exactly the interior cells within 2 ("+byScan.size()+"/"+within2.size()+")");
		check(byInc.equals(within2), label+": incremental flags exactly the interior cells within 2 ("+byInc.size()+"/"+within2.size()+")");
	}
}
