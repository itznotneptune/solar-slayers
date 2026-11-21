"""Generates tinted block-model overrides so a block colour provider can darken whole blocks.

usage: gen_shade.py <extracted vanilla assets root> <mod resources root>

For every block in BLOCKS, each model its blockstate uses is copied into the mod's minecraft namespace with its parent
swapped for a tinted copy (solarslayers:block/tinted_<parent>), in which every face has tintindex 0. Vanilla blocks
here have no tint of their own, so the colour provider registered for them (see GraveShade) decides the shade.
Prismarine has its own generator, gen_prismarine.py; the tinted templates are shared.
"""
import json, pathlib, sys

# World 1 (Grave Tyrant): the bright, pale blocks from the 2026-11-21 block scan. Arena-only blocks (bone, iron,
# light grey glazed terracotta) and anything interactive are left alone.
BLOCKS = [
    "sand", "red_sand",
    "sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall",
    "cut_sandstone", "cut_sandstone_slab", "chiseled_sandstone",
    "smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab",
    "red_sandstone", "red_sandstone_stairs", "red_sandstone_slab", "red_sandstone_wall",
    "cut_red_sandstone", "cut_red_sandstone_slab", "chiseled_red_sandstone",
    "smooth_red_sandstone", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab",
    "birch_planks", "birch_slab", "birch_stairs", "birch_fence", "birch_trapdoor",
    "smooth_quartz", "chiseled_quartz_block", "calcite", "white_wool",
]

root = pathlib.Path(sys.argv[1]) / "assets" / "minecraft"
van = root / "models" / "block"
out = pathlib.Path(sys.argv[2]) / "assets"
mine = out / "solarslayers" / "models" / "block"
over = out / "minecraft" / "models" / "block"
mine.mkdir(parents=True, exist_ok=True)
over.mkdir(parents=True, exist_ok=True)

def name_of(ref):
    return ref.split(":", 1)[-1].split("/")[-1]

def tinted_name(parent):
    return "solarslayers:block/tinted_" + name_of(parent)

made = set()
def make_tinted(parent):
    """Copy a vanilla parent model, tinting its faces; follow the chain while it has no elements."""
    name = name_of(parent)
    if name in made:
        return
    m = json.loads((van / (name + ".json")).read_text(encoding="utf-8"))
    if "elements" in m:
        for el in m["elements"]:
            for face in el.get("faces", {}).values():
                face["tintindex"] = 0
    else:
        make_tinted(m["parent"])
        m["parent"] = tinted_name(m["parent"])
    m.pop("__comment", None)
    (mine / ("tinted_" + name + ".json")).write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
    made.add(name)

def models_of(state):
    """Every model a blockstate file refers to (variants or multipart, single or weighted lists)."""
    refs = set()
    def take(v):
        for e in v if isinstance(v, list) else [v]:
            refs.add(name_of(e["model"]))
    for v in state.get("variants", {}).values():
        take(v)
    for part in state.get("multipart", []):
        take(part["apply"])
    return refs

count = 0
for block in BLOCKS:
    state = json.loads((root / "blockstates" / (block + ".json")).read_text(encoding="utf-8"))
    for model in sorted(models_of(state)):
        m = json.loads((van / (model + ".json")).read_text(encoding="utf-8"))
        if "elements" in m:
            raise SystemExit(f"{model} has its own elements; tint them here if it is ever needed")
        make_tinted(m["parent"])
        m["parent"] = tinted_name(m["parent"])
        (over / (model + ".json")).write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
        count += 1
print("overrides:", count, "templates:", sorted(made))
