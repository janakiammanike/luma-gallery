"""Sanity-check the generated .ipk structure."""

import io
import os
import tarfile

HERE = os.path.dirname(os.path.abspath(__file__))
IPK = os.path.join(HERE, "com.luma.tvcompanion_1.0.0_all.ipk")


def read_ar_member(raw, name):
    pos = len(b"!<arch>\n")
    while pos < len(raw):
        header = raw[pos:pos + 60]
        if len(header) < 60:
            break
        member_name = header[:16].decode("ascii").strip()
        size = int(header[48:58].decode("ascii").strip())
        data = raw[pos + 60:pos + 60 + size]
        if member_name == name:
            return data
        pos += 60 + size + (size % 2)
    raise SystemExit("missing ar member: " + name)


with open(IPK, "rb") as fh:
    raw = fh.read()

assert raw.startswith(b"!<arch>\n"), "not an ar archive"
print("debian-binary:", read_ar_member(raw, "debian-binary").decode().strip())

for member in ("control.tar.gz", "data.tar.gz"):
    blob = read_ar_member(raw, member)
    with tarfile.open(fileobj=io.BytesIO(blob), mode="r:gz") as tar:
        names = tar.getnames()
        print(member, "->", len(names), "entries")
        for name in names:
            print("  ", name)
        if member == "control.tar.gz":
            assert "./control" in names
        else:
            assert any(n.endswith("appinfo.json") for n in names)
            assert any(n.endswith("services/luma-companion/index.js") for n in names)
            assert any(n.endswith("services/luma-companion/wsclient.js") for n in names)

print("IPK structure OK")
