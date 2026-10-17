#!/usr/bin/env python3
"""Check real-device review geometry after opening its manual field and keyboard.

Pass the UI Automator XML captured in that state. Inspect the corresponding
screenshot to confirm the real keyboard is visible; this check does not create
or emulate IME insets. It performs no device actions and commits no meal.
"""
import argparse
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def visible_bounds(node):
    values = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    assert len(values) == 4, "Missing native bounds"
    left, top, right, bottom = values
    assert right > left and bottom > top, (
        f"{node.get('resource-id')} is clipped outside the visible app area: {values}"
    )
    assert left >= 0 and top >= 0, "Control is outside the screen"
    return values


def verify(hierarchy, package, expected_input, keyboard_top):
    root = ET.parse(hierarchy).getroot()
    nodes = [node for node in root.iter("node") if node.get("package") == package]

    def control(resource_id):
        matches = [node for node in nodes if node.get("resource-id") == resource_id]
        assert len(matches) == 1, f"Expected one {resource_id}, found {len(matches)}"
        assert matches[0].get("enabled") == "true", f"{resource_id} is disabled"
        return matches[0]

    field = control("photo_analysis_manual_ingredient_input")
    assert field.get("focused") == "true", "Open and focus the real manual input first"
    assert field.get("text") == expected_input, "The focused field does not contain the expected QA text"
    visible_bounds(control("capture_close"))
    for resource_id in [
        "photo_analysis_manual_ingredient_input", "capture_scan_again", "photo_analysis_add_to_meal"
    ]:
        bounds = visible_bounds(control(resource_id))
        if keyboard_top is not None:
            assert bounds[3] <= keyboard_top, f"{resource_id} extends behind the keyboard: {bounds}"
    print("PASS: focused manual text, Close, Retake and Add to meal are visible with the real IME")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hierarchy", type=Path, required=True)
    parser.add_argument("--package", default="app.supernutrition")
    parser.add_argument("--expected-input", default="apple")
    parser.add_argument("--keyboard-top", type=int)
    args = parser.parse_args()
    verify(args.hierarchy, args.package, args.expected_input, args.keyboard_top)
