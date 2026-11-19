#!/usr/bin/env python3
"""Produce properly UV-aligned swamp equipment textures by recoloring vanilla 26.3 art."""
import base64
import io
import json
import random
import zipfile
from pathlib import Path
from PIL import Image

# Original Minecraft 26.3 textures are bundled as exact pixel/UV references.
# These images are transformed; no original files are distributed unchanged.
VANILLA = {"armor_body":"iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgBAMAAABQs2O3AAAAGFBMVEUAAAD////l5eXZ2dnR0dHCwsK+vr63t7dk3vYkAAAAAXRSTlMAQObYZgAAAT5JREFUeNqdk7FuwyAQht2hzV4p6l7LYg9GeYCAWYPhwprBxmscuPD6xcqEnVKpJ/Ez8HH8HFy1hEWIVSnUZ83KwMDLwL1z4g+AHouA72hTBHR7IEUAItjSIsJCpAGAW1DVVMwtnww97NXnib24wUDQT3aZFXdbIDiI57frLoKTd+fkCw9JPq5JIoSBl69rT9+2Kgba6n/RDRtneap2A6jcbN1uqjPmCXENxPjqTZI81aYprt9Fsf68Y0nRfqmm3yuiSH6qmMnM7kLVB9nLCzHS5D6MVLQ+GjnTdgwiiIcIMgM06zlni3YMIy4ecqNh9JSyMOqk2cLTrwUvfMrgReBOrmo2N4tjM5lTK83k6aqJtNBSCy8AL5yPgGbdRI+jGQN7pF27obtV1bvFW56h6ZlvNMnqm/8RjNFG+O0r/AA8Tmiiltp9cgAAAABJRU5ErkJggg==","armor_legs":"iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgBAMAAABQs2O3AAAAFVBMVEUAAAD////l5eXZ2dnR0dHCwsK3t7dubhI2AAAAAXRSTlMAQObYZgAAAIxJREFUeNrt0LERwzAIBdCskKyQEXJogkCvEzBAEH//EXJ25ztjLeBf0PxXAI87d46xXIDeFiCwALgG5ukGq5GLirLWADwoWnAJlKcoB5VgNE0l5RJAnGZqlkAzoM2lBuwJVjEYLBPmjgNwy73r1F8f6q0/3+cLR/6+kLFNLh42MBk8MeUUYKuxz8NFf0ZbLhItcp1XAAAAAElFTkSuQmCC","elytra_worn":"iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgCAMAAACVQ462AAAAIVBMVEUAAAB/j593iJptf5JneIpfb4BYZnVPXGpKVmNGUV1CTFgLTatUAAAAAXRSTlMAQObYZgAAAJpJREFUeNrtzzESwjAMRFFJ1kpa3//ATMxQAAVBblLkH+DNrvyIpK6kGym7kZMk5w6wBEq3FzB3AfYBHkLmBsBZmQnpVZOsqow2UJWZWRHSKysrMwCX09m7EBEA4NoEAkAAPs4D+gUg3Ed3AeDjyK25wIcvoXlhCcOPB90L8hxgZt0FpqarLiCqZvYP4PI5QZfRBsQWoXJ3d9EeqAUEMMmFoygAAAAASUVORK5CYII=","boots_item":"iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAQAAAC1+jfqAAAAVklEQVR42mOgI9D9D4IIFob0//83/kv+R2ajKbgBFoSxj2FTcAyu4Bg2BccIK5iGXQFCCKFgGpSHJp0NV5CNUALVAZGGCgBpiAhcAUgKzVGSUDG6xBEAKT16Y6EMtjEAAAAASUVORK5CYII=","leggings_item":"iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAAAAAA6mKC9AAAAAnRSTlMAAHaTzTgAAABQSURBVHjajc/BDcAgDATBa2OLoDL3cyW5BndDFJAA5RGxD0uel6272oy1V+9VmcGCyjdvSNsfgDggE4kTjNT+wEbCB8SAdQcxIDYAc149+gDniirHVvZbNgAAAABJRU5ErkJggg==","elytra_item":"iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAG1BMVEUAAACPj7OMjIx/f5hzc3Nwbo1paWlLS0s1NTXBGAVdAAAAAXRSTlMAQObYZgAAAFpJREFUeNqNT0kOwEAIgtEO/P/DNrWxvQ4XFtFEnEGSLemzGZIUKbXPIA2IjBQWUEU0WMUOwPX4RRSwWrxBDxKYDUwjwAmyWftKA85ruxvE3Bh4+6dWHj569AbZrwlib1Ty2AAAAABJRU5ErkJggg==","hide_item":"iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAElBMVEUAAADHnme0i1uogFSCYEFOOicIyDQdAAAAAXRSTlMAQObYZgAAAFJJREFUeNptjUkKwDAMA63t/18uCTSxS4XRYWCsWrF3p95YXpXqZMEOKDXDZTJlX4ECrRyAFd4ZEyDGCkGmBsAE+hgCAWW+aE7kQPEhSWpf6icPu2UBXtuWX1UAAAAASUVORK5CYII=","feather_item":"iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAQAAAC1+jfqAAAATklEQVR42rXRsQ2AUAwD0ew/QMbJEh7oUdH9gISE24tzheu/xOtBPOLGjtGIvW82HIw+46giWk6CIDuugtUetxvn/uj9eWttbrxljA+jXU9CdVLa6TttAAAAAElFTkSuQmCC"}
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parent
BUILD=ROOT/"build"
BUILD.mkdir(parents=True,exist_ok=True)

def open_ref(name):
    return Image.open(io.BytesIO(base64.b64decode(VANILLA[name]))).convert("RGBA")

def recolor(img, family, detail=0):
    out=Image.new("RGBA",img.size)
    inp=img.load()
    dest=out.load()
    for y in range(img.height):
        for x in range(img.width):
            r,g,b,a=inp[x,y]
            if a==0:
                continue
            bright=(r*0.30+g*0.59+b*0.11)/255
            # Match original armor and wing silhouette/shadows, not an arbitrary square atlas.
            # Minecraft's original shading is preserved via a green/olive palette transformation.
            if family=="skin":
                low=(30,60,31);high=(181,211,105)
            elif family=="leaves":
                low=(24,56,35);high=(192,219,106)
            elif family=="wings":
                low=(31,63,41);high=(213,224,137)
            elif family=="pink":
                low=(136,67,89);high=(249,206,210)
            else:
                low=(44,74,49);high=(194,231,134)
            value=max(0,min(1,(bright-.16)/.80))
            # Re-use the genuine iron / elytra pixel edges to get realistic worn shading.
            if detail and x>0 and y>0 and ((x*17+y*29+detail)%31==0):
                value=max(.08,value-.18)
            if detail and (x*13+2*y*7+detail)%43==0:
                value=min(1,value+.09)
            dest[x,y]=tuple(round(low[i]*(1-value)+high[i]*value) for i in range(3))+(a,)
    return out

def add_moss(img, family):
    # Subtle irregular green dapples, only on pixels that exist in the original UV shape.
    out=img.copy()
    pix=out.load()
    W,H=img.size
    for y in range(1,H-1):
        for x in range(1,W-1):
            r,g,b,a=pix[x,y]
            if a<160:
                continue
            if (x*11+y*17+(x//4)*3)%47==0:
                pix[x,y]=(min(255,r+9),min(255,g+13),max(0,b-6),a)
            if family=="wings" and (x*9-y*4)%41==0:
                pix[x,y]=(min(255,r+7),min(255,g+19),min(255,b+3),a)
    return out

def png(img):
    out=io.BytesIO()
    img.save(out,format="PNG",optimize=True)
    return out.getvalue()

def textures():
    mapping={
      "frog_hide":("hide_item","skin",1),
      "frog_boots":("boots_item","skin",2),
      "frog_leggings":("leggings_item","leaves",3),
      "frog_wings":("elytra_item","wings",4),
      "pig_wing":("feather_item","pink",5)
    }
    files={}
    for name,(source,palette,seed) in mapping.items():
        img=recolor(open_ref(source),palette,seed)
        files[f"assets/swampsky/textures/item/{name}.png"]=png(img)
        files[f"assets/swampsky/models/item/{name}.json"]=json.dumps({
          "parent":"minecraft:item/generated",
          "textures":{"layer0":f"swampsky:item/{name}"}
        }).encode("utf-8")
        files[f"assets/swampsky/items/{name}.json"]=json.dumps({
          "model":{"type":"minecraft:model","model":f"swampsky:item/{name}"}
        }).encode("utf-8")

    # Replace placeholder artwork with the approved individual textures. The worn
    # frog Elytra is a real 64x32 UV image and the pig source is ONE wing, reused
    # twice with a mirrored ItemDisplay.
    def custom(name):
        encoded=(ROOT/"assets"/(name+".png.b64")).read_text(encoding="ascii").strip()
        return base64.b64decode(encoded,validate=True)
    files["assets/swampsky/textures/item/pig_wing.png"]=custom("pig_wing")
    files["assets/swampsky/textures/item/frog_hide.png"]=custom("frog_hide")
    files["assets/swampsky/textures/item/frog_wings.png"]=custom("frog_wings_item")

    # Real vanilla armor UVs: 64×32 humanoid for boots, humanoid_leggings for pants.
    # The vanilla armor renderer itself supplies the 3D boots/pants geometry.
    # These replace only textures on SwampSky items with the specified equippable asset.
    for name,source,family,seed in [
      ("humanoid/frog_armor.png","armor_body","skin",11),
      ("humanoid_leggings/frog_armor_legs.png","armor_legs","leaves",12),
      ("wings/frog_wings.png","elytra_worn","wings",13)
    ]:
        asset=add_moss(recolor(open_ref(source),family,seed),family)
        assert asset.size==(64,32),(name,asset.size)
        files[f"assets/swampsky/textures/entity/equipment/{name}"]=png(asset)

    # Replace only Elytra worn UV; keep vanilla-based 3D boots and leggings UV.
    worn=custom("frog_wings_worn")
    assert Image.open(io.BytesIO(worn)).size==(64,32)
    files["assets/swampsky/textures/entity/equipment/wings/frog_wings.png"]=worn

    files["assets/swampsky/equipment/frog_armor.json"]=json.dumps({
      "layers":{"humanoid":[{"texture":"swampsky:frog_armor"}],
                "humanoid_leggings":[{"texture":"swampsky:frog_armor_legs"}]}
    }).encode("utf-8")
    files["assets/swampsky/equipment/frog_wings.json"]=json.dumps({
      "layers":{"wings":[{"texture":"swampsky:frog_wings","use_player_texture":False}]}
    }).encode("utf-8")

    # Pack icon: modified swamp-hide item on a flat dark-green square.
    icon=Image.new("RGBA",(64,64),(35,69,45,255))
    hide=recolor(open_ref("hide_item"),"skin",1).resize((48,48),Image.Resampling.NEAREST)
    icon.alpha_composite(hide,(8,8))
    files["pack.png"]=png(icon)
    return files

def mcmeta():
    return json.dumps({"pack":{"description":"TalismanForge + SwampSky 1.2 | 26.3 frog armor and single pig wing",
                                "min_format":[97,1],"max_format":[97,1]}}).encode("utf-8")

def write_zip(path,entries):
    with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            z.writestr(name,data)
    print(f"{path.name}: {path.stat().st_size} bytes, {len(entries)} files")

def build():
    assets=textures()
    standalone={"pack.mcmeta":mcmeta(),**assets}
    write_zip(BUILD/"SwampSky-ResourcePack.zip",standalone)

    # Merge with the existing user pack, preserving its files and only adding our namespace.
    previous=Path(__import__("os").environ.get("SWAMPSKY_BASE_PACK",str(REPO/"resourcepacks"/"TalismanForge-2.0.0-ResourcePack.zip")))
    if not previous.exists():
        raise FileNotFoundError(f"Cannot merge: missing existing resourcepack {previous}")
    merged={}
    with zipfile.ZipFile(previous,"r") as z:
        for info in z.infolist():
            if not info.is_dir() and info.filename not in ("pack.mcmeta",):
                merged[info.filename]=z.read(info.filename)
    # Keep the original TalismanForge pack.png so its recognizable pack icon survives.
    merged.update({key:value for key,value in standalone.items() if key != "pack.png"})
    # Restore four TalismanForge grappling-hook assets present in the previous
    # combined pack but absent from the base v2.0.0 pack. Never overwrite an
    # asset if a newer TalismanForge resource already supplies it.
    for name in ("grappling_chain_link","grappling_hook","grappling_hook_cast","grappling_hook_tip"):
        texture_name=f"assets/talismanforge/textures/item/{name}.png"
        if texture_name not in merged:
            encoded=(ROOT/"assets"/(name+".png.b64")).read_text(encoding="ascii").strip()
            merged[texture_name]=base64.b64decode(encoded,validate=True)
        merged.setdefault(
            f"assets/talismanforge/items/{name}.json",
            json.dumps({"model":{"type":"minecraft:model","model":f"talismanforge:item/{name}"}}).encode("utf-8")
        )
        merged.setdefault(
            f"assets/talismanforge/models/item/{name}.json",
            json.dumps({"parent":"minecraft:item/generated" if name=="grappling_chain_link" else "minecraft:item/handheld",
                        "textures":{"layer0":f"talismanforge:item/{name}"}}).encode("utf-8")
        )
    write_zip(BUILD/"TalismanForge-SwampSky-Combined.zip",merged)
    # Save reference previews for visual inspection but do not add unneeded mob textures.
    for name in ("frog_boots","frog_leggings","frog_wings","frog_hide","pig_wing"):
        out=BUILD/"previews"/(name+".png")
        out.parent.mkdir(parents=True,exist_ok=True)
        out.write_bytes(assets[f"assets/swampsky/textures/item/{name}.png"])
    for name,src in (("worn_boots", "humanoid/frog_armor.png"),
                     ("worn_leggings","humanoid_leggings/frog_armor_legs.png"),
                     ("worn_elytra","wings/frog_wings.png")):
        out=BUILD/"previews"/(name+".png")
        out.write_bytes(assets[f"assets/swampsky/textures/entity/equipment/{src}"])

if __name__=="__main__":
    build()
