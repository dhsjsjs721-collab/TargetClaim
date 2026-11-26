#!/usr/bin/env python3
"""Validate ZIP CRC, PNG integrity, internal model, equipment and font references."""
import hashlib,json,struct,zlib,zipfile
from pathlib import Path
p=Path(__file__).resolve().parents[2]/"resourcepacks"/"TalismanForge-2.0.0-ResourcePack.zip"
with zipfile.ZipFile(p) as z:
    names=set(z.namelist())
    assert len(names)==len(z.namelist()) and z.testzip() is None
    assert hashlib.sha1(p.read_bytes()).hexdigest()==p.with_suffix(".sha1").read_text().strip()
    def resolve(value, folder, ext):
        if not isinstance(value,str) or value.startswith("#"): return
        ns, sep, path=value.partition(":")
        if not sep: ns,path="minecraft",ns
        if ns=="minecraft": return
        name=f"assets/{ns}/{folder}/{path}{ext}"
        assert name in names, f"missing reference: {name}"
    for name in sorted(names):
        raw=z.read(name)
        if name.endswith(".png"):
            assert raw[:8]==b"\x89PNG\r\n\x1a\n",name
            pos=8
            while pos<len(raw):
                n=struct.unpack_from(">I",raw,pos)[0]
                chunk=raw[pos+4:pos+8+n]
                assert zlib.crc32(chunk)==struct.unpack_from(">I",raw,pos+8+n)[0],name
                pos+=12+n
            assert pos==len(raw),name
        if not name.endswith(".json"):continue
        obj=json.loads(raw)
        if "/items/" in name: resolve(obj["model"]["model"],"models",".json")
        if "/models/" in name:
            resolve(obj.get("parent"),"models",".json")
            for v in obj.get("textures",{}).values():resolve(v,"textures",".png")
        if "/equipment/" in name:
            for layer, entries in obj["layers"].items():
                for item in entries:resolve(item["texture"],"textures/entity/equipment/"+layer,".png")
        if "/font/" in name:
            for provider in obj["providers"]:
                if provider.get("type")=="bitmap":resolve(provider["file"],"textures","")
    for key in ("thor","anubis","koschei","kitsune","medusa","koschei_soul","koschei_block"):
        for suffix in ("","_awakened"):
            assert f"assets/talismanforge/items/mythic_{key}{suffix}.json" in names
    for ns,key in [("targetclaim","claim_core"),("targetclaim","independent_core"),("minecraftunified","obsidian_trap"),("minecraftunified","youtube_disc"),("minecraftunified","shop_token"),("minecraftunified","mirror_chestplate"),("minecraftunified","miner_helmet"),("enchantmentforge","return_charm"),("enchantmentforge","soulbound"),("enchantmentforge","triple_shot")]:
        assert f"assets/{ns}/items/{key}.json" in names
    print(f"Resource pack validated: {len(names)} files, SHA-1, ZIP/PNG CRC and custom references")
