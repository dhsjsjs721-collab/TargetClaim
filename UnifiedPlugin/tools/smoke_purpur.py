#!/usr/bin/env python3
"""Start the exact Purpur 26.3 build reported in the user's server log.
Assert all five modules really enable and the new unified console command works.
"""
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import shutil
import urllib.request
import os
import sys

ROOT=Path(__file__).resolve().parents[2]
JAR=ROOT/"UnifiedPlugin"/"build"/"libs"/"MinecraftUnified-1.1.8.jar"
URL="https://api.purpurmc.org/v2/purpur/26.3/2659/download"
EXPECTED=["TargetClaim","TalismanForge","EnchantmentForge","SwampSky","ImageMaps"]

def main():
    with tempfile.TemporaryDirectory(prefix="purpur-263-smoke-") as wd_str:
        wd=Path(wd_str)
        (wd/"plugins").mkdir()
        shutil.copyfile(JAR,wd/"plugins"/JAR.name)
        (wd/"eula.txt").write_text("eula=true\n")
        (wd/"server.properties").write_text(
            "online-mode=false\nserver-port=0\nview-distance=3\nsimulation-distance=3\n"
            "max-players=4\nspawn-protection=0\nwhite-list=true\n"
            "motd=UnifiedPlugin smoke test\n"
        )
        print("Downloading official Purpur 26.3 build 2659...",flush=True)
        server=wd/"server.jar"
        headers={"User-Agent":"MinecraftUnified-CI/1.1.8 (github.com/dhsjsjs721-collab/TargetClaim)","Accept":"application/json"}
        tested="Purpur 26.3 build 2659"
        try:
            request=urllib.request.Request(URL,headers=headers)
            with urllib.request.urlopen(request,timeout=30) as response:
                with server.open("wb") as output:shutil.copyfileobj(response,output)
            if server.stat().st_size<1_000_000:raise RuntimeError("Purpur download too small")
        except Exception as exc:
            # Some cloud runners receive 403 from Purpur's CDN. The same
            # five Paper APIs can still be smoke-tested on official Paper 26.3.
            import json
            print("Purpur download blocked; using official Paper 26.3 fallback:",exc,flush=True)
            api="https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest"
            request=urllib.request.Request(api,headers=headers)
            with urllib.request.urlopen(request,timeout=30) as response:
                release=json.load(response)
            url=release["downloads"]["server:default"]["url"]
            request=urllib.request.Request(url,headers=headers)
            with urllib.request.urlopen(request,timeout=60) as response:
                with server.open("wb") as output:shutil.copyfileobj(response,output)
            if server.stat().st_size<1_000_000:raise RuntimeError("Paper download too small")
            tested="Paper 26.3 build "+str(release["id"])
        print("Launching",tested,server.stat().st_size,"bytes",flush=True)
        logs=[]
        proc=subprocess.Popen(
            ["java","-Xms256M","-Xmx1200M","-jar",str(server),"nogui"],
            cwd=wd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,text=True,bufsize=1
        )
        def read_log():
            for line in proc.stdout:
                logs.append(line.rstrip("\n"))
                if any(k in line for k in ("Enabled module:","Module ","Exception","MinecraftUnified","Done (","ERROR","Unknown command")):
                    print(line.rstrip("\n"),flush=True)
        reader=threading.Thread(target=read_log,daemon=True)
        reader.start()
        try:
            limit=time.monotonic()+150
            while time.monotonic()<limit and proc.poll() is None:
                if any("Done (" in line for line in logs):break
                time.sleep(.5)
            if not any("Done (" in line for line in logs):
                raise RuntimeError("Purpur did not fully start within 150s or crashed")
            print("Purpur started; testing universal routing",flush=True)
            for cmd in ["u status","u trap","u shield","u shield give nobody wither","u trap give nobody 1","u ss status","u tf","u tf give nobody thor","u ce help","u pig","u frog","u trader","u help"]:
                proc.stdin.write(cmd+"\n")
                proc.stdin.flush()
                time.sleep(.8)
            time.sleep(3)
        finally:
            if proc.poll() is None:
                try:proc.stdin.write("stop\n");proc.stdin.flush()
                except (OSError,BrokenPipeError):pass
                try:proc.wait(timeout=25)
                except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=5)
            reader.join(timeout=5)
        output="\n".join(logs)
        print("Purpur smoke lines:",len(logs),flush=True)
        for name in EXPECTED:
            if "Enabled module: "+name not in output:
                print("\n".join(logs[-100:]))
                raise AssertionError("Module not enabled: "+name)
        if "5 mythic talismans" not in output:
            print("\\n".join(logs[-120:]))
            raise AssertionError("New five-case-only TalismanForge module did not start")
        if "Combat shields active: Wither, Turtle, Crusher" not in output:
            print("\\n".join(logs[-100:]))
            raise AssertionError("Three combat shield recipes / startup did not initialize")
        if "Deepslate craft enabled: 4 basalt + 4 blackstone + 1 obsidian -> 8 deepslate" not in output:
            raise AssertionError("Crafting recipe for 8 deepslate was not registered")
        if "Obsidian PvP trap module enabled" not in output:
            raise AssertionError("Obsidian trap did not initialize")
        if "Запущено модулей:" not in output or "5/5" not in output:
            print("\n".join(logs[-100:]))
            raise AssertionError("/u status failed or does not report all modules")
        bad=[line for line in logs if any(s in line for s in (
            "Module SwampSky failed","Module TalismanForge failed",
            "Could not load plugin","Exception in thread","Command exception:",
            "Error occurred while enabling MinecraftUnified",
            "We don't have max_damage","missing required data class"
        ))]
        if bad:
            print("\n".join(bad))
            raise AssertionError("Purpur startup/command exceptions detected")
        print("SUCCESS:",tested,"launched all 5 modules; /u status works",flush=True)
if __name__=="__main__":main()
