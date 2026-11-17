#!/usr/bin/env python3
"""Gera o catálogo compacto de troca de exercício embarcado no app.

H3-44: o app precisa listar substitutos offline, antes de qualquer download.
O backend é a fonte de verdade; este script deriva um asset com os campos da
troca (id, nome, músculos, equipamento) e da mídia do detalhe (instruções EN/PT,
paths de imagem, nome em PT) — bug relatado por usuário: o substituto escolhido
na troca abria o detalhe sem imagem e sem descrição quando o catálogo do
backend nunca foi baixado, porque a troca "Só hoje" não tem mídia embutida no
plano para cobrir.

Uso:
    python3 scripts/gen-swap-catalog.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "back" / "app" / "exercise_catalog.json"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "exercise_swap_catalog.json"
VERSION = 1


def build(catalog: list[dict]) -> dict:
    exercises = []
    seen: set[str] = set()
    for raw in catalog:
        exercise_id = (raw.get("id") or "").strip()
        name = (raw.get("name") or "").strip()
        muscles = [
            str(m).strip()
            for m in (raw.get("primaryMuscles") or [])
            if str(m).strip()
        ]
        if not exercise_id or not name or not muscles or exercise_id in seen:
            continue
        seen.add(exercise_id)
        exercises.append(
            {
                "id": exercise_id,
                "name": name,
                "namePt": (raw.get("namePt") or "").strip(),
                "primaryMuscles": muscles,
                "equipment": (raw.get("equipment") or "").strip(),
                "instructions": [
                    str(step).strip()
                    for step in (raw.get("instructions") or [])
                    if str(step).strip()
                ],
                "instructionsPt": [
                    str(step).strip()
                    for step in (raw.get("instructionsPt") or [])
                    if str(step).strip()
                ],
                "images": [
                    str(image).strip()
                    for image in (raw.get("images") or [])
                    if str(image).strip()
                ],
            }
        )
    exercises.sort(key=lambda item: item["id"])
    return {"version": VERSION, "exercises": exercises}


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"ERRO: catálogo de origem não encontrado: {SOURCE}")
    catalog = json.loads(SOURCE.read_text(encoding="utf-8"))
    if not isinstance(catalog, list):
        raise SystemExit("ERRO: exercise_catalog.json deve ser uma lista.")
    payload = build(catalog)
    if not payload["exercises"]:
        raise SystemExit("ERRO: nenhum exercício válido encontrado no catálogo.")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(f"OK: {len(payload['exercises'])} exercícios em {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()