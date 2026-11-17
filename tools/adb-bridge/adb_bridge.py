#!/usr/bin/env python3
"""
ADB bridge for Smart AutoClicker.

Runs on the computer hosting BlueStacks (or any emulator). The "ADB command" action of the app POSTs a command to this
bridge, which runs it with the local `adb`. No third-party dependency, Python 3.8+.

    python3 adb_bridge.py --token <secret> [--port 8765] [--serial 127.0.0.1:5555]

In the app, set the bridge address to the computer as seen from the emulator (10.0.2.2 for the Android emulator, the
computer's LAN IP for BlueStacks), the same port and token, and a command such as `shell input keyevent 4`.

Security: the bridge only ever runs `adb` (no shell), requires the token, and refuses `adb` sub-commands that act on the
computer instead of the device (see BLOCKED). Anyone holding the token can control the device, so keep it secret and
only run the bridge on a network you trust.
"""
import argparse
import hmac
import shlex
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

BLOCKED = {"kill-server", "start-server", "connect", "disconnect", "pair", "reboot-bootloader", "emu", "forward",
           "reverse", "tcpip", "usb", "keygen"}
MAX_BODY = 8192


def make_handler(token, adb_path, serial, timeout):
    class Handler(BaseHTTPRequestHandler):
        def _reply(self, code, text):
            data = text.encode()
            self.send_response(code)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            if self.path != "/adb":
                return self._reply(404, "not found")
            given = self.headers.get("X-Adb-Bridge-Token", "")
            if not hmac.compare_digest(given.encode(), token.encode()):
                return self._reply(401, "bad token")
            length = int(self.headers.get("Content-Length", 0))
            if length > MAX_BODY:
                return self._reply(413, "too large")
            command = parse_qs(self.rfile.read(length).decode()).get("command", [""])[0]
            try:
                args = shlex.split(command)
            except ValueError as ex:
                return self._reply(400, f"bad command: {ex}")
            if not args or args[0] == "adb":
                args = args[1:]
            if not args:
                return self._reply(400, "empty command")
            if args[0] in BLOCKED or "-s" in args[:1]:
                return self._reply(403, f"adb {args[0]} is not allowed")

            full = [adb_path] + (["-s", serial] if serial else []) + args
            print("run:", " ".join(shlex.quote(a) for a in full), flush=True)
            try:
                result = subprocess.run(full, capture_output=True, text=True, timeout=timeout)
            except subprocess.TimeoutExpired:
                return self._reply(504, "adb timed out")
            except FileNotFoundError:
                return self._reply(500, f"adb not found: {adb_path}")
            self._reply(200 if result.returncode == 0 else 502, result.stdout + result.stderr)

        def log_message(self, *_):
            pass

    return Handler


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--token", required=True, help="shared secret the app must send")
    ap.add_argument("--host", default="0.0.0.0", help="interface to listen on (default: all)")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--adb", default="adb", help="path to the adb binary")
    ap.add_argument("--serial", help="device to target (adb -s), e.g. 127.0.0.1:5555 for BlueStacks")
    ap.add_argument("--timeout", type=int, default=10, help="seconds before an adb call is killed")
    a = ap.parse_args()
    server = ThreadingHTTPServer((a.host, a.port), make_handler(a.token, a.adb, a.serial, a.timeout))
    print(f"ADB bridge listening on {a.host}:{a.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
