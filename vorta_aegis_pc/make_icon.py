import math
import os
import struct
import zlib

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
SVG_PATH = os.path.join(ASSETS_DIR, "icon.svg")
ICO_PATH = os.path.join(ASSETS_DIR, "vorta_aegis.ico")

ICON_SVG = '''<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 24 24" fill="none">
  <circle cx="12" cy="12" r="11" fill="#0E0F11" stroke="#2A2D35" stroke-width="0.6"/>
  <path d="M12,3.5L18.5,6.3V11.8C18.5,16.1 15.6,19.4 12,20.5C8.4,19.4 5.5,16.1 5.5,11.8V6.3L12,3.5Z"
        fill="#17181C" stroke="#F2F3F5" stroke-width="1.75" stroke-linejoin="round"/>
  <path d="M8.2,12C9.2,9.8 10.6,9.8 12,12C13.4,14.2 14.8,14.2 15.8,12"
        stroke="#E5A93C" stroke-width="1.75" stroke-linecap="round"/>
</svg>
'''


def _sample_cubic(p0, p1, p2, p3, steps=24):
    pts = []
    for k in range(steps + 1):
        t = k / steps
        mt = 1.0 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def _build_shield_polygon():
    pts = [(12.0, 3.5), (18.5, 6.3), (18.5, 11.8)]
    pts.extend(_sample_cubic((18.5, 11.8), (18.5, 16.1), (15.6, 19.4), (12.0, 20.5), 24)[1:])
    pts.extend(_sample_cubic((12.0, 20.5), (8.4, 19.4), (5.5, 16.1), (5.5, 11.8), 24)[1:])
    pts.append((5.5, 6.3))
    return pts


def _build_wave_polyline():
    pts = _sample_cubic((8.2, 12.0), (9.2, 9.8), (10.6, 9.8), (12.0, 12.0), 20)
    pts.extend(_sample_cubic((12.0, 12.0), (13.4, 14.2), (14.8, 14.2), (15.8, 12.0), 20)[1:])
    return pts


SHIELD_POLY = _build_shield_polygon()
WAVE_POLY = _build_wave_polyline()


def _dist_seg(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    denom = vx * vx + vy * vy
    if denom <= 1e-12:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / denom))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy))


def _poly_min_dist(px, py, poly, closed=True):
    best = 1e9
    n = len(poly)
    limit = n if closed else n - 1
    for i in range(limit):
        a = poly[i]
        b = poly[(i + 1) % n]
        d = _dist_seg(px, py, a[0], a[1], b[0], b[1])
        if d < best:
            best = d
    return best


def _point_in_poly(px, py, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-15) + xi):
            inside = not inside
        j = i
    return inside


def _blend(dst, src):
    sr, sg, sb, sa = src[0], src[1], src[2], src[3] / 255.0
    if sa <= 0.0:
        return dst
    dr, dg, db, da = dst[0], dst[1], dst[2], dst[3] / 255.0
    out_a = sa + da * (1.0 - sa)
    if out_a <= 1e-6:
        return (0, 0, 0, 0)
    r = (sr * sa + dr * da * (1.0 - sa)) / out_a
    g = (sg * sa + dg * da * (1.0 - sa)) / out_a
    b = (sb * sa + db * da * (1.0 - sa)) / out_a
    return (int(r + 0.5), int(g + 0.5), int(b + 0.5), int(out_a * 255.0 + 0.5))


def _render_png(size: int) -> bytes:
    px_scale = 24.0 / size
    aa = max(px_scale * 1.1, 0.08)
    raw = bytearray()

    for y in range(size):
        raw.append(0)  # PNG filter type 0
        vy = (y + 0.5) * px_scale
        for x in range(size):
            vx = (x + 0.5) * px_scale
            pixel = (0, 0, 0, 0)

            # Outer circle (#0E0F11) radius 11 + #2A2D35 0.6px rim
            rc = math.hypot(vx - 12.0, vy - 12.0)
            if rc <= 11.4 + aa:
                if rc <= 11.0 + aa:
                    a_c = max(0.0, min(1.0, (11.0 + aa * 0.5 - rc) / aa))
                    pixel = _blend(pixel, (14, 15, 17, int(a_c * 255)))
                d_rim = abs(rc - 11.0) - 0.3
                if d_rim < aa * 0.5:
                    a_r = max(0.0, min(1.0, (aa * 0.5 - d_rim) / aa))
                    pixel = _blend(pixel, (42, 45, 53, int(a_r * 255)))

                # Shield interior (#17181C) & stroke (#F2F3F5, width 1.75)
                if 4.0 <= vx <= 20.0 and 2.0 <= vy <= 22.0:
                    d_sh = _poly_min_dist(vx, vy, SHIELD_POLY, closed=True)
                    in_sh = _point_in_poly(vx, vy, SHIELD_POLY)
                    sd_sh = -d_sh if in_sh else d_sh
                    if sd_sh <= aa * 0.5:
                        a_fill = max(0.0, min(1.0, (aa * 0.5 - sd_sh) / aa))
                        pixel = _blend(pixel, (23, 24, 28, int(a_fill * 255)))
                    half_sw = 1.75 * 0.5
                    if d_sh <= half_sw + aa * 0.5:
                        a_st = max(0.0, min(1.0, (half_sw + aa * 0.5 - d_sh) / aa))
                        pixel = _blend(pixel, (242, 243, 245, int(a_st * 255)))

                # Amber sine wave (#E5A93C, width 1.75)
                if 7.0 <= vx <= 17.0 and 8.5 <= vy <= 15.5:
                    d_wv = _poly_min_dist(vx, vy, WAVE_POLY, closed=False)
                    half_ww = 1.75 * 0.5
                    if d_wv <= half_ww + aa * 0.5:
                        a_wv = max(0.0, min(1.0, (half_ww + aa * 0.5 - d_wv) / aa))
                        pixel = _blend(pixel, (229, 169, 60, int(a_wv * 255)))

            raw.extend(pixel)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")


def build_icons():
    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(SVG_PATH, "w", encoding="utf-8") as f:
        f.write(ICON_SVG)

    sizes = [48, 256]
    pngs = [_render_png(s) for s in sizes]
    header = struct.pack("<HHH", 0, 1, len(sizes))
    offset = 6 + 16 * len(sizes)
    entries = []
    for s, data in zip(sizes, pngs):
        wb = 0 if s >= 256 else s
        entries.append(struct.pack("<BBBBHHII", wb, wb, 0, 0, 1, 32, len(data), offset))
        offset += len(data)

    with open(ICO_PATH, "wb") as f:
        f.write(header)
        for e in entries:
            f.write(e)
        for data in pngs:
            f.write(data)

    print(f"Generated icons -> {SVG_PATH}, {ICO_PATH} ({os.path.getsize(ICO_PATH)} bytes)")


if __name__ == "__main__":
    build_icons()
