package pvp;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
/** One temporary vanilla mannequin in an unpublished integrated world. */
final class PracticeGhost {
private static final String TAG="land_of_losers_practice_ghost";
private record Active(IntegratedServer server,UUID id) {}
private static volatile Active active;
static boolean local(Minecraft mc) {
return mc.hasSingleplayerServer() && mc.getSingleplayerServer()!=null && !mc.getSingleplayerServer().isPublished();
}
static boolean owns(Minecraft mc,LivingEntity target) {
var current=active;
return local(mc) && current!=null && current.server()==mc.getSingleplayerServer() && target instanceof Mannequin && current.id().equals(target.getUUID());
}
static void register() {
// Practice targets are temporary; do not restore them from a save.
ServerEntityEvents.ALLOW_LOAD.register((entity,level,reason,fromDisk)->!(fromDisk && entity instanceof Mannequin && entity.entityTags().contains(TAG) && level.getServer() instanceof IntegratedServer));
ServerLifecycleEvents.SERVER_STOPPING.register(PracticeGhost::clear);
}
private static void clear(MinecraftServer server) {
var current=active;
if(current==null || current.server()!=server) return;
active=null;
for(var level:server.getAllLevels()) {
var entity=level.getEntity(current.id());
if(entity instanceof Mannequin && entity.entityTags().contains(TAG)) entity.discard();
}
}
private static void feedback(Minecraft mc,IntegratedServer server,String message) {
mc.execute(()->{ if(mc.getSingleplayerServer()==server && mc.player!=null) mc.gui.hud.setOverlayMessage(Component.literal(message),false); });
}
static boolean request(Minecraft mc,boolean spawn) {
if(!local(mc) || mc.player==null || mc.level==null) return false;
var server=mc.getSingleplayerServer();
UUID playerId=mc.player.getUUID();
float yaw=mc.player.getYRot();
PracticeClient.stopPractice(mc);
server.execute(()->{
if(server.isPublished()) return;
if(!spawn) { clear(server); feedback(mc,server,"Ghost NPC removed"); return; }
var player=server.getPlayerList().getPlayer(playerId);
if(player==null || !player.isAlive() || player.isSpectator()) { feedback(mc,server,"Ghost NPC: player unavailable"); return; }
var level=player.level();
var npc=EntityTypes.MANNEQUIN.create(level,EntitySpawnReason.COMMAND);
if(npc==null) { feedback(mc,server,"Ghost NPC: creation failed"); return; }
double radians=Math.toRadians(yaw);
boolean found=false;
// Prefer a flat, clear position in front of the player, not inside a wall or over a ledge.
for(double distance:new double[]{2.5,2.0,3.0}) {
for(double offset:new double[]{0,1,-1}) {
npc.setPos(player.getX()-Math.sin(radians)*distance,Math.floor(player.getY()+.0001)+offset,player.getZ()+Math.cos(radians)*distance);
var box=npc.getBoundingBox();
if(level.noCollision(npc,box) && !level.noCollision(npc,box.move(0,-.12,0)) && player.hasLineOfSight(npc)) { found=true; break; }
}
if(found) break;
}
if(!found) { feedback(mc,server,"Ghost NPC: clear ground required in front"); return; }
npc.setCustomName(Component.literal("Ghost NPC"));
npc.setCustomNameVisible(true);
npc.setInvulnerable(false);
npc.setHealth(npc.getMaxHealth());
npc.setYRot(yaw+180);
npc.setXRot(0);
npc.setYHeadRot(yaw+180);
npc.setYBodyRot(yaw+180);
npc.addTag(TAG);
if(!level.addFreshEntity(npc)) { feedback(mc,server,"Ghost NPC: spawn rejected"); return; }
clear(server);
active=new Active(server,npc.getUUID());
feedback(mc,server,"Ghost NPC spawned | R to practice");
});
return true;
}
private PracticeGhost() {}
}
