package pvp;
public class CoreTest {
private static void check(boolean value) { if(!value) throw new AssertionError(); }
public static void main(String[] args) {
GuiState gui=new GuiState();
gui.update(true,false); check(!gui.isOpen());
gui.update(false,true); gui.update(true,true); check(gui.isOpen());
gui.update(true,true); check(gui.isOpen());
gui.update(false,true); gui.update(true,true); check(!gui.isOpen());
gui.update(false,true); gui.update(true,true); check(gui.isOpen());
gui.lock(); check(!gui.isOpen() && gui.isLocked());
gui.update(false,true); gui.update(true,true); check(!gui.isOpen());
check(!new GuiState().isLocked());
check(TargetFilter.accepts(new TargetFilter.Candidate(true,false,true,true,false,false)));
check(!TargetFilter.accepts(new TargetFilter.Candidate(true,false,true,false,false,false)));
check(!TargetFilter.accepts(new TargetFilter.Candidate(false,false,true,true,false,true)));
check(!TargetFilter.accepts(new TargetFilter.Candidate(true,true,true,true,false,false)));
check(!TargetFilter.accepts(new TargetFilter.Candidate(true,false,false,true,false,false)));
check(!TargetFilter.accepts(new TargetFilter.Candidate(true,false,true,true,true,false)));
check(TargetFilter.practiceTarget(true,true,true,false));
check(!TargetFilter.practiceTarget(true,false,true,false));
check(!TargetFilter.practiceTarget(true,true,false,true));
check(!TargetFilter.practiceTarget(false,true,true,false));
check(!TargetFilter.practiceTarget(false,false,false,false));
check(TargetFilter.practiceTarget(false,false,false,true));
check(TargetFilter.practiceTarget(false,false,true,true));
check(TargetFilter.lockedPracticeTarget(true,false,true,true,true));
check(!TargetFilter.lockedPracticeTarget(true,true,true,true,false));
check(TargetFilter.lockedPracticeTarget(false,false,false,true,true));
check(!TargetFilter.lockedPracticeTarget(false,true,false,false,false));
check(TargetFilter.nearer(36,36,true));
check(!TargetFilter.nearer(36.01,36,true));
check(TargetFilter.nearer(4,9,false));
check(!TargetFilter.nearer(9,9,false));
check(!TargetFilter.nearer(Double.NaN,36,true));
check(!CombatSettings.defaults().aimEnabled());
System.out.println("Core tests passed");
}
}