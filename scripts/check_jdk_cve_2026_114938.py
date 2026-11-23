#!/usr/bin/env python3
"""Fail if a JVM this repo runs is affected by Java4Shell (CVE-2026-114938).

Affected: 17.0.6 - 17.0.21 (fix 17.0.21.1), 21 < 21.0.13.1, 25 < 25.0.5.1,
27 < 27.0.1.1. Java 8, 11 and 17.0.0 - 17.0.5 are not affected. Feature
releases between 17 and 27 with no fixed build are treated as affected.

Usage:
  scripts/check_jdk_cve_2026_114938.py                # docker-compose.yml JVM images
  scripts/check_jdk_cve_2026_114938.py --java java    # a local java binary
  scripts/check_jdk_cve_2026_114938.py --self-test
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

FIXED = {17: (17, 0, 21, 1), 21: (21, 0, 13, 1), 25: (25, 0, 5, 1), 27: (27, 0, 1, 1)}
FIRST_AFFECTED_17 = (17, 0, 6)

# compose service -> java binary inside its image
COMPOSE_JVM_SERVICES = {
    "keycloak": "java",
    "elasticsearch": "/usr/share/elasticsearch/jdk/bin/java",
}

REPO_ROOT = Path(__file__).resolve().parent.parent


def parse_version(text):
    m = re.search(r'version "([^"]+)"', text)
    if not m:
        raise ValueError(f"no java version in output: {text!r}")
    raw = m.group(1)
    if raw.startswith("1."):  # 1.8.0_402 -> 8
        return (int(raw.split(".")[1]),)
    return tuple(int(p) for p in re.findall(r"\d+", raw.split("-")[0].split("+")[0]))


def pad(v, n=4):
    return tuple(v) + (0,) * (n - len(v))


def is_affected(version):
    feature = version[0]
    if feature < 17:
        return False
    if feature == 17 and pad(version) < pad(FIRST_AFFECTED_17):
        return False
    if feature in FIXED:
        return pad(version) < pad(FIXED[feature])
    return feature < 27


def java_version_output(cmd):
    res = subprocess.run(cmd + ["-version"], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} -version failed: {res.stderr.strip()}")
    return res.stderr + res.stdout


def compose_images():
    out = subprocess.run(
        ["docker", "compose", "-f", str(REPO_ROOT / "docker-compose.yml"), "config", "--format", "json"],
        capture_output=True, text=True, check=True,
    ).stdout
    services = json.loads(out)["services"]
    return {name: services[name]["image"] for name in COMPOSE_JVM_SERVICES}


def check(label, output, image=None):
    version = parse_version(output)
    problems = []
    if is_affected(version):
        problems.append(f"JDK {'.'.join(map(str, version))} is affected by CVE-2026-114938")
    if image is not None and "@sha256:" not in image:
        problems.append(f"image {image} is not pinned by digest")
    status = "FAIL" if problems else "OK"
    print(f"[{status}] {label}: JDK {'.'.join(map(str, version))}" + "".join(f"\n       - {p}" for p in problems))
    return not problems


def self_test():
    cases = {
        'openjdk version "1.8.0_402"': False,
        'openjdk version "11.0.25" 2024-10-15': False,
        'openjdk version "17.0.5" 2022-10-18': False,
        'openjdk version "17.0.6" 2023-01-17': True,
        'openjdk version "17.0.10" 2024-01-16 LTS': True,
        'openjdk version "17.0.21" 2026-10-20': True,
        'openjdk version "17.0.21.1" 2026-11-17': False,
        'openjdk version "21.0.1" 2023-10-17': True,
        'openjdk version "21.0.13" 2026-10-20 LTS': True,
        'openjdk version "21.0.13.1" 2026-11-17 LTS': False,
        'openjdk version "22.0.2" 2024-07-16': True,
        'openjdk version "25.0.5" 2026-10-20': True,
        'openjdk version "25.0.5.1" 2026-11-17': False,
        'openjdk version "27.0.1.0.3" 2026-10-25': True,
        'openjdk version "27.0.1.1" 2026-11-17': False,
        'openjdk version "28-ea" 2027-03-16': False,
    }
    failed = [(o, e) for o, e in cases.items() if is_affected(parse_version(o)) != e]
    for o, e in failed:
        print(f"self-test FAIL: {o!r} expected affected={e}")
    print(f"self-test: {len(cases) - len(failed)}/{len(cases)} passed")
    return not failed


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--java", help="check this local java binary instead of the compose images")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return 0 if self_test() else 1
    if args.java:
        return 0 if check(args.java, java_version_output([args.java])) else 1

    ok = True
    for service, image in compose_images().items():
        out = java_version_output(["docker", "run", "--rm", "--entrypoint", COMPOSE_JVM_SERVICES[service], image])
        ok = check(f"{service} ({image})", out, image) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
