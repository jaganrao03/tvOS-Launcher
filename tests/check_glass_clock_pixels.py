"""Verify actual 1080p ADB captures carry wallpaper colour inside the clock.
Usage: python3 tests/check_glass_clock_pixels.py BEFORE.png AFTER.png
Capture both during the same hour and wallpaper. BEFORE is the solid-clock
baseline. This is a real-device integration check, not a synthetic render.
"""
import statistics
import sys
from PIL import Image


def check(before_path, after_path):
    before = Image.open(before_path).convert('RGB')
    after = Image.open(after_path).convert('RGB')
    assert before.size == after.size == (1920, 1080)

    def white(pixel):
        return min(pixel) > 235 and max(pixel) - min(pixel) < 8

    mask = {(x, y) for x in range(80, 245) for y in range(170, 307)
            if white(before.getpixel((x, y)))}
    # Erode the baseline hour-glyph mask to exclude anti-aliasing and rims.
    interior = [(x, y) for x, y in mask if all(
        (x + dx, y + dy) in mask for dx, dy in [(-2, 0), (2, 0), (0, -2), (0, 2)])]
    assert len(interior) > 300, 'Baseline hour glyph mask missing; capture home first'
    pixels = [after.getpixel(point) for point in interior]
    opaque = sum(white(pixel) for pixel in pixels) / len(pixels)
    colour = statistics.mean(max(pixel) - min(pixel) for pixel in pixels)
    print(f'Clock interiors: {len(pixels)} pixels; opaque white={opaque:.3f}; RGB spread={colour:.2f}')
    assert opaque < 0.60, 'Clock remains opaque white, not wallpaper-tinted glass'
    assert colour > 12, 'Clock interior is not carrying wallpaper colour'
    print('PASS: wallpaper-tinted rather than opaque white interiors')


if __name__ == '__main__':
    check(sys.argv[1], sys.argv[2])
