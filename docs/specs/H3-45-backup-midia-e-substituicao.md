# H3-45 — Backup preserva mídia e a troca de plano volta a refletir na Home

> Evolui [H3-24](H3-24-midia-exercicio-plano.md) (mídia persistida no plano) e
> [H3-15](H3-15-planos-multiplos.md) (planos múltiplos e seleção na Home).
> Corrige dois sintomas encontrados após exportar/importar backup: o plano
> restaurado aparecia sem instruções/imagem, e um novo treino principal não
> substituía o plano restaurado na tela.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-45` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-12` |
| Implementação | `concluída` |

**Depende de:** [H3-24](H3-24-midia-exercicio-plano.md) e
[H3-15](H3-15-planos-multiplos.md).
**Não altera contrato de API/DTO do backend.**

## Objetivo

1. Exportar/importar um backup sem perder `instructions` e `images` dos
   exercícios do plano.
2. Garantir que a mídia salva no plano apareça mesmo quando a entrada do
   catálogo (cache ou asset offline) não tiver instruções/imagem.
3. Depois de restaurar um backup ou gerar um novo plano principal, a Home
   mostrar o plano principal correto em vez de um plano antigo selecionado.

## Fora de escopo

- Incluir imagens/instruções nas sessões do histórico (`SessionExerciseEntity`
  não guarda esses campos; o detalhe continua resolvendo pelo plano/catálogo).
- Mudar o schema do backend ou o contrato de sync.
- Backup de preferências (`generateConfigJson`), catálogo de mídia ou usage
  events (H3-42).
- Troca de exercício durante o treino (H3-44).

## Causa raiz (evidência no código)

1. `BackupPlanExercise` não tem `instructions`/`images`
   (`BackupDtos.kt:65-84`); `BackupMapper.toDto`/`toEntity`
   (`BackupMapper.kt:45-65`, `155-175`) também não os copiam. O backup exporta
   só séries/reps/equipamento; ao restaurar, `WorkoutPlanExerciseEntity`
   volta com as colunas vazias (`Entities.kt:88-94`).
2. `resolveExerciseMedia` retorna a entrada do catálogo assim que ela existe,
   sem olhar se ela tem mídia (`ExerciseMediaRepository.kt:345-346`). O asset
   offline da H3-44 tem nome/músculo/equipamento, mas **não** tem
   instruções/imagem; com o cache ausente, ele sombreia a mídia salva no plano.
3. `confirmImport` não limpa `_selectedPlanId`
   (`AppViewModel.kt:1872-1890`). Se a seleção anterior apontava para um plano
   `extra`, a Home continua nele mesmo depois de restaurar/gerar um main; o
   novo main fica ativo, mas não é o plano exibido.
4. `refreshPendingGeneration` não seleciona o plano recém-criado
   (`AppViewModel.kt:952-980`), então a geração de um main pode não aparecer na
   Home quando há um `extra` selecionado.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | `BackupPlanExercise` ganha `instructions: String = ""` e `images: String = ""` (aditivo, sem bump de schema). |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | `toDto`/`toEntity` copiam `instructions` e `images` nos dois sentidos. |
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | `resolveExerciseMedia` passa a fundir catálogo e plano: usa catálogo quando tem nome/músculos/equipamento/instruções/imagem; completa cada campo vazio com o que está no plano. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `confirmImport` limpa `_selectedPlanId`; `refreshPendingGeneration` (e `acceptReview`) selecionam o plano `main` recém-salvo; `extra` preserva a seleção. |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | Round-trip de `instructions`/`images`. |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | Export/import de mídia; backup antigo sem os campos continua válido. |
| `app/src/test/java/com/treinos/app/data/backup/BackupImportTest.kt` | Import preserva instruções/imagens no Room. |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | Catálogo sem mídia não esconde a mídia do plano; catálogo com mídia completa continua vencendo. |
| `app/src/test/java/com/treinos/app/ui/GenerationJobFlowTest.kt` | Geração `main` seleciona o novo plano; `extra` não altera a seleção. |
| `docs/specs/H3-15-planos-multiplos.md` | Registrar que gerar um `main` seleciona o novo plano; `extra` preserva a seleção. |
| `ROADMAP.md` | Adicionar H3-45 à Horizon 3 e ao índice de specs. |

## Modelo de dados / contrato

### Backup (aditivo, `schemaVersion` continua 3)

```json
{
  "schemaVersion": 3,
  "plans": [
    {
      "days": [
        {
          "exercises": [
            {
              "exerciseId": "Barbell_Bench_Press",
              "instructions": "Deite no banco.\nSegure a barra.",
              "images": "Barbell_Bench_Press/0.jpg,Barbell_Bench_Press/1.jpg"
            }
          ]
        }
      ]
    }
  ]
}
```

- `instructions`: passos separados por `\n`, igual ao Room.
- `images`: paths relativos separados por `,`, igual ao Room.
- Campos ausentes em backups antigos decodificam como `""`; não invalidam o
  arquivo e não exigem bump de versão (precedente H3-7/H3-15).

### Resolução de mídia

```text
catálogo tem o id?
  não  → usa a mídia do plano; sem mídia no plano, retorna null
  sim  → começa pela entrada do catálogo e preenche cada campo vazio:
         name        ← catálogo.name        (se vazio, plano)
         muscles     ← catálogo.muscles     (se vazio, plano)
         equipment   ← catálogo.equipment   (se vazio, plano)
         instructions← catálogo.instructions(se vazio, plano)
         imageUrl    ← catálogo.imageUrl    (se vazio, plano)
```

Assim o asset offline (que só tem nome/músculo/equipamento) não apaga a mídia
que veio no plano, e um catálogo completo continua sendo a fonte preferencial.

### Seleção de plano

- Importar backup: `_selectedPlanId = null`; a Home volta ao primeiro `main`
  ativo (ou ao primeiro plano, se não houver main).
- Gerar `main`: seleciona o novo plano (`_selectedPlanId = planId`).
- Gerar `extra`: mantém a seleção atual (H3-15).
- Aceitar revisão (`acceptReview`): seleciona o plano revisado (é um `main`).

## Regras e casos de borda

- Backup antigo sem `instructions`/`images`: importa com campos vazios; a UI
  usa o catálogo (cache/asset) e o fallback do plano quando houver.
- Catálogo completo (cache/backend): continua vencendo; a fusão não degrada.
- Catálogo só com metadados (asset): plano fornece instruções/imagem; se o
  plano também não tiver, a UI mostra "Sem instruções disponíveis.".
- Plano sem mídia e catálogo sem mídia: `null`, como hoje.
- `imageUrl` do catálogo vazio/em branco não bloqueia a imagem do plano.
- Import falha antes de escrever: seleção não muda (a limpeza só ocorre após
  `repository.importBackup` retornar).
- Gerar `main` com falha ao salvar: seleção não muda.
- `extra` não desativa nem seleciona nada: comportamento H3-15 preservado.

## Critérios de aceite

1. **Dado** um plano com instruções e imagens, **quando** exportado e
   importado, **então** o detalhe do exercício mostra as mesmas instruções e a
   mesma imagem.
2. **Dado** um backup antigo sem mídia no exercício, **quando** importado,
   **então** o app não falha e usa o catálogo/fallback disponível.
3. **Dado** um catálogo só com nome/músculo/equipamento (asset offline) e um
   plano com instruções/imagem, **quando** o detalhe abre, **então** as
   instruções e a imagem do plano aparecem.
4. **Dado** um catálogo completo, **quando** o detalhe abre, **então** a mídia
   do catálogo continua sendo usada (regressão da H3-24).
5. **Dado** um plano `extra` selecionado, **quando** um novo `main` é gerado,
   **então** a Home passa a mostrar o novo `main`.
6. **Dado** um plano selecionado, **quando** um `extra` é gerado, **então** a
   seleção não muda.
7. **Dado** um backup importado, **quando** a Home é aberta, **então** o plano
   exibido é o `main` restaurado, não uma seleção antiga.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `BackupMapperTest.kt` | `midia_mapeada_dto_entity`, `midia_ausente_vira_vazio` |
| `BackupSerializationTest.kt` | `midia_round_trip`, `midia_ausente_em_backup_antigo` |
| `BackupImportTest.kt` | `import_preserva_instrucoes_e_imagens` |
| `ExerciseMediaTest.kt` | `catalogo_sem_midia_usa_midia_do_plano`, `catalogo_completo_vence_o_plano` |
| `GenerationJobFlowTest.kt` | `refresh_done_main_seleciona_novo_plano`, `refresh_done_extra_preserva_selecao` |
| `PlanSelectionTest.kt` | (regressão, sem mudança) `selecao_sobrevive_a_geracao` continua válido para a função pura |

## Verificação manual

1. Exportar backup de um plano com instruções/imagem; importar em instalação
   nova/offline; abrir o exercício e conferir texto + imagem.
2. Importar backup antigo (sem mídia) e abrir o exercício: não deve fechar.
3. Com o asset offline (cache limpo), abrir um exercício que veio com mídia no
   plano: instruções/imagem do plano aparecem.
4. Restaurar backup, gerar um novo treino principal e conferir que a Home
   mostra o novo plano.
5. Gerar um complementar e conferir que a seleção não muda.

## Comando de verificação

```bash
./scripts/verify.sh
```

Critério: exit code `0` e linha
`VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`.

## Riscos e rollback

- **Mudança em `resolveExerciseMedia`** pode alterar o que a UI mostra quando o
  catálogo e o plano divergem; a regra "catálogo completo vence" é preservada e
  testada. Rollback = reverter o `copy` e voltar ao `return catalogEntry`.
- **Selecionar o novo main** muda a seleção após geração; a spec H3-15 é
  atualizada e o teste de seleção de `extra` continua cobrindo o caso.
- **Backup aditivo**: app antigo lê o novo backup ignorando os campos extras
  (`ignoreUnknownKeys`); app novo lê backup antigo com default `""`.
- Sem migration de Room e sem mudança de schema.