"""Downloads one candidate vision AI (model + image encoder "mmproj") and runs the test images
through vision-eval. Used by .github/workflows/vision-eval.yml."""
import fnmatch
import json
import subprocess
import sys
from pathlib import Path

from gguf import GGUFReader
from huggingface_hub import HfApi, hf_hub_download

HERE = Path(__file__).parent
model_id, eval_bin = sys.argv[1:3]
models = json.loads((HERE / "models.json").read_text())
spec = models[model_id]
if "same_as" in spec:
    spec = {**models[spec["same_as"]], **spec}
api = HfApi()
work = Path("work")
work.mkdir(exist_ok=True)

# Smaller image encoders first: they load faster and use less memory on a phone.
MMPROJ_PATTERNS = ["mmproj*q8_0*.gguf", "mmproj*f16*.gguf", "mmproj*bf16*.gguf", "mmproj*.gguf", "*mmproj*.gguf"]


def find(repos, patterns, exclude_mmproj):
    for repo in repos:
        try:
            files = api.list_repo_files(repo)
        except Exception as e:  # repo missing or gated
            print(f"skip {repo}: {e}")
            continue
        print(f"{repo}: {[f for f in files if f.endswith('.gguf')]}")
        for pattern in patterns:
            matches = sorted(f for f in files if fnmatch.fnmatch(f.lower(), pattern.lower())
                             and not (exclude_mmproj and "mmproj" in f.lower()))
            if matches:
                print(f"using {repo}/{matches[0]}")
                return repo, Path(hf_hub_download(repo, matches[0], local_dir=work))
    return None, None


def template_hint(path):
    try:
        field = GGUFReader(str(path)).fields.get("tokenizer.chat_template")
        t = bytes(field.parts[field.data[0]]).decode() if field else ""
    except Exception as e:
        return f"unreadable: {e}"
    return t[:200] + " ... " + t[-300:]


repo, model = find(spec["repos"], spec["file"], exclude_mmproj=True)
if model is None:
    sys.exit(f"no GGUF found for {model_id}")
mm_repo, mmproj = find(spec.get("mmproj_repos", [repo]), MMPROJ_PATTERNS, exclude_mmproj=False)
if mmproj is None:
    sys.exit(f"no mmproj found for {model_id}")

args = [eval_bin, str(model), str(mmproj), str(HERE / "tasks.json"), str(HERE / "results" / f"{model_id}.jsonl")]
text = None
if spec.get("describe"):
    _, text = find(["bartowski/Sailor2-1B-Chat-GGUF"], ["*Q4_K_M.gguf"], exclude_mmproj=True)
    args += ["--describe", str(text)]
if spec.get("max_image_tokens"):
    args += ["--max-image-tokens", str(spec["max_image_tokens"])]
if spec.get("no_think"):
    args.append("--no-think")
(HERE / "results").mkdir(exist_ok=True)
subprocess.run(args, check=True)
(HERE / "results" / f"{model_id}.meta.json").write_text(json.dumps({
    "model": f"{repo}/{model.name}",
    "model_mb": round(model.stat().st_size / 1024 / 1024),
    "mmproj": f"{mm_repo}/{mmproj.name}",
    "mmproj_mb": round(mmproj.stat().st_size / 1024 / 1024),
    "text_model": text.name if text else None,
    "template": template_hint(model),
}, indent=1, ensure_ascii=False))
