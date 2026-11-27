package dev.landminimap;

import net.fabricmc.api.ClientModInitializer;
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
    private static final int CELL=4, SIZE=64, PIXEL=2, WIDTH=SIZE*PIXEL;
    private static final int FOG=0xff15191e, ALLY=0xff48e39a;
    private static ClientLevel level;
    private static TeamPayload packet;
    private static Set<UUID> allies=Set.of();
    private static long received;
    private static Exploration map;
    private static UUID world,viewer;
    private static int ticks;
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(TeamPayload.TYPE,TeamPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TeamPayload.TYPE,(data,context)->{
            Minecraft mc=context.client();
            if(mc.level==null || mc.player==null || !data.viewer().equals(mc.player.getUUID()))return;
            if(level!=mc.level || !data.world().equals(world) || !data.viewer().equals(viewer)) {
                reset();
                level=mc.level;world=data.world();viewer=data.viewer();
                map=new Exploration(FabricLoader.getInstance().getGameDir().resolve("land-minimap").resolve(viewer.toString()).resolve(world.toString()));
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
    private static boolean active(Minecraft mc) {
        return mc.player!=null && mc.level!=null && mc.level==level && packet!=null
                && System.nanoTime()-received<3_000_000_000L && viewer.equals(mc.player.getUUID());
    }
    public static boolean glow(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        return active(mc) && entity instanceof Player && entity!=mc.player && allies.contains(entity.getUUID());
    }
    private static void reset() {
        if(map!=null)map.flush();
        packet=null;allies=Set.of();map=null;level=null;world=null;viewer=null;received=0;ticks=0;
    }
    private static int cell(int coordinate){return Math.floorDiv(coordinate,CELL);}
    private static void tick(Minecraft mc) {
        if(level!=null && mc.level!=level){reset();return;}
        if(!active(mc))return;
        if(++ticks%5!=0)return;
        BlockPos pos=mc.player.blockPosition();
        int x=cell(pos.getX()),z=cell(pos.getZ());
        map.prepare(x,z,SIZE/2+1);
        // Only the cell physically occupied by the local player is recorded. No chunk scans.
        if(!mc.player.isSpectator()){
            BlockPos floor=pos.below();
            var block=mc.level.getBlockState(floor);
            if(!block.isAir()){
                int color=block.getMapColor(mc.level,floor).col;
                if(color!=0)map.visit(x,z,0xff000000|color);
            }
        }
        if(ticks%200==0)map.flush();
    }
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc=Minecraft.getInstance();
        if(!active(mc)||map==null)return;
        int left=g.guiWidth()-WIDTH-8,top=10;
        if(left<4)return;
        int cx=cell(mc.player.blockPosition().getX()),cz=cell(mc.player.blockPosition().getZ());
        int startX=cx-SIZE/2,startZ=cz-SIZE/2;
        g.fill(left-2,top-2,left+WIDTH+2,top+WIDTH+2,0xff50606b);
        g.fill(left,top,left+WIDTH,top+WIDTH,FOG);
        for(int row=0;row<SIZE;row++){
            int x=0;
            while(x<SIZE){
                int color=map.color(startX+x,startZ+row);
                int end=x+1;while(end<SIZE&&map.color(startX+end,startZ+row)==color)end++;
                if(color!=0)g.fill(left+x*PIXEL,top+row*PIXEL,left+end*PIXEL,top+(row+1)*PIXEL,color);
                x=end;
            }
        }
        int midX=left+WIDTH/2+1,midY=top+WIDTH/2+1;
        g.fill(midX-2,midY-2,midX+3,midY+3,0xfff8f9fa);
        g.text(mc.font,"N",left+WIDTH/2-3,top+2,0xffeeeeee,true);
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
    }
}
