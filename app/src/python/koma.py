from pathlib import Path

import aggdraw
import cv2
import numpy as np
import numpy.typing as npt
from PIL import Image, ImageDraw, ImageFont

BASE_SIZE = 1024

SIZES = {
    "ldpi": 36,
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

KOMA_LIST = {
    "pawn": "歩兵",
    "lance": "香車",
    "knight": "桂馬",
    "silver": "銀将",
    "gold": "金将",
    "ou": "王将",
    "bishop": "角行",
    "rook": "飛車",
    "king": "玉将",
    "prom_pawn": "と",
    "prom_lance": "成香",
    "prom_knight": "成桂",
    "prom_silver": "成銀",
    "prom_bishop": "龍馬",
    "prom_rook": "竜王",
}

FONT_FILE = "./YujiMai-Regular.ttf"
TARGET_DIR = "../main/res/drawable"

CONTOUR_COLOR = "#333333"
FILL_COLOR = "#ffdead"
SHADOW_COLOR = "#00000033"


def save_image(name: str, img: npt.NDArray[np.uint8]) -> None:
    for dpi, size in SIZES.items():
        res = cv2.resize(img, (size, size), interpolation=cv2.INTER_AREA)
        res = cv2.cvtColor(res, cv2.COLOR_RGBA2BGRA)

        outdir = Path(TARGET_DIR + "-" + dpi)
        outdir.mkdir(parents=True, exist_ok=True)

        outfile = outdir / f"koma_{name}.png"
        cv2.imwrite(str(outfile), res)
        print(f"Saved: {outfile}")


def main():
    # フォントと輪郭の設定 (共通)
    font = ImageFont.truetype(FONT_FILE, int(BASE_SIZE * 0.35))
    contour = np.array(
        [
            (BASE_SIZE * 0.50, BASE_SIZE * 0.10),
            (BASE_SIZE * 0.23, BASE_SIZE * 0.22),
            (BASE_SIZE * 0.15, BASE_SIZE * 0.90),
            (BASE_SIZE * 0.85, BASE_SIZE * 0.90),
            (BASE_SIZE * 0.77, BASE_SIZE * 0.22),
        ],
        dtype="float32",
    )

    # 駒ごとに画像を生成
    for name, kanji in KOMA_LIST.items():
        img = Image.new("RGBA", (BASE_SIZE, BASE_SIZE), 0x00000000)

        # 輪郭線の描画
        d = aggdraw.Draw(img)
        brush = aggdraw.Brush(CONTOUR_COLOR, 128)
        d.polygon(((contour - BASE_SIZE // 2) * 1.05 + BASE_SIZE // 2).flatten(), brush)
        brush = aggdraw.Brush(FILL_COLOR, 255)
        d.polygon(contour.flatten(), brush)
        pen = aggdraw.Pen(CONTOUR_COLOR, 10)
        d.polygon(contour.flatten(), pen)
        d.flush()

        # 駒の文字の描画
        imdraw = ImageDraw.Draw(img)
        text_color = "#dd1100" if name.startswith("prom") else "#000000"
        if len(kanji) == 2:
            imdraw.text(
                (BASE_SIZE // 2, BASE_SIZE * 0.33),
                text=kanji[0],
                font=font,
                fill=text_color,
                anchor="mm",
            )
            imdraw.text(
                (BASE_SIZE // 2, BASE_SIZE * 0.67),
                text=kanji[1],
                font=font,
                fill=text_color,
                anchor="mm",
            )
        else:
            imdraw.text(
                (BASE_SIZE // 2, BASE_SIZE // 2),
                text=kanji,
                font=font,
                fill=text_color,
                anchor="mm",
            )
        img = np.array(img, dtype="uint8")

        # 画像のリサイズと保存
        save_image(name, img)

    # 陰の画像を生成
    img = Image.new("RGBA", (BASE_SIZE, BASE_SIZE), 0x00000000)
    d = aggdraw.Draw(img)
    brush = aggdraw.Brush(SHADOW_COLOR, 128)
    d.polygon(contour.flatten(), brush)
    d.flush()

    img = np.array(img, dtype="uint8")
    img = cv2.GaussianBlur(img, (129, 129), 0)
    save_image("shadow", img)


if __name__ == "__main__":
    main()
