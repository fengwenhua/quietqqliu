#!/usr/bin/env python3
"""Verify a signed release APK: certificate pin, v2/v3 scheme, package id, version and debug flag.

Used by .github/workflows/release.yml after Gradle assembles the signed release.
Only public values are printed; no keystore material is read.
"""
import argparse
import json
import re
from pathlib import Path


def checked(command):
    import subprocess
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise SystemExit("command failed: %s\n%s" % (Path(command[0]).name, result.stderr.strip()))
    return result.stdout


def read_properties(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def verify_schemes(tool, apk, expected_cert):
    # minSdk 29 lets v3 satisfy verification without exercising v2; verify down to
    # API 24 as well so both signatures are checked. This does not change minSdk.
    for options, schemes in [((), (3,)), (("--min-sdk-version", "24"), (2, 3))]:
        report = checked([str(tool), "verify", "--verbose", "--print-certs", *options, str(apk)])
        lines = report.splitlines()
        for scheme in schemes:
            expected = "Verified using v%d scheme (APK Signature Scheme v%d): true" % (scheme, scheme)
            if expected not in lines:
                raise SystemExit("APK did not verify with signature scheme v%d" % scheme)
        certs = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", report)
        if len(certs) != 1:
            raise SystemExit("expected exactly one signer certificate")
        if certs[0].lower() != expected_cert:
            raise SystemExit("certificate mismatch: %s != %s" % (certs[0].lower(), expected_cert))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--trust", type=Path, default=Path("release-trust.properties"))
    parser.add_argument("--version", type=Path, default=Path("version.properties"))
    args = parser.parse_args()

    version = read_properties(args.version)
    expected_cert = read_properties(args.trust)["certificateSha256"].lower()

    verify_schemes(args.build_tools / "apksigner", args.apk, expected_cert)

    badging = checked([str(args.build_tools / "aapt2"), "dump", "badging", str(args.apk)])
    package = "io.github.fengwenhua.quietqqliu"
    for needle in (
            "package: name='%s'" % package,
            "versionCode='%s'" % version["versionCode"],
            "versionName='%s'" % version["versionName"]):
        if needle not in badging:
            raise SystemExit("APK badging mismatch: missing %r" % needle)
    if "application-debuggable" in badging:
        raise SystemExit("release APK must not be debuggable")

    print(json.dumps({
        "apk": args.apk.name,
        "package": package,
        "versionCode": int(version["versionCode"]),
        "versionName": version["versionName"],
        "certificateSha256": expected_cert,
    }, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
