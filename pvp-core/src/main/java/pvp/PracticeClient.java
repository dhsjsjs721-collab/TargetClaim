package pvp;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.io.IOException;
public final class PracticeClient implements ClientModInitializer {
static boolean guiLocked;
static CombatSettings settings=CombatSettings.defaults();
private static Object hotkeyWorld,hotkeyPlayer;
private static boolean hotkeyActive,hotkeyHeld,supportBusy;
private static int hudTicks;
private static boolean orbitHeld;
static boolean hotkeyActive() { return hotkeyActive; }
static void stopPractice(Minecraft mc) {
var s=settings;
settings=new CombatSettings(false,false,s.horizontalSpeed(),s.verticalSpeed(),s.fov());
hotkeyActive=false; hotkeyWorld=null; hotkeyPlayer=null;
PracticeFreeLook.stop(mc,true);
PracticeCombat.finishPractice();
PracticeSupport.reset(); supportBusy=false; hudTicks=0;
PracticeCombat.autoMove=false;
PracticeCombat.clearServerTarget();
PracticeCombat.releaseInput(mc);
}
private static void togglePractice(Minecraft mc) {
if(mc.player==null || mc.level==null) return;
if(hotkeyActive || settings.aimEnabled() || settings.triggerEnabled() || PracticeCombat.autoMove) {
stopPractice(mc);
mc.gui.hud.setOverlayMessage(Component.literal("Land of Losers: OFF"),false);
return;
}
String name=PracticeCombat.selectNearestPracticeTarget(mc);
if(name==null) {
mc.gui.hud.setOverlayMessage(Component.literal("Land of Losers: no eligible target within 6 blocks"),false);
return;
}
var s=settings;
settings=new CombatSettings(true,true,s.horizontalSpeed(),s.verticalSpeed(),s.fov());
PracticeCombat.autoMove=true;
hotkeyActive=true; hotkeyWorld=mc.level; hotkeyPlayer=mc.player;
PracticeCombat.beginPractice(mc);
mc.gui.hud.setOverlayMessage(Component.literal("Land of Losers: ON | "+name),false);
}
private static void checkHotkeySession(Minecraft mc) {
if(hotkeyActive && (mc.level!=hotkeyWorld || mc.player!=hotkeyPlayer || mc.player==null || !mc.player.isAlive() || !PracticeCombat.hasLockedTarget(mc))) stopPractice(mc);
}
private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("land-of-losers.properties"); }
static void savePreferences() {
try { new ClientPreferences(settings.fov(),PracticeCombat.golemTest).write(configFile()); }
catch(IOException | IllegalArgumentException | SecurityException ex) { System.err.println("Land of Losers preferences: "+ex.getMessage()); }
}
private static void loadPreferences() {
ClientPreferences p=ClientPreferences.defaults();
try { p=ClientPreferences.read(configFile()); }
catch(IOException | IllegalArgumentException | SecurityException ex) { System.err.println("Land of Losers preferences: "+ex.getMessage()); }
var s=CombatSettings.defaults();
settings=new CombatSettings(false,false,s.horizontalSpeed(),s.verticalSpeed(),p.fov());
PracticeCombat.golemTest=p.golemTest();
PracticeCombat.autoMove=false;
}
public void onInitializeClient() {
loadPreferences();
var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pvp_practice","practice"));
var key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvp_practice.open",InputConstants.Type.KEYBOARD,InputConstants.KEY_DOWN,category));
var toggle=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvp_practice.toggle",InputConstants.Type.KEYBOARD,InputConstants.KEY_R,category));
var orbit=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvp_practice.orbit",InputConstants.Type.KEYBOARD,InputConstants.KEY_F6,category));
ClientTickEvents.START_CLIENT_TICK.register(client -> {
checkHotkeySession(client);
boolean pressed=false;
while(toggle.consumeClick()) pressed=true;
if(pressed && !hotkeyHeld && client.gui.screen()==null) togglePractice(client);
hotkeyHeld=toggle.isDown();
boolean orbitPressed=false;
while(orbit.consumeClick()) orbitPressed=true;
if(orbitPressed && !orbitHeld && client.gui.screen()==null) PracticeFreeLook.toggle(client);
orbitHeld=orbit.isDown();
supportBusy=hotkeyActive && PracticeSupport.tick(client,PracticeCombat.supportDistance(client));
if(supportBusy) PracticeCombat.supportPause(client,PracticeSupport.status());
else PracticeCombat.startTick(client,settings);
});
ClientTickEvents.END_CLIENT_TICK.register(client -> {
PracticeCombat.releaseInput(client);
while(key.consumeClick()) {
if(!guiLocked && client.player != null && client.gui.screen()==null) client.gui.setScreen(new PracticeScreen());
}
if(!supportBusy) PracticeCombat.tick(client,settings);
PracticeFreeLook.tick(client);
PracticeCombat.captureFrame(client);
if(hotkeyActive && client.gui.screen()==null && ++hudTicks>=20) {
hudTicks=0;
client.gui.hud.setOverlayMessage(Component.literal(PracticeCombat.targetSummary(client)+" | "+PracticeSupport.inventorySummary(client)),false);
}
});
ScreenEvents.BEFORE_INIT.register((client,screen,w,h) -> {
if(screen instanceof PracticeScreen) ScreenKeyboardEvents.beforeKeyPress(screen).register((s,event) -> {
if(key.matches(event)) client.gui.setScreen(null);
});
});
}
}
