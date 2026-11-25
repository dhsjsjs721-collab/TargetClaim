"""Merge the auth-only bitmap font overlay into a copy of an existing pack."""
import argparse
import hashlib
import zipfile
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument("base",type=Path)
p.add_argument("overlay",type=Path)
p.add_argument("output",type=Path)
a=p.parse_args()
if a.output.exists() or a.output.resolve() in (a.base.resolve(),a.overlay.resolve()):
    p.error("Choose a new output ZIP")
expected={"assets/landoflosers/font/auth_art.json","assets/landoflosers/textures/font/auth_art.png"}
with zipfile.ZipFile(a.base) as base, zipfile.ZipFile(a.overlay) as overlay:
    if "pack.mcmeta" not in base.namelist() or not expected.issubset(overlay.namelist()):
        p.error("Invalid base pack or missing auth art assets")
    if expected.intersection(base.namelist()):
        p.error("Base pack already contains auth art; use original base")
    with zipfile.ZipFile(a.output,"x",compression=zipfile.ZIP_DEFLATED) as out:
        for info in base.infolist():
            out.writestr(info,base.read(info))
        for path in sorted(expected):
            out.writestr(path,overlay.read(path))
digest=hashlib.sha1(a.output.read_bytes()).hexdigest()
a.output.with_suffix(".sha1").write_text(digest+"\n",encoding="ascii")
print(digest)
