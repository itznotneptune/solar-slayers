"""One-shot, read-only dump of the GUI currently open in the game (title, slots, item names and lore).

Usage (same environment variables as record_boss.py):
    python tools/dump_screen.py [label]
Writes recordings/screen-<stamp>[-label].json and prints a short summary.
"""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import record_boss  # noqa: E402

record_boss.READ_ROUTES.add("screen")


def main():
    label = ("-" + sys.argv[1]) if len(sys.argv) > 1 else ""
    data = record_boss.get("screen", inv=1)
    out = Path(__file__).resolve().parent.parent / "recordings"
    out.mkdir(exist_ok=True)
    path = out / ("screen-%s%s.json" % (time.strftime("%Y%m%d-%H%M%S"), label))
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print("saved", path)
    print("title:", data.get("title"))
    for s in data.get("slots", []) if isinstance(data, dict) else []:
        print(" ", s.get("slot"), s.get("name"))


if __name__ == "__main__":
    main()
