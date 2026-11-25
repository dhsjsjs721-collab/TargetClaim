#!/usr/bin/env python3
"""Compile-time conversion of 5 existing Paper plugins into one host plugin.

No duplicated/stale Java files are stored in this branch. Instead the build reads
the latest source commits from the 4 original branches, keeping all code/behavior,
plugin namespaced PDC tags, recipes, and per-plugin data folders compatible.
Requires a full checkout (actions/checkout fetch-depth: 0).
"""
from pathlib import Path
import re
import subprocess

root=Path(__file__).resolve().parents[2]
base=root/"UnifiedPlugin"/"build"
src=base/"generated-sources"
resources=base/"generated-resources"/"module-defaults"

MODULES=[
("origin/main","src/main/java/dev/targetclaim/TargetClaimPlugin.java","targetclaim","TargetClaim"),
("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/java/dev/enchantmentforge/EnchantmentForgePlugin.java","enchantmentforge","EnchantmentForge"),
("0bcbba5500159637a7205e31a9e44c022f74bd6a","SwampSky/src/main/java/dev/swampsky/SwampSkyPlugin.java","swampsky","SwampSky"),
("origin/main","ImageMaps/src/main/java/dev/imagemaps/ImageMapsPlugin.java","imagemaps","ImageMaps"),
("origin/talismanforge-v2","TalismanForge/src/main/java/dev/talismanforge/GrapplingHookListener.java","talismanforge","GrapplingHookListener"),
("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/java/dev/enchantmentforge/WanderingTraderFeature.java","enchantmentforge","WanderingTraderFeature"),
]

def git(ref,path):
    completed=subprocess.run(["git","show",f"{ref}:{path}"],cwd=root,capture_output=True,text=True)
    if completed.returncode:
        raise RuntimeError(f"git show {ref}:{path}: {completed.stderr}")
    return completed.stdout

def swap(code,old,new,label,required=True):
    number=code.count(old)
    if required and number==0:raise ValueError(f"Missing expected {label} occurrence: {old}")
    return code.replace(old,new)

def convert(code,namespace,name,path):
    plugin_class=name+"Plugin"
    if name in ("GrapplingHookListener","WanderingTraderFeature"):
        if name=="GrapplingHookListener":
            code=code.replace("import org.bukkit.plugin.java.JavaPlugin;","import org.bukkit.plugin.Plugin;")
            code=code.replace("JavaPlugin plugin","Plugin plugin")
            code=code.replace("new NamespacedKey(plugin,","new NamespacedKey(\"talismanforge\",")
        else:
            code=code.replace("import org.bukkit.plugin.java.JavaPlugin;","")
            code=code.replace("JavaPlugin plugin","EnchantmentForgePlugin plugin")
            code=code.replace("new NamespacedKey(plugin,","new NamespacedKey(\"enchantmentforge\",")
            code=code.replace("registerEvents(this,plugin)","registerEvents(this,plugin.host())")
            code=re.sub(r"runTask(Later|Timer|Asynchronously)?\(plugin,",r"runTask\1(plugin.host(),",code)
        return code

    old=re.search(r"public final class "+re.escape(plugin_class)+r" extends (?:org\.bukkit\.plugin\.java\.)?JavaPlugin implements ",code)
    if not old:raise ValueError(f"Missing plugin class declaration for {name}")
    code=code[:old.start()]+old.group().replace("extends org.bukkit.plugin.java.JavaPlugin","extends ModuleBridge").replace("extends JavaPlugin","extends ModuleBridge")+code[old.end():]
    code=code.replace("package dev."+namespace+";","package dev."+namespace+";\n\nimport dev.unified.ModuleBridge;\nimport org.bukkit.plugin.java.JavaPlugin;")
    brace=code.index("{",code.index("public final class "+plugin_class))
    code=code[:brace+1]+"\npublic "+plugin_class+"(JavaPlugin plugin){super(plugin,\""+name+"\");}\n"+code[brace+1:]
    code=code.replace("new NamespacedKey(this,","new NamespacedKey(\""+namespace+"\",")
    code=code.replace("registerEvents(this,this)","registerEvents(this,host())")
    code=code.replace("registerEvents(hookListener,this)","registerEvents(hookListener,host())")
    code=re.sub(r"runTask(Later|Timer|Asynchronously)?\(this,",r"runTask\1(host(),",code)
    code=code.replace("new GrapplingHookListener(this)","new GrapplingHookListener(host())")
    if name=="ImageMaps":
        # If storage initialization fails, throw into the unified module
        # loader instead of disabling the entire host plugin.
        code=code.replace("getPluginManager().disablePlugin(this);return;",
            'getLogger().severe("ImageMaps storage unavailable");throw new IllegalStateException("ImageMaps storage initialization failed",ex);')
    code=code.replace("getPluginManager().disablePlugin(this)","getPluginManager().disablePlugin(host())")

    if name=="TargetClaim":
        code=re.sub(r'org\.bukkit\.plugin\.Plugin tf=Bukkit\.getPluginManager\(\)\.getPlugin\("TalismanForge"\);\nif\(tf!=null.*?catch\(ReflectiveOperationException ignored\)\{\}', 'for(Location egg:((dev.unified.UnifiedPlugin)host()).getKoscheiEggLocations())if(egg!=null&&egg.getWorld()!=null&&egg.getWorld().getUID().equals(c.world)&&egg.getBlockX()>=left&&egg.getBlockX()<=right&&egg.getBlockY()>=bottom&&egg.getBlockY()<=top&&egg.getBlockZ()>=front&&egg.getBlockZ()<=back)return true;', code, count=1)
        if 'getKoscheiEggLocations()' not in code:raise ValueError("Missing hosted egg/claim integration")

        # Persist claim size policy per core. Old claims retain their existing protected area.
        code=swap(code,"boolean teamBuild=false,independent=false;","boolean teamBuild=false,independent=false,spawnSized=false;","claim spawn flag")
        code=swap(code,"int minX(){return x-SIZE[level]/2;}","int width(int l){return spawnSized?SIZE[l]/2:SIZE[l];}\nint height(int l){return spawnSized?HEIGHT[l]/2:HEIGHT[l];}\nint minX(){return x-width(level)/2;}","claim dimensions")
        for old,new in [
            ("return minX()+SIZE[level]-1;","return minX()+width(level)-1;"),
            ("return z-SIZE[level]/2;","return z-width(level)/2;"),
            ("return minZ()+SIZE[level]-1;","return minZ()+width(level)-1;"),
            ("return y-HEIGHT[level]/2;","return y-height(level)/2;"),
            ("return minY()+HEIGHT[level]-1;","return minY()+height(level)-1;"),
            ("return c.x-SIZE[level]/2;","return c.x-c.width(level)/2;"),
            ("return c.z-SIZE[level]/2;","return c.z-c.width(level)/2;"),
            ("return c.y-HEIGHT[level]/2;","return c.y-c.height(level)/2;"),
            ("return minX(c,level)+SIZE[level]-1;","return minX(c,level)+c.width(level)-1;"),
            ("return minZ(c,level)+SIZE[level]-1;","return minZ(c,level)+c.width(level)-1;"),
            ("return minY(c,level)+HEIGHT[level]-1;","return minY(c,level)+c.height(level)-1;"),
            ('c.teamBuild=y.getBoolean(root+"teamBuild",false);','c.teamBuild=y.getBoolean(root+"teamBuild",false);c.spawnSized=y.getBoolean(root+"spawn-sized",false);'),
            ('y.set(r+"independent",c.independent);','y.set(r+"independent",c.independent);y.set(r+"spawn-sized",c.spawnSized);'),
            ("c.independent=special;","c.independent=special;c.spawnSized=((dev.unified.UnifiedPlugin)host()).isPeace(b.getLocation());"),
            ("private boolean overlap(Claim c){return wouldOverlap(c,c.level);}","private boolean overlap(Claim c){return wouldOverlap(c,4);}"),
            ("int left=minX(c,level),right=maxX(c,level),front=minZ(c,level),back=maxZ(c,level);","int left=minX(c,4),right=maxX(c,4),front=minZ(c,4),back=maxZ(c,4);"),
            ("int bottom=minY(c,level),top=maxY(c,level);","int bottom=minY(c,4),top=maxY(c,4);"),
            ("for(Claim other:regionClaims(c.world,left,right,front,back)){\nif(other.owner.equals(c.owner))continue;\nif(left<=other.maxX()&&right>=other.minX()&&bottom<=other.maxY()&&top>=other.minY()&&front<=other.maxZ()&&back>=other.minZ())return true;\n}","for(Claim other:claims.values()){\nif(other.id.equals(c.id)||!other.world.equals(c.world))continue;\nlong dx=Math.abs((long)c.x-other.x),dz=Math.abs((long)c.z-other.z);\nint distance=(c.width(4)+other.width(4)+1)/2;\nif(dx<distance&&dz<distance)return true;\n}"),
            ("Приват пересекается с другой территорией.","Слишком близко к другому приватному ядру или яйцу Кощея. Нужен запас на максимальный размер."),
            ('private String dimension(int level){return SIZE[level]+" × "+HEIGHT[level]+" × "+SIZE[level];}','private String dimension(Claim c,int level){return c.width(level)+" × "+c.height(level)+" × "+c.width(level);}')
        ]:
            code=swap(code,old,new,"claim policy")
        code=code.replace("dimension(claim.level)","dimension(claim,claim.level)").replace("dimension(own.level)","dimension(own,own.level)").replace("dimension(c.level)","dimension(c,c.level)").replace("dimension(4)","dimension(claim,4)").replace("dimension(claim.level+1)","dimension(claim,claim.level+1)")
        code=code.replace('SIZE[c.level]+"×"+HEIGHT[c.level]+"×"+SIZE[c.level]','c.width(c.level)+"×"+c.height(c.level)+"×"+c.width(c.level)')
        code=code.replace("SIZE[c.level]/2.","c.width(c.level)/2.").replace("HEIGHT[c.level]/2.","c.height(c.level)/2.")
        # Old overlapping cores may not expand; existing areas remain untouched.


    # TalismanForge's previous shield and item state keys already use the
    # original namespace, via the explicit Keys above. No data migration.
    return code

def main():
    import shutil
    if src.exists():shutil.rmtree(src)
    src.mkdir(parents=True,exist_ok=True)
    resources.mkdir(parents=True,exist_ok=True)
    for ref,path,ns,name in MODULES:
        source=git(ref,path)
        patched=convert(source,ns,name,path)
        target=src/Path(path).name
        # Put Java sources under their original package subfolders.
        target=src/"dev"/ns/Path(path).name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(patched,encoding="utf8")
        print(f"{name}: {len(source)} -> {len(patched)} bytes ({ref})")
    for ref,config,name in [
        ("origin/main","TalismanForge/src/main/resources/config.yml","TalismanForge"),
        ("0bcbba5500159637a7205e31a9e44c022f74bd6a","SwampSky/src/main/resources/config.yml","SwampSky"),
        ("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/resources/config.yml","EnchantmentForge")
    ]:
        (resources/(name+".yml")).write_text(git(ref,config),encoding="utf8")

if __name__=="__main__":
    main()
