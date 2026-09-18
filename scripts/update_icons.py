import re
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
KOTLIN_FILE = PROJECT_ROOT / "app/src/main/java/fr/bsodium/cron/ui/theme" / "MaterialSymbols.kt"
OUTPUT_DIR = PROJECT_ROOT / "app/src/main/res/font"
SOURCE_DIR = PROJECT_ROOT / "tools/fonts"

FONTS_TO_GENERATE = [
    ("Rounded", "MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf", "material_symbols_rounded.ttf"),
    ("Sharp", "MaterialSymbolsSharp[FILL,GRAD,opsz,wght].ttf", "material_symbols_sharp.ttf"),
    ("Outlined", "MaterialSymbolsOutlined[FILL,GRAD,opsz,wght].ttf", "material_symbols_standard.ttf"),
]

def main():
    if not KOTLIN_FILE.exists():
        print(f"⚠️ Warning: Kotlin file not found at {KOTLIN_FILE}. Skipping font subsetting.")
        sys.exit(0)

    # 1. Read unicode codepoints from MaterialSymbols.kt
    with open(KOTLIN_FILE, 'r', encoding='utf-8') as f:
        content = f.read()

    matches = set(re.findall(r'\\u([0-9A-Fa-f]{4})', content))
    if not matches:
        print("⚠️ No Unicode codepoints found in Kotlin file. Skipping font subsetting.")
        sys.exit(0)

    unicodes = ",".join(matches)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # 2. Subset fonts locally using fontTools
    for family, src_name, dest_name in FONTS_TO_GENERATE:
        src_path = SOURCE_DIR / src_name
        dest_path = OUTPUT_DIR / dest_name

        if not src_path.exists():
            print(f"❌ Missing source font file at {src_path}. Please place the source font in tools/fonts/")
            sys.exit(1)

        cmd = [
            sys.executable, "-m", "fontTools.subset",
            str(src_path),
            f"--unicodes={unicodes}",
            f"--output-file={str(dest_path)}",
            "--no-hinting", "--layout-features=*", "--glyph-names", "--recalc-bounds"
        ]

        try:
            subprocess.run(cmd, check=True)
            print(f"⚡ Subset generated successfully for {family} -> {dest_path.name}")
        except subprocess.CalledProcessError as e:
            print(f"❌ Subsetting failed for {family}: {e}")
            sys.exit(1)

if __name__ == "__main__":
    main()
