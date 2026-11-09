#!/usr/bin/env python3
"""Gera o catálogo bilíngue PT/EN (H3-36).

Baixa a versão partial-translation do dataset PT-BR (fork CC0 do
free-exercise-db, mantendo `equipment`/`level`/músculos em inglês porque
`_build_pool`, `MUSCLE_ALIASES` e os filtros do complementar comparam valores
em inglês), faz merge por `id` com o catálogo atual e grava `namePt`/
`instructionsPt` ADITIVOS. Nome/instruções EN, campos de filtro e imagens
nunca mudam — o merge só adiciona os campos PT.

Validações (falham sem escrever nada):
- IDs de saída == IDs do catálogo atual (nenhum ID novo/renomeado);
- contagens esperadas: total, com PT e sem tradução (defaults 876/873/3 —
  faltam `Kettlebell_Halo`, `Kettlebell_Halo_With_Overhead_Extension` e
  `Kettlebell_Overhead_Triceps_Extension` no fork);
- zero `namePt`/`instructionsPt` vazio-não-nulo (nunca inventar tradução);
- catálogo de saída idêntico ao atual em todos os campos exceto os PT.

Uso:
  python3 back/tools/build_bilingual_catalog.py                 # baixa e regenera
  python3 back/tools/build_bilingual_catalog.py --source pt.json  # cópia local
  python3 back/tools/build_bilingual_catalog.py --overrides o.json
  python3 back/tools/build_bilingual_catalog.py --strip-pt      # rollback
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional

DEFAULT_URL = (
    "https://raw.githubusercontent.com/gugeldev/exercicios-bd-ptbr/main/"
    "exercises/exercises-ptbr-partial-translation.json"
)
DEFAULT_CATALOG = Path(__file__).resolve().parents[1] / "app" / "exercise_catalog.json"

PT_FIELDS = ("namePt", "instructionsPt")


def _load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _download(url: str) -> Any:
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def _valid_pt_name(name: Any) -> bool:
    return isinstance(name, str) and bool(name.strip())


def _valid_pt_instructions(instructions: Any) -> bool:
    return (
        isinstance(instructions, list)
        and len(instructions) > 0
        and all(_valid_name(step) for step in instructions)
    )


def _valid_name(step: Any) -> bool:
    return isinstance(step, str) and bool(step.strip())


def _with_pt_fields(
    exercise: Dict[str, Any],
    name_pt: Optional[str],
    instructions_pt: Optional[List[str]],
) -> Dict[str, Any]:
    """Reconstrói o exercício com `namePt` após `name` e `instructionsPt`
    após `instructions` — ordem estável, diff mínimo."""
    out: Dict[str, Any] = {}
    for key, value in exercise.items():
        out[key] = value
        if key == "name":
            out["namePt"] = name_pt
        elif key == "instructions":
            out["instructionsPt"] = instructions_pt
    return out


def _strip_pt_fields(exercise: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in exercise.items() if key not in PT_FIELDS}


def _validate(
    original: List[Dict[str, Any]],
    merged: List[Dict[str, Any]],
    expected_total: int,
    expected_pt: int,
    expected_null: int,
) -> None:
    errors: List[str] = []

    original_ids = [ex["id"] for ex in original]
    merged_ids = [ex["id"] for ex in merged]
    if merged_ids != original_ids:
        errors.append(
            "IDs divergiram no merge (ordem/renome/novo): "
            f"original={len(original_ids)} merged={len(merged_ids)}"
        )

    total = len(merged)
    if total != expected_total:
        errors.append(f"total de exercícios {total} != esperado {expected_total}")

    with_pt = sum(1 for ex in merged if ex.get("namePt") is not None)
    if with_pt != expected_pt:
        errors.append(f"exercícios com namePt {with_pt} != esperado {expected_pt}")

    without_pt = sum(1 for ex in merged if ex.get("namePt") is None)
    if without_pt != expected_null:
        errors.append(f"exercícios sem tradução {without_pt} != esperado {expected_null}")

    for ex in merged:
        ex_id = ex.get("id", "?")
        name_pt = ex.get("namePt")
        instructions_pt = ex.get("instructionsPt")
        # Vazio-não-nulo é proibido: ausência de tradução é sempre null.
        if name_pt is not None and not _valid_pt_name(name_pt):
            errors.append(f"{ex_id}: namePt vazio-não-nulo")
        if instructions_pt is not None and not _valid_pt_instructions(instructions_pt):
            errors.append(f"{ex_id}: instructionsPt vazio-não-nulo")
        # Campos de filtro/nome/instruções EN e imagens byte-idênticos.
        source = next((item for item in original if item.get("id") == ex_id), None)
        if source is not None and _strip_pt_fields(ex) != source:
            errors.append(f"{ex_id}: campos não-PT divergiram do catálogo atual")

    if errors:
        raise SystemExit(
            "Validação do catálogo bilíngue falhou:\n  - " + "\n  - ".join(errors)
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--url", default=DEFAULT_URL, help="URL do dataset PT-BR")
    parser.add_argument(
        "--source", type=Path, default=None, help="JSON PT-BR local (pula o download)"
    )
    parser.add_argument(
        "--catalog", type=Path, default=DEFAULT_CATALOG, help="catálogo atual"
    )
    parser.add_argument(
        "--output", type=Path, default=DEFAULT_CATALOG, help="catálogo de saída"
    )
    parser.add_argument(
        "--overrides",
        type=Path,
        default=None,
        help='JSON {"id": {"namePt": ..., "instructionsPt": [...]}} aplicado por último '
        "(correção local de tradução sem esperar o fork)",
    )
    parser.add_argument(
        "--strip-pt",
        action="store_true",
        help="rollback: remove namePt/instructionsPt do catálogo",
    )
    parser.add_argument("--expected-total", type=int, default=876)
    parser.add_argument("--expected-pt", type=int, default=873)
    parser.add_argument("--expected-null", type=int, default=3)
    args = parser.parse_args()

    catalog: List[Dict[str, Any]] = _load_json(args.catalog)
    if not isinstance(catalog, list) or not all(isinstance(ex, dict) for ex in catalog):
        raise SystemExit(f"{args.catalog}: formato inesperado (esperado lista de objetos)")

    if args.strip_pt:
        stripped = [_strip_pt_fields(ex) for ex in catalog]
        leftover = [key for ex in stripped for key in ex if key in PT_FIELDS]
        if leftover:
            raise SystemExit(f"--strip-pt deixou campos PT: {sorted(set(leftover))}")
        args.output.write_text(
            json.dumps(stripped, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        print(f"Catálogo sem campos PT gravado em {args.output} ({len(stripped)} exercícios)")
        return

    if args.source is not None:
        pt_dataset = _load_json(args.source)
    else:
        print(f"Baixando {args.url} ...")
        pt_dataset = _download(args.url)
    if not isinstance(pt_dataset, list):
        raise SystemExit("dataset PT-BR: formato inesperado (esperado lista)")

    pt_by_id: Dict[str, Dict[str, Any]] = {
        ex["id"]: ex for ex in pt_dataset if isinstance(ex, dict) and "id" in ex
    }

    unknown = sorted(set(pt_by_id) - {ex["id"] for ex in catalog})
    if unknown:
        raise SystemExit(
            "dataset PT-BR tem IDs fora do catálogo (renome/versão defasada?): "
            + ", ".join(unknown)
        )

    overrides: Dict[str, Dict[str, Any]] = {}
    if args.overrides is not None:
        raw = _load_json(args.overrides)
        if not isinstance(raw, dict):
            raise SystemExit(f"{args.overrides}: esperado objeto com id -> campos PT")
        overrides = raw

    merged: List[Dict[str, Any]] = []
    for exercise in catalog:
        ex_id = exercise["id"]
        source = pt_by_id.get(ex_id)
        name_pt: Optional[str] = None
        instructions_pt: Optional[List[str]] = None
        if source is not None:
            # Merge aditivo: só os campos PT; o resto vem do catálogo atual.
            if _valid_pt_name(source.get("name")):
                name_pt = source["name"].strip()
            if _valid_pt_instructions(source.get("instructions")):
                instructions_pt = list(source["instructions"])
        override = overrides.get(ex_id) or {}
        if _valid_pt_name(override.get("namePt")):
            name_pt = str(override["namePt"]).strip()
        if _valid_pt_instructions(override.get("instructionsPt")):
            instructions_pt = list(override["instructionsPt"])
        merged.append(_with_pt_fields(exercise, name_pt, instructions_pt))

    _validate(catalog, merged, args.expected_total, args.expected_pt, args.expected_null)

    args.output.write_text(
        json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    with_pt = sum(1 for ex in merged if ex.get("namePt") is not None)
    print(
        f"Catálogo bilíngue gravado em {args.output}: "
        f"{len(merged)} exercícios, {with_pt} com PT, {len(merged) - with_pt} sem tradução"
    )


if __name__ == "__main__":
    main()
