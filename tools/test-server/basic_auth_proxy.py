#!/usr/bin/env python3
"""A tiny reverse proxy that asks for HTTP basic auth before forwarding to the local test server.

For trying the app's "proxy user / proxy password" fields: run it, then sign in with the server URL
http://10.0.2.2:8081 (emulator) and the proxy login below. Not for anything but local tests.

    python3 basic_auth_proxy.py [listen_port] [upstream]    (defaults: 8081 http://localhost:2283)
"""
import base64
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

USER, PASSWORD = "proxy-user", "proxy-pass"
LISTEN = int(sys.argv[1]) if len(sys.argv) > 1 else 8081
UPSTREAM = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:2283"
EXPECTED = "Basic " + base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _deny(self):
        body = b"proxy authentication required"
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="test proxy"')
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _forward(self):
        if self.headers.get("Authorization") != EXPECTED:
            print("denied", self.command, self.path, flush=True)
            return self._deny()
        length = int(self.headers.get("Content-Length") or 0)
        data = self.rfile.read(length) if length else None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "authorization", "connection")}
        request = urllib.request.Request(UPSTREAM + self.path, data=data, headers=headers, method=self.command)
        try:
            response = urllib.request.urlopen(request, timeout=60)
        except urllib.error.HTTPError as error:
            response = error
        payload = response.read()
        self.send_response(response.status if hasattr(response, "status") else response.code)
        for key, value in response.headers.items():
            if key.lower() not in ("transfer-encoding", "connection", "content-length"):
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
        print("ok", self.command, self.path, flush=True)

    do_GET = do_POST = do_PUT = do_DELETE = do_HEAD = _forward

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"proxy on :{LISTEN} -> {UPSTREAM}, login {USER}/{PASSWORD}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", LISTEN), Handler).serve_forever()
