# H3-44 — Substitutos de exercício disponíveis offline (swap sem "carregando")

> Evolui [H3-41](H3-41-troca-exercicio.md) e a H3-43 (troca na sessão em
> andamento, sem spec formal — ver `ROADMAP.md`): a troca de exercício já
> existe, mas o diálogo depende de um catálogo baixado da rede. Para um app
> *offline-first*, os substitutos precisam estar no aparelho antes de o usuário
> abrir o diálogo.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-44` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-12` |
| Implementação | `concluída` |

**Depende de:** [H3-41](H3-41-troca-exercicio.md) (regras de troca), H3-43
(troca na sessão em andamento, sem spec formal),
[H3-24](H3-24-midia-exercicio-plano.md) e [H1-3](H1-3-exercise-media.md)
(cache do catálogo).
**Não altera contrato de API/DTO do backend.**

## Objetivo

Abrir "Trocar exercício" e ver a lista de substitutos imediatamente, mesmo
sem internet, sem a mensagem "Carregando catálogo de exercícios…" e sem
esperar uma chamada de rede.

## Fora de escopo

- Mudar as regras de validação da troca (músculo, equipamento, duplicidade,
  escopo `THIS_SESSION`/`ALL_FUTURE`) — continuam as da H3-41/H3-43.
- Persistir uma tabela de "substitutos por exercício" no Room. Os candidatos
  são derivados em memória do catálogo local; a fonte de verdade continua
  sendo um único catálogo.
- Incluir imagens/instruções no catálogo embarcado. O asset é compacto e só
  cobre o necessário para listar e aplicar a troca; a mídia completa continua
  vindo do cache/H3-24 quando disponível.
- Corrigir a ausência de filtro por categoria (força vs. alongamento) na
  função de candidatos. É uma questão separada da H3-41; esta spec preserva o
  comportamento atual de filtragem.
- Tela de troca no relógio (Wear OS).

## Causa raiz (evidência no código)

1. `ExerciseMediaRepository.load()` só retorna o cache quando ele está **não
   expirado** (`isExpired()`, TTL de 7 dias). Com o cache expirado, a função
   faz `fetchFromBackend()` antes de devolver qualquer coisa: offline, a UI
   espera o timeout da rede mesmo tendo catálogo válido no aparelho.
2. `AppViewModel.init` carrega o catálogo de forma assíncrona
   (`loadExerciseMedia(false)`) e o diálogo lê `_exerciseMedia.value` na hora
   de abrir. Enquanto a coroutine não termina (ou se o cache não existe), o
   mapa está vazio.
3. `WorkoutScreen` trata `exerciseMedia.isEmpty()` como "carregando" e exibe
   `CircularProgressIndicator` + "Carregando catálogo de exercícios…", sem
   distinguir "ainda não carreguei" de "não tenho catálogo".
4. Não existe catálogo local embarcado: na primeira instalação, sem rede, não
   há nenhum substituto para mostrar.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | Separar leitura local de atualização de rede: `loadLocal()` (cache → asset, sem rede), `mergeLocal()` e `parseSwapCatalog()` puros/testáveis; `load()` passa a usar o local como base e só busca rede para atualizar; TTL vira gatilho de refresh, nunca motivo para esconder o cache. |
| `app/src/main/assets/exercise_swap_catalog.json` (novo) | Catálogo compacto embarcado (id, nome, músculos, equipamento) para o primeiro uso offline. |
| `scripts/gen-swap-catalog.py` (novo) | Gera o asset a partir de `back/app/exercise_catalog.json` de forma determinística. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `init` publica `loadLocal()` em `_exerciseMedia` antes do refresh de rede; `loadExerciseMedia` não limpa o mapa em falha; opcional `catalogReady` para a UI. |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Troca em `ModalBottomSheet`: busca por nome, cartões de candidato com seleção única (rádio + destaque), escopo explícito em `FilterChip` e confirmação única; sem spinner e com estado vazio explícito quando não há catálogo. |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | Testes puros de `mergeLocal`/`parseSwapCatalog`. |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaLocalTest.kt` (novo) | `loadLocal` com cache expirado/ausente/corrompido e fallback para o asset. |
| `app/src/test/java/com/treinos/app/data/media/SwapCatalogAssetTest.kt` (novo) | Valida o asset: parse, IDs iguais ao backend, sem duplicados, campos obrigatórios. |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | Diálogo de troca sem "Carregando catálogo" quando há catálogo; estado vazio sem `CircularProgressIndicator` quando não há. |
| `ROADMAP.md` | Adicionar H3-44 à Horizon 3 e ao índice de specs. |

## Modelo de dados / contrato

### Asset embarcado

`app/src/main/assets/exercise_swap_catalog.json`:

```json
{
  "version": 1,
  "exercises": [
    {
      "id": "Barbell_Bench_Press",
      "name": "Barbell Bench Press",
      "primaryMuscles": ["chest"],
      "equipment": "barbell"
    }
  ]
}
```

- `version`: inteiro, começa em `1`.
- `exercises[].id`: string não vazia, única, igual ao `exerciseId` do catálogo
  do backend.
- `exercises[].name`: string não vazia (nome canônico EN; o app usa o idioma
  configurado quando o cache tiver `namePt`).
- `exercises[].primaryMuscles`: lista não vazia.
- `exercises[].equipment`: string (pode ser vazia quando o backend não informa).
- Campos desconhecidos são ignorados (`ignoreUnknownKeys = true`), como no
  cache atual.
- O asset é gerado por `scripts/gen-swap-catalog.py` a partir de
  `back/app/exercise_catalog.json`, ordenado por `id`, sem duplicados. Não
  editar à mão.

### Ordem de precedência do catálogo local

```text
memória (resposta nova do backend)
  > cache em disco (exercise_catalog_v1.json), mesmo expirado
    > asset embarcado (exercise_swap_catalog.json)
      > mapa vazio
```

- `loadLocal()` nunca faz rede e nunca retorna vazio se houver cache ou asset.
- `load(false)`: usa `loadLocal()` como base; se o cache estiver expirado ou
  ausente, busca o backend em background e **só substitui** o mapa quando a
  resposta for não vazia (mesma regra do `mergeCatalog` da H3-24).
- `load(true)` (botão "Atualizar catálogo"): continua network-first, com
  fallback para o local em caso de falha.
- O TTL de 7 dias deixa de bloquear a exibição: ele decide apenas se vale a
  pena buscar uma versão nova.

### Sem mudança de Room

Nenhuma migration, nenhuma tabela nova. O catálogo continua sendo arquivo +
asset; a persistência da troca usa as entidades existentes (`SessionExercise`,
`WorkoutPlanExercise`) exatamente como H3-41/H3-43.

## Regras e casos de borda

- **Cache expirado + offline:** `loadLocal()` devolve o cache imediatamente; a
  UI lista os candidatos; o refresh em background falha em silêncio.
- **Cache ausente + offline:** `loadLocal()` devolve o asset; a lista funciona
  na primeira instalação.
- **Cache corrompido/ilegível/maior que 5 MB:** cai para o asset; não lança exceção.
- **Asset ausente/corrompido:** mapa vazio; a UI mostra "Catálogo offline
  indisponível" com ação "Atualizar"; nunca fica em "Carregando…" para sempre.
- **Resposta do backend vazia:** mantém o cache/asset (não envenena com vazio).
- **Resposta do backend válida:** substitui o mapa e grava o cache; a UI
  atualiza sem reiniciar a tela.
- **Troca durante a sessão:** candidatos continuam filtrando por músculo
  primário, equipamento informado e exercícios já presentes no dia; o próprio
  exercício nunca aparece.
- **`availableEquipment` vazio:** preserva o comportamento atual (não restringe
  por equipamento).
- **Idioma:** o asset é EN; com o app em PT e cache ausente, os nomes saem em
  EN (não inventar tradução). Após o primeiro refresh, o cache com `namePt`
  passa a valer.
- **Threading:** leitura/parse do asset e do cache em `Dispatchers.IO`; nunca
  na main thread.
- **Rotação/processo morto:** o diálogo reabre a partir do catálogo local já
  carregado; não há estado transitório de "carregando" que sobreviva.

## Polimento de UI/UX (H3-44)

O diálogo antigo era um `AlertDialog` com uma linha por candidato e dois
botões por linha ("Só hoje"/"Todos"), o que ficava apertado e não deixava o
escopo claro. A troca passa a ser um `ModalBottomSheet` com:

- cabeçalho "Trocar exercício" + exercício atual e botão fechar;
- busca por nome (`OutlinedTextField`) e contador "Substitutos (N)";
- lista rolável de candidatos em cartões selecionáveis: rádio, nome,
  músculos e equipamento; o selecionado ganha fundo `secondaryContainer` e
  borda `primary`;
- escopo explícito em `FilterChip`: "Só hoje" (padrão) ou "Todos os treinos",
  com uma linha de ajuda explicando o efeito;
- ações fixas no rodapé: "Cancelar" e "Trocar" (só habilitado após escolher);
- estados vazios distintos: catálogo ausente (com "Atualizar catálogo"),
  nenhum candidato para o músculo/equipamento e busca sem resultado.

A busca e o escopo são estado local do sheet, reiniciados a cada abertura.
A lista inteira é um único `LazyColumn`, então o teclado não esconde as ações
(`imePadding`) e o usuário rola até o rodapé sem depender de um diálogo com
altura fixa.

## Critérios de aceite

1. **Dado** uma instalação nova **e** o aparelho sem internet, **quando** o
   usuário inicia um treino e toca em "Trocar exercício", **então** a lista de
   substitutos aparece imediatamente a partir do asset, sem
   "Carregando catálogo de exercícios…".
2. **Dado** um cache local expirado (TTL > 7 dias) **e** o aparelho sem
   internet, **quando** o usuário abre o diálogo, **então** o cache é usado na
   hora e nenhum timeout de rede é esperado.
3. **Dado** um cache válido **e** internet disponível, **quando** o app inicia,
   **então** o catálogo local aparece antes do refresh e o refresh atualiza o
   mapa em background sem spinner na tela de treino.
4. **Dado** um cache corrompido, **quando** o app inicia, **então** o asset é
   usado e o app não quebra.
5. **Dado** catálogo local vazio (asset ausente/corrompido), **quando** o
   usuário abre o diálogo, **então** aparece uma mensagem explícita e um botão
   "Atualizar", sem `CircularProgressIndicator`.
6. **Dado** um substituto válido, **quando** o usuário confirma a troca,
   **então** a troca continua atômica e preserva séries/reps/ordem (regressão
   de H3-41/H3-43).
7. **Dado** o asset e o catálogo do backend, **quando** o teste do asset roda,
   **então** todos os IDs do backend estão presentes no asset, sem duplicados.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | `merge_local_prioriza_cache_sobre_asset`, `parse_swap_catalog_ignora_campos_desconhecidos`, `parse_swap_catalog_rejeita_json_invalido` |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaLocalTest.kt` (novo) | `load_local_usa_cache_mesmo_expirado`, `load_local_cai_para_asset_quando_sem_cache`, `cache_corrompido_cai_para_asset`, `cache_vazio_cai_para_asset` |
| `app/src/test/java/com/treinos/app/data/media/SwapCatalogAssetTest.kt` | `asset_catalogo_e_parseavel`, `asset_ids_iguais_ao_backend`, `asset_sem_ids_duplicados`, `asset_campos_obrigatorios`, `asset_ordem_deterministica` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `dialogo_troca_nao_mostra_carregando_quando_catalogo_presente`, `dialogo_troca_sem_catalogo_mostra_estado_vazio_sem_progresso`, `dialogo_troca_seleciona_substituto_e_escopo` |
| `app/src/test/java/com/treinos/app/data/ExerciseSwapTest.kt` | (regressão, sem mudança) `candidatos_filtram_por_musculo_e_equipamento`, `candidatos_nao_incluem_exercicios_ja_presentes_no_dia` |
| `app/src/test/java/com/treinos/app/data/SessionSwapTest.kt` | (regressão, sem mudança) `candidatos_filtram_por_musculo_e_equipamento` |

## Verificação manual

1. Instalação nova, modo avião: abrir um treino de força → "Trocar exercício"
   → conferir que a lista aparece sem "Carregando catálogo".
2. Usar o app por mais de 7 dias (ou ajustar o `fetchedAt` do cache), ficar
   offline e repetir o passo 1: a lista continua aparecendo.
3. Corromper `exercise_catalog_v1.json` (conteúdo inválido) e abrir o app: a
   lista vem do asset e o app não fecha.
4. Com internet, tocar em "Atualizar catálogo" em Ajustes: a mensagem de
   sucesso aparece e os candidatos continuam válidos.
5. Confirmar uma troca "Só hoje" e uma "Todos": séries/reps/ordem preservados.

## Comando de verificação

```bash
./scripts/verify.sh
```

Critério: exit code `0` e linha
`VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`.

## Riscos e rollback

- **Divergência entre asset e backend:** mitigada pelo script gerador e pelo
  teste que compara os IDs; se o backend mudar, rodar
  `python3 scripts/gen-swap-catalog.py` e regerar o asset.
- **APK ~100 KB maior:** aceitável; o asset compacto tem apenas os campos de
  troca. Se for um problema, reduzir para os IDs de força.
- **Escrita do cache durante leitura:** a atualização deve gravar em arquivo
  temporário e renomear (atômico) para não corromper o cache com o app aberto.
- **Rollback:** remover o asset e a chamada a `loadLocal()`, voltando
  `AppViewModel.init` para `loadExerciseMedia(false)`; nenhuma migration de
  Room foi criada, então não há dado a reverter.