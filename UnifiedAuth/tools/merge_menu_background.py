"""Merge optional menu art into a COPY of a Minecraft resource pack."""
import argparse
import hashlib
import struct
import zipfile
from pathlib import Path

PNG = b"\x89PNG\r\n\x1a\n"
GUI = "assets/minecraft/textures/gui/"

def read_square(path):
    data = path.read_bytes()
    if len(data) < 33 or data[:8] != PNG or data[12:16] != b"IHDR":
        raise ValueError(f"Invalid PNG header: {path}")
    width, height = struct.unpack(">II", data[16:24])
    if width != height or not 32 <= width <= 4096:
        raise ValueError(f"Expected square PNG 32-4096 px: {path}")
    return data, width

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path)
    parser.add_argument("background", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--panorama", type=Path)
    args = parser.parse_args()
    if args.output.resolve() == args.base.resolve() or args.output.exists():
        parser.error("Output must be a new ZIP, different from input")
    background, _ = read_square(args.background)
    replacements = {GUI + "menu_background.png": background}
    if args.panorama:
        sizes = set()
        for i in range(6):
            name = f"panorama_{i}.png"
            data, size = read_square(args.panorama / name)
            sizes.add(size)
            replacements[GUI + "title/background/" + name] = data
        if len(sizes) != 1:
            parser.error("All six panorama faces must have the same dimensions")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.base) as source:
        if "pack.mcmeta" not in source.namelist():
            parser.error("The base archive must contain pack.mcmeta at its root")
        with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as target:
            for info in source.infolist():
                if info.filename not in replacements:
                    target.writestr(info, source.read(info))
            for path, data in replacements.items():
                target.writestr(path, data)
    digest = hashlib.sha1(args.output.read_bytes()).hexdigest()
    args.output.with_suffix(".sha1").write_text(digest + "\n", encoding="ascii")
    print(f"{args.output}: {digest}")

if __name__ == "__main__":
    main()
