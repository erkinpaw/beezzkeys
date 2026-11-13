"""Generates the sources of the older game versions and of the other loaders from the main code in ../src.

The main code is written for Minecraft 26.x on Fabric. Each target below names the differences it needs: the rules turn
the 26.x code into code for that target, shared/<name>/ adds files common to several ports (the input records of the
versions before 1.21.9, the self-test, the mixin of a loader), and ports/<target>/overrides/ replaces generated files
outright (the entry point of another loader). A change to the mod is made once in ../src and every port is generated
again:

    python ports/gen.py                  all targets
    python ports/gen.py fabric-1.21.11   one

Each target is built in its own folder, see ports/README.md.
"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(HERE), "src")

# ------------------------------------------------------------------ rule sets: (regex, replacement or function of the match)

# 26.1 renamed the drawing class and its methods; 1.21.6 – 1.21.11 have the same features under the old names
GUI_GRAPHICS = [
    (r"\bGuiGraphicsExtractor\b", "GuiGraphics"),
    (r"\bextractRenderState\(", "render("),
    (r"\bg\.text\(", "g.drawString("),
    (r"\bg\.centeredText\(", "g.drawCenteredString("),
    (r"\bmc\.resizeGui\(\)", "mc.resizeDisplay()"),
]
# Fabric API renamed key bindings to key mappings, and some helpers, with 26.1
FABRIC_KEYBINDING = [
    (r"net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper", "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper"),
    (r"\bKeyMappingHelper\b", "KeyBindingHelper"),
    (r"\bScreens\.getWidgets\(", "Screens.getButtons("),
    (r"\bScreens\.getFont\(", "Screens.getTextRenderer("),
]
# NeoForge and Forge: no Fabric API; the key of a mapping is public there, the entry point is an override
NO_FABRIC = [
    (r"import net\.fabricmc\.[\w.]+;\n", ""),
    (r"KeyMappingHelper\.getBoundKeyOf\((\w+)\)", r"\1.getKey()"),
    (r"FabricLoader\.getInstance\(\)\.getConfigDir\(\)", "net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve(\"config\")"),
]
# 1.20.1: the options screens lived in other packages, a screen drew its own background, and the wheel had one axis
V1_20 = [
    (r"net\.minecraft\.client\.gui\.screens\.options\.controls\.KeyBindsScreen", "net.minecraft.client.gui.screens.controls.KeyBindsScreen"),
    (r"net\.minecraft\.client\.gui\.screens\.options\.OptionsSubScreen", "net.minecraft.client.gui.screens.OptionsSubScreen"),
    (r"\n(\s*)super\.render\(g, ", r"\n\1this.renderBackground(g);\n\1super.render(g, "),
    (r"public boolean mouseScrolled\(double mx, double my, double dx, double dy\) \{",
     "public boolean mouseScrolled(double mx, double my, double dy) {\n        double dx = 0;"),
    (r"super\.mouseScrolled\(mx, my, dx, dy\)", "super.mouseScrolled(mx, my, dy)"),
    (r"\.withColor\(([^()]*)\)", r".withStyle(st -> st.withColor(\1))"),
]
V1_20_SHARED = [(r"s\.mouseScrolled\(([^,]+), ([^,]+), 0, ([^)]+)\)", r"s.mouseScrolled(\1, \2, \3)")]
# helpers that older versions lack
OLD_HELPERS = [
    (r"InputConstants\.MOUSE_BUTTON_4\b", "3"),
    (r"InputConstants\.MOUSE_BUTTON_5\b", "4"),
    (r"AbstractWidget\.playButtonClickSound\((\w+(?:\.\w+\(\))*)\)",
     r"\1.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F))"),
]
# before 1.21.6: the pose is a 3D PoseStack, tooltips are drawn at once, and there are no strata
OLD_POSE = [
    (r"g\.pose\(\)\.pushMatrix\(\)", "g.pose().pushPose()"),
    (r"g\.pose\(\)\.popMatrix\(\)", "g.pose().popPose()"),
    (r"g\.pose\(\)\.translate\(([^;]*)\);", r"g.pose().translate(\1, 0);"),
    (r"g\.pose\(\)\.scale\(([^,;]*), ([^;]*)\);", r"g.pose().scale(\1, \2, 1);"),
    (r"g\.setTooltipForNextFrame\(", "g.renderTooltip("),
    (r"g\.setComponentTooltipForNextFrame\(", "g.renderComponentTooltip("),
    (r"g\.nextStratum\(\);", "g.pose().translate(0, 0, 400);"),
]
# before 1.21.9: key presses and clicks reach a screen as plain numbers, and a key mapping's category is a string.
# The records in shared/classic/.../input give the 26.x code the methods it calls on the newer event objects.
CLASSIC_INPUT = [
    (r"import net\.minecraft\.client\.input\.KeyEvent;", "import land.beezz.keys.input.KeyEvent;"),
    (r"import net\.minecraft\.client\.input\.MouseButtonEvent;", "import land.beezz.keys.input.MouseButtonEvent;"),
    (r"public boolean keyPressed\(KeyEvent event\) \{",
     "public boolean keyPressed(int keyCode, int scanCode, int modifiers) {\n        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);"),
    (r"public boolean keyReleased\(KeyEvent event\) \{",
     "public boolean keyReleased(int keyCode, int scanCode, int modifiers) {\n        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);"),
    (r"public boolean mouseClicked\(MouseButtonEvent event, boolean doubleClick\) \{",
     "public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {\n        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, mouseButton);"),
    (r"public boolean mouseReleased\(MouseButtonEvent event\) \{",
     "public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {\n        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, mouseButton);"),
    (r"super\.keyPressed\(event\)", "super.keyPressed(event.key(), event.scancode(), event.modifiers())"),
    (r"super\.keyReleased\(event\)", "super.keyReleased(event.key(), event.scancode(), event.modifiers())"),
    (r"super\.mouseClicked\(event, doubleClick\)", "super.mouseClicked(event.x(), event.y(), event.button())"),
    (r"super\.mouseReleased\(event\)", "super.mouseReleased(event.x(), event.y(), event.button())"),
    (r"InputConstants\.getKey\(event\)", "InputConstants.getKey(event.key(), event.scancode())"),
    (r"(\w+(?:\.\w+)*)\.getCategory\(\)\.label\(\)", r"Component.translatable(\1.getCategory())"),
    (r"\.getCategory\(\)\.id\(\)\.toString\(\)", ".getCategory()"),
    (r"KeyMapping\.Category\.(\w+)\.id\(\)\.toString\(\)", lambda m: '"key.categories.' + m[1].lower() + '"'),
    # a screen gets its minecraft in init() before 1.21.9, the 26.x constructors already use it
    (r"(\n    (?:public )?\w+Screen\([^)]*\) \{\n        super\([^;]*\);)", r"\1\n        this.minecraft = net.minecraft.client.Minecraft.getInstance();"),
    # the self-test of the older versions runs from the tick the mod already has
    (r"(public static void tick\(Minecraft mc\) \{)", r"\1\n        SelfTest.tick(mc);"),
]

TARGETS = {
    "fabric-1.21.11": {"rules": GUI_GRAPHICS + FABRIC_KEYBINDING, "compat": "glfw", "gametest": True, "java": 21, "loader": "0.17.0", "mc_range": "1.21.11"},
    "fabric-1.21.1": {"rules": GUI_GRAPHICS + FABRIC_KEYBINDING + OLD_POSE + CLASSIC_INPUT + OLD_HELPERS, "compat": "glfw", "java": 21, "loader": "0.16.0", "mc_range": ">=1.21 <=1.21.1",
                      "shared": ["classic", "classic-fabric"]},
    # NO_FABRIC goes first: it matches the 26.x names of Fabric API
    "neoforge-1.21.1": {"rules": NO_FABRIC + GUI_GRAPHICS + OLD_POSE + CLASSIC_INPUT + OLD_HELPERS, "compat": "glfw", "java": 21, "loader": "0",
                        "shared": ["classic", "classic-fabric"], "no_fabric_json": True},
    "fabric-1.20.1": {"rules": GUI_GRAPHICS + FABRIC_KEYBINDING + OLD_POSE + CLASSIC_INPUT + OLD_HELPERS + V1_20, "compat": "glfw", "java": 17, "mc_range": "1.20.1",
                      "loader": "0.15.0", "shared": ["classic", "classic-fabric"], "shared_rules": V1_20_SHARED},
    "forge-1.20.1": {"rules": NO_FABRIC + GUI_GRAPHICS + OLD_POSE + CLASSIC_INPUT + OLD_HELPERS + V1_20, "compat": "glfw", "java": 17, "loader": "0",
                     "shared": ["classic", "classic-fabric"], "shared_rules": V1_20_SHARED, "no_fabric_json": True},
}


def apply(text, rules):
    for pat, rep in rules:
        text = re.sub(pat, rep, text)
    return text


def copy_tree(src_root, dst_root, transform):
    for base, _, files in os.walk(src_root):
        for f in files:
            src = os.path.join(base, f)
            target = os.path.join(dst_root, os.path.relpath(src, src_root))
            os.makedirs(os.path.dirname(target), exist_ok=True)
            if f.endswith((".java", ".json")):
                text = transform(f, open(src, encoding="utf-8").read())
                open(target, "w", encoding="utf-8", newline="\n").write(text)
            else:
                shutil.copyfile(src, target)


def gen(name):
    t = TARGETS[name]
    root = os.path.join(HERE, name)
    out = os.path.join(root, "src")
    shutil.rmtree(out, ignore_errors=True)

    def transform(f, text):
        if f.endswith(".java"):
            return apply(text, t["rules"])
        # the Java level of the mixins and the oldest loader this game version works with
        # and the game versions it was built and tested for: the next minor versions changed the screen code
        text = text.replace('"~${minecraft_version}"', f'"{t.get("mc_range", "~${minecraft_version}")}"')
        return text.replace('"JAVA_25"', f'"JAVA_{t["java"]}"').replace('">=0.19.0"', f'">={t["loader"]}"')

    for part in ["client", "main"] + (["gametest"] if t.get("gametest") else []):
        copy_tree(os.path.join(SRC, part), os.path.join(out, part), transform)
    compat = os.path.join(SRC, "compat", t["compat"], "land", "beezz", "keys", "Compat.java")
    shutil.copyfile(compat, os.path.join(out, "client", "java", "land", "beezz", "keys", "Compat.java"))
    for s in t.get("shared", []):
        copy_tree(os.path.join(HERE, "shared", s), out, lambda f, text: apply(text, t.get("shared_rules", [])))
    if t.get("no_fabric_json"):
        os.remove(os.path.join(out, "main", "resources", "fabric.mod.json"))
    over = os.path.join(root, "overrides")
    if os.path.isdir(over):
        copy_tree(over, out, lambda f, text: text)
    print("generated", name)


if __name__ == "__main__":
    for n in (sys.argv[1:] or TARGETS):
        gen(n)
