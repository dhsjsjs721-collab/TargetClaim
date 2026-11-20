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
JAR=ROOT/"UnifiedPlugin"/"build"/"libs"/"MinecraftUnified-1.0.4.jar"
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
        server=wd/"purpur.jar"
        for attempt in range(3):
            try:
                urllib.request.urlretrieve(URL,server)
                if server.stat().st_size<1_000_000:raise RuntimeError("Purpur download too small")
                break
            except Exception as exc:
                if attempt==2:raise
                print("Retry download:",exc,flush=True)
                time.sleep(3)
        print("Launching Purpur",server.stat().st_size,"bytes",flush=True)
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
            for cmd in ["u status","u ss status","u tf","u ce help","u help"]:
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
        print("SUCCESS: Purpur 26.3 build 2659 launched all 5 modules; /u status works",flush=True)
if __name__=="__main__":main()
