"""Sentence embeddings with ONNX Runtime — the same model file the Kotlin server loads at query time.

tokenize (HF tokenizers) -> transformer (ONNX) -> mean pooling over real tokens -> L2 normalise.
That is exactly what sentence-transformers does for paraphrase-multilingual-MiniLM-L12-v2, without
needing PyTorch (~1 GB) installed.
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

import numpy as np

MODEL_REPO = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
# int8-quantised export from the model repo: 118 MB instead of 470 MB, same 384-d space.
DEFAULT_ONNX_FILE = "onnx/model_qint8_arm64.onnx"
MAX_TOKENS = 128
DIMENSIONS = 384


def default_model_dir() -> Path:
    return Path.home() / ".cache" / "athena-mcp" / "models" / "paraphrase-multilingual-MiniLM-L12-v2"


def download_model(dest: Path | None = None, onnx_file: str = DEFAULT_ONNX_FILE) -> Path:
    """Fetch model.onnx + tokenizer.json into `dest` (no PyTorch, no duplicate HF cache copy)."""
    from huggingface_hub import HfApi, hf_hub_download

    dest = dest or default_model_dir()
    dest.mkdir(parents=True, exist_ok=True)
    onnx_path = Path(hf_hub_download(MODEL_REPO, onnx_file, local_dir=dest))
    shutil.move(onnx_path, dest / "model.onnx")
    hf_hub_download(MODEL_REPO, "tokenizer.json", local_dir=dest)
    shutil.rmtree(dest / "onnx", ignore_errors=True)
    shutil.rmtree(dest / ".cache", ignore_errors=True)
    revision = HfApi().model_info(MODEL_REPO).sha
    (dest / "MODEL_INFO.json").write_text(json.dumps(
        {"repo": MODEL_REPO, "file": onnx_file, "revision": revision, "max_tokens": MAX_TOKENS}, indent=2))
    return dest


def mean_pool(token_vectors: np.ndarray, attention_mask: np.ndarray) -> np.ndarray:
    """[batch, seq, hidden] x [batch, seq] -> [batch, hidden], averaging only unmasked tokens."""
    mask = attention_mask[..., None].astype(np.float32)
    summed = (token_vectors * mask).sum(axis=1)
    counts = np.clip(mask.sum(axis=1), 1e-9, None)
    return summed / counts


def l2_normalise(vectors: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    return vectors / np.clip(norms, 1e-12, None)


class OnnxEmbedder:
    def __init__(self, model_dir: Path | None = None, max_tokens: int = MAX_TOKENS):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        model_dir = model_dir or default_model_dir()
        model_file = model_dir / "model.onnx"
        if not model_file.exists():
            candidates = sorted(model_dir.glob("*.onnx"))
            if len(candidates) != 1:
                raise FileNotFoundError(f"No model.onnx in {model_dir}; run `athena-ingest download-model`")
            model_file = candidates[0]
        self.name = f"onnx:{model_dir.name}/{model_file.name}"
        self.session = ort.InferenceSession(str(model_file), providers=["CPUExecutionProvider"])
        self.input_names = {i.name for i in self.session.get_inputs()}
        self.tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        self.tokenizer.enable_truncation(max_length=max_tokens)
        self.tokenizer.no_padding()

    def embed(self, texts: list[str]) -> np.ndarray:
        """One text per model run, never padded.

        The int8 model quantises activations dynamically, with one scale for the whole input tensor,
        so padding a batch shifts every vector in it (cosine ~0.992-0.997 vs. unbatched). The Kotlin
        server embeds queries one at a time, so the stored vectors must be made the same way.
        `OnnxParityTest` (Kotlin) guards this.
        """
        out = []
        for encoding in self.tokenizer.encode_batch(texts):
            ids = np.array([encoding.ids], dtype=np.int64)
            mask = np.array([encoding.attention_mask], dtype=np.int64)
            feeds = {"input_ids": ids, "attention_mask": mask}
            if "token_type_ids" in self.input_names:
                feeds["token_type_ids"] = np.array([encoding.type_ids], dtype=np.int64)
            token_vectors = self.session.run(None, feeds)[0]
            out.append(l2_normalise(mean_pool(token_vectors, mask)))
        return np.vstack(out) if out else np.zeros((0, DIMENSIONS), dtype=np.float32)
