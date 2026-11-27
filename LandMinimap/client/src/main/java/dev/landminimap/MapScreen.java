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
    private int cx,cz,zoom=1,side,left,top,panel,layer=LandMinimap.SURFACE;
    private int tab,step=19,buttonHeight=17,panelWidth=120;
    private String storedLabel="Метка";
    private Exploration selectedMap;
    private static final String[] TYPES={"Метка","База","Шахта","Ферма","Спавнер","Опасность","Помощь"};
    private int type,pingType;
    private boolean shared,showOverlays=true;
    private int pointIndex=-1;
    private UUID selected;
    private EditBox label;
    private String status="ЛКМ: метка · ПКМ: удалить";
    MapScreen(){super(Component.literal("Land of Losers · Карта"));var p=Minecraft.getInstance().player;if(p!=null){cx=LandMinimap.cell(p.blockPosition().getX());cz=LandMinimap.cell(p.blockPosition().getZ());}}
    @Override protected void init(){
        panelWidth=Math.min(126,Math.max(92,width/3));
        side=Math.max(32,Math.min(224,Math.min(width-panelWidth-20,height-61)));
        left=7;top=24;panel=left+side+7;
        panelWidth=Math.max(56,width-panel-6);
        step=Math.max(15,Math.min(20,(height-30)/7));buttonHeight=step-2;
        int b=Math.max(17,Math.min(25,(width-18)/7));
        button("←",left,2,b,()->pan(-1,0));button("↑",left+b+1,2,b,()->pan(0,-1));
        button("↓",left+2*(b+1),2,b,()->pan(0,1));button("→",left+3*(b+1),2,b,()->pan(1,0));
        button("−",left+4*(b+1),2,b,()->{zoom=Math.min(2,zoom*2);request();});
        button("+",left+5*(b+1),2,b,()->{zoom=Math.max(1,zoom/2);request();});
        button("Я",left+6*(b+1),2,b,()->{var p=Minecraft.getInstance().player;if(p!=null){cx=LandMinimap.cell(p.blockPosition().getX());cz=LandMinimap.cell(p.blockPosition().getZ());}request();});
        String[] tabs={"Метки","Клан","Слои"};
        button(tabs[tab]+"  ›",panel,top,panelWidth,()->{
            if(label!=null)storedLabel=label.getValue();
            tab=(tab+1)%3;clearWidgets();init();
        });
        int y=top+step,w=panelWidth,half=(w-2)/2;
        label=null;
        if(tab==0){
            label=new EditBox(font,panel,y,w,buttonHeight,Component.literal("Название метки"));
            label.setMaxLength(24);label.setValue(storedLabel);addRenderableWidget(label);
            button(shared?"Личная":"Клану",panel,y+step,w,()->{shared=!shared;status=shared?"Режим: клан":"Режим: личная";});
            button("Вид метки",panel,y+step*2,w,()->{type=(type+1)%TYPES.length;label.setValue(TYPES[type]);});
            button("Точки спавна",panel,y+step*3,w,()->{
                var points=LandMinimap.points();if(points.isEmpty()){status="Нет доступных точек";return;}
                pointIndex=(pointIndex+1)%points.size();var p=points.get(pointIndex);
                cx=LandMinimap.cell(p.x());cz=LandMinimap.cell(p.z());showOverlays=true;status=p.label();request();
            });
            button("Зоны",panel,y+step*4,half,()->showOverlays=!showOverlays);
            button("3D",panel+half+2,y+step*4,half,()->LandMinimap.waypoints=!LandMinimap.waypoints);
            button("Пинг",panel,y+step*5,half,()->{String[] types={"Помощь","Опасность","Сюда"};LandMinimap.action(9,ActionPayload.NONE,0,0,types[pingType]);});
            button("Вид",panel+half+2,y+step*5,half,()->{pingType=(pingType+1)%3;status="Пинг: "+new String[]{"Помощь","Опасность","Сюда"}[pingType];});
        }else if(tab==1){
            button("Союзник ›",panel,y,w,()->{
                var team=LandMinimap.members();if(team.isEmpty()){selected=null;return;}
                int i=-1;for(int j=0;j<team.size();j++)if(team.get(j).id().equals(selected))i=j;
                selected=team.get((i+1)%team.size()).id();
            });
            button("Инвентарь",panel,y+step,w,()->send(ActionPayload.VIEW));
            button("Разрешить",panel,y+step*2,w,()->send(ActionPayload.ALLOW));
            button("Отозвать",panel,y+step*3,w,()->send(ActionPayload.REVOKE));
            button("Разведка",panel,y+step*4,w,()->LandMinimap.action(7,ActionPayload.NONE,0,0,LandMinimap.sharing()?"off":"on"));
        }else{
            button("Поверхность",panel,y,w,()->{layer=LandMinimap.SURFACE;request();});
            button("Мой Y",panel,y+step,w,()->{var p=Minecraft.getInstance().player;if(p!=null)layer=Math.floorDiv(p.blockPosition().getY(),16);request();});
            button("Y−",panel,y+step*2,half,()->{layer=layer==LandMinimap.SURFACE?Math.floorDiv(Minecraft.getInstance().player.blockPosition().getY(),16):Math.max(-256,layer-1);request();});
            button("Y+",panel+half+2,y+step*2,half,()->{layer=layer==LandMinimap.SURFACE?Math.floorDiv(Minecraft.getInstance().player.blockPosition().getY(),16):Math.min(256,layer+1);request();});
        }
        request();
    }
    private void button(String text,int x,int y,int w,Runnable r){
        addRenderableWidget(Button.builder(Component.literal(text),b->r.run()).bounds(x,y,w,buttonHeight).build());
    }
    void prepareView(){
        if(!LandMinimap.localReady(Minecraft.getInstance()))return;
        selectedMap=LandMinimap.layerMap(layer);
        selectedMap.prepare(cx,cz,side*zoom/2+1);
    }
    private void pan(int x,int z){cx=LandMinimap.clamp(cx+x*side*zoom/3,-7500000,7500000);cz=LandMinimap.clamp(cz+z*side*zoom/3,-7500000,7500000);request();}
    void request(){prepareView();if(LandMinimap.featureReady())LandMinimap.request(layer,cx,cz,side*zoom/2+2);}
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
        if(!inside(x,y)||!LandMinimap.localReady(Minecraft.getInstance()))return super.mouseClicked(e,twice);
        if(e.button()==0){
            if(tab!=0)return true;
            String name=(label==null?storedLabel:label.getValue()).strip();if(name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>96||name.codePoints().anyMatch(Character::isISOControl)){status="Некорректное название";return true;}
            int bx=(startX()+(x-left)*zoom)*4+2,bz=(startZ()+(y-top)*zoom)*4+2;
            int by=layer==LandMinimap.SURFACE?Minecraft.getInstance().player.blockPosition().getY():layer*16+8;
            if(shared){LandMinimap.action(10,new UUID(0,by),bx,bz,name);status="Запрос создания командной метки · Y "+by;}
            else{status=LandMinimap.localMarks.add(new TeamPayload.Mark(UUID.randomUUID(),LandMinimap.viewer,bx,by,bz,name))?"Личная метка сохранена":"Лимит 64 или ошибка сохранения";}
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
        if(!LandMinimap.localReady(Minecraft.getInstance())||LandMinimap.map==null||selectedMap==null){g.text(font,"Мост сервера недоступен",left,top,0xffff7777,true);return;}
        // Limit the prepared working set: at maximum zoom only the visible square is read.
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
        for(var ping:LandMinimap.pings())mark(g,ping.x(),ping.z(),ping.label(),0xffff9c43);
        for(var m:LandMinimap.members())mark(g,(int)Math.floor(m.x()),(int)Math.floor(m.z()),m.name(),LandMinimap.ALLY);
        var p=Minecraft.getInstance().player;
        mark(g,p.blockPosition().getX(),p.blockPosition().getZ(),"Я",0xffffffff);
        var team=LandMinimap.members();if(selected==null&&!team.isEmpty())selected=team.get(0).id();
        if(tab==1){
            String nick=team.stream().filter(t->t.id().equals(selected)).map(TeamPayload.Member::name).findFirst().orElse("—");
            var info=LandMinimap.statuses().stream().filter(t->t.id().equals(selected)).findFirst().orElse(null);
            g.text(font,fit("Союзник: "+nick,panelWidth),panel,top+6*step,0xff48e39a,true);
            if(info!=null&&top+6*step+11<height)g.text(font,fit("HP "+Math.round(info.health())+"/"+Math.round(info.max()),panelWidth),panel,top+6*step+10,0xffeeeeee,true);
        }
        if(tab==2)g.text(font,fit(LandMinimap.layerName(layer),panelWidth),panel,top+4*step,0xffb9c6ce,true);
        if(inside(mx,my))g.text(font,"X "+((startX()+(mx-left)*zoom)*4)+"  Z "+((startZ()+(my-top)*zoom)*4),left,top+side+3,0xffdddddd,true);
        String detail=status;
        if(showOverlays&&inside(mx,my)){
            for(var zone:LandMinimap.zones())if(mx>=px(zone.x1())&&mx<=px(zone.x2())&&my>=py(zone.z1())&&my<=py(zone.z2()))detail=zone.label()+(zone.kind()==3?" · Y "+zone.y1()+".."+zone.y2():"");
            for(var point:LandMinimap.points())if(Math.abs(mx-px(point.x()))<=4&&Math.abs(my-py(point.z()))<=4){detail=point.label()+" · "+point.x()+", "+point.y()+", "+point.z();break;}
        }
        g.text(font,fit(detail,Math.max(30,width-left-6)),left,top+side+15,0xffeeeeee,true);
        g.text(font,fit(LandMinimap.layerName(layer)+" · "+(shared?"Клан":"Личная")+" · "+(LandMinimap.sharing()?"Разведка ON":"Разведка OFF"),width-left-6),left,top+side+27,0xffb4d2c8,true);
    }
    private String fit(String value,int limit){while(value.length()>0&&font.width(value)>limit)value=value.substring(0,value.length()-1);return value;}
    private int sample(int x,int z){
        int a=startX()+x*zoom,b=startZ()+z*zoom;
        for(int dz=0;dz<zoom;dz++)for(int dx=0;dx<zoom;dx++){int c=LandMinimap.color(selectedMap,layer,a+dx,b+dz);if(c!=0)return c;}
        return 0;
    }
    private void mark(GuiGraphicsExtractor g,int x,int z,String name,int color){
        int a=px(x),b=py(z);if(!inside(a,b))return;
        g.fill(Math.max(left,a-2),Math.max(top,b-2),Math.min(left+side,a+3),Math.min(top+side,b+3),color);
        if(a+4+font.width(name)<left+side&&b+9<top+side)g.text(font,name,a+4,b,color,true);
    }
}
