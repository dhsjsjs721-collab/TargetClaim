package pvp;
public class CombatTest {
static void check(boolean b) { if(!b) throw new AssertionError(); }
public static void main(String[] args) {
var t=new TargetFilter.Candidate(true,false,true,true,false,false);
var crit=new CombatEngine.Frame(1,false,.2,false,false,true,true,false,true,t);
check(CombatEngine.evaluate(CombatEngine.Mode.CRITICAL,crit)==CombatEngine.Decision.CRITICAL_WINDOW);
check(CombatEngine.evaluate(CombatEngine.Mode.COUNTER_CRITICAL,crit)==CombatEngine.Decision.CRITICAL_WINDOW);
check(CombatEngine.evaluate(CombatEngine.Mode.COMBO,crit)==CombatEngine.Decision.WAIT);
var combo=new CombatEngine.Frame(1,true,0,true,false,true,true,false,false,t);
check(CombatEngine.evaluate(CombatEngine.Mode.COMBO,combo)==CombatEngine.Decision.COMBO_WINDOW);
check(CombatEngine.evaluate(CombatEngine.Mode.CRITICAL,combo)==CombatEngine.Decision.WAIT);
var blocked=new CombatEngine.Frame(1,false,.2,false,false,true,true,false,true,new TargetFilter.Candidate(true,false,true,false,false,false));
check(CombatEngine.evaluate(CombatEngine.Mode.CRITICAL,blocked)==CombatEngine.Decision.WAIT);
var weak=new CombatEngine.Frame(.5,false,.2,false,false,true,true,false,true,t);
check(CombatEngine.evaluate(CombatEngine.Mode.CRITICAL,weak)==CombatEngine.Decision.WAIT);
var a=new AimModel.Angles(179,0); var b=new AimModel.Angles(-179,10);
var next=AimModel.step(a,b,new CombatSettings(true,true,.5,.5,35));
check(Math.abs(next.yaw())==180 && next.pitch()==5);
check(AimModel.step(a,b,CombatSettings.defaults()).equals(a));
var normal=new CombatEngine.Frame(1,true,0,false,false,true,true,false,false,t);
check(CombatEngine.evaluate(CombatEngine.Mode.NORMAL,normal)==CombatEngine.Decision.NORMAL_WINDOW);
var descending=new CombatEngine.Frame(1,false,.2,false,false,true,true,true,false,t);
check(CombatEngine.evaluate(CombatEngine.Mode.COUNTER_CRITICAL,descending)==CombatEngine.Decision.CRITICAL_WINDOW);
var box=new AimModel.Box(new AimModel.Vec(-.3,0,2),new AimModel.Vec(.3,1.8,2.6));
var pt=AimModel.nearestAimPoint(new AimModel.Vec(0,1.6,0),new AimModel.Angles(0,0),box);
check(pt.x()>=-.3 && pt.x()<=.3 && pt.y()>0 && pt.y()<1.8 && pt.z()>2 && pt.z()<2.6);
var rear=new AimModel.Angles(180,0);
var front=new AimModel.Angles(0,0);
check(AimModel.step(front,rear,new CombatSettings(true,false,.35,.25,150)).equals(front));
check(Math.abs(AimModel.step(front,rear,new CombatSettings(true,false,.35,.25,360)).yaw())==12);
check(CombatSettings.defaults().fov()==150);
var nearFull=new CombatEngine.Frame(.96,false,.2,false,false,true,true,false,false,t);
check(CombatEngine.evaluate(CombatEngine.Mode.CRITICAL,nearFull)==CombatEngine.Decision.CRITICAL_WINDOW);
check(CombatEngine.reason(CombatEngine.Mode.CRITICAL,normal).equals("Waiting for descent"));
check(CombatEngine.reason(CombatEngine.Mode.CRITICAL,weak).equals("Cooldown"));
check(CombatEngine.reason(CombatEngine.Mode.COMBO,normal).equals("Waiting for sprint"));
check(CombatEngine.reason(CombatEngine.Mode.CRITICAL,crit).equals("Attack ready"));
check(AimModel.wrap(181-179)==2);
var track1=AimModel.trackingPoint(new AimModel.Vec(0,1.6,0),front,box,0);
var track2=AimModel.trackingPoint(new AimModel.Vec(0,1.6,0),front,box,15);
check(!track1.equals(track2));
for(int phase=0;phase<200;phase++) {
var track=AimModel.trackingPoint(new AimModel.Vec(0,1.6,0),front,box,phase);
check(track.x()>-.3 && track.x()<.3 && track.y()>0 && track.y()<1.8 && track.z()>2 && track.z()<2.6);
}
System.out.println("Combat tests passed");
}
}