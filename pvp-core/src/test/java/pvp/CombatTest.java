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
check(CombatEngine.autoCritical(crit,1,false)==CombatEngine.Decision.CRITICAL_WINDOW);
check(CombatEngine.autoCritical(crit,2,true)==CombatEngine.Decision.WAIT);
check(CombatEngine.autoCritical(nearFull,1,false)==CombatEngine.Decision.WAIT);
check(CombatEngine.autoCritical(nearFull,2,false)==CombatEngine.Decision.CRITICAL_WINDOW);
check(CombatEngine.autoCritical(normal,2,false)==CombatEngine.Decision.WAIT);
check(CombatEngine.autoCritical(blocked,2,false)==CombatEngine.Decision.WAIT);
check(AimModel.retainTarget(.81,1.0));
check(!AimModel.retainTarget(.60,1.0));
check(!AimModel.retainTarget(.60,Double.POSITIVE_INFINITY));
check(AimModel.targetScore(10,150,4)<AimModel.targetScore(30,150,4));
check(AimModel.targetScore(10,150,4)<AimModel.targetScore(10,150,9));
var sprintFall=new CombatEngine.Frame(1,false,.4,true,false,true,true,false,false,t);
check(CombatEngine.autoCritical(sprintFall,3,false)==CombatEngine.Decision.WAIT);
var blockedFall=new CombatEngine.Frame(1,false,.4,false,true,true,true,false,false,t);
check(CombatEngine.autoCritical(blockedFall,3,false)==CombatEngine.Decision.WAIT);
int strikes=0;
for(int jump=0;jump<10;jump++) {
boolean spent=false;
for(int fall=1;fall<=5;fall++) {
if(CombatEngine.autoCritical(nearFull,fall,spent)!=CombatEngine.Decision.WAIT) { spent=true; strikes++; }
}
}
check(strikes==10);
var still=AimModel.movingStep(front,rear,new CombatSettings(true,false,.55,.4,360),0);
check(still.equals(AimModel.step(front,rear,new CombatSettings(true,false,.55,.4,360))));
var moving=AimModel.movingStep(front,rear,new CombatSettings(true,false,.55,.4,360),7);
check(Math.abs(moving.yaw())==18);
var ahead=AimModel.movingPoint(track1,box,new AimModel.Vec(.3,.1,0));
check(ahead.x()>track1.x());
var bounded=AimModel.movingPoint(track1,box,new AimModel.Vec(100,-100,100));
check(bounded.x()<.3 && bounded.y()>0 && bounded.z()<2.6);
check(AimModel.shouldApproach(12,10));
check(!AimModel.shouldApproach(4,0));
check(!AimModel.shouldApproach(12,40));
check(!AimModel.shouldApproach(40,0));
System.out.println("Combat tests passed");
}
}