#!/usr/bin/env python3
"""
Generates a retina (2x) liquid glass background for DeskFlow macOS DMG installer.
Dimensions: 1320x840 px (displayed at 660x420 pt).
"""
import math
from PIL import Image, ImageDraw, ImageFilter

def create_dmg_background(output_path="macos/DeskFlow/Resources/dmg_background.png"):
    width = 1320
    height = 840

    # 1. Base Gradient Canvas (Deep Night Obsidian / Twilight Violet)
    base = Image.new("RGBA", (width, height), (15, 11, 28, 255))
    draw = ImageDraw.Draw(base)

    # Vertical / diagonal dark gradient
    for y in range(height):
        ratio = y / height
        # Gradient from deep dark indigo #120922 to obsidian violet #0d0a1a to rich midnight #180d2f
        r = int(18 + 10 * math.sin(ratio * math.pi) + 6 * ratio)
        g = int(10 + 6 * math.sin(ratio * math.pi) + 4 * ratio)
        b = int(34 + 20 * math.sin(ratio * math.pi) + 12 * (1 - ratio))
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))

    # 2. Ambient Light Orbs (Glows for Liquid Glass Atmosphere)
    glow_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_layer)

    # Left ambient glow (DeskFlow App pedestal: cyan + purple)
    # DeskFlow icon sits at (x=320, y=430)
    for rad in range(240, 0, -8):
        alpha = int(45 * (1 - rad / 240.0) ** 1.8)
        glow_draw.ellipse(
            [320 - rad, 430 - rad, 320 + rad, 430 + rad],
            fill=(140, 50, 240, alpha)
        )
    for rad in range(160, 0, -6):
        alpha = int(60 * (1 - rad / 160.0) ** 1.5)
        glow_draw.ellipse(
            [320 - rad, 430 - rad, 320 + rad, 430 + rad],
            fill=(40, 180, 250, alpha)
        )

    # Right ambient glow (Applications pedestal: azure blue)
    # Applications folder sits at (x=1000, y=430)
    for rad in range(240, 0, -8):
        alpha = int(45 * (1 - rad / 240.0) ** 1.8)
        glow_draw.ellipse(
            [1000 - rad, 430 - rad, 1000 + rad, 430 + rad],
            fill=(50, 100, 240, alpha)
        )
    for rad in range(160, 0, -6):
        alpha = int(55 * (1 - rad / 160.0) ** 1.5)
        glow_draw.ellipse(
            [1000 - rad, 430 - rad, 1000 + rad, 430 + rad],
            fill=(56, 189, 248, alpha)
        )

    # Center arrow ambient glow
    for rad in range(120, 0, -6):
        alpha = int(35 * (1 - rad / 120.0) ** 1.5)
        glow_draw.ellipse(
            [660 - rad * 1.5, 430 - rad * 0.8, 660 + rad * 1.5, 430 + rad * 0.8],
            fill=(168, 85, 247, alpha)
        )

    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(35))
    base = Image.alpha_composite(base, glow_layer)

    # 3. Layered Organic Liquid Glass Clouds / Waves
    wave_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    wave_draw = ImageDraw.Draw(wave_layer)

    # Top subtle floating cloud 1 (Left)
    wave_draw.ellipse([80, 60, 360, 150], fill=(42, 22, 68, 85))
    wave_draw.ellipse([140, 80, 480, 160], fill=(36, 18, 58, 70))

    # Top subtle floating cloud 2 (Right)
    wave_draw.ellipse([860, 70, 1140, 160], fill=(46, 24, 76, 80))
    wave_draw.ellipse([920, 90, 1220, 170], fill=(38, 20, 62, 65))

    # Far-right middle accent cloud
    wave_draw.ellipse([1100, 320, 1360, 420], fill=(42, 20, 70, 75))

    # Bottom rolling wave 1 (Deep Purple wave in background)
    points_wave1 = []
    points_wave1.append((0, height))
    for x in range(0, width + 10, 10):
        # Organic curve
        y = height - 220 + 45 * math.sin(x * 0.0038) + 25 * math.cos(x * 0.007)
        points_wave1.append((x, y))
    points_wave1.append((width, height))
    wave_draw.polygon(points_wave1, fill=(28, 14, 48, 160))

    # Bottom rolling wave 2 (Foreground deeper wave)
    points_wave2 = []
    points_wave2.append((0, height))
    for x in range(0, width + 10, 10):
        y = height - 140 + 35 * math.sin(x * 0.005 + 1.2) - 20 * math.cos(x * 0.003)
        points_wave2.append((x, y))
    points_wave2.append((width, height))
    wave_draw.polygon(points_wave2, fill=(20, 10, 36, 220))

    wave_layer = wave_layer.filter(ImageFilter.GaussianBlur(14))
    base = Image.alpha_composite(base, wave_layer)

    # 4. Liquid Glass Pedestal Rings (Drop targets for App & Applications)
    glass_pedestals = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ped_draw = ImageDraw.Draw(glass_pedestals)

    # Left glass pedestal (Deskflow.app)
    ped_w, ped_h = 240, 240
    ped_draw.ellipse(
        [320 - ped_w // 2, 430 - ped_h // 2, 320 + ped_w // 2, 430 + ped_h // 2],
        fill=(255, 255, 255, 8),
        outline=(255, 255, 255, 30),
        width=2
    )

    # Right glass pedestal (Applications)
    ped_draw.ellipse(
        [1000 - ped_w // 2, 430 - ped_h // 2, 1000 + ped_w // 2, 430 + ped_h // 2],
        fill=(255, 255, 255, 8),
        outline=(255, 255, 255, 30),
        width=2
    )

    base = Image.alpha_composite(base, glass_pedestals)

    # 5. Sleek Glowing Vector Arrow (Pointing from left to right)
    # Shaft: from x=530 to x=770 at y=430
    arrow_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    arrow_draw = ImageDraw.Draw(arrow_layer)

    start_x, end_x, y_pos = 530, 780, 430
    shaft_width = 8

    # Arrow outer glow
    for glow_w in range(24, 0, -4):
        glow_alpha = int(40 * (1 - glow_w / 24.0))
        # Shaft glow
        arrow_draw.line(
            [(start_x, y_pos), (end_x, y_pos)],
            fill=(140, 210, 255, glow_alpha),
            width=shaft_width + glow_w
        )
        # Chevron head glow
        arrow_draw.line(
            [(end_x - 36, y_pos - 32), (end_x, y_pos)],
            fill=(140, 210, 255, glow_alpha),
            width=shaft_width + glow_w
        )
        arrow_draw.line(
            [(end_x - 36, y_pos + 32), (end_x, y_pos)],
            fill=(140, 210, 255, glow_alpha),
            width=shaft_width + glow_w
        )

    arrow_layer = arrow_layer.filter(ImageFilter.GaussianBlur(8))

    # Arrow crisp core (solid luminous white with soft rounded caps)
    core_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    core_draw = ImageDraw.Draw(core_layer)

    core_draw.line(
        [(start_x, y_pos), (end_x, y_pos)],
        fill=(255, 255, 255, 240),
        width=shaft_width
    )
    core_draw.line(
        [(end_x - 36, y_pos - 32), (end_x, y_pos)],
        fill=(255, 255, 255, 240),
        width=shaft_width
    )
    core_draw.line(
        [(end_x - 36, y_pos + 32), (end_x, y_pos)],
        fill=(255, 255, 255, 240),
        width=shaft_width
    )

    # Arrowhead tip cap
    core_draw.ellipse([end_x - 4, y_pos - 4, end_x + 4, y_pos + 4], fill=(255, 255, 255, 255))
    core_draw.ellipse([start_x - 4, y_pos - 4, start_x + 4, y_pos + 4], fill=(255, 255, 255, 255))

    base = Image.alpha_composite(base, arrow_layer)
    base = Image.alpha_composite(base, core_layer)

    # Convert to RGB and save as PNG
    final_img = base.convert("RGB")
    final_img.save(output_path, "PNG", optimize=True)
    print(f"Successfully generated {output_path} ({width}x{height})")

if __name__ == "__main__":
    create_dmg_background()
