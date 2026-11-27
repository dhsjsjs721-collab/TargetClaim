package dev.landminimap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;
final class WorldWaypoints {
    record Target(int x,int y,int z,String label,int color){}
    static void draw(GuiGraphicsExtractor g,Minecraft mc){
        if(mc.player==null||mc.gui.screen()!=null||!LandMinimap.waypoints)return;
        List<Target> targets=new ArrayList<>();
        for(var m:LandMinimap.personal())targets.add(new Target(m.x(),m.y(),m.z(),m.label(),0xffffcf5b));
        for(var m:LandMinimap.shared())targets.add(new Target(m.x(),m.y(),m.z(),m.label(),0xff49c8ff));
        for(var p:LandMinimap.pings())targets.add(new Target(p.x(),p.y(),p.z(),p.label(),0xffff9c43));
        for(var p:LandMinimap.points())if(p.kind()==1||p.kind()==2||p.kind()==5)targets.add(new Target(p.x(),p.y(),p.z(),p.label(),OverlayPayload.pointColor(p.kind())));
        if(targets.isEmpty())return;
        double yaw=Math.toRadians(mc.player.getYRot()),pitch=Math.toRadians(mc.player.getXRot());
        double sin=Math.sin(yaw),cos=Math.cos(yaw),sp=Math.sin(pitch),cp=Math.cos(pitch);
        int w=g.guiWidth(),h=g.guiHeight();if(w<100||h<90)return;
        double scale=h/(2*Math.tan(Math.toRadians(Math.max(30,Math.min(110,mc.options.fov().get())))/2));
        targets.sort(Comparator.comparingDouble(t->Math.pow(t.x+.5-mc.player.getX(),2)+Math.pow(t.y+.5-mc.player.getEyeY(),2)+Math.pow(t.z+.5-mc.player.getZ(),2)));
        int shown=0;
        for(Target t:targets){
            if(shown>=8)break;
            double dx=t.x+.5-mc.player.getX(),dy=t.y+.5-mc.player.getEyeY(),dz=t.z+.5-mc.player.getZ();
            double right=-cos*dx-sin*dz,flat=-sin*dx+cos*dz,up=cp*dy+sp*flat,forward=cp*flat-sp*dy;
            double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(forward<=.2)continue;
            int x=(int)Math.round(w/2.0+right*scale/forward),y=(int)Math.round(h/2.0-up*scale/forward);
            if(x<16||x>w-16||y<20||y>h-50)continue;
            String label=t.label+" "+(int)distance+"m";
            int tw=mc.font.width(label);int lx=LandMinimap.clamp(x-tw/2,3,Math.max(3,w-tw-3));
            g.fill(x-2,y-2,x+3,y+3,t.color);
            g.fill(lx-2,y-13,lx+tw+2,y-2,0xc0192027);g.text(mc.font,label,lx,y-12,t.color,true);shown++;
        }
        Target nearest=targets.getFirst();
        double dx=nearest.x+.5-mc.player.getX(),dz=nearest.z+.5-mc.player.getZ();
        double angle=Math.toDegrees(Math.atan2(-dx,dz))-mc.player.getYRot();angle=((angle+540)%360)-180;
        String dir=Math.abs(angle)<12?"↑":angle>0?"→":"←";
        String label=dir+" "+nearest.label+" · "+(int)Math.hypot(dx,dz)+"m";
        g.text(mc.font,label,Math.max(2,(w-mc.font.width(label))/2),3,nearest.color,true);
    }
}
