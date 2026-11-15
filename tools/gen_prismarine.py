"""Generates tinted model templates and prismarine model overrides for Solar Slayers' Prismarine Shade.

usage: gen_prismarine.py <extracted vanilla assets root> <mod resources root>
Every face of the copied templates gets tintindex 0, so the block colour provider can darken the block.
"""
import json, pathlib, sys

van = pathlib.Path(sys.argv[1]) / "assets" / "minecraft" / "models" / "block"
out = pathlib.Path(sys.argv[2]) / "assets"
mine = out / "solarslayers" / "models" / "block"
over = out / "minecraft" / "models" / "block"
mine.mkdir(parents=True, exist_ok=True)
over.mkdir(parents=True, exist_ok=True)

def strip_ns(ref):
    return ref.split(":", 1)[-1]

def tinted_name(parent):
    return "solarslayers:block/tinted_" + strip_ns(parent).split("/")[-1]

made = set()
def make_tinted(parent):
    """Copy a vanilla parent model, tinting its faces; follow the chain while it has no elements."""
    name = strip_ns(parent).split("/")[-1]
    if name in made:
        return
    m = json.loads((van / (name + ".json")).read_text(encoding="utf-8"))
    if "elements" in m:
        for el in m["elements"]:
            for face in el.get("faces", {}).values():
                face["tintindex"] = 0
    else:
        # e.g. cube_all -> cube: tint the parent and point at the tinted copy
        make_tinted(m["parent"])
        m["parent"] = tinted_name(m["parent"])
    m.pop("__comment", None)
    (mine / ("tinted_" + name + ".json")).write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
    made.add(name)

count = 0
for f in sorted(van.glob("*prismarine*.json")):
    if f.stem.endswith("_inventory"):
        continue  # inventory-only model; items stay as they are
    m = json.loads(f.read_text(encoding="utf-8"))
    make_tinted(m["parent"])
    m["parent"] = tinted_name(m["parent"])
    (over / f.name).write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
    count += 1
print("overrides:", count, "templates:", sorted(made))
