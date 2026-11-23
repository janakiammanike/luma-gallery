"""Build a webOS .ipk for the Luma companion app without ares-cli.

An .ipk is an ar archive containing debian-binary, control.tar.gz and
data.tar.gz. The app payload lives under /usr/palm/applications/<app id>.
"""

import io
import os
import tarfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
APP_ID = "com.luma.tvcompanion"
VERSION = "1.0.0"
OUTPUT = os.path.join(HERE, "{}_{}_all.ipk".format(APP_ID, VERSION))

PAYLOAD_FILES = [
    "packageinfo.json",
    "appinfo.json",
    "index.html",
    "app.css",
    "app.js",
    "icon.png",
    "largeIcon.png",
    "services/luma-companion/package.json",
    "services/luma-companion/services.json",
    "services/luma-companion/index.js",
    "services/luma-companion/wsclient.js",
]


def add_file(tar, disk_path, arcname):
    info = tar.gettarinfo(disk_path, arcname=arcname)
    info.mode = 0o755 if arcname.endswith(".js") else 0o644
    info.uid = 0
    info.gid = 0
    info.uname = ""
    info.gname = ""
    with open(disk_path, "rb") as fh:
        tar.addfile(info, fh)


def make_tar_gz(entries):
    out = io.BytesIO()
    with tarfile.open(fileobj=out, mode="w:gz") as tar:
        for arcname, disk_path in entries:
            add_file(tar, disk_path, arcname)
    return out.getvalue()


def make_control_tar_gz():
    control = (
        "Package: {}\n"
        "Version: {}\n"
        "Architecture: all\n"
        "Maintainer: Luma\n"
        "Section: misc\n"
        "Description: Luma TV companion LAN discovery beacon\n"
        "WebOS-Package-Format-Version: 2\n"
    ).format(APP_ID, VERSION).encode("utf-8")
    out = io.BytesIO()
    with tarfile.open(fileobj=out, mode="w:gz") as tar:
        info = tarfile.TarInfo("./control")
        info.size = len(control)
        info.mode = 0o644
        info.mtime = int(time.time())
        tar.addfile(info, io.BytesIO(control))
    return out.getvalue()


def ar_entry(name, data):
    header = "{:<16}{:<12}{:<6}{:<6}{:<8}{:<10}`\n".format(
        name, int(time.time()), 0, 0, 0o644, len(data)
    ).encode("ascii")
    if len(data) % 2:
        data += b"\n"
    return header + data


def main():
    missing = [p for p in PAYLOAD_FILES if not os.path.isfile(os.path.join(HERE, p))]
    if missing:
        raise SystemExit("Missing files: " + ", ".join(missing))

    data_entries = [
        ("./usr/palm/applications/{}/{}".format(APP_ID, p), os.path.join(HERE, p))
        for p in PAYLOAD_FILES
    ]
    pieces = [
        ar_entry("debian-binary", b"2.0\n"),
        ar_entry("control.tar.gz", make_control_tar_gz()),
        ar_entry("data.tar.gz", make_tar_gz(data_entries)),
    ]
    with open(OUTPUT, "wb") as fh:
        fh.write(b"!<arch>\n")
        for piece in pieces:
            fh.write(piece)
    print("Built", OUTPUT, os.path.getsize(OUTPUT), "bytes")


if __name__ == "__main__":
    main()
