# H3-59 — Wear: i18n completa e TalkBack

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-59` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `pendente` |

**Origem:** fora de escopo declarado da H3-34/H3-37 (que cobriram só o
celular) + varredura de polimento de 2026-11-13. O wear tem `strings.xml`
com 10 chaves, mas `WorkoutWearActivity.kt` mostra literais em português
("Treino Concluído!", "Sem conexão", "Série X / Y", "Alvo:", "Última:",
"Descanso:", "Concluir Série", "Atualizar") e não declara rótulos para
TalkBack.

## Objetivo

O relógio respeita o idioma do sistema (PT/EN) e é navegável por TalkBack.

## Fora de escopo

- Seletor de idioma no relógio (segue o sistema, como o padrão Android).
- Novas telas/funcionalidades no wear; backend.
- TalkBack avançado (anúncio a cada tick de segundo — avaliar custo na
  implementação; mínimo: rótulos e estados anunciáveis).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | literais → `stringResource`/`getString`; `contentDescription` nos botões e estados ("Concluir série", "Sem conexão", timer) |
| `wear/src/main/res/values/strings.xml` | chaves novas (PT = texto atual) |
| `wear/src/main/res/values-en/strings.xml` (novo) | mesmas chaves em inglês |
| `wear/src/test/java/com/treinos/wear/ui/WearStringParityTest.kt` (novo) | paridade de chaves PT/EN (o módulo wear já tem testes JVM) |

## Modelo de dados / contrato

Sem schema. Contrato de chaves: toda chave existe nos dois `values` com o
mesmo nome; interpolações ("Série %1$d / %2$d") com placeholders posicionais.

## Regras e casos de borda

- Nenhuma literal PT visível no wear; strings nos dois `values`.
- Todo elemento clicável tem `contentDescription` ou texto anunciável; os
  estados de conexão ("Sem conexão") são anunciáveis.
- Idioma do wear = idioma do sistema do relógio (sem seletor).
- PT permanece byte a byte igual ao atual.

## Critérios de aceite

1. **Dado** relógio em inglês, **quando** abrir o fluxo de treino, **então**
   todos os textos estão em inglês.
2. **Dado** TalkBack ativo, **quando** navegar por todos os controles,
   **então** cada um anuncia rótulo/estado.
3. **Dado** PT no relógio, **quando** abrir, **então** textos idênticos aos
   atuais.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `wear/src/test/java/com/treinos/wear/ui/WearStringParityTest.kt` | `toda_chave_pt_existe_em_en` (mesmo padrão do `StringParityTest` do app) |

## Verificação manual

1. Mudar o idioma do relógio para inglês → percorrer o fluxo (início,
   séries, descanso, concluído, sem conexão).
2. TalkBack no relógio → navegar por todos os controles.
3. Voltar para PT → nenhuma mudança de copy.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Baixo: strings + `contentDescription`. Rollback por `git revert`.
