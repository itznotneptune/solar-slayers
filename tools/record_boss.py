"""Read-only recorder for a slayer fight, via the Claude Control bridge.

Polls only the read routes (state, chat, entities); it never sends input, chat or commands.

Usage (from a terminal you run yourself):
    set CLAUDECONTROL_HOST=<game PC's LAN IP> (omit when running on the gaming PC itself)
    set CLAUDECONTROL_TOKEN=<token>           (not needed on the gaming PC itself)
    python tools/record_boss.py [--radius 32] [--interval 1.0]
Stop with Ctrl+C.

Writes into recordings/:
    <stamp>-raw.jsonl     every poll: {"t", "state", "entities"} plus new chat lines
    <stamp>-events.log    human-readable changes: entities appearing/leaving/renamed, chat, action bar, sidebar
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HOST = os.environ.get("CLAUDECONTROL_HOST", "127.0.0.1")
PORT = os.environ.get("CLAUDECONTROL_PORT", "25599")
TOKEN = os.environ.get("CLAUDECONTROL_TOKEN", "")
READ_ROUTES = {"state", "chat", "entities"}


def get(route, **params):
    if route not in READ_ROUTES:
        raise ValueError("recorder is read-only: " + route)
    url = "http://%s:%s/%s" % (HOST, PORT, route)
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url)
    if TOKEN:
        req.add_header("X-Claude-Token", TOKEN)
    with urllib.request.urlopen(req, timeout=5) as r:
        return json.loads(r.read().decode("utf-8"))


def ent_label(e):
    s = "#%s %s %r" % (e.get("id"), e.get("type"), e.get("name"))
    if e.get("text"):
        s += " text=%r" % e["text"]
    return s + " @%s d=%s" % (e.get("pos"), e.get("dist"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--radius", type=int, default=32)
    ap.add_argument("--interval", type=float, default=1.0)
    a = ap.parse_args()

    out = Path(__file__).resolve().parent.parent / "recordings"
    out.mkdir(exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    raw = open(out / (stamp + "-raw.jsonl"), "a", encoding="utf-8")
    ev = open(out / (stamp + "-events.log"), "a", encoding="utf-8")

    def event(msg):
        line = "%s  %s" % (time.strftime("%H:%M:%S"), msg)
        print(line)
        ev.write(line + "\n")
        ev.flush()

    try:
        first = get("state")
    except urllib.error.HTTPError as e:
        sys.exit("bridge refused: HTTP %s %s" % (e.code, e.read().decode("utf-8", "replace")))
    except Exception as e:
        sys.exit("cannot reach bridge at %s:%s: %s" % (HOST, PORT, e))
    event("connected: player=%s server=%s" % (first.get("name"), first.get("server")))

    chat_since = get("chat", since=0, limit=1).get("lines", [{}])
    chat_since = chat_since[-1].get("id", 0) if chat_since else 0
    known = {}          # entity id -> (name, text)
    sidebar = None
    action = None

    try:
        while True:
            t = time.time()
            try:
                st = get("state")
                ents = get("entities", r=a.radius)
                ch = get("chat", since=chat_since, limit=200)
            except Exception as e:
                event("poll error: %s" % e)
                time.sleep(a.interval)
                continue

            lines = ch.get("lines", [])
            if lines:
                chat_since = lines[-1]["id"]
            raw.write(json.dumps({"t": t, "state": st, "entities": ents, "chat": lines,
                                  "actionBar": ch.get("actionBar")}, ensure_ascii=False) + "\n")
            raw.flush()

            for l in lines:
                event("CHAT  %s" % l.get("text"))
            if ch.get("actionBar") != action:
                action = ch.get("actionBar")
                event("ACTION  %s" % action)
            sb = st.get("sidebar")
            if sb != sidebar:
                sidebar = sb
                event("SIDEBAR  %s" % json.dumps(sb, ensure_ascii=False))

            now = {}
            for e in ents:
                now[e["id"]] = e
                key = (e.get("name"), e.get("text"))
                if e["id"] not in known:
                    event("SPAWN  " + ent_label(e))
                elif known[e["id"]] != key:
                    event("CHANGE " + ent_label(e))
                known[e["id"]] = key
            for gone in [i for i in known if i not in now]:
                event("GONE   #%s %r" % (gone, known.pop(gone)[0]))

            time.sleep(max(0.0, a.interval - (time.time() - t)))
    except KeyboardInterrupt:
        event("stopped")


if __name__ == "__main__":
    main()
