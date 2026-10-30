"""Downloads the photo test images from Wikimedia Commons (freely licensed) into images/,
and records each file's title, author and licence in results/photos.json."""
import json
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
PHOTOS = {
    "photo-nasi-lemak.jpg": "nasi lemak sambal telur",
    "photo-durian.jpg": "durian fruit opened",
    "photo-jalan.jpg": "Kuala Lumpur street",
}
UA = {"User-Agent": "AiSendiriVisionEval/1.0 (https://github.com/khairilaswan/aisendiri-com)"}


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


credits = {}
for name, query in PHOTOS.items():
    q = urllib.parse.urlencode({
        "action": "query", "format": "json", "generator": "search", "gsrnamespace": 6, "gsrlimit": 5,
        "gsrsearch": f"filetype:bitmap {query}", "prop": "imageinfo", "iiprop": "url|extmetadata|mime",
        "iiurlwidth": 1024,
    })
    pages = json.loads(get(f"https://commons.wikimedia.org/w/api.php?{q}"))["query"]["pages"]
    page = next(p for p in sorted(pages.values(), key=lambda p: p["index"])
                if p["imageinfo"][0]["mime"] == "image/jpeg")
    info = page["imageinfo"][0]
    (HERE / "images" / name).write_bytes(get(info["thumburl"]))
    meta = info.get("extmetadata", {})
    credits[name] = {
        "title": page["title"],
        "url": info["descriptionurl"],
        "author": meta.get("Artist", {}).get("value"),
        "licence": meta.get("LicenseShortName", {}).get("value"),
    }
    print(name, credits[name])
(HERE / "results").mkdir(exist_ok=True)
(HERE / "results" / "photos.json").write_text(json.dumps(credits, indent=1, ensure_ascii=False))
