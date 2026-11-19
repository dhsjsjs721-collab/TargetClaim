#!/usr/bin/env python3
"""Build an original Minecraft 26.3 pixel-art resource pack with Python stdlib."""
import io
import json
import math
import struct
import zipfile
import zlib
from pathlib import Path

OUT=Path(__file__).resolve().parents[1]/"build"/"SwampSky-ResourcePack.zip"
C={
"dark":(28,63,44,255),"green":(65,145,76,255),"lime":(137,208,91,255),
"mint":(172,241,149,255),"teal":(50,137,134,255),"blue":(104,204,185,255),
"pink":(236,144,177,255),"rose":(174,87,123,255),"white":(246,233,217,255),
"brown":(98,70,50,255),"gold":(222,188,105,255)
}
def rgba(w,h):
    return bytearray(w*h*4)
def dot(img,w,h,x,y,col):
    if 0<=x<w and 0<=y<h:
        i=(y*w+x)*4
        img[i:i+4]=bytes(C[col] if isinstance(col,str) else col)
def rect(img,w,h,x1,y1,x2,y2,color):
    for y in range(y1,y2):
        for x in range(x1,x2):dot(img,w,h,x,y,color)
def oval(img,w,h,cx,cy,rx,ry,color):
    for y in range(cy-ry,cy+ry+1):
        for x in range(cx-rx,cx+rx+1):
            if ((x-cx)/max(1,rx))**2+((y-cy)/max(1,ry))**2<=1:dot(img,w,h,x,y,color)
def segment(img,w,h,x1,y1,x2,y2,color):
    n=max(abs(x2-x1),abs(y2-y1),1)
    for t in range(n+1):
        x=round(x1+(x2-x1)*t/n);y=round(y1+(y2-y1)*t/n)
        dot(img,w,h,x,y,color)
def icon(which):
    w=h=16
    a=rgba(w,h)
    if which=="frog_hide":
        oval(a,w,h,8,8,6,5,"dark")
        oval(a,w,h,8,7,5,4,"green")
        oval(a,w,h,7,6,3,2,"lime")
        for x,y in [(4,9),(11,5),(10,10),(6,8),(12,8)]:dot(a,w,h,x,y,"mint")
    elif which=="frog_boots":
        for x in (2,9):
            rect(a,w,h,x+1,2,x+4,10,"dark")
            rect(a,w,h,x+2,3,x+4,9,"lime")
            rect(a,w,h,x,9,x+6,12,"dark")
            rect(a,w,h,x+1,10,x+5,11,"mint")
            rect(a,w,h,x,12,x+6,13,"teal")
    elif which=="frog_leggings":
        rect(a,w,h,3,2,13,6,"dark")
        rect(a,w,h,4,3,12,5,"lime")
        rect(a,w,h,3,6,7,14,"dark")
        rect(a,w,h,9,6,13,14,"dark")
        rect(a,w,h,4,6,6,12,"green")
        rect(a,w,h,10,6,12,12,"green")
        rect(a,w,h,3,13,7,15,"mint")
        rect(a,w,h,9,13,13,15,"mint")
        dot(a,w,h,8,4,"teal")
    elif which=="frog_wings":
        for sign in (-1,1):
            for y in range(3,14):
                span=max(1,6-abs(y-6)//2)
                for k in range(1,span+1):
                    x=8+sign*k
                    if 0<=x<16:dot(a,w,h,x,y,"dark" if k==span else "teal" if k>span-2 else "green")
            segment(a,w,h,8,4,8+sign*5,9,"mint")
            segment(a,w,h,8,5,8+sign*4,12,"lime")
        rect(a,w,h,7,3,9,12,"dark")
        rect(a,w,h,7,5,9,10,"mint")
    elif which=="pig_wing":
        oval(a,w,h,8,7,7,4,"rose")
        oval(a,w,h,8,6,6,3,"pink")
        oval(a,w,h,9,5,4,2,"white")
        for x,y in [(3,7),(5,9),(8,10),(12,7)]:dot(a,w,h,x,y,"white")
        for x in (0,1,2):dot(a,w,h,x,11-x,"pink")
    return a

def equipment(kind):
    w,h=64,32
    a=rgba(w,h)
    color="green" if kind!="legs" else "teal"
    rect(a,w,h,0,0,64,32,color)
    for y in range(h):
        for x in range(w):
            if x%9==0 or y%7==0:dot(a,w,h,x,y,"dark")
            if (x*3+y*5)%29==0:dot(a,w,h,x,y,"mint")
    if kind=="wings":
        for y in range(2,30):
            for x in range(64):
                if (x+y*2)%13==0:dot(a,w,h,x,y,"lime")
    return a

def png(w,h,rgba):
    rows=bytearray()
    for y in range(h):
        rows.append(0)
        rows.extend(rgba[y*w*4:(y+1)*w*4])
    def chunk(tag,body):
        return struct.pack(">I",len(body))+tag+body+struct.pack(">I",zlib.crc32(tag+body)&0xffffffff)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,h,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(bytes(rows),9))+chunk(b"IEND",b"")

def build():
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        def add(file,value):
            z.writestr(file,value)
        def js(file,value):
            add(file,json.dumps(value,ensure_ascii=False,separators=(",",":")))
        js("pack.mcmeta",{"pack":{"description":"SwampSky: giant frog gear and flying-pig wings for Minecraft 26.3","min_format":[97,1],"max_format":97}})
        for name in ("frog_hide","frog_boots","frog_leggings","frog_wings","pig_wing"):
            add(f"assets/swampsky/textures/item/{name}.png",png(16,16,icon(name)))
            js(f"assets/swampsky/models/item/{name}.json",{"parent":"minecraft:item/generated","textures":{"layer0":f"swampsky:item/{name}"}})
            js(f"assets/swampsky/items/{name}.json",{"model":{"type":"minecraft:model","model":f"swampsky:item/{name}"}})
        add("assets/swampsky/textures/entity/equipment/humanoid/frog_armor.png",png(64,32,equipment("armor")))
        add("assets/swampsky/textures/entity/equipment/humanoid_leggings/frog_armor_legs.png",png(64,32,equipment("legs")))
        add("assets/swampsky/textures/entity/equipment/wings/frog_wings.png",png(64,32,equipment("wings")))
        js("assets/swampsky/equipment/frog_armor.json",{"layers":{"humanoid":[{"texture":"swampsky:frog_armor"}],"humanoid_leggings":[{"texture":"swampsky:frog_armor_legs"}]}})
        js("assets/swampsky/equipment/frog_wings.json",{"layers":{"wings":[{"texture":"swampsky:frog_wings","use_player_texture":False}]}})
        a=rgba(64,64)
        rect(a,64,64,0,0,64,64,"dark")
        for y in range(64):
            for x in range(64):
                if (x+y)%6==0:dot(a,64,64,x,y,"teal")
        for y in range(16):
            for x in range(16):
                c=icon("frog_hide")[(y*16+x)*4:(y*16+x+1)*4]
                if len(c)==4 and c[3]>0:rect(a,64,64,x*3+8,y*3+8,x*3+11,y*3+11,tuple(c))
        add("pack.png",png(64,64,a))
    print(OUT,OUT.stat().st_size)

if __name__=="__main__":build()
