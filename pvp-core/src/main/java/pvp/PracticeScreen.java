package pvp;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
public final class PracticeScreen extends Screen {
public PracticeScreen() { super(Component.literal("PvP Practice")); }
private static Component status(String name, boolean value) { return Component.literal(name+": "+(value?"ON":"OFF")); }
@Override protected void init() {
int x=width/2-110, y=Math.max(2,height/2-108);
addRenderableWidget(Button.builder(status("AimAssist",PracticeClient.settings.aimEnabled()),button -> {
var s=PracticeClient.settings;
PracticeClient.settings=new CombatSettings(!s.aimEnabled(),s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),s.fov());
button.setMessage(status("AimAssist",PracticeClient.settings.aimEnabled()));
}).bounds(x,y,220,20).build());
addRenderableWidget(Button.builder(status("AutoCritical",PracticeClient.settings.triggerEnabled()),button -> {
var s=PracticeClient.settings;
PracticeClient.settings=new CombatSettings(s.aimEnabled(),!s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),s.fov());
button.setMessage(status("AutoCritical",PracticeClient.settings.triggerEnabled()));
}).bounds(x,y+24,220,20).build());
addRenderableWidget(Button.builder(Component.literal("FOV: "+(int)PracticeClient.settings.fov()),button -> {
var s=PracticeClient.settings;
double fov=s.fov()>=360?35:s.fov()<60?60:s.fov()<90?90:s.fov()<150?150:s.fov()<180?180:s.fov()<270?270:360;
PracticeClient.settings=new CombatSettings(s.aimEnabled(),s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),fov);
button.setMessage(Component.literal("FOV: "+(int)fov));
}).bounds(x,y+48,220,20).build());
addRenderableWidget(Checkbox.builder(Component.literal("Iron Golem Test"),font).pos(x,y+72).selected(PracticeCombat.golemTest).onValueChange((checkbox,value)->PracticeCombat.golemTest=value).build());
addRenderableWidget(Checkbox.builder(Component.literal("Network Test (allowed servers)"),font).pos(x,y+96).selected(PracticeCombat.networkTest).onValueChange((checkbox,value)->PracticeCombat.networkTest=value).build());
addRenderableWidget(Button.builder(Component.literal("Lock GUI (until restart)"),button -> {
PracticeClient.guiLocked=true;
onClose();
}).bounds(x,y+120,220,20).build());
var result=Button.builder(Component.literal("Attack attempts: "+PracticeCombat.readyTicks()),button -> {}).bounds(x,y+144,220,20).build();
result.active=false;
addRenderableWidget(result);
var aim=Button.builder(Component.literal(PracticeCombat.status()),button -> {}).bounds(x,y+168,220,20).build();
aim.active=false;
addRenderableWidget(aim);
addRenderableWidget(Button.builder(Component.literal("Close"),button -> onClose()).bounds(x,y+192,220,20).build());
}
@Override public boolean isPauseScreen() { return false; }
}
