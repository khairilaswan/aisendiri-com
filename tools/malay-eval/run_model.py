"""Downloads one candidate model, converting it to Q4_K_M GGUF if no ready-made file exists,
then runs the prompt set through malay-eval. Used by .github/workflows/malay-eval.yml."""
import fnmatch
import json
import subprocess
import sys
from pathlib import Path

from huggingface_hub import HfApi, hf_hub_download, snapshot_download

HERE = Path(__file__).parent
model_id, eval_bin, llama_src, quantize_bin = sys.argv[1:5]
spec = json.loads((HERE / "models.json").read_text())[model_id]
api = HfApi()
work = Path("work")
work.mkdir(exist_ok=True)


def find_gguf():
    for repo in spec.get("repos", []):
        try:
            files = api.list_repo_files(repo)
        except Exception as e:  # repo missing or gated
            print(f"skip {repo}: {e}")
            continue
        matches = sorted(f for f in files if fnmatch.fnmatch(f.lower(), spec["file"].lower()))
        if matches:
            print(f"using {repo}/{matches[0]}")
            return Path(hf_hub_download(repo, matches[0], local_dir=work))
    return None


def convert():
    src = spec["convert_from"]
    print(f"converting {src}")
    hf_dir = Path(snapshot_download(src, local_dir=work / "hf",
                                    allow_patterns=["*.json", "*.safetensors", "*.model", "*.txt", "*.jinja"]))
    f16 = work / "model-f16.gguf"
    subprocess.run([sys.executable, f"{llama_src}/convert_hf_to_gguf.py", str(hf_dir), "--outtype", "f16",
                    "--outfile", str(f16)], check=True)
    q4 = work / "model-Q4_K_M.gguf"
    subprocess.run([quantize_bin, str(f16), str(q4), "Q4_K_M"], check=True)
    return q4


gguf = find_gguf() or (convert() if "convert_from" in spec else None)
if gguf is None:
    sys.exit(f"no GGUF found for {model_id}")
print(f"size: {gguf.stat().st_size / 1024 / 1024:.0f} MB")

out = HERE / "results" / f"{model_id}.jsonl"
out.parent.mkdir(exist_ok=True)
args = [eval_bin, str(gguf), str(HERE / "prompts.json"), str(out)]
if spec.get("no_think"):
    args.append("--no-think")
subprocess.run(args, check=True)
(HERE / "results" / f"{model_id}.meta.json").write_text(json.dumps(
    {"file": gguf.name, "size_mb": round(gguf.stat().st_size / 1024 / 1024)}))
