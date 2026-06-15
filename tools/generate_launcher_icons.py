#!/usr/bin/env python3
"""
Generate the legacy mipmap launcher icons (PNG) for DriveReply.

This is a *visual* companion to the adaptive icon vector drawables:
  - app/src/main/res/drawable/ic_launcher_foreground.xml
  - app/src/main/res/drawable/ic_launcher_background.xml
  - app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

Why a script: minSdk = 29 means adaptive icons (mipmap-anydpi-v26) cover
all supported devices, but some OEM launchers and the App Info screen still
prefer the legacy mipmap PNGs. We derive them deterministically from the
same design constants as the vector, so the two never drift.

The design:
    - Deep-navy background with a soft radial gradient.
    - Electric-cyan chat bubble (rounded square + bottom-left tail).
    - White steering wheel inside the bubble (3 spokes + central hub).

Run:
    python tools/generate_launcher_icons.py
"""

from __future__ import annotations

import math
import os
import sys
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw

# --- design constants ------------------------------------------------------

# Color palette (kept in sync with ic_launcher_foreground.xml and
# ic_launcher_background.xml).
BG_OUTER = (10, 22, 34, 255)        # #0A1622
BG_INNER = (26, 44, 69, 255)        # #1A2C45
BUBBLE = (0, 194, 255, 255)         # #00C2FF
WHEEL = (255, 255, 255, 255)        # #FFFFFF

# Master canvas is 1024x1024. The vector viewport is 108x108, so the scale
# factor is 1024/108 ≈ 9.4815 px / dp. We draw at the master scale and let
# Pillow downsample with high-quality resampling to the density sizes.
SCALE = 1024.0 / 108.0
MASTER = 1024

# Geometry, expressed in *dp* (matching the vector drawable exactly).
GEOM = {
    "bubble_x1": 24.0,
    "bubble_y1": 28.0,
    "bubble_x2": 84.0,
    "bubble_y2": 70.0,
    "bubble_radius": 12.0,
    "tail_a": (45.0, 70.0),
    "tail_b": (37.0, 80.0),
    "tail_c": (50.0, 70.0),
    "wheel_cx": 54.0,
    "wheel_cy": 49.0,
    "wheel_r": 15.0,
    "wheel_stroke": 4.0,
    "spoke_top": (54.0, 34.0),
    "spoke_bl": (41.0, 56.5),
    "spoke_br": (67.0, 56.5),
    "hub_r": 4.0,
}

# Standard Android launcher icon densities (px).
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def dp(value: float) -> float:
    return value * SCALE


def draw_background(img: Image.Image) -> None:
    """Paint the deep-navy background with a soft radial gradient."""
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, MASTER, MASTER], fill=BG_OUTER)
    # Radial gradient: nested ellipses with decreasing alpha on a separate
    # layer, then alpha-composited. Centered slightly off-center to mirror
    # the vector drawable's gradient.
    grad_center = (dp(40), dp(35))
    grad_radius = dp(60)
    layers = 48
    for i in range(layers, 0, -1):
        t = i / layers
        r = grad_radius * t
        alpha = int(80 * (1 - t) ** 1.6)
        if alpha <= 0:
            continue
        layer = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
        ldraw = ImageDraw.Draw(layer)
        bbox = [
            grad_center[0] - r,
            grad_center[1] - r,
            grad_center[0] + r,
            grad_center[1] + r,
        ]
        ldraw.ellipse(bbox, fill=(BG_INNER[0], BG_INNER[1], BG_INNER[2], alpha))
        img.alpha_composite(layer)


def draw_bubble(img: Image.Image) -> None:
    """Paint the electric-cyan chat bubble with its tail."""
    draw = ImageDraw.Draw(img)
    body = [
        dp(GEOM["bubble_x1"]),
        dp(GEOM["bubble_y1"]),
        dp(GEOM["bubble_x2"]),
        dp(GEOM["bubble_y2"]),
    ]
    radius = dp(GEOM["bubble_radius"])
    draw.rounded_rectangle(body, radius=radius, fill=BUBBLE)
    # Tail triangle at the bottom-left.
    tail = [
        (dp(GEOM["tail_a"][0]), dp(GEOM["tail_a"][1])),
        (dp(GEOM["tail_b"][0]), dp(GEOM["tail_b"][1])),
        (dp(GEOM["tail_c"][0]), dp(GEOM["tail_c"][1])),
    ]
    draw.polygon(tail, fill=BUBBLE)


def draw_wheel(img: Image.Image) -> None:
    """Paint the white steering wheel: ring, three spokes, central hub."""
    draw = ImageDraw.Draw(img)
    cx, cy = dp(GEOM["wheel_cx"]), dp(GEOM["wheel_cy"])
    r = dp(GEOM["wheel_r"])
    stroke = dp(GEOM["wheel_stroke"])

    # Outer ring: stroked circle, no fill. Use two filled circles (outer
    # white, inner background-color) so the ring thickness is exact.
    draw.ellipse(
        [cx - r, cy - r, cx + r, cy + r],
        fill=WHEEL,
    )
    draw.ellipse(
        [cx - r + stroke, cy - r + stroke, cx + r - stroke, cy + r - stroke],
        fill=BG_OUTER,
    )

    # 3 spokes (top, bottom-left, bottom-right). Drawn with stroke + round
    # caps so the line endpoints match the hub circle smoothly.
    spoke_points = [
        GEOM["spoke_top"],
        GEOM["spoke_bl"],
        GEOM["spoke_br"],
    ]
    for end in spoke_points:
        draw.line(
            [(cx, cy), (dp(end[0]), dp(end[1]))],
            fill=WHEEL,
            width=int(round(stroke)),
            joint="curve",
        )

    # Central hub: filled white circle, large enough to cover the spoke
    # origins cleanly.
    hub_r = dp(GEOM["hub_r"]) + stroke / 2
    draw.ellipse(
        [cx - hub_r, cy - hub_r, cx + hub_r, cy + hub_r],
        fill=WHEEL,
    )


def render_master() -> Image.Image:
    img = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    draw_background(img)
    draw_bubble(img)
    draw_wheel(img)
    return img


def apply_circular_mask(img: Image.Image) -> Image.Image:
    """Apply a circular alpha mask (used for the round mipmap variant)."""
    mask = Image.new("L", (MASTER, MASTER), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, MASTER, MASTER], fill=255)
    out = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def write_mipmaps(
    master: Image.Image,
    master_round: Image.Image,
    out_root: Path,
) -> Iterable[Path]:
    """Downsample the master to every density and write both variants."""
    written: list[Path] = []
    for density, size in DENSITIES.items():
        target_dir = out_root / f"mipmap-{density}"
        target_dir.mkdir(parents=True, exist_ok=True)
        for name, src in (("ic_launcher", master), ("ic_launcher_round", master_round)):
            resized = src.resize((size, size), Image.Resampling.LANCZOS)
            # Plain Android mipmaps use RGB (no alpha) for compatibility; but
            # PNG supports alpha. Keep RGBA so the round variant's transparent
            # corners remain transparent.
            out_path = target_dir / f"{name}.png"
            resized.save(out_path, format="PNG", optimize=True)
            written.append(out_path)
    return written


def main(argv: list[str]) -> int:
    repo_root = Path(__file__).resolve().parent.parent
    res_root = repo_root / "app" / "src" / "main" / "res"
    if not res_root.is_dir():
        print(f"ERROR: res root not found at {res_root}", file=sys.stderr)
        return 1

    print(f"Rendering master icon ({MASTER}x{MASTER})…")
    master = render_master()
    master_round = apply_circular_mask(master)

    print(f"Writing mipmaps under {res_root}…")
    written = write_mipmaps(master, master_round, res_root)
    for path in written:
        rel = path.relative_to(repo_root)
        print(f"  wrote {rel} ({path.stat().st_size} bytes)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
