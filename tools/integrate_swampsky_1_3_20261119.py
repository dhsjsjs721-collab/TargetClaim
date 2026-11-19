"""Merge the SwampSky 1.3.0 changes supplied by the server owner.
All previous talisman, grappling-hook and other add-on assets remain intact.
"""
import base64
from pathlib import Path
from tempfile import NamedTemporaryFile
import zipfile
import os
import hashlib

TARGET = Path("resourcepacks/TalismanForge-2.0.0-ResourcePack.zip")
CHANGES = {
    "assets/swampsky/models/equipment/frog_armor.json":
        '{"layers": {"humanoid": [{"texture": "swampsky:frog_armor"}], "humanoid_leggings": [{"texture": "swampsky:frog_armor_legs"}]}}'.encode(),
    "assets/swampsky/models/equipment/frog_wings.json":
        '{"layers": {"wings": [{"texture": "swampsky:frog_wings", "use_player_texture": false}]}}'.encode(),
    "pack.mcmeta":
        '{"pack": {"description": "TalismanForge + SwampSky 1.3.0 | biome spawns and repaired equipment textures", "min_format": [97, 1], "max_format": [97, 1]}}'.encode(),
    "assets/swampsky/textures/item/pig_wing.png":
        base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAACqklEQVR4nO2av2tTURTHvxULgnWQQNYWqzhYhNYuBaGLqFCnZlBHN9uli5QuraWlINKli7oJOmiHZlJQN/8IB4cKjhaCk5NDHdJv9J6803Nf3utL8J7PEt7tS94tn+8990cCOI7j/IdsrCwfbqwsH1r3naqiM4PMUL87UBYxtgFg7cnT4H9OPgGn+92BvFimV7ceAwBaBwdB+7Od55n3J5+Aga8BmvG5OzcBAFMzM0G7Zl6OfZJ8AgauBkjjNE0s43lJPgEDUwNoXhofHR8HANTqdQB/jX/+8BEAMHv7VubnWWOfeAL69WDN+Pt3nwAAi0sLQTuNE2lem+c1mAxPQFUPsozfvdcI2nff7gEArly+AMAe6xqr648AAJvr2wB8L9BF6esAax5nVW/9aFdzmpfGNfPf9/eDaxrG8NmwI79/RfU3+QSUVgMs8yMj54JrGiea8dp5YZYI481Xr4Pr+fvzAPSxT5JPQOEaoJmX1V0a5zyvGlZovmlmtkvjjTP1qHR7Anp9ozavc6xL82UZp2kJzf+zggyS6TVAwUyAdSJjVffOPB2JZrz182heF9Wfu0O5dyC+GzRQE0DznJ8nJicz7+vVeMeooGvNf2S8Vm+/Fj0BkiSfgM74kMZpQu7Dv3z9FlzLWUDCtX9e5HMl2nmANeYlySdArQH6mVu7fezSRQC9G5bPkWj7f0mv5oknwLpBS8LU9NVSOtC1sovc13PlV5TkE9A1bvZevjj221c5O2hr82gi9/USa58fS/IJOPHvBrX9O5GGLeNlk3wCctcAwnWARdHZoqyxruEJ0P5gJSE2AbEwKSdtXOIJsG5gEvLuAmPh6TGpyjxJPgHmOqDx4GFghOcG167fyDQV+4vNshJUlOQTUBmxv96uGk+A4ziO4ziO4zhJ8gc35gORJwWl8gAAAABJRU5ErkJggg=="
        ),
}

if not TARGET.is_file():
    raise RuntimeError(f"Cannot find current resource pack: {TARGET}")
with zipfile.ZipFile(TARGET) as old:
    existing = [(info, old.read(info.filename)) for info in old.infolist()]
    if old.testzip() is not None:
        raise RuntimeError("Existing pack is corrupt")
    all_names = {info.filename for info, _ in existing}

# Preserve exact entry paths, timestamps, and all unmodified content.
with NamedTemporaryFile(dir=TARGET.parent, prefix=".swampsky-merge-", suffix=".zip", delete=False) as tmp:
    tmp_path = Path(tmp.name)
try:
    with zipfile.ZipFile(tmp_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as new:
        for info, original in existing:
            new.writestr(info, CHANGES.get(info.filename, original))
        for name, payload in CHANGES.items():
            if name not in all_names:
                new.writestr(name, payload, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(tmp_path) as checked:
        if checked.testzip() is not None:
            raise RuntimeError("Merged pack is corrupt")
        for name, payload in CHANGES.items():
            assert checked.read(name) == payload, name
        for info, original in existing:
            if info.filename not in CHANGES:
                assert checked.read(info.filename) == original, info.filename
        assert len(checked.namelist()) == len(all_names | CHANGES.keys())
        print("Entries:", len(checked.namelist()))
    os.replace(tmp_path, TARGET)
finally:
    if tmp_path.exists():
        tmp_path.unlink()
print("SHA1:", hashlib.sha1(TARGET.read_bytes()).hexdigest())
