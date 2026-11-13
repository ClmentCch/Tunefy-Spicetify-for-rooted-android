#!/usr/bin/env python3
"""Fill TuneFy repository checksums and mirror the index into the APK assets.

Usage: python3 scripts/update-repository.py [--skip-remote]

- Every item with a fileName matching repository/packages/<fileName> gets its
  real SHA-256 and byte size.
- Items marked AUTO_REMOTE are downloaded (unless --skip-remote) so the release
  bundle checksum stays verifiable.
- The result is written to repository/index.json and copied to
  app/src/main/assets/repository/index.json.
"""

from __future__ import annotations

import hashlib
import json
import pathlib
import shutil
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
INDEX = ROOT / "repository" / "index.json"
PACKAGES = ROOT / "repository" / "packages"
ASSET_INDEX = ROOT / "app" / "src" / "main" / "assets" / "repository" / "index.json"
ASSET_PATCHES = ROOT / "app" / "src" / "main" / "assets" / "patches" / "patches-list.json"
PATCHES_URL = "https://raw.githubusercontent.com/spicetify/morphe-patches/main/patches-list.json"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 256), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "TuneFy-repository/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def main() -> int:
    skip_remote = "--skip-remote" in sys.argv
    repository = json.loads(INDEX.read_text(encoding="utf-8"))
    changed = 0

    for item in repository["apps"]:
        file_name = item.get("fileName")
        if file_name:
            package = PACKAGES / file_name
            if package.exists():
                data = package.read_bytes()
                digest = sha256_bytes(data)
                item["sha256"] = digest
                item["sizeBytes"] = len(data)
                for version in item.get("versions", []):
                    if version.get("version") == item.get("version"):
                        version["sha256"] = digest
                        version["sizeBytes"] = len(data)
                changed += 1
            elif item.get("sha256") == "AUTO_REMOTE":
                if not skip_remote:
                    try:
                        data = fetch(item["downloadUrl"])
                        item["sha256"] = sha256_bytes(data)
                        item["sizeBytes"] = len(data)
                        for version in item.get("versions", []):
                            if version.get("version") == item.get("version"):
                                version["sha256"] = item["sha256"]
                                version["sizeBytes"] = len(data)
                        changed += 1
                        print(f"remote checksum  {item['id']}: {item['sha256'][:16]}…")
                    except Exception as error:  # noqa: BLE001 - maintainer script
                        print(f"warning: cannot fetch {item['id']}: {error}")
                else:
                    print(f"skipped remote   {item['id']}")
            elif item.get("sha256") == "AUTO":
                print(f"warning: missing package for {item['id']} ({file_name})")

    INDEX.write_text(json.dumps(repository, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    ASSET_INDEX.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(INDEX, ASSET_INDEX)
    print(f"index written: {len(repository['apps'])} items, {changed} checksums updated")

    if not skip_remote:
        try:
            ASSET_PATCHES.parent.mkdir(parents=True, exist_ok=True)
            ASSET_PATCHES.write_bytes(fetch(PATCHES_URL))
            print("patches snapshot refreshed")
        except Exception as error:  # noqa: BLE001
            print(f"warning: cannot refresh patch snapshot: {error}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
