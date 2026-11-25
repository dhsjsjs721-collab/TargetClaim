package pvp;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
public final class PracticeClient implements ClientModInitializer {
static boolean guiLocked;
static CombatSettings settings=CombatSettings.defaults();
static CombatEngine.Mode mode=CombatEngine.Mode.CRITICAL;
public void onInitializeClient() {
var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pvp_practice","practice"));
var key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvp_practice.open",InputConstants.Type.KEYBOARD,InputConstants.KEY_DOWN,category));
ClientTickEvents.END_CLIENT_TICK.register(client -> {
while(key.consumeClick()) {
if(!guiLocked && client.player != null && client.gui.screen()==null) client.gui.setScreen(new PracticeScreen());
}
PracticeCombat.tick(client,settings,mode);
});
ScreenEvents.BEFORE_INIT.register((client,screen,w,h) -> {
if(screen instanceof PracticeScreen) ScreenKeyboardEvents.beforeKeyPress(screen).register((s,event) -> {
if(key.matches(event)) client.gui.setScreen(null);
});
});
}
}
