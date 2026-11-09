# H3-39 — Imagens dos alongamentos (mobilidade)

> **Template obrigatório** para itens do [ROADMAP](../../ROADMAP.md).
> Copie para `docs/specs/<ID>-<slug>.md`, preencha e remova as instruções em
> itálico. A spec é o contrato: se algo não está aqui, não está no escopo.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-39` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-09` |
| Implementação | `concluída` |

## Objetivo

Os movimentos da tela de Mobilidade (alongamentos) exibem uma imagem visual, tanto
na lista de rotinas quanto no player, mesmo quando o app está offline e o
catálogo remoto não pôde ser carregado.

## Fora de escopo

- Vídeo ou animação de movimento.
- Baixar catálogo completo antecipadamente ou garantir uso 100% offline na
  primeira execução sem nenhuma imagem local.
- Editar o catálogo de exercícios ou o backend.
- Substituir o nome do exercício por imagem (o nome continua visível).
- Imagens dos exercícios de musculação/corrida (coberto por H1-3/H3-24).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/res/drawable/` | Adicionar drawables vetoriais locais para os movimentos de mobilidade (nome padronizado) |
| `app/src/main/java/com/treinos/app/ui/MobilityRoutine.kt` | Adicionar `imageRes: Int?` a `MobilityMovement` e um mapa `imageResForMovement` puro (testável em JVM) |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseImage.kt` | Aceitar `imageRes: Int?` como fallback quando `imageUrl` for nulo/vazio |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Passar o drawable local do movimento para o `ExerciseImage` no player e na lista |
| `app/src/test/java/com/treinos/app/ui/MobilityImageTest.kt` | **Novo.** Mapeamento de drawable, validação de existência dos recursos |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

`MobilityMovement` ganha um campo opcional:

```kotlin
data class MobilityMovement(
    val name: String,
    val durationSeconds: Int,
    val side: String? = null,
    val cue: String,
    val catalogId: String? = catalogIdForMovement(name),
    val imageRes: Int? = null,   // H3-39: drawable local de fallback (offline)
)
```

`imageRes` aponta para um drawable empacotado no APK (sem rede). O mapeamento é
puro e testável em JVM:

```kotlin
fun imageResForMovement(name: String): Int?   // drawable ou null
```

Quando `imageRes` é null e o catálogo remoto não tem imagem, o `ExerciseImage`
usa o fallback de letra existente (quadrado nunca vazio).

## Regras e casos de borda

1. Movimento sem drawable local → `imageRes = null`, cai no drawable do catálogo
   remoto se houver, senão no fallback de letra.
2. Drawable inexistente no `R.drawable` → erro de compilação (referência
   inválida); o mapa só referencia drawables que existem.
3. O player mostra a imagem do movimento atual; se houver tanto drawable local
   quanto URL remota, o URL remota vence (catálogo é fonte preferencial, H3-24).
4. Catálogo offline + drawable local → imagem aparece sem rede.
5. Catálogo offline + sem drawable local → fallback de letra (comportamento de
   hoje).
6. Cada rotina de mobilidade deve ter pelo menos um movimento com drawable
   local (cobertura visual mínima).
7. Não há mudança de schema Room nem de backend (mobilidade é local/offline).

## Critérios de aceite

1. **Dado** o app offline sem catálogo carregado, **quando** o usuário abre o
   player de mobilidade, **então** o movimento atual mostra o drawable local em
   vez do quadrado vazio.
2. **Dado** o catálogo remoto carregado com imagem para o movimento, **quando**
   abre o player, **então** a imagem remota vence sobre o drawable local.
3. **Dado** a lista de rotinas, **quando** um movimento da rotina tem drawable
   local, **então** a miniatura da rotina (primeiro movimento com imagem) exibe
   esse drawable.
4. **Dado** um movimento sem drawable local e sem catálogo, **quando** abre o
   player, **então** aparece o fallback de letra (não quadrado vazio) e o
   treino não trava.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/MobilityImageTest.kt` | `image_res_mapeia_movimentos_conhecidos`, `movimento_sem_drawable_retorna_null`, `todos_drawables_referenciados_existem_no_recursos`, `pelo_menos_um_movimento_por_rotina_tem_drawable` |

## Verificação manual

1. Abra “Mobilidade” em modo avião (sem catálogo remoto).
2. Inicie uma rotina e confira que o movimento atual exibe uma imagem (não
   quadrado vazio).
3. Conecte o backend, carregue o catálogo e repita: a imagem remota deve
   aparecer quando houver.
4. Na lista de rotinas, confira a miniatura de capa de cada rotina.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Tamanho do APK:** drawables vetoriais são leves; não adicionar PNGs grandes.
- **Referência quebrada:** o mapa só referencia drawables que existem (teste
  `todos_drawables_referenciados_existem_no_recursos` falha na compilação se
  houver referência inválida).
- **Rollback:** remover o campo `imageRes`, o mapa e as chamadas em
  `MobilityScreen`; o comportamento volta ao de hoje (catálogo remoto ou letra).
