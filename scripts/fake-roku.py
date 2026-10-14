"""Roku falso para probar la app en un emulador de Android sin tener un Roku a mano.

Imita lo que la app usa de un Roku: ECP (puerto 8060: device-info, active-app, media-player, launch/input/keypress)
y el estado del canal FreeVideocaster (puerto 8061: /status en JSON). La reproducción es simulada (posición en
tiempo real, 10 minutos de duración). /log del puerto 8061 devuelve las órdenes recibidas, en orden.

Uso:  python scripts/fake-roku.py
Solo escucha en 127.0.0.1; el emulador lo ve como 10.0.2.2, así que en la app: Buscar › Agregar por IP › 10.0.2.2.
No es un Roku real: sirve para comprobar pantallas, servicio y notificación, no la reproducción de video."""
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

lock = threading.Lock()
S = {"state": "idle", "title": "", "key": "", "pos": 0.0, "dur": 600000, "seq": 0, "started": 0.0, "active": False, "log": []}


def now_pos():
    if S["state"] == "play":
        return S["pos"] + (time.time() - S["started"]) * 1000
    return S["pos"]


def freeze():
    S["pos"] = now_pos()
    S["started"] = time.time()


def handle(action, q):
    with lock:
        S["log"].append(action)
        if action == "play":
            S.update(state="play", title=q.get("title", [""])[0], key=q.get("key", [""])[0], pos=float(q.get("start", ["0"])[0]) * 1000,
                     started=time.time(), active=True)
            S["seq"] += 1
        elif action in ("toggle",):
            freeze()
            S["state"] = "pause" if S["state"] == "play" else "play"
            S["seq"] += 1
        elif action == "pause":
            freeze(); S["state"] = "pause"
        elif action == "resume":
            freeze(); S["state"] = "play"
        elif action == "skip":
            freeze(); S["pos"] = max(0, S["pos"] + float(q.get("delta", ["0"])[0]) * 1000)
        elif action == "seek":
            S["pos"] = float(q.get("pos", ["0"])[0]) * 1000; S["started"] = time.time()
        elif action == "stop":
            S.update(state="idle", pos=0.0, title="", key="")
            S["seq"] += 1


class Ecp(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def reply(self, body, ctype="text/xml", code=200):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/query/device-info":
            self.reply("<device-info><serial-number>FAKE0001</serial-number><user-device-name>Roku Falso</user-device-name>"
                       "<model-name>Roku Simulado</model-name><model-number>SIM1</model-number><is-tv>true</is-tv>"
                       "<developer-enabled>true</developer-enabled><ecp-setting-mode>permissive</ecp-setting-mode>"
                       "<software-version>15.3.4</software-version><ui-resolution>720p</ui-resolution></device-info>")
        elif path == "/query/active-app":
            self.reply('<active-app><app id="%s">x</app></active-app>' % ("dev" if S["active"] else "12"))
        elif path == "/query/media-player":
            with lock:
                st = S["state"]
                self.reply('<player state="%s" error="false"><plugin id="dev"/><format audio="aac" video="avc1"/>'
                           '<position>%d ms</position><duration>%d ms</duration></player>' % (st, now_pos(), S["dur"]))
        elif path == "/query/apps":
            self.reply('<apps><app id="dev">FreeVideocaster</app></apps>')
        else:
            self.reply("", code=404)

    def do_POST(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        if u.path.startswith("/launch/dev") or u.path == "/input":
            if u.path.startswith("/launch/dev"):
                with lock:
                    S["active"] = True
            if "action" in q:
                handle(q["action"][0], q)
            self.reply("")
        elif u.path.startswith("/keypress/"):
            key = u.path.rsplit("/", 1)[1]
            with lock:
                S["log"].append("key:" + key)
            if key == "Play":
                handle("toggle", {})
            self.reply("")
        else:
            self.reply("", code=404)


class Status(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path.startswith("/status"):
            with lock:
                body = json.dumps({"audio": [], "audiocur": "", "duration": S["dur"] if S["state"] != "idle" else 0, "error": "",
                                   "key": S["key"], "position": int(now_pos()), "queue": [], "seq": S["seq"], "state": S["state"],
                                   "subcur": "", "subs": [], "title": S["title"], "v": 2}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path.startswith("/log"):
            body = json.dumps(S["log"]).encode()
            self.send_response(200); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
        else:
            self.send_response(404); self.end_headers()


if __name__ == "__main__":
    a = ThreadingHTTPServer(("127.0.0.1", 8060), Ecp)
    b = ThreadingHTTPServer(("127.0.0.1", 8061), Status)
    threading.Thread(target=a.serve_forever, daemon=True).start()
    b.serve_forever()
