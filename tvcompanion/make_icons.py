"""Generate the two PNG icons webOS requires (no third-party Python modules)."""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
COLOR = (160, 107, 255)


def chunk(tag, data):
    out = struct.pack("!I", len(data)) + tag + data
    return out + struct.pack("!I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def make_png(path, size):
    rows = b"".join(b"\x00" + bytes(COLOR) * size for _ in range(size))
    ihdr = struct.pack("!IIBBBBB", size, size, 8, 2, 0, 0, 0)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", ihdr))
        fh.write(chunk(b"IDAT", zlib.compress(rows, 9)))
        fh.write(chunk(b"IEND", b""))
    print("wrote", path)


if __name__ == "__main__":
    make_png(os.path.join(HERE, "icon.png"), 80)
    make_png(os.path.join(HERE, "largeIcon.png"), 130)
