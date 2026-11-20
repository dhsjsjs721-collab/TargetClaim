#!/usr/bin/env python3
"""Idempotently update THE existing main resource pack, keeping all old assets."""
import base64
import hashlib
import io
import json
import os
import pathlib
import tempfile
import zipfile
from PIL import Image
from new_equipment_assets_20261121 import assets as new_equipment_assets

ROOT=pathlib.Path(__file__).resolve().parents[1]
PACK=ROOT/"resourcepacks"/"TalismanForge-2.0.0-ResourcePack.zip"
SOURCES=ROOT/"resourcepacks"/"unified-assets-20261120.json"

def j(obj):
    return json.dumps(obj,ensure_ascii=False,separators=(",",":")).encode("utf-8")

def build_reliquary():
    textures={"particle":"talismanforge:block/reliquary_side",
              "front":"talismanforge:block/reliquary_front",
              "side":"talismanforge:block/reliquary_side",
              "top":"talismanforge:block/reliquary_top",
              "bottom":"talismanforge:block/reliquary_bottom"}
    faces={name:{"texture":"#"+tex} for name,tex in {
        "north":"front","south":"side","east":"side","west":"side",
        "up":"top","down":"bottom"}.items()}
    return {"parent":"minecraft:block/block","textures":textures,
            "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":faces}],
            "display":{
                "gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[.72,.72,.72]},
                "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1.95,1.95,1.95]},
                "ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[.55,.55,.55]},
                "firstperson_righthand":{"rotation":[0,45,0],"translation":[0,1,0],"scale":[.5,.5,.5]},
                "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2,0],"scale":[.45,.45,.45]}
            }}

def main():
    if not PACK.is_file():raise SystemExit("Main pack missing: "+str(PACK))
    source=json.loads(SOURCES.read_text(encoding="utf8"))
    incoming={}
    for name,encoded in source.items():
        if not name.startswith("assets/") or not name.endswith(".png"):
            raise ValueError("Unsafe resource path: "+name)
        raw=base64.b64decode(encoded,validate=True)
        image=Image.open(io.BytesIO(raw))
        image.verify()
        incoming[name]=raw
    incoming.update(new_equipment_assets())
    incoming.update({
        "assets/targetclaim/items/chest_key.json":j({
            "model":{"type":"minecraft:model","model":"targetclaim:item/chest_key"}
        }),
        "assets/targetclaim/models/item/chest_key.json":j({
            "parent":"minecraft:item/handheld",
            "textures":{"layer0":"targetclaim:item/chest_key"}
        }),
        "assets/talismanforge/items/reliquary.json":j({
            "model":{"type":"minecraft:model","model":"talismanforge:block/reliquary"}
        }),
        "assets/talismanforge/models/block/reliquary.json":j(build_reliquary()),
        "pack.mcmeta":j({
            "pack":{"description":"TalismanForge + SwampSky + TargetClaim + Shields | unified 2026-11-21",
                    "min_format":[97,1],"max_format":[97,1]}})
    })
    with zipfile.ZipFile(PACK) as zip_in:
        if zip_in.testzip() is not None:raise ValueError("Existing main pack has a bad CRC")
        old={item.filename:zip_in.read(item.filename) for item in zip_in.infolist() if not item.is_dir()}
    before=set(old)
    old.update(incoming)
    assert len(old)>=len(before)
    assert all(old[k]==v for k,v in incoming.items())
    with tempfile.NamedTemporaryFile(dir=PACK.parent,suffix=".zip",delete=False) as tmp:
        tmp_name=pathlib.Path(tmp.name)
    try:
        with zipfile.ZipFile(tmp_name,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zip_out:
            for name in sorted(old):zip_out.writestr(name,old[name])
        with zipfile.ZipFile(tmp_name) as check:
            assert check.testzip() is None
            assert len(check.namelist())==len(set(check.namelist()))
            assert all(check.read(k)==v for k,v in incoming.items())
        os.replace(tmp_name,PACK)
    finally:
        if tmp_name.exists():tmp_name.unlink()
    sha=hashlib.sha1(PACK.read_bytes()).hexdigest()
    (PACK.parent/"TalismanForge-2.0.0-ResourcePack.sha1").write_text(sha+"\n")
    print("MAIN PACK:",PACK.name,"entries:",len(old),"sha1:",sha,"bytes:",PACK.stat().st_size)
    for ns in ("talismanforge","swampsky","targetclaim","minecraftunified"):
        print(ns,sum(k.startswith("assets/"+ns+"/") for k in old))
if __name__=="__main__":main()
