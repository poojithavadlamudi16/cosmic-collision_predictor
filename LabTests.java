// Run from the DSA3 folder:  javac *.java && java LabTests
import java.util.*;
public class LabTests {
    static int pass=0, fail=0;
    static void check(String name, boolean ok){ if(ok){pass++; System.out.println("PASS  "+name);} else {fail++; System.out.println("FAIL  "+name);} }
    static SpaceObject o(int id,double x,double y){ return new SpaceObject(id,x,y,0,0,1); }
    static SpaceObject mv(int id,double x,double y,double vx,double vy,double r){ return new SpaceObject(id,x,y,vx,vy,r); }
    static boolean near(double a,double b){ return Math.abs(a-b)<1e-6; }

    public static void main(String[] args){
        // ---------- KDTree ----------
        KDTree t = new KDTree(); t.build(new ArrayList<>());
        check("KD-01 empty tree: isEmpty, size 0, height 0", t.isEmpty() && t.size()==0 && t.height()==0);
        check("KD-02 empty tree: nearest returns null", t.nearest(5,5,null)==null);
        List<SpaceObject> out = new ArrayList<>(); t.radiusSearch(5,5,100,out);
        check("KD-03 empty tree: radius search returns nothing", out.isEmpty());

        List<SpaceObject> one = List.of(o(0,10,10)); t = new KDTree(); t.build(one);
        check("KD-04 single point: nearest is that point", t.nearest(99,99,null)==one.get(0));
        check("KD-05 single point: exclude itself -> null", t.nearest(10,10,one.get(0))==null);
        check("KD-06 single point: height 1", t.height()==1);

        List<SpaceObject> pts = new ArrayList<>(List.of(o(0,2,3),o(1,5,4),o(2,9,6),o(3,4,7),o(4,8,1),o(5,7,2)));
        t = new KDTree(); t.build(pts);
        check("KD-07 6 known points: size 6", t.size()==6);
        check("KD-08 nearest to (9,2) is #4 (8,1)", t.nearest(9,2,null).id==4);
        check("KD-09 nearest to (2,3) is #0 itself (distance 0)", t.nearest(2,3,null).id==0 && near(t.lastNearestDistance(),0));
        check("KD-10 nearest to (2,3) excluding #0 is #1 (5,4)", t.nearest(2,3,pts.get(0)).id==1);
        out = new ArrayList<>(); t.radiusSearch(5,4,3.2,out);
        Set<Integer> ids = new TreeSet<>(); for(SpaceObject s:out) ids.add(s.id);
        check("KD-11 radius 3.2 around (5,4) finds {0,1,3,5}", ids.equals(new TreeSet<>(List.of(0,1,3,5))));
        out = new ArrayList<>(); t.radiusSearch(5,4,0,out);
        check("KD-12 radius 0 exactly on a point finds that one point", out.size()==1 && out.get(0).id==1);
        out = new ArrayList<>(); t.radiusSearch(5,4,5,out);   // (2,3)->3.16, (9,6)->4.47 ; (8,1)->4.24 ; (4,7)->3.16 ; (7,2)->2.83
        check("KD-13 radius 5 around (5,4) finds all 6", out.size()==6);
        out = new ArrayList<>(); t.radiusSearch(500,500,10,out);
        check("KD-14 query far away: radius search empty", out.isEmpty());
        List<SpaceObject> line = List.of(o(0,0,0),o(1,3,0),o(2,6,0)); KDTree tb = new KDTree(); tb.build(line);
        out = new ArrayList<>(); tb.radiusSearch(0,0,3,out);
        check("KD-16 distance exactly == r included (collinear, r=3 finds 2 pts)", out.size()==2);

        List<SpaceObject> dup = new ArrayList<>(); for(int i=0;i<50;i++) dup.add(o(i,5,5));
        t = new KDTree(); t.build(dup); out = new ArrayList<>(); t.radiusSearch(5,5,0.5,out);
        check("KD-17 50 duplicate points: build terminates, radius finds all 50", out.size()==50);
        List<SpaceObject> vert = new ArrayList<>(); for(int i=0;i<100;i++) vert.add(o(i,7,i));
        t = new KDTree(); t.build(vert); out = new ArrayList<>(); t.radiusSearch(7,50,5,out);
        check("KD-18 100 points on same x: radius 5 at y=50 finds 11", out.size()==11);
        check("KD-19 nearest on same-x line to (7,49.4) is #49", t.nearest(7,49.4,null).id==49);
        List<SpaceObject> neg = List.of(o(0,-5,-5),o(1,-1,-1),o(2,3,3)); t=new KDTree(); t.build(neg);
        check("KD-20 negative coordinates: nearest to (-2,-2) is #1", t.nearest(-2,-2,null).id==1);

        // height ~ log2 n
        for(int n : new int[]{1,7,15,100,1000,10000}){
            List<SpaceObject> L = SpaceObject.randomField(n,1000,1000,n,20); t=new KDTree(); t.build(L);
            int exp = (int)Math.ceil(Math.log(n+1)/Math.log(2));
            check("KD-21 balanced: n="+n+" height "+t.height()+" == ceil(log2(n+1)) "+exp, t.height()==exp);
        }
        // randomised vs brute force
        Random rnd = new Random(11); boolean allOk = true;
        for(int n : new int[]{2,10,100,1000,5000}){
            List<SpaceObject> L = SpaceObject.randomField(n,1000,1000,n+3,20); t=new KDTree(); t.build(L);
            for(int q=0;q<200;q++){
                double qx=rnd.nextDouble()*1000, qy=rnd.nextDouble()*1000, r=rnd.nextDouble()*300;
                double bd=1e18; for(SpaceObject s:L) bd=Math.min(bd,Math.hypot(s.x-qx,s.y-qy));
                SpaceObject nn=t.nearest(qx,qy,null);
                if(Math.abs(Math.hypot(nn.x-qx,nn.y-qy)-bd)>1e-9) allOk=false;
                int exp=0; for(SpaceObject s:L) if(Math.hypot(s.x-qx,s.y-qy)<=r) exp++;
                List<SpaceObject> f=new ArrayList<>(); t.radiusSearch(qx,qy,r,f); if(f.size()!=exp) allOk=false;
            }
        }
        check("KD-22 2,000 random queries (nearest + radius) match brute force", allOk);
        List<SpaceObject> big = SpaceObject.randomField(10000,1000,1000,5,20); t=new KDTree(); t.build(big); t.resetStats(); t.nearest(500,500,null);
        check("KD-23 pruning works: NN on 10,000 pts visits "+t.getNodesVisited()+" nodes (<< 10,000)", t.getNodesVisited()<200 && t.getSubtreesPruned()>0);
        t.enableTrace(true); t.resetStats(); t.nearest(500,500,null);
        check("KD-24 trace size equals nodesVisited", t.getTrace().size()==t.getNodesVisited());

        // ---------- SpaceObject ----------
        SpaceObject s = mv(0,98,50,10,0,2); s.move(1,100,100);
        check("SO-01 bounces off right wall: x=98, vx negative", near(s.x,98) && s.vx<0);
        s = mv(0,3,50,-10,0,2); s.move(1,100,100);
        check("SO-02 bounces off left wall: x=2, vx positive", near(s.x,2) && s.vx>0);
        s = mv(0,50,50,3,4,1); s.move(2,100,100);
        check("SO-03 free move: (56,58)", near(s.x,56)&&near(s.y,58));
        check("SO-04 speed of (3,4) is 5", near(mv(0,0,0,3,4,1).speed(),5));
        check("SO-05 randomField is repeatable for same seed", SpaceObject.randomField(50,100,100,9,20).get(7).x==SpaceObject.randomField(50,100,100,9,20).get(7).x);
        boolean inside=true; for(SpaceObject q:SpaceObject.randomField(2000,1000,800,1,20)) if(q.x<q.radius||q.x>1000-q.radius||q.y<q.radius||q.y>800-q.radius) inside=false;
        check("SO-06 randomField keeps all objects inside the world", inside);

        // ---------- CollisionEngine.evaluatePair ----------
        CollisionEngine.CollisionEvent e;
        e = CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,0,-10,0,1), 10);
        check("CE-01 head-on: impact t=4.9, closest t=5, minDist 0", e!=null && near(e.impactTime,4.9) && near(e.closestTime,5) && near(e.minDistance,0));
        check("CE-02 head-on but horizon 3 s -> no collision", CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,0,-10,0,1), 3)==null);
        check("CE-03 same velocity (parallel) -> null", CollisionEngine.evaluatePair(mv(0,0,0,5,5,1), mv(1,20,0,5,5,1), 100)==null);
        e = CollisionEngine.evaluatePair(mv(0,0,0,0,0,5), mv(1,6,0,0,0,5), 10);
        check("CE-04 already overlapping (static) -> impactTime 0", e!=null && e.impactTime==0);
        check("CE-05 paths pass 10 apart, R=2 -> miss", CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,10,-10,0,1), 20)==null);
        e = CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,2,-10,0,1), 20);
        check("CE-06 grazing (offset == R=2) -> collision at t=5", e!=null && near(e.impactTime,5));
        check("CE-07 moving apart -> null", CollisionEngine.evaluatePair(mv(0,0,0,-10,0,1), mv(1,50,0,10,0,1), 10)==null);
        check("CE-08 both stationary and far -> null", CollisionEngine.evaluatePair(mv(0,0,0,0,0,1), mv(1,50,0,0,0,1), 10)==null);
        e = CollisionEngine.evaluatePair(mv(0,0,0,0,0,1), mv(1,0,50,0,-5,1), 20);
        check("CE-09 one stationary, one approaching along y: t=(50-2)/5=9.6", e!=null && near(e.impactTime,9.6));
        e = CollisionEngine.evaluatePair(mv(0,0,0,10,10,1), mv(1,100,100,-10,-10,1), 20);
        check("CE-10 diagonal head-on: collides, impact before closest approach", e!=null && e.impactTime<e.closestTime);
        check("CE-11 collision exactly at horizon boundary accepted", CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,0,-10,0,1), 4.9)!=null);

        // ---------- heap ordering ----------
        List<SpaceObject> hs = new ArrayList<>();
        hs.add(mv(0,0,0,10,0,1)); hs.add(mv(1,100,0,-10,0,1));        // impact 4.9
        hs.add(mv(2,0,200,10,0,1)); hs.add(mv(3,40,200,-10,0,1));     // impact 1.9
        hs.add(mv(4,0,400,10,0,1)); hs.add(mv(5,200,400,-10,0,1));    // impact 9.9
        CollisionEngine eng = new CollisionEngine(); CollisionEngine.Result res = eng.predictWithKDTree(hs,15);
        List<CollisionEngine.CollisionEvent> top = res.topEvents(3);
        check("HP-01 heap gives 3 events", res.eventCount()==3);
        check("HP-02 earliest first: 1.9 < 4.9 < 9.9", top.size()==3 && near(top.get(0).impactTime,1.9)&&near(top.get(1).impactTime,4.9)&&near(top.get(2).impactTime,9.9));
        check("HP-03 topEvents does not consume heap", res.eventCount()==3);
        check("HP-04 min-heap poll returns earliest", near(res.heap.peek().impactTime,1.9));

        // ---------- predict: KD vs brute ----------
        check("PR-01 empty list: 0 events (KD)", eng.predictWithKDTree(new ArrayList<>(),5).eventCount()==0);
        check("PR-02 empty list: 0 events (brute)", eng.predictBruteForce(new ArrayList<>(),5).eventCount()==0);
        check("PR-03 single object: 0 events", eng.predictWithKDTree(List.of(mv(0,5,5,1,1,1)),5).eventCount()==0);
        check("PR-04 two objects: brute pair checks = 1", eng.predictBruteForce(List.of(mv(0,0,0,10,0,1),mv(1,100,0,-10,0,1)),10).candidatePairs==1);
        boolean same=true; String det="";
        for(int n : new int[]{2,10,100,500,1000,3000}) for(long seed=1;seed<=5;seed++){
            List<SpaceObject> L = SpaceObject.randomField(n,1000,1000,seed*31+n,20);
            for(double h : new double[]{1,3,10}){
                CollisionEngine.Result a=eng.predictWithKDTree(L,h), b=eng.predictBruteForce(L,h);
                if(a.eventCount()!=b.eventCount()) { same=false; det+=" n="+n+" seed="+seed+" h="+h; }
                else if(a.eventCount()>0 && !near(a.heap.peek().impactTime,b.heap.peek().impactTime)) { same=false; det+=" top-mismatch n="+n; }
            }
        }
        check("PR-05 KD-tree finds exactly the same collisions as brute force (90 runs)"+det, same);
        List<SpaceObject> L = SpaceObject.randomField(5000,1000,1000,3,20);
        CollisionEngine.Result a=eng.predictWithKDTree(L,3), b=eng.predictBruteForce(L,3);
        check("PR-06 5,000 objs: KD pair tests "+a.candidatePairs+" << brute "+b.candidatePairs, a.candidatePairs < b.candidatePairs/20);
        List<SpaceObject> ov = new ArrayList<>(); for(int i=0;i<5;i++) ov.add(mv(i,10+i,10,0,0,5));
        check("PR-07 5 overlapping static circles: C(5,2)=10 events", eng.predictWithKDTree(ov,1).eventCount()==10);
        // ---------- object types: asteroid / planet / spaceship ----------
        SpaceObject.Type AST=SpaceObject.Type.ASTEROID, PLA=SpaceObject.Type.PLANET, SHP=SpaceObject.Type.SPACESHIP;
        check("TY-01 mass = density*PI*r^2 (planet r=6)", near(new SpaceObject(0,PLA,0,0,0,0,6).mass, 5.5*Math.PI*36));
        check("TY-02 same radius: planet heavier than asteroid heavier than ship",
              new SpaceObject(0,PLA,0,0,0,0,2).mass > new SpaceObject(1,AST,0,0,0,0,2).mass
           && new SpaceObject(1,AST,0,0,0,0,2).mass > new SpaceObject(2,SHP,0,0,0,0,2).mass);
        check("TY-03 doubling the radius quadruples the mass (2D area law)", near(new SpaceObject(0,AST,0,0,0,0,4).mass/new SpaceObject(1,AST,0,0,0,0,2).mass, 4));
        check("TY-04 old 6-argument constructor creates an asteroid", mv(0,0,0,0,0,1).type==AST);
        check("TY-05 Type.pick boundaries: 0.0 asteroid, 0.71 planet, 0.99 ship", SpaceObject.Type.pick(0.0)==AST && SpaceObject.Type.pick(0.71)==PLA && SpaceObject.Type.pick(0.99)==SHP);
        List<SpaceObject> fld = SpaceObject.randomField(30000,1000,1000,5,20);
        int nA=0,nP=0,nS=0, bigA=0; double sumP=0,sumS=0; boolean inRange=true;
        for(SpaceObject q:fld){
            if(q.type==AST){nA++; if(q.radius>3.0) bigA++;} else if(q.type==PLA){nP++; sumP+=q.speed();} else {nS++; sumS+=q.speed();}
            if(q.radius<q.type.rMin-1e-9||q.radius>q.type.rMax+1e-9) inRange=false;
            if(q.speed()<q.type.vLo*20-1e-9||q.speed()>q.type.vHi*20+1e-9) inRange=false;
        }
        check("TY-06 mix of 30,000 objects is about 70% asteroid / 3% planet / 27% ship", Math.abs(nA/30000.0-0.70)<0.02 && Math.abs(nP/30000.0-0.03)<0.01 && Math.abs(nS/30000.0-0.27)<0.02);
        check("TY-07 every radius and speed is inside its type's range", inRange);
        check("TY-08 power law: P(asteroid radius > 3) is about (1.5/3)^2 = 0.25", Math.abs(bigA/(double)nA-0.25)<0.02);
        check("TY-09 planets are slower than ships on average", sumP/nP < sumS/nS);
        check("TY-10 kinetic energy 1/2 m v^2 (ship r=1, speed 10)", near(new SpaceObject(0,SHP,0,0,10,0,1).kineticEnergy(), 0.5*(0.5*Math.PI)*100));

        // ---------- impact energy ----------
        e = CollisionEngine.evaluatePair(mv(0,0,0,10,0,1), mv(1,100,0,-10,0,1), 10);
        double mA = 2.5*Math.PI;
        check("CE-12 relative speed of head-on pair = 20", e!=null && near(e.relSpeed,20));
        check("CE-13 equal masses head-on: E = 1/2*(m/2)*20^2 = 100 m", e!=null && near(e.impactEnergy,100*mA));
        check("CE-14 overlapping and stationary: energy 0", CollisionEngine.evaluatePair(mv(0,0,0,0,0,5), mv(1,6,0,0,0,5), 10).impactEnergy==0);
        SpaceObject ship1=new SpaceObject(1,SHP,100,0,-10,0,1), planet0=new SpaceObject(0,PLA,0,0,10,0,1), ast0=new SpaceObject(2,AST,0,0,10,0,1);
        double ePlanetShip = CollisionEngine.evaluatePair(planet0, ship1, 10).impactEnergy;
        double eAstShip    = CollisionEngine.evaluatePair(ast0, ship1, 10).impactEnergy;
        check("CE-15 planet-ship impact releases more energy than asteroid-ship at same speeds", ePlanetShip > eAstShip);
        check("CE-16 energy never exceeds 1/2 * (lighter mass) * v_rel^2 (since mu < min mass)", ePlanetShip < 0.5*ship1.mass*400 + 1e-9);

        System.out.println("\nTOTAL: "+pass+" passed, "+fail+" failed");
    }
}
