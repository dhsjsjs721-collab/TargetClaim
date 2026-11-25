package pvp;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.io.IOException;
public final class PracticeClient implements ClientModInitializer {
static boolean guiLocked;
static CombatSettings settings=CombatSettings.defaults();
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
ClientTickEvents.START_CLIENT_TICK.register(client -> PracticeCombat.startTick(client,settings));
ClientTickEvents.END_CLIENT_TICK.register(client -> {
PracticeCombat.releaseInput(client);
while(key.consumeClick()) {
if(!guiLocked && client.player != null && client.gui.screen()==null) client.gui.setScreen(new PracticeScreen());
}
PracticeCombat.tick(client,settings);
});
ScreenEvents.BEFORE_INIT.register((client,screen,w,h) -> {
if(screen instanceof PracticeScreen) ScreenKeyboardEvents.beforeKeyPress(screen).register((s,event) -> {
if(key.matches(event)) client.gui.setScreen(null);
});
});
}
}
