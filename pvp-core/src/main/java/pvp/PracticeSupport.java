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
private static int totemDelay,potionDelay,armorDelay,checkDelay,expectedSlot=-1;
private static ItemStack expected=ItemStack.EMPTY,expectedSource=ItemStack.EMPTY;
private static int sourceSlot=-1;
private static boolean suspended;
private static final SupportObservation effects=new SupportObservation();
private static String status="Support idle";
static String status() { return status; }
static void idleTick() { if(totemDelay>0) totemDelay--; if(potionDelay>0) potionDelay--; if(armorDelay>0) armorDelay--; }
static void reset() {
totemDelay=potionDelay=armorDelay=checkDelay=0; clearSwap(); effects.reset(); suspended=false; status="Support idle";
}
private static void clearSwap() { expectedSlot=sourceSlot=-1; checkDelay=0; expected=expectedSource=ItemStack.EMPTY; }
private static void suspend(String reason) { clearSwap(); suspended=true; PracticeCombat.supportObservation(reason); status=reason; }
static void observe(Minecraft mc,boolean interrupted) {
var self=mc.player;
if(self==null) return;
if(interrupted && expectedSlot>=0) suspend("Inventory observation interrupted; support suspended");
if(!effects.pending()) return;
var result=interrupted?effects.interrupt():effects.observe(snapshot(self));
if(result!=SupportObservation.Result.PENDING) PracticeCombat.supportObservation("Potion observation: "+result.name());
}
private static SupportObservation.Effect effect(net.minecraft.world.effect.MobEffectInstance value) {
return value==null?SupportObservation.Effect.absent():new SupportObservation.Effect(value.getAmplifier(),value.getDuration());
}
private static SupportObservation.Snapshot snapshot(LocalPlayer self) {
return new SupportObservation.Snapshot(self.getHealth(),effect(self.getEffect(MobEffects.SPEED)),effect(self.getEffect(MobEffects.STRENGTH)));
}
static boolean tick(Minecraft mc,double distance) {
idleTick();
if(suspended) return false;
var self=mc.player;
if(self==null || mc.level==null || mc.gameMode==null || !self.isAlive() || self.isCreative() || self.isSpectator() || mc.gui.screen()!=null || mc.isPaused() || mc.getCameraEntity()!=self || self.isUsingItem() || self.isHandsBusy() || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown() || self.containerMenu!=self.inventoryMenu || !self.inventoryMenu.getCarried().isEmpty()) return false;
var menu=self.inventoryMenu;
if(expectedSlot>=0) {
if(--checkDelay>0) { status="Inventory settle"; return true; }
var actual=menu.getSlot(expectedSlot).getItem();
var source=menu.getSlot(sourceSlot).getItem();
boolean observed=SupportObservation.swapObserved(ItemStack.isSameItemSameComponents(source,expectedSource),source.getCount(),expectedSource.getCount(),ItemStack.isSameItemSameComponents(actual,expected),actual.getCount(),expected.getCount());
clearSwap();
if(!observed) suspend("Inventory mismatch; support suspended");
else status="Inventory locally observed";
return true;
}
if(totemDelay==0 && !self.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
for(int i=9;i<45;i++) {
if(!menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) continue;
expect(i,45,menu.getSlot(45).getItem(),menu.getSlot(i).getItem());
mc.gameMode.handleContainerInput(menu.containerId,i,40,ContainerInput.SWAP,self);
totemDelay=8; status="AutoTotem swap"; return true;
}
}
if(!SupportPlan.safeDistance(distance,self.onGround()) || self.isInWater() || self.isPassenger()) return false;
if(potionDelay>0 || effects.pending()) return tryArmor(mc,self,distance);
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
expect(source,36+empty,ItemStack.EMPTY,menu.getSlot(source).getItem());
mc.gameMode.handleContainerInput(menu.containerId,source,empty,ContainerInput.SWAP,self);
status="AutoReplenish potion"; return true;
}
}
if(best<0) return tryArmor(mc,self,distance);
var inv=self.getInventory();
inv.setSelectedSlot(best);
var stack=self.getMainHandItem();
var before=snapshot(self);
int mask=effectMask(self,stack);
float pitch=self.getXRot();
boolean success=false;
try {
self.setXRot(85f);
var result=mc.gameMode.useItem(self,InteractionHand.MAIN_HAND);
success=result instanceof InteractionResult.Success;
if(success) self.swing(InteractionHand.MAIN_HAND,stack.getInteractAnimation(),false);
} finally { self.setXRot(pitch); inv.setSelectedSlot(old); }
if(success && mask!=0) effects.begin(before,mask,24);
potionDelay=success?24:8;
status=success?(bestScore>=100?"AutoBuff heal attempt":"AutoBuff effect attempt"):"AutoBuff use rejected";
return true;
}
/** Fill only empty armor slots in a private local world, never remove equipped items. */
private static boolean tryArmor(Minecraft mc,LocalPlayer self,double distance) {
if(armorDelay>0 || !mc.hasSingleplayerServer() || mc.getSingleplayerServer()==null || mc.getSingleplayerServer().isPublished()) return false;
var menu=self.inventoryMenu;
for(int slot=5;slot<=8;slot++) {
if(!SupportPlan.mayEquipArmor(true,menu.getSlot(slot).getItem().isEmpty(),self.onGround(),distance)) continue;
int best=-1; double bestScore=-1;
for(int source=9;source<45;source++) {
var item=menu.getSlot(source).getItem();
int protection=vanillaArmor(item,slot);
if(protection<=0 || item.getMaxDamage()<=0 || !menu.getSlot(slot).mayPlace(item)) continue;
double durability=(item.getMaxDamage()-item.getDamageValue())/(double)item.getMaxDamage();
double score=SupportPlan.armorScore(protection,durability,item.isEnchanted());
if(score>bestScore) { bestScore=score; best=source; }
}
if(best<0) continue;
expect(best,slot,ItemStack.EMPTY,menu.getSlot(best).getItem());
mc.gameMode.handleContainerInput(menu.containerId,best,0,ContainerInput.QUICK_MOVE,self);
armorDelay=12; status="AutoArmor fill"; return true;
}
return false;
}
private static int vanillaArmor(ItemStack item,int slot) {
return switch(slot) {
case 5 -> item.is(Items.NETHERITE_HELMET)||item.is(Items.DIAMOND_HELMET)?3:item.is(Items.IRON_HELMET)||item.is(Items.CHAINMAIL_HELMET)||item.is(Items.GOLDEN_HELMET)?2:item.is(Items.LEATHER_HELMET)?1:0;
case 6 -> item.is(Items.NETHERITE_CHESTPLATE)||item.is(Items.DIAMOND_CHESTPLATE)?8:item.is(Items.IRON_CHESTPLATE)?6:item.is(Items.CHAINMAIL_CHESTPLATE)||item.is(Items.GOLDEN_CHESTPLATE)?5:item.is(Items.LEATHER_CHESTPLATE)?3:0;
case 7 -> item.is(Items.NETHERITE_LEGGINGS)||item.is(Items.DIAMOND_LEGGINGS)?6:item.is(Items.IRON_LEGGINGS)?5:item.is(Items.CHAINMAIL_LEGGINGS)?4:item.is(Items.GOLDEN_LEGGINGS)?3:item.is(Items.LEATHER_LEGGINGS)?2:0;
case 8 -> item.is(Items.NETHERITE_BOOTS)||item.is(Items.DIAMOND_BOOTS)?3:item.is(Items.IRON_BOOTS)?2:item.is(Items.CHAINMAIL_BOOTS)||item.is(Items.GOLDEN_BOOTS)||item.is(Items.LEATHER_BOOTS)?1:0;
default -> 0;
};
}
private static void expect(int source,int slot,ItemStack sourceAfter,ItemStack item) { sourceSlot=source; expectedSlot=slot; expectedSource=sourceAfter.copy(); expected=item.copy(); checkDelay=3; }
static String inventorySummary(Minecraft mc) {
if(mc.player==null) return "";
var menu=mc.player.inventoryMenu;
int totems=0,potions=0;
for(int i=9;i<=45;i++) {
var stack=menu.getSlot(i).getItem();
if(stack.is(Items.TOTEM_OF_UNDYING)) totems+=stack.getCount();
if(stack.is(Items.SPLASH_POTION)) potions+=stack.getCount();
}
int armor=0; for(int i=5;i<=8;i++) if(!menu.getSlot(i).getItem().isEmpty()) armor++;
return "T "+totems+" P "+potions+" A "+armor+"/4 | SPD "+effectTime(mc.player.getEffect(MobEffects.SPEED))+" STR "+effectTime(mc.player.getEffect(MobEffects.STRENGTH));
}
private static String effectTime(net.minecraft.world.effect.MobEffectInstance effect) {
if(effect==null) return "-";
int ticks=effect.getDuration();
return (effect.getAmplifier()+1)+":"+(ticks<0?"inf":((ticks+19)/20)+"s");
}
private static int effectMask(LocalPlayer self,ItemStack stack) {
var contents=stack.get(DataComponents.POTION_CONTENTS);
if(contents==null) return 0;
int mask=0;
for(var offered:contents.getAllEffects()) {
var type=offered.getEffect();
if(type.equals(MobEffects.INSTANT_HEALTH) && SupportPlan.needsHeal(self.getHealth(),self.getMaxHealth())) mask|=SupportObservation.HEAL;
if(type.equals(MobEffects.SPEED) || type.equals(MobEffects.STRENGTH)) {
var active=self.getEffect(type);
if(SupportPlan.needsBuff(active==null?0:active.getDuration(),active==null?-1:active.getAmplifier(),offered.getAmplifier())) mask|=type.equals(MobEffects.SPEED)?SupportObservation.SPEED:SupportObservation.STRENGTH;
}
}
return mask;
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
