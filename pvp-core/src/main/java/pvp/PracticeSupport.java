package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/** Ordinary inventory and item interactions during explicitly activated practice. */
final class PracticeSupport {
private static int delay;
private static String status="Support idle";
static String status() { return status; }
static void reset() { delay=0; status="Support idle"; }
static boolean tick(Minecraft mc,double distance) {
if(delay>0) { delay--; return false; }
var self=mc.player;
if(self==null || mc.level==null || mc.gameMode==null || !self.isAlive() || self.isCreative() || self.isSpectator() || mc.gui.screen()!=null || self.isUsingItem() || self.isHandsBusy() || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown() || self.containerMenu!=self.inventoryMenu || !self.inventoryMenu.getCarried().isEmpty()) return false;
if(!self.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
var menu=self.inventoryMenu;
for(int i=9;i<45;i++) {
if(!menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) continue;
mc.gameMode.handleContainerInput(menu.containerId,i,40,ContainerInput.SWAP,self);
delay=8; status="AutoTotem swap"; return true;
}
}
if(!SupportPlan.safeDistance(distance,self.onGround()) || self.isInWater() || self.isPassenger()) return false;
int best=-1,score=0;
for(int i=0;i<9;i++) {
var stack=self.getInventory().getItem(i);
if(self.getCooldowns().isOnCooldown(stack)) continue;
int value=score(self,stack);
if(value>score) { score=value; best=i; }
}
if(best<0) return false;
var inv=self.getInventory();
int old=inv.getSelectedSlot();
inv.setSelectedSlot(best);
var stack=self.getMainHandItem();
float pitch=self.getXRot();
boolean success=false;
try {
self.setXRot(85f);
var result=mc.gameMode.useItem(self,InteractionHand.MAIN_HAND);
success=result instanceof InteractionResult.Success;
if(success) self.swing(InteractionHand.MAIN_HAND,stack.getInteractAnimation(),false);
} finally { self.setXRot(pitch); inv.setSelectedSlot(old); }
delay=success?24:8;
status=success?(score>=100?"AutoBuff heal":"AutoBuff effect"):"AutoBuff use rejected";
return true;
}
private static int score(LocalPlayer self,ItemStack stack) {
if(!stack.is(Items.SPLASH_POTION)) return 0;
var contents=stack.get(DataComponents.POTION_CONTENTS);
if(contents==null) return 0;
int score=0;
for(var offered:contents.getAllEffects()) {
var type=offered.getEffect();
if(type.value().getCategory()==MobEffectCategory.HARMFUL) return 0;
if(type.equals(MobEffects.INSTANT_HEALTH)) {
if(!SupportPlan.needsHeal(self.getHealth(),self.getMaxHealth())) return 0;
score+=100+offered.getAmplifier()*5;
} else if(type.equals(MobEffects.SPEED) || type.equals(MobEffects.STRENGTH)) {
var active=self.getEffect(type);
if(!SupportPlan.needsBuff(active==null?0:active.getDuration(),active==null?-1:active.getAmplifier(),offered.getAmplifier())) continue;
score+=type.equals(MobEffects.STRENGTH)?30:20;
score+=offered.getAmplifier()*3;
}
}
return score;
}
private PracticeSupport() {}
}
