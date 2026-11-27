package dev.landminimap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
final class ZoneDraw {
    private ZoneDraw(){}
    static void outline(GuiGraphicsExtractor g,int left,int top,int width,int x1,int y1,int x2,int y2,int color){
        int right=left+width,bottom=top+width;
        int a=Math.max(left,x1),b=Math.max(top,y1),c=Math.min(right,x2+1),d=Math.min(bottom,y2+1);
        if(c<=a||d<=b)return;
        // Draw real boundary segments only; don't invent borders at the viewport edges.
        if(y1>=top&&y1<bottom)g.fill(a,y1,c,y1+1,color);
        if(y2>=top&&y2<bottom)g.fill(a,y2,c,y2+1,color);
        if(x1>=left&&x1<right)g.fill(x1,b,x1+1,d,color);
        if(x2>=left&&x2<right)g.fill(x2,b,x2+1,d,color);
    }
}
