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
/** A single support action per tick; inventory changes use normal container input. */
final class PracticeSupport {
private static int totemDelay,potionDelay,checkDelay,expectedSlot=-1;
private static ItemStack expected=ItemStack.EMPTY;
private static String status="Support idle";
static String status() { return status; }
static void reset() { totemDelay=potionDelay=checkDelay=0; expectedSlot=-1; expected=ItemStack.EMPTY; status="Support idle"; }
static boolean tick(Minecraft mc,double distance) {
if(totemDelay>0) totemDelay--;
if(potionDelay>0) potionDelay--;
var self=mc.player;
if(self==null || mc.level==null || mc.gameMode==null || !self.isAlive() || self.isCreative() || self.isSpectator() || mc.gui.screen()!=null || self.isUsingItem() || self.isHandsBusy() || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown() || self.containerMenu!=self.inventoryMenu || !self.inventoryMenu.getCarried().isEmpty()) return false;
var menu=self.inventoryMenu;
if(expectedSlot>=0) {
if(--checkDelay>0) { status="Inventory settle"; return true; }
boolean observed=ItemStack.isSameItemSameComponents(menu.getSlot(expectedSlot).getItem(),expected);
expectedSlot=-1; expected=ItemStack.EMPTY;
status=observed?"Inventory observed":"Inventory mismatch";
if(!observed) { potionDelay=Math.max(potionDelay,12); totemDelay=Math.max(totemDelay,12); }
return true;
}
if(totemDelay==0 && !self.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
for(int i=9;i<45;i++) {
if(!menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) continue;
expect(45,menu.getSlot(i).getItem());
mc.gameMode.handleContainerInput(menu.containerId,i,40,ContainerInput.SWAP,self);
totemDelay=8; status="AutoTotem swap"; return true;
}
}
if(potionDelay>0 || !SupportPlan.safeDistance(distance,self.onGround()) || self.isInWater() || self.isPassenger()) return false;
int best=-1,bestScore=0;
for(int i=0;i<9;i++) {
var stack=self.getInventory().getItem(i);
if(self.getCooldowns().isOnCooldown(stack)) continue;
int value=score(self,stack);
if(value>bestScore) { bestScore=value; best=i; }
}
int source=-1,sourceScore=0;
for(int i=9;i<36;i++) {
var stack=menu.getSlot(i).getItem();
if(self.getCooldowns().isOnCooldown(stack)) continue;
int value=score(self,stack);
if(value>sourceScore) { sourceScore=value; source=i; }
}
int old=self.getInventory().getSelectedSlot();
if(source>=0 && sourceScore>bestScore) {
int empty=-1;
for(int i=0;i<9;i++) if(i!=old && self.getInventory().getItem(i).isEmpty()) { empty=i; break; }
if(empty>=0) {
expect(36+empty,menu.getSlot(source).getItem());
mc.gameMode.handleContainerInput(menu.containerId,source,empty,ContainerInput.SWAP,self);
status="AutoReplenish potion"; return true;
}
}
if(best<0) return false;
var inv=self.getInventory();
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
potionDelay=success?24:8;
status=success?(bestScore>=100?"AutoBuff heal attempt":"AutoBuff effect attempt"):"AutoBuff use rejected";
return true;
}
private static void expect(int slot,ItemStack item) { expectedSlot=slot; expected=item.copy(); checkDelay=3; }
static String inventorySummary(Minecraft mc) {
if(mc.player==null) return "";
var menu=mc.player.inventoryMenu;
int totems=0,potions=0;
for(int i=9;i<=45;i++) {
var stack=menu.getSlot(i).getItem();
if(stack.is(Items.TOTEM_OF_UNDYING)) totems+=stack.getCount();
if(stack.is(Items.SPLASH_POTION)) potions+=stack.getCount();
}
return "T "+totems+" P "+potions+" | SPD "+effectTime(mc.player.getEffect(MobEffects.SPEED))+" STR "+effectTime(mc.player.getEffect(MobEffects.STRENGTH));
}
private static String effectTime(net.minecraft.world.effect.MobEffectInstance effect) {
if(effect==null) return "-";
int ticks=effect.getDuration();
return (effect.getAmplifier()+1)+":"+(ticks<0?"inf":((ticks+19)/20)+"s");
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
