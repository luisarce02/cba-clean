#!/usr/bin/env python3
"""
Assemble the per-step D2 PNG frames (flow-animation/1.png .. 11.png) into a
single looping GIF: flow-animation.gif.

Prerequisites:
  1. Render the step PNGs first (produces flow-animation/1.png .. 11.png):
       d2 flow-animation.d2 flow-animation.png --layout elk --theme 0
  2. pip install Pillow

Usage:
  python render.py
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).parent
FRAMES_DIR = HERE / "flow-animation"
OUT_GIF = HERE / "flow-animation.gif"

# Step 7 (end of runtime flow) and step 11 (end of deploy flow) hold longer
# so the completed state of each flow is readable before it moves on / loops.
HOLD_MS = {7: 2800, 11: 3200}
DEFAULT_MS = 1500


def main() -> None:
    frame_paths = sorted(
        FRAMES_DIR.glob("*.png"), key=lambda p: int(p.stem)
    )
    if not frame_paths:
        raise SystemExit(
            f"No frames found in {FRAMES_DIR}. Render step PNGs first "
            "(see docstring)."
        )

    frames = [Image.open(p).convert("RGBA") for p in frame_paths]
    max_w = max(f.width for f in frames)
    max_h = max(f.height for f in frames)

    # Scale down to a README/portfolio-friendly width; keeps the GIF small.
    TARGET_WIDTH = 900
    if max_w > TARGET_WIDTH:
        scale = TARGET_WIDTH / max_w
        max_w, max_h = TARGET_WIDTH, round(max_h * scale)
        frames = [
            f.resize(
                (round(f.width * scale), round(f.height * scale)),
                Image.LANCZOS,
            )
            for f in frames
        ]

    padded = []
    for f in frames:
        canvas = Image.new("RGBA", (max_w, max_h), "#FFFFFF")
        canvas.paste(f, (0, 0), f)
        padded.append(canvas.convert("P", palette=Image.ADAPTIVE, colors=256))

    durations = [
        HOLD_MS.get(int(p.stem), DEFAULT_MS) for p in frame_paths
    ]

    padded[0].save(
        OUT_GIF,
        save_all=True,
        append_images=padded[1:],
        duration=durations,
        loop=0,
        optimize=False,
        disposal=2,
    )
    print(f"Wrote {OUT_GIF} ({len(padded)} frames, {max_w}x{max_h})")


if __name__ == "__main__":
    main()
