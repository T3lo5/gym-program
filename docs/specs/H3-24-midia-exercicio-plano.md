# H3-24 — Imagens e descrição do exercício: plano como fonte + catálogo resiliente

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-24` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

**Substitui a [H3-14](H3-14-imagens-exercicios.md)** (proposta, nunca
implementada): a H3-14 ligava as miniaturas nos cards; esta spec resolve a
causa de fundo — a mídia que **já viaja no plano** é descartada pelo app, e o
catálogo só existe no aparelho se o backend responder.

## Objetivo

Todo exercício do plano tem imagem e instruções no app, mesmo sem nunca ter
baixado o catálogo — porque o backend já envia a mídia junto com o plano e o
app passa a usá-la.

## Contexto (estado atual verificado)

- **O catálogo é bom:** `back/app/exercise_catalog.json` tem 876 exercícios;
  873 com `images` (paths relativos `X/0.jpg`) e 871 com `instructions`. URL
  montada pelo app testada em 2026-11-07: `HTTP 200, image/jpeg`
  (`IMAGE_BASE_URL` + path, `ExerciseMediaRepository.kt` L50–53).
- **O plano já vem com mídia:** `enrich_plan` (`plan_builder.py` L488–489)
  preenche `instructions` e `images` em **todos** os exercícios do plano (IA e
  fallback) e os DTOs do app têm os campos (`BackendDtos.kt` L345–346) — mas
  **nada no app lê esses campos**. A mídia do plano é descartada no save.
- **Room não persiste mídia:** `WorkoutPlanExerciseEntity`
  (`data/local/Entities.kt` L68–88) não tem colunas de `instructions`/`images`;
  após reiniciar o app, nem a mídia que veio no plano sobrevive.
- **Catálogo depende de backend + botão manual:** `ExerciseMediaRepository`
  só preenche mídia via `GET /v1/catalog` (cache 7 dias, 5 MB); sem backend
  conectado, o mapa fica vazio → placeholder de letra e "Sem instruções
  disponíveis." (`ExerciseDetailSheet.kt` L92–93).
- **Bug de cache:** `fetchFromBackend` retorna `emptyMap()` quando a resposta
  vem vazia e `load` trata como sucesso e **cacheia o catálogo vazio por 7
  dias** (`ExerciseMediaRepository.kt` L62–66 e L78) — uma resposta vazia
  transitória envenena o cache.
- `imageUrlFor` não faz URL-encode (paths hoje limpos; frágil para o futuro).

## Fora de escopo

- Vídeo de exercício (continua fora, como na H3-14 original).
- Tradução das instruções (o catálogo é em inglês; decisão consciente —
  renomear/translate é outro item).
- Editar o catálogo pelo app.
- Incluir `instructions`/`images` no backup JSON ou no sync (a mídia é
  derivável do `exerciseId` via catálogo; inflar o arquivo de backup não paga
  o custo — documentado na spec).
- Baixar as imagens para armazenamento local (Coil já cacheia em disco).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `WorkoutPlanExerciseEntity` ganha `instructions: String = ""` (passos separados por `\n`) e `images: String = ""` (paths separados por `,`) — colunas aditivas |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `version = 4` + `MIGRATION_3_4` (ALTER TABLE ADD COLUMN ×2, nulas/defaults) |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Mapper plano↔entidade persiste os dois campos (L136 e L462); leitura expõe os campos na entidade de detalhe |
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | `fetchFromBackend` retorna `null` quando `exercises.isEmpty()` (não cacheia vazio); `imageUrlFor` faz URL-encode por segmento do path |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `openExerciseDetail` recebe também a mídia embutida do exercício do plano (fallback do catálogo); `connectDiscoveredBackend`/salvar URL dispara `loadExerciseMedia(false)` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `selectedMedia` resolve: catálogo primeiro; sem entrada no catálogo, usa a mídia do plano (`ExerciseMedia` construído dos campos persistidos) |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Migração 3→4 preservando linhas |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | Cache não envenena; URL-encode |
| `app/src/test/java/com/treinos/app/WorkoutDaoTest.kt` (ou mapper test) | Round-trip dos campos novos |

## Modelo de dados / contrato

```text
Room v3 → v4 (aditivo, sem destruição):
  workout_plan_exercises:
    + instructions TEXT NOT NULL DEFAULT ''   -- passos separados por '\n'
    + images       TEXT NOT NULL DEFAULT ''   -- paths separados por ','

Resolução de mídia na UI (determinística):
  media(exerciseId) = catálogo[exerciseId]  SE existir
                    senão ExerciseMedia(id, name, primaryMuscles, equipment,
                                        instructions = campo do plano,
                                        imageUrl = IMAGE_BASE_URL + primeiro path)
  sem nenhuma das duas ⇒ estado atual (letra + "Sem instruções disponíveis.")

Backup/sync: SEM mudança (mídia não viaja; catálogo recarrega do backend).
```

## Regras e casos de borda

- Migração 3→4 preserva todas as linhas existentes (colunas novas vazias) —
  padrão H1-6/H3-15, com teste.
- Resposta do catálogo vazia **nunca** substitui um cache válido; sem cache
  anterior, o mapa continua vazio (estado atual) e a mídia do plano cobre.
- `instructions` do plano vazio + catálogo sem o ID ⇒ "Sem instruções
  disponíveis." (estado atual mantido, não é erro).
- Paths de imagem com espaço/acento passam a ser URL-encodados por segmento
  (o catálogo atual não tem, mas o encoder não pode quebrar os limpos).
- Recarregar o catálogo ao conectar um backend não bloqueia a UI (mesma
  corrotina de fundo de hoje).
- Exercícios sintéticos (`run_*`/`mobility_*`) não têm mídia no catálogo: a
  tela de corrida/mobilidade continua sem sheet de detalhe (como hoje).

## Critérios de aceite

1. **Dado** um plano gerado (IA ou fallback) e **nenhum** catálogo baixado, **quando** o usuário abre o detalhe de um exercício de força, **então** vê imagem (URL do catálogo montada do campo `images` do plano) e as instruções do plano.
2. **Dado** o app reiniciado (processo morto), **quando** o usuário abre o mesmo plano offline, **então** imagem e instruções continuam disponíveis (persistidas no Room).
3. **Dado** o catálogo baixado, **quando** o detalhe abre, **então** a mídia exibida é a do catálogo (fonte preferencial, inclui músculos/equipamento).
4. **Dado** uma resposta vazia de `GET /v1/catalog` com cache válido anterior, **quando** `load(false)` roda, **então** o cache anterior é mantido (não é substituído por vazio).
5. **Dado** um app na v3 com planos existentes, **quando** a migração 3→4 roda, **então** os planos preservam todos os campos e as colunas novas valem `''`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_3_4_preserva_linhas` |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | `catalogo_vazio_nao_substitui_cache_valido`, `url_encode_por_segmento`, `media_do_plano_quando_catalogo_nao_tem_id` |
| `app/src/test/java/com/treinos/app/WorkoutDaoTest.kt` | `exercicio_do_plano_persiste_instrucoes_e_imagens` (round-trip) |

## Verificação manual

1. Limpar dados do app, desconectar o backend, gerar um plano: abrir o detalhe de um exercício → imagem e instruções aparecem (vindas do plano).
2. Matar o app e reabrir offline: o detalhe continua completo.
3. Conectar o backend e tocar em "Atualizar catálogo": miniaturas passam a usar o catálogo; a mensagem "Catálogo atualizado em ..." aparece.
4. Caminho de erro: com o backend fora e sem cache, o detalhe mostra o estado atual (letra + "Sem instruções disponíveis.") sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Migração de schema Room é o maior risco: colunas aditivas com default vazio,
  teste de migração no gate, sem `fallbackToDestructiveMigration` (padrão já
  consolidado em H1-6/H3-15). Rollback da migração exige voltar o `version`
  e a migration no mesmo commit.
- Instruções em inglês podem incomodar: é o conteúdo do catálogo (fonte
  única); tradução é item separado e não bloqueia este fix.
- Tamanho do banco cresce pouco (texto dos passos + paths); sem impacto no
  backup, que não carrega os campos novos.
