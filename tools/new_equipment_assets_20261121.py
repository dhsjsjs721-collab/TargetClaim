"""Reproducible 32x32 pixel textures for grappling hooks and unique shields.

Only adds/upgrades paths inside the ONE existing unified resource pack.
No new pack URL or independent pack ZIP.
"""
import io
import base64
from pathlib import Path
import json
from PIL import Image,ImageDraw

def j(data):
    return json.dumps(data,separators=(",",":"),ensure_ascii=False).encode("utf-8")

def to_png(image):
    raw=io.BytesIO()
    image.save(raw,format="PNG",optimize=True)
    return raw.getvalue()

def empty():
    return Image.new("RGBA",(32,32),(0,0,0,0))

def chain_sprite():
    image=empty(); d=ImageDraw.Draw(image)
    for y in (1,11,21):
        d.rounded_rectangle((10,y,22,y+13),radius=4,outline=(22,28,42,255),width=3)
        d.rounded_rectangle((11,y+1,21,y+12),radius=3,outline=(135,173,194,255),width=2)
        d.line((12,y+2,17,y+2),fill=(227,245,247,255),width=1)
    return image

def hook_sprite(cast=False,tip=False):
    image=empty(); d=ImageDraw.Draw(image)
    dark=(20,25,36,255)
    steel=(112,157,186,255)
    light=(224,242,244,255)
    if not tip:
        for x,y in ((4,24),(8,20),(12,16),(16,12)):
            d.ellipse((x,y,x+5,y+5),outline=dark,width=2)
            d.arc((x+1,y+1,x+4,y+4),0,180,fill=light,width=1)
    d.line((22,5,24,15),fill=dark,width=6)
    d.line((22,5,24,15),fill=steel,width=4)
    d.line((22,5,24,15),fill=light,width=1)
    d.polygon([(17,14),(26,13),(28,18),(25,25),(20,29),(16,25),(21,23),(23,17)],fill=dark)
    d.polygon([(19,15),(25,14),(26,18),(23,24),(20,27),(18,25),(22,22),(24,17)],fill=steel)
    d.line([(20,16),(24,16),(23,21)],fill=light,width=1)
    d.ellipse((19,8,22,11),fill=(231,168,61,255))
    if cast:
        d.line((9,12,14,9),fill=(91,211,241,220),width=2)
        d.line((9,16,13,14),fill=(91,211,241,190),width=1)
        d.point((5,10),fill=(255,243,184,255))
    return image

def shield_sprite(theme):
    palettes={
    "wither":((20,15,32,255),(75,29,105,255),(187,77,221,255),(228,193,245,255)),
    "turtle":((14,34,34,255),(31,102,84,255),(97,200,153,255),(214,247,184,255)),
    "crusher":((42,29,23,255),(125,77,49,255),(221,139,63,255),(255,215,144,255)),
    "spectral":((19,27,66,255),(55,82,165,255),(112,191,249,255),(228,247,255,255))
    }
    edge,base,shine,bright=palettes[theme]
    image=empty(); d=ImageDraw.Draw(image)
    d.polygon([(4,2),(28,2),(28,18),(25,24),(16,31),(7,24),(4,18)],fill=edge)
    d.polygon([(6,4),(26,4),(26,17),(23,23),(16,28),(9,23),(6,17)],fill=base)
    d.line([(7,4),(25,4),(25,15)],fill=shine,width=2)
    d.line([(7,5),(7,18),(15,26)],fill=bright,width=1)
    d.polygon([(12,4),(20,4),(19,7),(13,7)],fill=bright)
    if theme=="wither":
        d.polygon([(11,10),(21,10),(23,16),(20,21),(12,21),(9,16)],fill=(24,17,31,255))
        d.rectangle((12,14,14,16),fill=(216,104,225,255))
        d.rectangle((18,14,20,16),fill=(216,104,225,255))
        d.line([(14,19),(18,19)],fill=(227,201,240,255),width=1)
        d.line([(11,23),(21,23)],fill=(94,38,124,255),width=2)
    if theme=="turtle":
        d.polygon([(16,9),(22,13),(20,20),(16,24),(12,20),(10,13)],fill=(18,57,49,255))
        d.polygon([(16,11),(20,14),(19,19),(16,22),(13,19),(12,14)],fill=(123,204,114,255))
        d.line([(16,11),(16,22)],fill=(41,101,68,255))
        d.line([(12,14),(20,14)],fill=(41,101,68,255))
    if theme=="spectral":
        d.polygon([(11,10),(16,6),(21,10),(23,16),(16,25),(9,16)],fill=(31,48,108,255))
        d.polygon([(16,9),(20,16),(16,22),(12,16)],fill=(139,221,254,255))
        d.line([(16,10),(16,22)],fill=(237,250,255,255),width=2)
    if theme=="crusher":
        d.polygon([(13,9),(20,9),(20,15),(23,18),(18,21),(16,25),(13,21),(10,18),(13,15)],fill=(31,30,39,255))
        d.polygon([(14,10),(19,10),(19,16),(21,18),(17,21),(16,23),(14,20),(12,18),(14,16)],fill=(218,195,114,255))
        d.rectangle((15,9,17,22),fill=(249,218,167,255))
    return image

def shield_cube_model(name):
    # Real extruded cuboids: shield panel is three pixels thick, with a
    # raised center boss and a physical rear handle (not a flat 2D sprite).
    texture=f"minecraftunified:item/{name}"
    edge=f"minecraftunified:item/{name}_edge"
    def faces(front=False):
        if front:
            return {
                "north":{"texture":"#front","uv":[0,0,16,16]},
                "south":{"texture":"#edge","uv":[0,0,16,16]},
                "east":{"texture":"#edge"},"west":{"texture":"#edge"},
                "up":{"texture":"#edge"},"down":{"texture":"#edge"}
            }
        return {direction:{"texture":"#edge"} for direction in
                ("north","south","east","west","up","down")}
    return {
        "parent":"minecraft:block/block",
        "textures":{"particle":texture,"front":texture,"edge":edge},
        "elements":[
            {"from":[2,1,6.5],"to":[14,16,9.5],"faces":faces()},
            {"from":[3,2,6],"to":[13,15,6.5],"faces":faces(front=True)},
            {"from":[6.3,6.5,5.2],"to":[9.7,10,6],"faces":faces()},
            {"from":[6,6,9.5],"to":[10,11,12],"faces":faces()}
        ],
        "display":{
            "gui":{"rotation":[25,215,0],"translation":[0,0,0],"scale":[.85,.85,.85]},
            "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},
            "thirdperson_righthand":{"rotation":[0,-90,25],"translation":[0,2,1],"scale":[.75,.75,.75]},
            "firstperson_righthand":{"rotation":[0,-80,15],"translation":[1,2,1],"scale":[.85,.85,.85]},
            "ground":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[.4,.4,.4]}
        }
    }

def shield_edge_image(name):
    colors={
        "shield_wither":(48,24,70,255),
        "shield_turtle":(36,93,70,255),
        "shield_crusher":(111,72,39,255),
        "shield_spectral":(56,102,181,255)
    }
    image=Image.new("RGBA",(16,16),colors[name])
    d=ImageDraw.Draw(image)
    d.rectangle((1,1,14,14),outline=(218,234,242,255),width=1)
    d.rectangle((3,3,12,12),outline=(24,27,37,255),width=1)
    return image

def assets():
    icons={
    "talismanforge": {
        "grappling_hook":hook_sprite(),
        "grappling_hook_cast":hook_sprite(cast=True),
        "grappling_hook_tip":hook_sprite(tip=True),
        "grappling_chain_link":chain_sprite()
    },
    "minecraftunified":{
        "shield_wither":shield_sprite("wither"),
        "shield_turtle":shield_sprite("turtle"),
        "shield_crusher":shield_sprite("crusher"),
        "shield_spectral":shield_sprite("spectral")
    }}
    result={}
    for namespace,names in icons.items():
        for name,icon in names.items():
            result[f"assets/{namespace}/textures/item/{name}.png"]=to_png(icon)
            result[f"assets/{namespace}/items/{name}.json"]=j({
                "model":{"type":"minecraft:model","model":f"{namespace}:item/{name}"}
            })
            if namespace=="minecraftunified" and name.startswith("shield_"):
                result[f"assets/{namespace}/textures/item/{name}_edge.png"]=to_png(shield_edge_image(name))
                result[f"assets/{namespace}/models/item/{name}.json"]=j(shield_cube_model(name))
            else:
                result[f"assets/{namespace}/models/item/{name}.json"]=j({
                    "parent":"minecraft:item/handheld",
                    "textures":{"layer0":f"{namespace}:item/{name}"},
                    "display":{
                        "gui":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},
                        "thirdperson_righthand":{"rotation":[0,-90,55],"translation":[0,3,1],"scale":[.72,.72,.72]},
                        "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1,3,1],"scale":[.72,.72,.72]},
                        "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1.1,1.1,1.1]}
                    }
                })
    overrides=Path(__file__).resolve().parents[1]/'resourcepacks'/'mythic-ritual-assets.json'
    if overrides.is_file():
        result.update({k:base64.b64decode(v) for k,v in json.loads(overrides.read_text()).items()})
    return result
