# Photo AI check (October 2026)

Which on-device AI can read a screenshot or a photo and answer about it in Bahasa Melayu, on phones like the
Galaxy A51 (4–6 GB, no dot-product) and A15?

## How it was tested

- 9 tasks in `tools/vision-eval/tasks.json`, all with the app's BM system prompt:
  - 5 screenshots drawn by `make_images.py`: a water cut notice, a mamak receipt (total and one price), a WhatsApp
    group chat (summary in BM, one question in English), a "Storage almost full" warning, and a spending chart.
  - 3 photos from Wikimedia Commons (`fetch_photos.py`): nasi lemak, durian, a Kuala Lumpur street (the search
    returned a heron, which still tests "describe this").
- `tools/vision-eval/vision_eval.cpp` formats, encodes and samples like the app (llama.cpp `mtmd`, same version,
  same chat formatting, temperature 0.7). `.github/workflows/vision-eval.yml` runs each model in `models.json` on
  GitHub's servers and commits the replies to `tools/vision-eval/results/`. Times are from a 4-thread x86 server:
  only useful to compare models with each other. A phone is several times slower.
- "+sailor2" rows: the vision AI describes the image in English, then Sailor2 1B answers in BM from that text.
- Replies were read and graded by hand.

## Round 1

| Model (+ image encoder) | Size | Image tokens / encode | Reading | Malay |
|---|---|---|---|---|
| **Gemma-SEA-LION v4.5 E2B** + its F16 encoder | 3.2 + 0.9 GB | 220–570 / 10–33 s | All screenshots right, recognised nasi lemak and durian. | Natural BM. Writes English notes before the answer (the app hides them, see round 2). |
| Gemma-SEA-LION v4 4B VL | 2.3 + 0.8 GB | 256 / 45 s | All right except calling nasi lemak "nasi ayam". | Excellent. Too slow to encode for a 4–6 GB phone. |
| Gemma 3 4B | 2.3 + 0.8 GB | 256 / 47 s | Good. | Good, slower replies. |
| **Qwen3-VL 2B** (Q8_0) | 1.7 + 0.4 GB | ~240 / 7 s | Screenshots read accurately, photos described well. | Mostly Malaysian BM with some Indonesian words (`Anda`, `bagian`). |
| Qwen3-VL 2B + Sailor2 | 1.7 + 0.4 + 0.7 GB | ~240 / 11 s | Sailor2 invents details (wrong receipt total, wrong chart month). | Good BM, wrong facts. |
| LFM2-VL 1.6B (+ Sailor2) | 1.2 + 0.5 GB | 240–1780 / up to 77 s | Wrong chart and storage answers. | Indonesian. |
| InternVL3 1B | 0.6 + 0.3 GB | 1280–2560 / 38–78 s | Many wrong answers ("bunga mallowa" for durian). | Indonesian. |
| LFM2-VL 450M, SmolVLM2 500M + Sailor2 | 0.4 + 0.1 GB | | Gibberish or invented. | |
| Gemma 3n E2B, Gemma 4 E2B (Google) | | | Not run: no image encoder published for llama.cpp at the time / failed to download. | |

## Round 2

The app's actual setup: "Normal" photo detail (at most 280 image tokens), empty thought block for SEA-LION,
Qwen3-VL 2B at Q4_K_M, and Gemma 4's smaller Q8_0 encoder tried with SEA-LION. See `results/*-280.jsonl`.

| Model (+ encoder) | Size | Image tokens / encode | Reply speed | Result |
|---|---|---|---|---|
| **SEA-LION v4.5 E2B** + its F16 encoder | 3.2 + 0.9 GB | 220–270 / 10–12 s | 16 tok/s | Answers after `<channel|>` all right and in natural BM. Still writes 100–250 tokens of English notes first despite the empty thought block, so the app hides everything before `<channel|>` and waits for the answer. On a phone the notes add real waiting time. |
| SEA-LION v4.5 E2B + Gemma 4 Q8_0 encoder | 3.2 + 0.5 GB | 220–270 / 11–14 s | 16 tok/s | Works, but its notes mistook the durian for a young coconut. Kept the F16 encoder. |
| **Qwen3-VL 2B** Q4_K_M + Q8_0 encoder | 1.0 + 0.4 GB | 250–270 / 10–11 s | 23 tok/s | Short, direct BM answers. Right: water notice, teh tarik price, receipt total, chart month, durian, storage advice. Wrong: meeting time (said 3.30 instead of 3), why Raj can't come (said deadline, not a sick child), called nasi lemak "nasi goreng", heron called "burung kuda". Some Indonesian (`Anda`, `file`). |

## Choices in the app

- **AI Pintar / Smart**: Gemma-SEA-LION v4.5 E2B sees photos itself, with AI Singapore's encoder (+0.9 GB).
- **AI Ringan / Light** (and the Advanced-only small AIs): they can't see, so the photo add-on brings
  **Qwen3-VL 2B** (Q4_K_M, Apache 2.0) with its encoder (about 1.5 GB together). A chat with a photo switches to it,
  and follow-up questions in that chat stay with it. Its Indonesian words are the known weakness.
- **Photo detail** (Advanced): Fast 140 / Normal 280 / Detailed 560 image tokens. Fewer tokens = faster on old phones.
