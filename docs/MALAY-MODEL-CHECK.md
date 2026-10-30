# Malay model check (October 2026)

Which small on-device AI answers best in Bahasa Melayu, Manglish and mixed BM/English?

## How it was tested

- 19 prompts in `tools/malay-eval/prompts.json`: formal BM, casual BM, SMS short forms (`x pe la, aku dh mkn td`),
  Manglish, code-switching, translation both ways, Malaysian facts (KWSP vs PERKESO, first PM, capital of Sabah),
  simple money maths, an honesty question (Hari Raya 2027 date), a memory question, and two voice-style prompts.
- `tools/malay-eval/malay_eval.cpp` formats and samples exactly like the app (built-in chat template, same sampler,
  temperature 0.7, 2048 context), using the same llama.cpp version.
- `.github/workflows/malay-eval.yml` runs every model in `models.json` on GitHub's servers and commits the replies
  to `tools/malay-eval/results/`. Speeds there are from an x86 server: only useful to compare models with each other.
- All models at Q4_K_M. Replies were read and graded by hand.

## Results

| Model | Size | Speed (relative) | Malay quality |
|---|---|---|---|
| **Gemma-SEA-LION v4.5 E2B** (AI Singapore) | 3.3 GB | 17 tok/s | Best by far. Natural Malaysian BM and casual tone, all facts right, honest about the Raya 2027 date, clean translations. Sometimes writes `<|im_end|>` as text: the app now cuts the reply there. |
| Gemma 3n E2B | 2.9 GB | 19 | Very good, but some Indonesian words (`bisa`) and a confidently wrong Raya date. |
| Gemma 3 4B | 2.4 GB | 12.5 | Good tone and Manglish, but said the capital of Sabah is Kuala Lumpur. |
| Qwen3 4B 2507 | 2.4 GB | 12.6 | Indonesian words (`kantor`, `silakan`), wrong first PM. |
| **Sailor2 1B** (SEA AI Lab) | 0.7 GB | 40 | Best of the small ones: proper Malaysian BM, never Indonesian, knew KWSP/PERKESO, the first PM and Kota Kinabalu, got the SMS short forms. Weak at maths, often long-winded with headings, sometimes replies in English to Manglish. |
| Gemma 3 1B (old Balanced) | 0.8 GB | 31 | Mixed: invented what KWSP is, said Sabah's capital is "Malaysia", Indonesian in the Manglish answer. |
| Malaysian Qwen2.5 1.5B / 0.5B (mesolitica) | 1.1 / 0.5 GB | 31 / 84 | Very natural Malaysian tone, but many wrong facts and unhelpful answers. |
| MaLLaM 1.1B (mesolitica) | 0.7 GB | 62 | Knows facts, but often repeats the question or ignores the task. |
| SeaLLMs v3 1.5B | 0.9 GB | 34 | Indonesian words, invented facts. |
| Qwen2.5 1.5B (old Smart) | 1.1 GB | 30 | Indonesian (`kantor`, `bisa`), invented facts, refused one Manglish question. |
| Qwen3 1.7B | 1.1 GB | 26 | Indonesian, said Sabah's capital is Singapore, sometimes leaks its `<think>` notes. |
| Qwen2.5 0.5B (old Light) | 0.5 GB | 57 | Mostly Indonesian and often nonsense. |
| Qwen3 0.6B | 0.4 GB | 83 | Nonsense, one answer in Georgian script. |
| Llama 3.2 1B | 0.8 GB | 37 | Almost all Indonesian, wrong facts. |
| Sailor2 3B | 3.1 GB | 8 | Good Malay but floods replies with emoji and headings, cut short 8 of 19 times. |
| Malaysian Llama 3.2 3B | 2.1 GB | 16 | Good casual Malay, too many wrong facts for its size. |

## Defaults in the app

- **AI Ringan / Light: Sailor2 1B** for all phones from about 3 GB RAM (Galaxy A51 and A15 included), and for 6 GB phones.
- **AI Pintar / Smart: Gemma-SEA-LION v4.5 E2B** recommended from 8 GB RAM. 6 GB phones can still pick it ("may be slow").
- Qwen2.5 0.5B, Gemma 3 1B, Qwen2.5 1.5B and Llama 3.2 1B moved to Advanced mode. Phones that already downloaded one keep using it.

## Still to check on a real phone

- Speed of Sailor2 1B on the Galaxy A51 (no dot-product instructions) and the A15.
- Whether SEA-LION E2B loads and stays responsive on an 8 GB phone.
