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
System.out.println("Combat tests passed");
}
}