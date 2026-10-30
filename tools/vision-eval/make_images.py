"""Draws the screenshot-style test images in images/. Run once; the PNGs are committed.
Photos come from Wikimedia Commons at test time (see fetch_photos.py)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent / "images"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


def notice():
    im = Image.new("RGB", (720, 1000), "white")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 720, 120), fill="#0b3d91")
    d.text((40, 35), "NOTIS GANGGUAN BEKALAN AIR", font=font(34, True), fill="white")
    y = 170
    for line, size, bold in [
        ("Tarikh: Sabtu, 7 November 2026", 30, True),
        ("Masa: 9.00 pagi hingga 6.00 petang", 30, True),
        ("", 20, False),
        ("Kawasan terlibat:", 28, True),
        ("- Taman Melati, Setapak", 28, False),
        ("- Taman Bunga Raya", 28, False),
        ("- Jalan Genting Klang", 28, False),
        ("", 20, False),
        ("Sebab: Kerja-kerja menukar paip utama.", 26, False),
        ("Pengguna dinasihatkan menyimpan air", 26, False),
        ("secukupnya. Lori air akan disediakan", 26, False),
        ("di Dewan Orang Ramai Taman Melati.", 26, False),
        ("", 20, False),
        ("Talian aduan: 1-800-88-5252", 26, True),
        ("Kami mohon maaf atas kesulitan.", 26, False),
    ]:
        d.text((40, y), line, font=font(size, bold), fill="black")
        y += size + 22
    im.save(OUT / "notis-air.png")


def receipt():
    im = Image.new("RGB", (560, 900), "#fbfaf5")
    d = ImageDraw.Draw(im)
    mono = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 24)
    monob = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 26)
    lines = [
        ("RESTORAN SRI MURNI", monob), ("No 12, Jalan SS2/24, PJ", mono), ("Tel: 03-7877 1234", mono),
        ("", mono), ("Meja: 5      30/10/2026 13:42", mono), ("-" * 34, mono),
        ("Nasi Lemak Ayam      1   9.50", mono), ("Roti Canai           2   3.00", mono),
        ("Teh Tarik            2   5.00", mono), ("Milo Ais             1   3.80", mono),
        ("Mee Goreng Mamak     1   8.00", mono), ("-" * 34, mono),
        ("Jumlah kecil            29.30", mono), ("Caj perkhidmatan 10%     2.93", mono),
        ("JUMLAH               RM 32.23", monob), ("-" * 34, mono), ("Tunai                   50.00", mono),
        ("Baki                    17.77", mono), ("", mono), ("   Terima kasih, sila datang lagi!", mono),
    ]
    y = 40
    for text, f in lines:
        d.text((30, y), text, font=f, fill="#222")
        y += 38
    im.save(OUT / "resit-kedai.png")


def chat():
    im = Image.new("RGB", (720, 1300), "#e5ddd5")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 720, 110), fill="#075e54")
    d.text((30, 30), "Group Projek Bangsar", font=font(32, True), fill="white")
    y = 150

    def bubble(who, text, me=False):
        nonlocal y
        lines = text.split("\n")
        h = 30 + 36 * len(lines) + (0 if me else 34)
        x0, x1 = (200, 690) if me else (30, 560)
        d.rounded_rectangle((x0, y, x1, y + h), radius=16, fill="#dcf8c6" if me else "white")
        ty = y + 15
        if not me:
            d.text((x0 + 18, ty), who, font=font(22, True), fill="#c0392b")
            ty += 34
        for line in lines:
            d.text((x0 + 18, ty), line, font=font(24), fill="black")
            ty += 36
        y += h + 22

    bubble("Aiman", "Guys, meeting dengan client esok\njadi ke? Pukul 3 petang kan?")
    bubble("Mei Ling", "Jadi. But they asked to move to\n3.30pm sebab traffic teruk.")
    bubble("Aiman", "Ok noted. Kat office dia Bangsar\nSouth or kita punya office?")
    bubble("Mei Ling", "Office dia, tingkat 8. Bawa laptop\n& slide deck yg updated tau")
    bubble("", "Ok set. Saya bawa projector\nsekali just in case", me=True)
    bubble("Raj", "Sorry guys, I can't make it esok,\nanak demam. Nanti share notes ya")
    im.save(OUT / "whatsapp.png")


def phone_alert():
    im = Image.new("RGB", (720, 1300), "#1f1f1f")
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((60, 420, 660, 860), radius=30, fill="#2d2d2d")
    d.text((100, 470), "Storage almost full", font=font(34, True), fill="white")
    y = 540
    for line in ["Only 1.2 GB of 64 GB is free.", "Some apps may stop working", "properly and photos may not be",
                 "saved. Free up space now?"]:
        d.text((100, y), line, font=font(26), fill="#dddddd")
        y += 42
    d.text((330, 780), "Later", font=font(28, True), fill="#8ab4f8")
    d.text((460, 780), "Free up", font=font(28, True), fill="#8ab4f8")
    im.save(OUT / "storage-full.png")


def chart():
    im = Image.new("RGB", (800, 600), "white")
    d = ImageDraw.Draw(im)
    d.text((40, 25), "Perbelanjaan Bulanan 2026 (RM)", font=font(30, True), fill="black")
    data = [("Jan", 1850), ("Feb", 2100), ("Mac", 3400), ("Apr", 1950), ("Mei", 2300), ("Jun", 2050)]
    base, top, left = 520, 110, 90
    d.line((left, top, left, base), fill="black", width=2)
    d.line((left, base, 760, base), fill="black", width=2)
    for v in (1000, 2000, 3000):
        yy = base - (base - top) * v / 3600
        d.text((20, yy - 12), str(v), font=font(18), fill="#555")
        d.line((left, yy, 760, yy), fill="#ddd")
    for i, (m, v) in enumerate(data):
        x = left + 30 + i * 110
        yy = base - (base - top) * v / 3600
        d.rectangle((x, yy, x + 70, base), fill="#cc0001" if v == 3400 else "#010066")
        d.text((x + 15, base + 12), m, font=font(22), fill="black")
        d.text((x + 5, yy - 28), str(v), font=font(18), fill="black")
    im.save(OUT / "carta-belanja.png")


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    notice(); receipt(); chat(); phone_alert(); chart()
