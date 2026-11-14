"""Exercise compact-screen editor navigation using current Android accessibility bounds."""
import re
import subprocess
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=30)


def snapshot():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/studio-check.xml")
        tree = ET.fromstring(adb("exec-out", "cat", "/sdcard/studio-check.xml"))
        # The emulator's launcher can ANR during software GPU tests. Dismiss only
        # that observed external-app dialog; an app-under-test ANR must still fail.
        if any(n.get("text") == "Pixel Launcher isn't responding" for n in tree.iter("node")):
            tap(named(tree, "Close app"))
            continue
        return tree
    raise AssertionError("Emulator launcher repeatedly blocked the editor")


def named(tree, label):
    matches = [n for n in tree.iter("node") if n.get("text") == label]
    assert matches, "Missing control: " + label
    return matches[0]


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    assert x2 > x1 and y2 > y1, "Control is offscreen"
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name):
    with open(name, "wb") as out:
        out.write(adb("exec-out", "screencap", "-p"))


tree = snapshot()
with open("studio-ui.xml", "wb") as out:
    out.write(ET.tostring(tree))
capture("studio-editor.png")
tap(named(tree, "✦ Studio+"))
tap(named(snapshot(), "Edit layer di kanvas"))
tree = snapshot()
named(tree, "Pilih layer untuk digeser")
rows = [n for n in tree.iter("node") if n.get("resource-id") == "android:id/text1"]
assert rows, "No editable layers"
tap(rows[0])
tree = snapshot()
back = named(tree, "↩ Editor")
assert not any(n.get("text") == "AUDIO REACTIVE  •  CUSTOM FX" for n in tree.iter("node"))
gizmos = [n for n in tree.iter("node") if n.get("content-desc", "").startswith("Geser tengah")]
assert gizmos, "Canvas manipulation controls are missing"
_, top, _, bottom = map(int, re.findall(r"\d+", gizmos[0].get("bounds")))
assert bottom - top >= 300, "Expanded canvas is too short on the compact test screen"
capture("studio-canvas.png")
tap(back)
tree = snapshot()
named(tree, "AUDIO REACTIVE  •  CUSTOM FX")
tap(named(tree, "▤ Timeline ▸"))
tree = snapshot()
named(tree, "▤ Timeline ▾")
assert not any(n.get("text") == "AUDIO REACTIVE  •  CUSTOM FX" for n in tree.iter("node"))
capture("studio-timeline.png")
tap(named(tree, "▤ Timeline ▾"))
named(snapshot(), "AUDIO REACTIVE  •  CUSTOM FX")
print("PASS: compact-screen canvas expansion, return to editor and timeline navigation")
