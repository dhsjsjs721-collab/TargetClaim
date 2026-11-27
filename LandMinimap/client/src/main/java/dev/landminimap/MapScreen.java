package dev.landminimap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
final class MapScreen extends Screen {
    private int cx,cz,zoom=1,side,left,top,panel;
    private boolean shared,showOverlays=true;
    private int pointIndex=-1;
    private UUID selected;
    private EditBox label;
    private String status="ЛКМ: метка · ПКМ: удалить";
    MapScreen(){super(Component.literal("Land of Losers · Карта"));var p=Minecraft.getInstance().player;if(p!=null){cx=LandMinimap.cell(p.blockPosition().getX());cz=LandMinimap.cell(p.blockPosition().getZ());}}
    @Override protected void init(){
        side=Math.max(48,Math.min(256,Math.min(width-156,height-58)));left=8;top=24;panel=left+side+8;
        button("↑",left+54,2,26,()->pan(0,-1));button("↓",left+80,2,26,()->pan(0,1));
        button("←",left+28,2,26,()->pan(-1,0));button("→",left+106,2,26,()->pan(1,0));
        button("−",left+132,2,24,()->zoom=Math.min(2,zoom*2));button("+",left+156,2,24,()->zoom=Math.max(1,zoom/2));
        button("Я",left+182,2,24,()->{var p=Minecraft.getInstance().player;if(p!=null){cx=LandMinimap.cell(p.blockPosition().getX());cz=LandMinimap.cell(p.blockPosition().getZ());}});
        label=new EditBox(font,panel,24,Math.max(50,width-panel-8),20,Component.literal("Название метки"));
        label.setMaxLength(24);label.setValue("Метка");addRenderableWidget(label);
        button("Личная",panel,46,65,()->{shared=false;status="Личная метка: видна только тебе";});
        button("Клану",panel+67,46,65,()->{shared=true;status="Командная метка: видна клану";});
        button("След. союзник",panel,92,132,()->{
            var team=LandMinimap.members();if(team.isEmpty()){selected=null;return;}
            int i=-1;for(int j=0;j<team.size();j++)if(team.get(j).id().equals(selected))i=j;
            selected=team.get((i+1)%team.size()).id();
        });
        button("Инвентарь",panel,114,132,()->send(ActionPayload.VIEW));
        button("Разрешить",panel,136,65,()->send(ActionPayload.ALLOW));
        button("Отозвать",panel+67,136,65,()->send(ActionPayload.REVOKE));
        button("Точки спавна",panel,158,132,()->{
            var points=LandMinimap.points();if(points.isEmpty()){status="Нет доступных точек в этом мире";return;}
            pointIndex=(pointIndex+1)%points.size();var p=points.get(pointIndex);
            cx=LandMinimap.cell(p.x());cz=LandMinimap.cell(p.z());showOverlays=true;status=p.label()+" · Y "+p.y();
        });
        button("Зоны вкл/выкл",panel,180,132,()->showOverlays=!showOverlays);
    }
    private void button(String text,int x,int y,int w,Runnable r){addRenderableWidget(Button.builder(Component.literal(text),b->r.run()).bounds(x,y,w,20).build());}
    private void pan(int x,int z){cx=LandMinimap.clamp(cx+x*side*zoom/3,-7500000,7500000);cz=LandMinimap.clamp(cz+z*side*zoom/3,-7500000,7500000);}
    private void send(int op){
        var member=LandMinimap.members().stream().filter(m->m.id().equals(selected)).findFirst().orElse(null);
        if(member==null){status="Сначала выбери союзника";return;}
        LandMinimap.action(op,selected,0,0,"");status="Запрос отправлен: "+member.name();
    }
    private int startX(){return cx-side*zoom/2;}private int startZ(){return cz-side*zoom/2;}
    private int px(int block){return left+Math.floorDiv(LandMinimap.cell(block)-startX(),zoom);}
    private int py(int block){return top+Math.floorDiv(LandMinimap.cell(block)-startZ(),zoom);}
    private boolean inside(int x,int y){return x>=left&&x<left+side&&y>=top&&y<top+side;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        int x=(int)e.x(),y=(int)e.y();
        if(!inside(x,y)||!LandMinimap.active(Minecraft.getInstance()))return super.mouseClicked(e,twice);
        if(e.button()==0){
            String name=label.getValue().strip();if(name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>96||name.codePoints().anyMatch(Character::isISOControl)){status="Некорректное название";return true;}
            int bx=(startX()+(x-left)*zoom)*4+2,bz=(startZ()+(y-top)*zoom)*4+2;
            if(shared){LandMinimap.action(ActionPayload.ADD,ActionPayload.NONE,bx,bz,name);status="Запрос создания командной метки";}
            else{status=LandMinimap.localMarks.add(new TeamPayload.Mark(UUID.randomUUID(),LandMinimap.viewer,bx,bz,name))?"Личная метка сохранена":"Лимит 64 или ошибка сохранения";}
            return true;
        }
        if(e.button()==1){
            TeamPayload.Mark best=null;boolean local=false;double d=49;
            for(var m:LandMinimap.personal()){double nd=Math.pow(px(m.x())-x,2)+Math.pow(py(m.z())-y,2);if(nd<d){best=m;d=nd;local=true;}}
            for(var m:LandMinimap.shared())if(m.owner().equals(LandMinimap.viewer)){double nd=Math.pow(px(m.x())-x,2)+Math.pow(py(m.z())-y,2);if(nd<d){best=m;d=nd;local=false;}}
            if(best!=null){if(local)LandMinimap.localMarks.remove(best.id());else LandMinimap.action(ActionPayload.REMOVE,best.id(),0,0,"");status="Удаление метки";}
            return true;
        }
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        if(!LandMinimap.active(Minecraft.getInstance())||LandMinimap.map==null){g.text(font,"Мост сервера недоступен",left,top,0xffff7777,true);return;}
        // Limit the prepared working set: at maximum zoom only the visible square is read.
        LandMinimap.map.prepare(cx,cz,side*zoom/2+1);
        g.fill(left-1,top-1,left+side+1,top+side+1,0xff58636c);
        g.fill(left,top,left+side,top+side,LandMinimap.FOG);
        for(int y=0;y<side;y++){
            int x=0;
            while(x<side){int color=sample(x,y),end=x+1;while(end<side&&sample(end,y)==color)end++;
                if(color!=0)g.fill(left+x,top+y,left+end,top+y+1,color);x=end;}
        }
        if(showOverlays){
            for(var zone:LandMinimap.zones()){
                int x1=px(zone.x1()),z1=py(zone.z1()),x2=px(zone.x2()),z2=py(zone.z2());
                int color=OverlayPayload.zoneColor(zone.kind());
                ZoneDraw.outline(g,left,top,side,x1,z1,x2,z2,color);
                if(inside(x1,z1)&&x1+font.width(zone.label())<left+side&&z1+10<top+side)g.text(font,zone.label(),x1+1,z1+1,color,true);
            }
            for(var point:LandMinimap.points())mark(g,point.x(),point.z(),point.label(),OverlayPayload.pointColor(point.kind()));
        }
        for(var m:LandMinimap.personal())mark(g,m.x(),m.z(),m.label(),0xffffcf5b);
        for(var m:LandMinimap.shared())mark(g,m.x(),m.z(),m.label(),0xff49c8ff);
        for(var m:LandMinimap.members())mark(g,(int)Math.floor(m.x()),(int)Math.floor(m.z()),m.name(),LandMinimap.ALLY);
        var p=Minecraft.getInstance().player;
        mark(g,p.blockPosition().getX(),p.blockPosition().getZ(),"Я",0xffffffff);
        g.text(font,shared?"Метка: клану":"Метка: личная",panel,69,shared?0xff49c8ff:0xffffcf5b,true);
        var team=LandMinimap.members();if(selected==null&&!team.isEmpty())selected=team.get(0).id();
        String nick=team.stream().filter(m->m.id().equals(selected)).map(TeamPayload.Member::name).findFirst().orElse("—");
        g.text(font,"Союзник: "+nick,panel,81,0xff48e39a,true);
        if(inside(mx,my))g.text(font,"X "+((startX()+(mx-left)*zoom)*4)+"  Z "+((startZ()+(my-top)*zoom)*4),left,top+side+3,0xffdddddd,true);
        String detail=status;
        if(showOverlays&&inside(mx,my)){
            for(var zone:LandMinimap.zones())if(mx>=px(zone.x1())&&mx<=px(zone.x2())&&my>=py(zone.z1())&&my<=py(zone.z2()))detail=zone.label()+(zone.kind()==3?" · Y "+zone.y1()+".."+zone.y2():"");
            for(var point:LandMinimap.points())if(Math.abs(mx-px(point.x()))<=4&&Math.abs(my-py(point.z()))<=4){detail=point.label()+" · "+point.x()+", "+point.y()+", "+point.z();break;}
        }
        g.text(font,detail,left,top+side+15,0xffeeeeee,true);
    }
    private int sample(int x,int z){
        int a=startX()+x*zoom,b=startZ()+z*zoom;
        for(int dz=0;dz<zoom;dz++)for(int dx=0;dx<zoom;dx++){int c=LandMinimap.map.color(a+dx,b+dz);if(c!=0)return c;}
        return 0;
    }
    private void mark(GuiGraphicsExtractor g,int x,int z,String name,int color){
        int a=px(x),b=py(z);if(!inside(a,b))return;
        g.fill(Math.max(left,a-2),Math.max(top,b-2),Math.min(left+side,a+3),Math.min(top+side,b+3),color);
        if(a+4+font.width(name)<left+side&&b+9<top+side)g.text(font,name,a+4,b,color,true);
    }
}
