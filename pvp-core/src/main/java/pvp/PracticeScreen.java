package pvp;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class PracticeScreen extends Screen {
private static CombatEngine.Mode mode=CombatEngine.Mode.CRITICAL;
public PracticeScreen() { super(Component.literal("PvP Practice")); }
@Override protected void init() {
addRenderableWidget(Button.builder(Component.literal("Practice: "+mode), button -> {
mode=CombatEngine.Mode.values()[(mode.ordinal()+1)%CombatEngine.Mode.values().length];
button.setMessage(Component.literal("Practice: "+mode));
}).bounds(width/2-100,height/2-24,200,20).build());
addRenderableWidget(Button.builder(Component.literal("Close"),button -> onClose()).bounds(width/2-100,height/2+4,200,20).build());
}
@Override public boolean isPauseScreen() { return false; }
}
