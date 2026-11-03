# <ID> — <Título curto>

> **Template obrigatório** para itens do [ROADMAP](../../ROADMAP.md).
> Copie para `docs/specs/<ID>-<slug>.md`, preencha e remova as instruções em
> itálico. A spec é o contrato: se algo não está aqui, não está no escopo.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `<ID>` |
| MoSCoW | `M` / `S` / `C` / `W` |
| Spec aprovada em | `AAAA-MM-DD` |
| Implementação | `pendente` / `em andamento` / `concluída` |

## Objetivo

*Uma frase sobre o resultado para o usuário. O “porquê”, não a solução.*

## Fora de escopo

*Liste o que alguém poderia achar que faz parte, mas não faz. Isso evita scope
creep de modelo.*

- ...
- ...

## Arquivos e símbolos que devem mudar

*Caminhos exatos. Se não tiver certeza, investigue antes de escrever a spec.*

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/...` | ... |
| `app/src/test/java/...` | ... |

## Modelo de dados / contrato

*Se houver JSON, schema, DTO, endpoint ou migration, escreva o contrato aqui.
Inclua versão e compatibilidade. Para mudança de Room, informe se há migration
e como ela é testada.*

```text
...
```

## Regras e casos de borda

*Regras determinísticas. Cada regra vira um teste. Inclua entradas vazias,
inválidas, duplicadas, limites e rollback.*

- ...
- ...

## Critérios de aceite

*Formato Given/When/Then. O agente deve conseguir provar cada um.*

1. **Dado** ... **quando** ... **então** ...
2. **Dado** ... **quando** ... **então** ...

## Testes a criar

*Arquivo + nomes dos testes. Testes de lógica pura em `app/src/test`; backend em
`back/tests`. UI/instrumentado só se a spec exigir.*

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../XTest.kt` | `caso_a`, `caso_b` |
| `back/tests/test_x.py` | `test_caso_a` |

## Verificação manual

*Passos no device/emulador, quando houver UI. Inclua o caminho de erro.*

1. ...
2. ...

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

*O que pode dar errado e como reverter. Para dados, diga o que acontece se a
importação falhar no meio.*
