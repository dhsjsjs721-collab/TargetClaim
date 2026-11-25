package pvp;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.ChatFormatting;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.io.IOException;
import net.minecraft.network.chat.Component;
public final class PracticeScreen extends Screen {
public PracticeScreen() { super(Component.literal("Land of Losers")); }
private Button hitStatus,combatStatus;
private static Component status(String name, boolean value) { return Component.literal(name+": ").append(Component.literal(value?"ON":"OFF").withStyle(value?ChatFormatting.GREEN:ChatFormatting.GRAY)); }
private static Component hitLabel() { return Component.literal("Hits: "+PracticeCombat.confirmedHits()+"/"+PracticeCombat.readyTicks()+" | Jump: "+PracticeCombat.jumpCount()); }
private static Component combatLabel() { return Component.literal(PracticeCombat.status()+" | Timeout: "+PracticeCombat.expiredHits()); }
@Override public void tick() { super.tick(); if(hitStatus!=null) hitStatus.setMessage(hitLabel()); if(combatStatus!=null) combatStatus.setMessage(combatLabel()); }
@Override protected void init() {
int x=width/2-110;
int step=Math.max(16,Math.min(24,(height-34)/9)), h=step-2;
int y=Math.max(30,(height-(9*step+30))/2+30);
addRenderableOnly(new StringWidget(x,y-28,220,12,Component.literal("LAND OF LOSERS").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),font));
addRenderableOnly(new StringWidget(x,y-15,220,10,Component.literal("Adaptive Combat Engine | 1.8.3").withStyle(ChatFormatting.GRAY),font));
addRenderableWidget(Button.builder(status("AimAssist",PracticeClient.settings.aimEnabled()),button -> {
var s=PracticeClient.settings;
PracticeClient.settings=new CombatSettings(!s.aimEnabled(),s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),s.fov());
button.setMessage(status("AimAssist",PracticeClient.settings.aimEnabled()));
}).tooltip(Tooltip.create(Component.literal("Adaptive target assistance."))).bounds(x,y,220,h).build());
addRenderableWidget(Button.builder(status("AutoCritical",PracticeClient.settings.triggerEnabled()),button -> {
var s=PracticeClient.settings;
PracticeClient.settings=new CombatSettings(s.aimEnabled(),!s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),s.fov());
button.setMessage(status("AutoCritical",PracticeClient.settings.triggerEnabled()));
}).tooltip(Tooltip.create(Component.literal("Weapon-aware critical and normal attack timing."))).bounds(x,y+1*step,220,h).build());
addRenderableWidget(Button.builder(Component.literal("FOV: "+(int)PracticeClient.settings.fov()),button -> {
var s=PracticeClient.settings;
double fov=s.fov()>=360?35:s.fov()<60?60:s.fov()<90?90:s.fov()<150?150:s.fov()<180?180:s.fov()<270?270:360;
PracticeClient.settings=new CombatSettings(s.aimEnabled(),s.triggerEnabled(),s.horizontalSpeed(),s.verticalSpeed(),fov);
button.setMessage(Component.literal("FOV: "+(int)fov));
PracticeClient.savePreferences();
}).tooltip(Tooltip.create(Component.literal("Field of view for target selection."))).bounds(x,y+2*step,220,h).build());
addRenderableWidget(Checkbox.builder(Component.literal("Iron Golem Test"),font).pos(x,y+3*step).selected(PracticeCombat.golemTest).onValueChange((checkbox,value)->{ PracticeCombat.golemTest=value; PracticeClient.savePreferences(); }).build());
addRenderableWidget(Checkbox.builder(Component.literal("Auto Move"),font).pos(x+120,y+3*step).selected(PracticeCombat.autoMove).onValueChange((checkbox,value)->PracticeCombat.autoMove=value).build());
addRenderableWidget(Checkbox.builder(Component.literal("Network Test (allowed servers)"),font).pos(x,y+4*step).selected(PracticeCombat.networkTest).onValueChange((checkbox,value)->PracticeCombat.networkTest=value).build());
addRenderableWidget(Button.builder(Component.literal("Disable all & close"),button -> {
var defaults=CombatSettings.defaults();
var current=PracticeClient.settings;
PracticeClient.settings=new CombatSettings(false,false,defaults.horizontalSpeed(),defaults.verticalSpeed(),current.fov());
PracticeCombat.networkTest=false;
PracticeCombat.releaseInput(net.minecraft.client.Minecraft.getInstance());
onClose();
}).bounds(x,y+5*step,220,h).build());
hitStatus=Button.builder(hitLabel(),button -> {}).bounds(x,y+6*step,220,h).build();
hitStatus.active=false;
addRenderableWidget(hitStatus);
combatStatus=Button.builder(combatLabel(),button -> {}).bounds(x,y+7*step,220,h).build();
combatStatus.active=false;
addRenderableWidget(combatStatus);
addRenderableWidget(Button.builder(Component.literal("Save diagnostics"),button -> {
String report=PracticeCombat.diagnostics();
System.out.println(report);
try {
var logs=FabricLoader.getInstance().getGameDir().resolve("logs");
Files.createDirectories(logs);
Files.writeString(logs.resolve("land-of-losers-diagnostics.txt"),report);
button.setMessage(Component.literal("Report saved").withStyle(ChatFormatting.GREEN));
} catch(IOException | SecurityException e) {
button.setMessage(Component.literal("Save failed").withStyle(ChatFormatting.RED));
System.err.println("Land of Losers: "+e.getMessage());
}
}).tooltip(Tooltip.create(Component.literal("Save logs/land-of-losers-diagnostics.txt locally. No upload."))).bounds(x,y+8*step,136,h).build());
addRenderableWidget(Button.builder(Component.literal("Close"),button -> onClose()).bounds(x+140,y+8*step,80,h).build());
}
@Override public boolean isPauseScreen() { return false; }
}
