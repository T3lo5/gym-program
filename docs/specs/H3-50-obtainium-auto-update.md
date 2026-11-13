# H3-50 — Auto-update do celular via Obtainium (documentação)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-50` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

## Objetivo

O celular atualiza o app sozinho a partir do GitHub Releases, sem computador e
sem Play Store, usando o Obtainium.

## Contexto

O workflow já publica o release rolling `latest` com assets de nome estável
(`treinos.apk`, `wear.apk`, SHA-256 individuais e combinado) — exatamente o que
o Obtainium precisa para acompanhar um repositório. O manual já aponta o link
direto `.../releases/latest/download/treinos.apk` (linha ~105). Falta
documentar o Obtainium como caminho de atualização automática.

## Fora de escopo

- Qualquer código (celular, backend, CI) — mudança só de documentação.
- F-Droid, Play Store, APKMirror ou repositório próprio de APKs.
- In-app updater (H3-51).
- Assinatura estável (H3-48 — pré-requisito para o update por cima funcionar).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `docs/MANUAL.md` | nova seção "Atualização automática (Obtainium)": instalar o Obtainium, adicionar o app pela URL do repositório, selecionar o asset `treinos.apk`, habilitar a verificação periódica; nota de que updates instalam por cima sem desinstalar (após a H3-48) |

## Modelo de dados / contrato

Nenhum.

## Regras e casos de borda

- Documentar com o asset estável `treinos.apk` (não o pacote combinado).
- Mencionar que o Obtainium pode exigir permissão de "instalar apps
  desconhecidos" e que a verificação periódica depende do fabricante (bateria/
  restrições de segundo plano).
- Não prometer comportamento que o Obtainium não tem (ex.: delta updates,
  rollback de versão).

## Critérios de aceite

1. **Dado** a seção nova do manual, **quando** seguida em um celular limpo,
   **então** o Obtainium passa a acompanhar o release `latest` do repositório e
   oferece a atualização quando há release novo.
2. **Dado** release novo publicado, **quando** o Obtainium verifica, **então**
   baixa `treinos.apk` e instala por cima sem desinstalar (depende da H3-48).

## Testes a criar

Nenhum (mudança de documentação).

| Arquivo | Testes |
|---|---|
| — | — |

## Verificação manual

1. Seguir a seção nova do manual em um celular real e confirmar que o
   Obtainium detecta o release `latest`.
2. Publicar (ou simular) um release novo e confirmar que o Obtainium oferece a
   atualização.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Docs-only; rollback trivial.
- Risco: o Obtainium mudar de interface — a seção descreve o fluxo conceitual
  (URL do repositório + asset estável), não capturas de tela.
