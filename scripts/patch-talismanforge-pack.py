from pathlib import Path
import base64, hashlib, io, json, zipfile

PATH = Path(__file__).resolve().parents[1] / 'resourcepacks' / 'TalismanForge-2.0.0-ResourcePack.zip'
PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAADFUlEQVR4nLWWTWgTQRiGny2p4s/BqKE0GmpVtHhRRCwqaUvrT6P0YqroRQ+GVsRagoqHWhUkltKDVDwoWEQPgj+5KG3qISJJ2yBILh7qoWp/oLFGqaAWRGE8lI2zm910N00/mMPufDPP+70zuzMKBYgSV7nwVtXlzInHogBMpT8p8ntHocA+X72lMfFYVMgilFzJhQQDRCL9qghg1g3bDpS4yoUZePeu7ZZEqOLjsaiwJUCF69fbClifp7ph2wFvVV2mennCUnfpnGNTk6kssUVWwWr1ANeuXLZctRxGIi0LWFS8mEXFi3mTGABgePgj+/cdzLRwuJdAoIUVziWKvoXDvYYifL5661+Bx10hALpv3c7qaz13lonJ96Zz9dx7JgD8/kOkJlOZ90OJpHUHzKL13Nk5c04FGhWAcLiXpUuXafosC1ArtAI0i711XgCNiLz/hLKQXPYDNPoDQn6emfnFUCIJ2HAgXzjMbja1ejkikX57Dsgwj7tC5IKrn+2Nji4NfGbmlyYv7yWQ4S5nmShyaM2UwaFQF8HgGcN55nUaqnD4/3ve+eM4q45OU+v0EwpdNBwzlEgSifQTj0XnJ0CFV5Stw+er59sTJwC1Tj8dkYtZVasbTz0HYB4OyHCAn3d2wMoPHGmu5endV2zElwWXwfFYlKn0JyWv+4Ae3uh4oOn/vfIDDTfLDeH6m5FtAWbwkw/LeHBiDIDlp99qxshw/ZXMlgA9fOLzNMG1zzU5duBgYw8YwQFSSpJSkX3RkMMMblmAGfyY51omJ6UkNUA5zOCWBOjhOzc4mPqicHjN1Sx4z0iQotGinEB95Ew0gr9IfM30yyJ6RoIApKfHbO0r08PIDL7ZsxqAhl2rGfzTPS84mDgwFxzg+++/ALwfG80bbiigva0zc3Yf2HOQl4N9PL7/aEHgWdHe1ilKXOtF5ZYaMdD3TlRuqRElrvWiva1TbHJvFQ2VdcK7rVp4t1ULl7NMqE4tGFxtqoiFgBtuwvMXWkwHFNr2jIDroUtKU1Mzo+lxTcJoepympmZe9/YVfs2Bf5kUeZ9BGuiAAAAAAElFTkSuQmCC'

items = {
    'assets/talismanforge/items/grappling_hook.json': json.dumps({"model":{"type":"minecraft:model","model":"talismanforge:item/grappling_hook"}},indent=2).encode('utf-8'),
    'assets/talismanforge/models/item/grappling_hook.json': json.dumps({"parent":"minecraft:item/handheld","textures":{"layer0":"talismanforge:item/grappling_hook"}},indent=2).encode('utf-8'),
    'assets/talismanforge/textures/item/grappling_hook.png': base64.b64decode(PNG_BASE64),
}

with zipfile.ZipFile(PATH, 'r') as source:
    entries = [(info,source.read(info.filename)) for info in source.infolist() if info.filename not in items]
buffer = io.BytesIO()
with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as target:
    for info,data in entries:
        if info.filename=='pack.mcmeta':
            mcmeta=json.loads(data)
            mcmeta['pack']['description']='TalismanForge 2.4: totems and grappling hook'
            data=json.dumps(mcmeta,indent=2).encode('utf-8')
        target.writestr(info,data)
    for name,data in items.items():
        info=zipfile.ZipInfo(name,(2026,1,1,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        target.writestr(info,data)
PATH.write_bytes(buffer.getvalue())
sha=hashlib.sha1(PATH.read_bytes()).hexdigest()
PATH.with_suffix('.sha1').write_text(sha+'\n',encoding='utf-8')
print('Updated resource pack',PATH,'SHA-1:',sha)
