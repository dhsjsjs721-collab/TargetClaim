package dev.landminimap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.*;

public final class LandMinimap implements ClientModInitializer {
    static final int CELL=4, SIZE=64, PIXEL=2, WIDTH=SIZE*PIXEL;
    static final int FOG=0xff15191e, ALLY=0xff48e39a;
    private static ClientLevel level;
    private static TeamPayload packet;
    private static OverlayPayload overlay;
    private static FeaturePayload features;
    private static long featureReceived;
    static final int SURFACE=10000;
    static boolean waypoints=true;
    static int currentLayer=SURFACE;
    private static Path mapDir;
    private static final LinkedHashMap<Integer,Exploration> layers=new LinkedHashMap<>(8,.75f,true);
    private static long overlayReceived;
    private static Set<UUID> allies=Set.of();
    private static long received;
    static Exploration map;
    static LocalMarks localMarks;
    private static KeyMapping mapKey;
    private static UUID world;
    static UUID viewer;
    private static int ticks;
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(TeamPayload.TYPE,TeamPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OverlayPayload.TYPE,OverlayPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FeaturePayload.TYPE,FeaturePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FeaturePayload.TYPE,(data,context)->{
            if(active(context.client())&&data.viewer().equals(viewer)&&data.world().equals(world)){features=data;featureReceived=System.nanoTime();}
        });
        ClientPlayNetworking.registerGlobalReceiver(OverlayPayload.TYPE,(data,context)->{
            if(active(context.client())&&data.viewer().equals(viewer)&&data.world().equals(world)){overlay=data;overlayReceived=System.nanoTime();}
        });
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE,ActionPayload.CODEC);
        mapKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.landminimap.map",InputConstants.Type.KEYBOARD,InputConstants.KEY_M,KeyMapping.Category.register(Identifier.fromNamespaceAndPath("landminimap","controls"))));
        ClientPlayNetworking.registerGlobalReceiver(TeamPayload.TYPE,(data,context)->{
            Minecraft mc=context.client();
            if(mc.level==null || mc.player==null || !data.viewer().equals(mc.player.getUUID()))return;
            if(level!=mc.level || !data.world().equals(world) || !data.viewer().equals(viewer)) {
                reset();
                level=mc.level;world=data.world();viewer=data.viewer();
                Path dir=FabricLoader.getInstance().getGameDir().resolve("land-minimap").resolve(viewer.toString()).resolve(world.toString());
                mapDir=dir;
                currentLayer=SURFACE;
                map=layerMap(SURFACE);
                localMarks=new LocalMarks(dir.resolve("personal.bin"),viewer);
            }
            packet=data;
            Set<UUID> ids=new HashSet<>();for(var member:data.members())ids.add(member.id());
            allies=Set.copyOf(ids);received=System.nanoTime();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(LandMinimap::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("landminimap","minimap"),(graphics,delta)->draw(graphics));
    }
    static boolean active(Minecraft mc) {
        return mc.player!=null && mc.level!=null && mc.level==level && packet!=null
                && System.nanoTime()-received<3_000_000_000L && viewer.equals(mc.player.getUUID());
    }
    public static boolean glow(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        return active(mc) && entity instanceof Player && entity!=mc.player && allies.contains(entity.getUUID());
    }
    private static void reset() {
        for(var l:layers.values())l.flush();layers.clear();mapDir=null;
        features=null;featureReceived=0;currentLayer=SURFACE;
        packet=null;overlay=null;overlayReceived=0;allies=Set.of();map=null;localMarks=null;level=null;world=null;viewer=null;received=0;ticks=0;
    }
    static int cell(int coordinate){return Math.floorDiv(coordinate,CELL);}
    private static void tick(Minecraft mc) {
        if(level!=null && mc.level!=level){reset();return;}
        while(mapKey.consumeClick())if(active(mc)&&mc.gui.screen()==null)mc.gui.setScreen(new MapScreen());
        if(!active(mc))return;
        if(++ticks%5!=0)return;
        BlockPos pos=mc.player.blockPosition();
        int x=cell(pos.getX()),z=cell(pos.getZ());
        currentLayer=featureReady()?features.actualLayer():Math.floorDiv(pos.getY(),16);
        map=layerMap(currentLayer);
        if(!(mc.gui.screen() instanceof MapScreen))map.prepare(x,z,SIZE/2+1);
        if(ticks%40==0&&featureReady()){if(mc.gui.screen() instanceof MapScreen screen)screen.request();else request(currentLayer,x,z,40);}
        // Only the cell physically occupied by the local player is recorded. No chunk scans.
        if(!mc.player.isSpectator()){
            BlockPos floor=pos.below();
            var block=mc.level.getBlockState(floor);
            if(!block.isAir()){
                int color=block.getMapColor(mc.level,floor).col;
                if(color!=0)map.visit(x,z,0xff000000|color);
            }
        }
        if(ticks%200==0)for(var l:layers.values())l.flush();
    }
    static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    static Exploration layerMap(int layer){
        Exploration result=layers.get(layer);if(result!=null)return result;
        result=new Exploration(mapDir.resolve("layers").resolve(layer==SURFACE?"surface":"y_"+layer));
        layers.put(layer,result);
        if(layers.size()>5){var e=layers.entrySet().iterator().next();e.getValue().flush();layers.remove(e.getKey());}
        return result;
    }
    static boolean featureReady(){return active(Minecraft.getInstance())&&features!=null&&System.nanoTime()-featureReceived<6_000_000_000L;}
    static boolean sharing(){return featureReady()&&features.sharing();}
    static boolean truncated(){return featureReady()&&features.truncated();}
    static List<FeaturePayload.Status> statuses(){return featureReady()?features.team():List.of();}
    static List<FeaturePayload.Ping> pings(){return featureReady()?features.pings():List.of();}
    static int color(Exploration local,int layer,int x,int z){int c=local.color(x,z);if(c!=0)return c;return featureReady()&&features.sharing()&&features.layer()==layer?features.colors().getOrDefault(FeaturePayload.key(x,z),0):0;}
    static void request(int layer,int x,int z,int radius){action(8,ActionPayload.NONE,x,z,layer+":"+clamp(radius,32,256));}
    static String layerName(int layer){return layer==SURFACE?"Поверхность":"Y "+layer*16+"…"+(layer*16+15);}
    static boolean overlaysReady(){return active(Minecraft.getInstance())&&overlay!=null&&System.nanoTime()-overlayReceived<6_000_000_000L;}
    static List<OverlayPayload.Point> points(){return overlaysReady()?overlay.points():List.of();}
    static List<OverlayPayload.Zone> zones(){return overlaysReady()?overlay.zones():List.of();}
    static List<TeamPayload.Member> members(){return active(Minecraft.getInstance())?packet.members():List.of();}
    static List<TeamPayload.Mark> shared(){return active(Minecraft.getInstance())?packet.marks():List.of();}
    static List<TeamPayload.Mark> personal(){return localMarks==null?List.of():localMarks.all();}
    static void action(int op,UUID id,int x,int z,String label){
        if(active(Minecraft.getInstance())&&ClientPlayNetworking.canSend(ActionPayload.TYPE))ClientPlayNetworking.send(new ActionPayload(op,id,x,z,label));
        else notice("Серверный мост недоступен.");
    }
    static void notice(String msg){var p=Minecraft.getInstance().player;if(p!=null)p.sendSystemMessage(Component.literal("LandMap: "+msg));}
    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc=Minecraft.getInstance();
        if(!active(mc)||map==null||mc.gui.screen() instanceof MapScreen)return;
        int left=g.guiWidth()-WIDTH-8,top=10;
        if(left<4)return;
        int cx=cell(mc.player.blockPosition().getX()),cz=cell(mc.player.blockPosition().getZ());
        int startX=cx-SIZE/2,startZ=cz-SIZE/2;
        g.fill(left-2,top-2,left+WIDTH+2,top+WIDTH+2,0xff50606b);
        g.fill(left,top,left+WIDTH,top+WIDTH,FOG);
        for(int row=0;row<SIZE;row++){
            int x=0;
            while(x<SIZE){
                int color=color(map,currentLayer,startX+x,startZ+row);
                int end=x+1;while(end<SIZE&&color(map,currentLayer,startX+end,startZ+row)==color)end++;
                if(color!=0)g.fill(left+x*PIXEL,top+row*PIXEL,left+end*PIXEL,top+(row+1)*PIXEL,color);
                x=end;
            }
        }
        for(var zone:zones()){
            int x1=left+(cell(zone.x1())-startX)*PIXEL,z1=top+(cell(zone.z1())-startZ)*PIXEL;
            int x2=left+(cell(zone.x2())-startX)*PIXEL+PIXEL-1,z2=top+(cell(zone.z2())-startZ)*PIXEL+PIXEL-1;
            ZoneDraw.outline(g,left,top,WIDTH,x1,z1,x2,z2,OverlayPayload.zoneColor(zone.kind()));
        }
        for(var point:points()){
            int x=left+(cell(point.x())-startX)*PIXEL+1,z=top+(cell(point.z())-startZ)*PIXEL+1;
            if(x>=left+2&&x<left+WIDTH-2&&z>=top+2&&z<top+WIDTH-2){
                int color=OverlayPayload.pointColor(point.kind());
                g.fill(x-2,z-2,x+3,z+3,0xff131820);g.fill(x-1,z-1,x+2,z+2,color);
            }
        }
        int midX=left+WIDTH/2+1,midY=top+WIDTH/2+1;
        g.fill(midX-2,midY-2,midX+3,midY+3,0xfff8f9fa);
        g.text(mc.font,"N",left+WIDTH/2-3,top+2,0xffeeeeee,true);
        for(var m:personal())drawMark(g,m,0xffffcf5b,left,top,midX,midY,mc);
        for(var m:shared())drawMark(g,m,0xff49c8ff,left,top,midX,midY,mc);
        for(var ping:pings()){int x=left+(cell(ping.x())-startX)*PIXEL+1,z=top+(cell(ping.z())-startZ)*PIXEL+1;if(x>=left&&x<left+WIDTH&&z>=top&&z<top+WIDTH)g.fill(x-2,z-2,x+3,z+3,0xffff9c43);}
        int row=0;
        for(var m:packet.members()){
            int rawX=(int)Math.floor((m.x()-mc.player.getX())/CELL*PIXEL)+midX;
            int rawY=(int)Math.floor((m.z()-mc.player.getZ())/CELL*PIXEL)+midY;
            int px=clamp(rawX,left+3,left+WIDTH-4),py=clamp(rawY,top+3,top+WIDTH-4);
            g.fill(px-2,py-2,px+3,py+3,0xff0b2018);
            g.fill(px-1,py-1,px+2,py+2,ALLY);
            int dist=(int)Math.round(Math.hypot(m.x()-mc.player.getX(),m.z()-mc.player.getZ()));
            String label=m.name()+" "+dist+"m"+(rawX!=px||rawY!=py?" >":"");
            if(row<4)g.text(mc.font,label,left,top+WIDTH+5+row++*10,ALLY,true);
        }
        g.text(mc.font,layerName(currentLayer),left,top+WIDTH+5+row*10,0xffb9c6ce,true);
        int sy=12;
        for(var status:statuses()){
            if(sy>g.guiHeight()/2)break;
            String line=status.name()+(status.speaking()?" [MIC]":"")+(status.dead()?" †":"")+(status.sameWorld()?"":" [другой мир]");
            g.text(mc.font,line,8,sy,0xff48e39a,true);
            String info=String.format(java.util.Locale.ROOT,"HP %.0f/%.0f · Б %d · Е %d",status.health(),status.max(),status.armor(),status.food());
            g.text(mc.font,info,8,sy+10,status.health()<=6?0xffff6868:0xffeeeeee,true);
            sy+=24;
            if(!status.race().isEmpty()){g.text(mc.font,status.race(),8,sy-2,0xffc39bff,true);sy+=10;}
        }
        WorldWaypoints.draw(g,mc);
    }
    private static void drawMark(GuiGraphicsExtractor g,TeamPayload.Mark m,int color,int left,int top,int midX,int midY,Minecraft mc){
        int x=clamp((int)Math.floor((m.x()-mc.player.getX())/CELL*PIXEL)+midX,left+3,left+WIDTH-4);
        int y=clamp((int)Math.floor((m.z()-mc.player.getZ())/CELL*PIXEL)+midY,top+3,top+WIDTH-4);
        g.fill(x-2,y-2,x+3,y+3,0xff141a1d);g.fill(x-1,y-1,x+2,y+2,color);
    }
}
