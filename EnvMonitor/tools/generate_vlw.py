#!/usr/bin/env python3
"""
generate_vlw.py — 自動化 VLW 字體生成工具
從 lang.h 擷取所有使用到的字元，產生 LovyanGFX 相容的 VLW 格式 C Array header。

VLW 二進位格式 (Big-Endian):
  Header  (24 bytes = 6 × uint32):  gCount, version(11), fontSize, mboxY(0), ascent, descent
  Glyphs  (28 bytes × gCount):      unicode, height, width, gxAdvance, dY, dX, padding(0)
  Bitmaps (連續排列):               每個 glyph 的 width×height bytes (8-bit alpha)
"""

import os
import re
import json
import struct
import urllib.request
from PIL import ImageFont

# ==== 設定 ====
FONT_URL = "https://github.com/notofonts/noto-cjk/raw/main/Sans/OTF/TraditionalChinese/NotoSansCJKtc-Medium.otf"
FONT_FILE = "NotoSansCJKtc-Medium.otf"
LANG_H_PATH = "../lang.h"

# 要產生的字體規格
FONTS = [
    {
        "name": "font_ui",
        "size": 20,
        "desc": "UI general text (labels, titles, buttons, values)",
        "chars": "all",       # 全部字元
    },
    {
        "name": "font_big",
        "size": 28,
        "desc": "Large percentage numbers",
        "chars": "digits",    # 只有數字相關
    },
]

DIGIT_CHARS = set("0123456789%.-— ")


def download_font():
    """下載字型檔（若本地已存在則跳過）"""
    if os.path.exists(FONT_FILE):
        print(f"  Font already exists: {FONT_FILE}")
        return
    print(f"  Downloading {FONT_FILE} ...")
    urllib.request.urlretrieve(FONT_URL, FONT_FILE)
    print(f"  Downloaded.")


def extract_chars_from_lang_h():
    """從 lang.h 的 JSON 中擷取所有實際使用到的字元"""
    with open(LANG_H_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # lang.h 使用 C++ raw string literal: = R"json( {JSON} )json"
    # 注意：註解中也有 R"json( 字樣，必須匹配實際賦值的那個
    start_marker = '= R"json('
    end_marker = ')json"'
    si = content.find(start_marker)
    if si < 0:
        print("  ERROR: Cannot find R\"json( in lang.h")
        return set()
    si += len(start_marker)
    ei = content.find(end_marker, si)
    if ei < 0:
        print("  ERROR: Cannot find )json\" in lang.h")
        return set()
    json_str = content[si:ei].strip()
    data = json.loads(json_str)

    chars = set()
    # 遞迴收集所有字串值中的每個字元
    def collect(obj):
        if isinstance(obj, str):
            for c in obj:
                chars.add(c)
        elif isinstance(obj, dict):
            for v in obj.values():
                collect(v)
        elif isinstance(obj, list):
            for v in obj:
                collect(v)
    collect(data)

    # 補上 ASCII 可印刷字元（UI 程式碼中也會用到，如 "%.1f C", "ERR" 等）
    for c in range(32, 127):
        chars.add(chr(c))
    # 度數符號
    chars.add('°')

    return chars


def build_vlw(font_path, font_size, char_set):
    """
    產生符合 LovyanGFX 規範的 VLW 二進位資料。

    格式：
      [Header 24 bytes]  → gCount, version=11, fontSize, mboxY=0, ascent, descent
      [Glyph table]      → 28 bytes × gCount (所有 glyph metadata 先寫完)
      [Bitmap data]      → 所有 glyph 的 bitmap 連續排列
    """
    font = ImageFont.truetype(font_path, font_size)
    ascent, descent = font.getmetrics()

    char_list = sorted(char_set)
    gcount = len(char_list)

    # 收集每個 glyph 的 metadata 和 bitmap
    glyphs = []
    for ch in char_list:
        unicode_val = ord(ch)
        try:
            mask = font.getmask(ch, mode="L")
            w, h = mask.size
            bbox = font.getbbox(ch)
            if bbox:
                left, top, right, bottom = bbox
            else:
                left, top = 0, 0
            advance_x = int(font.getlength(ch))
            # dY = distance from baseline to top of glyph (topExtent)
            dy = ascent - top
            # dX = left bearing
            dx = left
            bitmap = bytes(mask) if w * h > 0 else b""
        except Exception:
            w, h, advance_x, dy, dx = 0, 0, font_size, 0, 0
            bitmap = b""

        glyphs.append({
            "unicode": unicode_val,
            "height": h,
            "width": w,
            "advance": advance_x,
            "dy": dy,
            "dx": dx,
            "bitmap": bitmap,
        })

    # === 組裝 VLW binary ===
    vlw = bytearray()

    # 1. Header (24 bytes = 6 × uint32, big-endian)
    vlw.extend(struct.pack(">IIIIII", gcount, 11, font_size, 0, ascent, descent))

    # 2. Glyph metadata table (28 bytes × gcount, ALL headers first)
    for g in glyphs:
        vlw.extend(struct.pack(">IIIIiii",
                               g["unicode"], g["height"], g["width"],
                               g["advance"], g["dy"], g["dx"], 0))

    # 3. Bitmap data (ALL bitmaps, in same order as metadata)
    for g in glyphs:
        vlw.extend(g["bitmap"])

    return vlw, gcount


def write_c_header(vlw_data, name, desc, font_size, gcount, output_path):
    """將 VLW 資料寫成 C header 檔"""
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("#pragma once\n")
        f.write("#include <stdint.h>\n")
        f.write(f"// {desc}\n")
        f.write(f"// Noto Sans TC Medium, {font_size}px, {gcount} glyphs, {len(vlw_data)} bytes\n")
        f.write(f"const uint8_t {name}[{len(vlw_data)}] PROGMEM = {{\n")

        for i in range(0, len(vlw_data), 16):
            chunk = vlw_data[i:i+16]
            hex_str = ", ".join(f"0x{b:02X}" for b in chunk)
            trailing = "," if i + 16 < len(vlw_data) else ""
            f.write(f"    {hex_str}{trailing}\n")

        f.write("};\n")
    print(f"  Wrote {output_path} ({len(vlw_data)} bytes, {gcount} glyphs)")


def main():
    print("=== VLW Font Generator ===\n")

    # Step 1: Download font
    print("[1/3] Font file")
    download_font()

    # Step 2: Extract characters
    print("\n[2/3] Extracting characters from lang.h")
    all_chars = extract_chars_from_lang_h()
    print(f"  Found {len(all_chars)} unique characters")

    # Step 3: Generate fonts
    print("\n[3/3] Generating VLW fonts")
    for spec in FONTS:
        print(f"\n  --- {spec['name']} ({spec['size']}px) ---")
        if spec["chars"] == "all":
            char_set = all_chars
        elif spec["chars"] == "digits":
            char_set = DIGIT_CHARS
        else:
            char_set = set(spec["chars"])

        vlw, gcount = build_vlw(FONT_FILE, spec["size"], char_set)
        output_path = f"../{spec['name']}.h"
        write_c_header(vlw, spec["name"], spec["desc"], spec["size"], gcount, output_path)

    # Validate format
    print("\n=== Validation ===")
    for spec in FONTS:
        path = f"../{spec['name']}.h"
        if spec["chars"] == "all":
            n = len(all_chars)
        else:
            n = len(DIGIT_CHARS)
        expected_metadata_end = 24 + 28 * n
        print(f"  {spec['name']}: header=24 bytes, metadata={28*n} bytes, "
              f"bitmap starts at offset {expected_metadata_end}")

    print("\nDone!")


if __name__ == "__main__":
    main()
