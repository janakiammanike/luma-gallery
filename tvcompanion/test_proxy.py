"""Local integration test for the companion command proxy.

Starts a fake webOS SSAP WebSocket server on 127.0.0.1:3000, launches the
companion service, then posts /command requests and checks the responses.
On a real TV the fake server is replaced by webOS itself.
"""

import base64
import json
import os
import socket
import socketserver
import struct
import subprocess
import sys
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SERVICE = os.path.join(HERE, "services", "luma-companion", "index.js")
INPUT_FRAMES = []


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        part = sock.recv(n - len(data))
        if not part:
            raise EOFError()
        data += part
    return data


def read_frame(sock):
    b0, b1 = recv_exact(sock, 2)
    length = b1 & 0x7F
    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", recv_exact(sock, 8))[0]
    if b1 & 0x80:
        mask = recv_exact(sock, 4)
        data = recv_exact(sock, length)
        data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
    else:
        data = recv_exact(sock, length)
    return b0 & 0x0F, data


def send_frame(sock, text):
    payload = text.encode("utf-8")
    header = bytearray([0x81])
    if len(payload) < 126:
        header.append(len(payload))
    elif len(payload) < 65536:
        header.append(126)
        header += struct.pack("!H", len(payload))
    else:
        header.append(127)
        header += struct.pack("!Q", len(payload))
    sock.sendall(bytes(header) + payload)


class FakeSsap(socketserver.BaseRequestHandler):
    def handle(self):
        sock = self.request
        request = b""
        while b"\r\n\r\n" not in request:
            request += sock.recv(4096)
        key = base64.b64encode(b"0123456789abcdef").decode()
        response = (
            "HTTP/1.1 101 Switching Protocols\r\n"
            "Upgrade: websocket\r\nConnection: Upgrade\r\n"
            "Sec-WebSocket-Accept: " + key + "\r\n\r\n"
        )
        sock.sendall(response.encode())
        while True:
            try:
                opcode, data = read_frame(sock)
            except Exception:
                return
            if opcode != 0x1:
                continue
            try:
                msg = json.loads(data.decode("utf-8"))
            except Exception:
                INPUT_FRAMES.append(data.decode("utf-8"))
                continue
            if msg.get("type") == "hello":
                send_frame(sock, json.dumps({"type": "hello", "payload": {}}))
            elif msg.get("type") == "register":
                send_frame(sock, json.dumps({"type": "registered", "id": msg.get("id"), "payload": {}}))
            else:
                INPUT_FRAMES.append(data.decode("utf-8"))
                uri = msg.get("uri", "")
                payload = {"returnValue": True, "uri": uri}
                if "getPointerInputSocket" in uri:
                    payload["socketPath"] = "/pointer"
                if "getStatus" in uri:
                    payload["volume"] = 19
                send_frame(sock, json.dumps({"type": "response", "id": msg.get("id"), "payload": payload}))


def post(path, obj):
    data = json.dumps(obj).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:8081" + path,
        data=data,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=8) as res:
        return res.status, json.loads(res.read().decode())


def main():
    server = socketserver.ThreadingTCPServer(("127.0.0.1", 3000), FakeSsap)
    server.daemon_threads = True
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    log_path = os.path.join(HERE, "proxy_test_node.log")
    with open(log_path, "wb") as log:
        proc = subprocess.Popen(["node", SERVICE], stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(40):
            try:
                with urllib.request.urlopen("http://127.0.0.1:8081/status", timeout=1) as res:
                    status = json.loads(res.read().decode())
                break
            except Exception:
                time.sleep(0.15)
        else:
            raise SystemExit("companion service did not start")

        token = status["token"]

        # Cold-start burst: eight simultaneous commands must all queue behind
        # the single hello/register handshake (this caught a real race).
        burst_ok = []
        burst_errors = []

        def burst():
            try:
                _, burst_result = post("/command", {
                    "token": token, "key": "test-key", "uri": "audio/getStatus",
                })
                burst_ok.append(burst_result)
            except Exception as error:
                burst_errors.append(str(error))

        workers = [threading.Thread(target=burst) for _ in range(8)]
        for worker in workers:
            worker.start()
        for worker in workers:
            worker.join()
        print("Cold burst ->", len(burst_ok), "ok,", len(burst_errors), "errors")
        assert not burst_errors, burst_errors
        assert len(burst_ok) == 8

        _, result = post("/command", {
            "token": token,
            "key": "test-key",
            "uri": "audio/getStatus",
        })
        print("SSAP command ->", result)
        assert result.get("returnValue") is True and result.get("volume") == 19

        _, result = post("/command", {
            "token": token,
            "key": "test-key",
            "input": "type:button\nname:RIGHT\n\n",
        })
        print("Input command ->", result)
        assert result.get("returnValue") is True
        assert any("name:RIGHT" in frame for frame in INPUT_FRAMES), INPUT_FRAMES

        print("PROXY TEST OK")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            proc.kill()
        server.shutdown()
        with open(log_path, "r", encoding="utf-8", errors="replace") as log:
            print("NODE LOG:", log.read()[:2000])


if __name__ == "__main__":
    main()
