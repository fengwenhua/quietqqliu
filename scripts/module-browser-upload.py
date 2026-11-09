#!/usr/bin/env python3
"""Official GitHub web fallback for Xposed-Modules-Repo OAuth restrictions.

Uses an already authenticated macOS Chrome tab. The script never reads or
prints cookies, tokens or CSRF values. It expects the main GitHub Release to
be public and byte-identical to the local dist assets, then fills the module
repository release editor and uploads the same APK + checksum.

Usage:
    open -a 'Google Chrome' \
      'https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu/releases/new?tag=4-0.2.2&title=0.2.2'
    python3 scripts/module-browser-upload.py inspect
    python3 scripts/module-browser-upload.py upload
    python3 scripts/module-browser-upload.py publish
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.quietqqliu/releases/"


def version():
    return dict(line.split("=", 1) for line in (ROOT / "version.properties").read_text().splitlines()
                if "=" in line and not line.startswith("#"))


def browser(javascript):
    script = '''tell application "Google Chrome"
repeat with w in windows
repeat with t in tabs of w
if URL of t starts with PREFIX then
return execute t javascript EXPR
end if
end repeat
end repeat
return "MODULE_TAB_MISSING"
end tell'''.replace("PREFIX", json.dumps(PREFIX)).replace("EXPR", json.dumps(javascript))
    result = subprocess.run(["osascript", "-"], input=script, capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise RuntimeError("Chrome automation unavailable: " + result.stderr[:300])
    value = result.stdout.strip()
    if value == "MODULE_TAB_MISSING":
        raise RuntimeError("Open this repository's release editor in Chrome first")
    return value


def state():
    return json.loads(browser('''JSON.stringify({
      url:location.href,
      tag:document.getElementById('release_tag_name')?.value,
      title:document.getElementById('release_name')?.value,
      body:document.getElementById('release_body')?.value,
      assets:Array.from(document.querySelectorAll('input')).filter(n=>
        n.name==='release[release_assets_attributes][][name]'&&n.value).map(n=>n.value),
      errors:Array.from(document.querySelectorAll('.flash-error,.js-upload-error'))
        .filter(n=>n.getClientRects().length&&n.textContent.trim())
        .map(n=>n.textContent.trim().slice(0,300))
    })'''))


def files_for(stamp):
    name = "quietqqliu-%s.apk" % stamp["versionName"]
    apk = ROOT / "dist" / ("xposed-" + stamp["versionName"]) / name
    sha = apk.with_name(name + ".sha256")
    if not apk.is_file() or not sha.is_file():
        raise RuntimeError("Run the release workflow and place the verified APK/checksum under dist/")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if sha.read_text().strip().split()[0] != digest:
        raise RuntimeError("Local checksum does not match the APK")
    return [apk, sha]


def public_main(files, tag):
    request = urllib.request.Request(
        "https://api.github.com/repos/fengwenhua/quietqqliu/releases/tags/" + tag,
        headers={"User-Agent": "QuietQQliu-release"})
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(request, timeout=30) as response:
        release = json.load(response)
    if release["draft"] or release["prerelease"]:
        raise RuntimeError("Main Release must already be public and stable")
    for file in files:
        asset = next((a for a in release["assets"] if a["name"] == file.name), None)
        if not asset or asset["size"] != file.stat().st_size:
            raise RuntimeError("Main asset missing or size mismatch: " + file.name)
        request = urllib.request.Request(asset["browser_download_url"],
                                         headers={"User-Agent": "QuietQQliu-release"})
        with urllib.request.urlopen(request, timeout=90) as response:
            data = response.read(70 * 1024 * 1024)
        if hashlib.sha256(data).digest() != hashlib.sha256(file.read_bytes()).digest():
            raise RuntimeError("Main asset digest mismatch: " + file.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inspect", "upload", "publish"))
    args = parser.parse_args()
    stamp = version()
    tag = stamp["versionCode"] + "-" + stamp["versionName"]
    files = files_for(stamp)
    notes = (ROOT / "docs" / "release-notes" / ("release-notes-" + stamp["versionName"] + ".md")).read_text()
    current = state()

    if args.action == "inspect":
        current["bodyLength"] = len(current.pop("body", "") or "")
        print(json.dumps(current, ensure_ascii=False))
        return

    public_main(files, "v" + stamp["versionName"])
    if (current.get("tag") != tag or not current["url"].startswith(PREFIX)
            or not ("/new" in current["url"] or "/edit/" in current["url"])):
        raise RuntimeError("Unexpected module release editor or tag")

    if args.action == "upload":
        if current["assets"]:
            raise RuntimeError("Assets already attached; inspect/resume rather than duplicate them")
        # ensure_ascii escapes Chinese notes, because AppleScript's execute
        # javascript rejects some non-ASCII literals on macOS.
        script = ('''var title=document.getElementById('release_name');
Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(title,TITLE);
title.dispatchEvent(new Event('input',{bubbles:true}));
var body=document.getElementById('release_body');
Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(body,NOTES);
body.dispatchEvent(new Event('input',{bubbles:true}));
body.dispatchEvent(new Event('change',{bubbles:true}));
window.quietQQliuReleaseFiles=[];'ready';'''
                  .replace("TITLE", json.dumps(stamp["versionName"]))
                  .replace("NOTES", json.dumps(notes, ensure_ascii=True)))
        browser(script)
        for file in files:
            encoded = base64.b64encode(file.read_bytes()).decode()
            browser("window.quietQQliuReleaseChunks=[];'ready'")
            for start in range(0, len(encoded), 400_000):
                browser("window.quietQQliuReleaseChunks.push("
                        + json.dumps(encoded[start:start + 400_000]) + ");'ok'")
            browser("window.quietQQliuReleaseFiles.push(new File([Uint8Array.from("
                    "atob(window.quietQQliuReleaseChunks.join('')),c=>c.charCodeAt(0))],"
                    + json.dumps(file.name) + ",{type:'application/octet-stream'}));'added'")
            print(json.dumps({"prepared": file.name, "bytes": file.stat().st_size}), flush=True)
        browser('''var input=document.getElementById('releases-upload');if(!input)throw Error('File input missing');
var transfer=new DataTransfer();window.quietQQliuReleaseFiles.forEach(f=>transfer.items.add(f));
input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));'upload started' ''')
        expected = {p.name for p in files}
        for _ in range(50):
            time.sleep(1)
            uploaded = state()
            if expected.issubset(set(uploaded["assets"])):
                print(json.dumps({"draft": tag, "assets": sorted(expected)}, ensure_ascii=False))
                return
            if uploaded["errors"]:
                raise RuntimeError("GitHub upload error: " + str(uploaded["errors"]))
        raise RuntimeError("Upload still pending; inspect the draft, do not upload duplicates")

    if current["title"] != stamp["versionName"] or current["body"] != notes:
        raise RuntimeError("Release title/body differ from committed source")
    expected = {p.name for p in files}
    if set(current["assets"]) != expected or len(current["assets"]) != len(expected):
        raise RuntimeError("Expected the verified assets exactly once")
    browser('''var button=Array.from(document.querySelectorAll('button'))
      .find(n=>n.textContent.trim()==='Publish release');
if(!button||button.disabled)throw Error('Publish button unavailable');button.click();'publish requested' ''')
    print(json.dumps({"publishing": tag, "verifyPublicDownloadsNext": True}))


if __name__ == "__main__":
    main()
