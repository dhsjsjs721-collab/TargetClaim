package pvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
/** Local practice camera. Does not change the camera entity, player rotations or reach. */
public final class PracticeFreeLook {
private static final OrbitView view=new OrbitView();
private static LocalPlayer owner;
private static Object world;
private static CameraType prior;
private static boolean active,rendered;
private static int startup;
private static boolean eligible(Minecraft mc) {
return mc.player!=null && mc.level!=null && mc.hasSingleplayerServer() && mc.getSingleplayerServer()!=null && !mc.getSingleplayerServer().isPublished() && mc.getCameraEntity()==mc.player && mc.player.isAlive() && !mc.player.isSpectator() && PracticeClient.hotkeyActive() && mc.gui.screen()==null;
}
public static void toggle(Minecraft mc) {
if(active) { stop(mc,true); return; }
if(!eligible(mc)) {
if(mc.player!=null) mc.gui.hud.setOverlayMessage(Component.literal("FreeLook: start local R practice first"),false);
return;
}
owner=mc.player; world=mc.level; prior=mc.options.getCameraType();
view.reset(owner.getYRot(),owner.getXRot());
active=true; rendered=false; startup=0;
mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
}
public static void tick(Minecraft mc) {
if(!active) return;
if(mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK) { stop(mc,false); return; }
if(!eligible(mc) || mc.player!=owner || mc.level!=world) { stop(mc,true); return; }
if(!rendered && ++startup>40) {
stop(mc,true);
mc.gui.hud.setOverlayMessage(Component.literal("FreeLook unavailable: camera hook not observed"),false);
}
}
public static void stop(Minecraft mc,boolean restore) {
if(!active) return;
active=false; rendered=false; startup=0;
if(restore && prior!=null && mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(prior);
prior=null; owner=null; world=null;
}
private static boolean usable(Minecraft mc) {
return active && eligible(mc) && mc.player==owner && mc.level==world && mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK;
}
public static boolean applyCamera(Object entity) {
var mc=Minecraft.getInstance();
if(entity!=owner || !usable(mc)) return false;
rendered=true;
return true;
}
public static boolean mouseTurn(Object entity,double dx,double dy) {
var mc=Minecraft.getInstance();
if(entity!=owner || !rendered || !usable(mc)) return false;
view.turn(dx,dy); return true;
}
public static float yaw() { return view.yaw(); }
public static float pitch() { return view.pitch(); }
private PracticeFreeLook() {}
}
