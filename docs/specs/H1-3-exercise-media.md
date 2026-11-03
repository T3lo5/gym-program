# H1-3 — Imagens dos exercícios

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-3` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída` |

## Objetivo

Mostrar a imagem do exercício no treino e em um detalhe com instruções, para
que o usuário reconheça o movimento sem depender só do nome em inglês.

## Fora de escopo

- Vídeo (o catálogo atual só tem `images`; vídeo fica para uma spec futura).
- Baixar todas as imagens antecipadamente ou garantir uso 100% offline na
  primeira execução.
- Alterar o schema Room ou adicionar tabela de catálogo.
- Editar o catálogo de exercícios ou o backend.
- Substituir o nome do exercício por imagem.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | Dependência `io.coil-kt:coil-compose:2.7.0` |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | DTOs `CatalogResponseDto` / `CatalogExerciseDto` |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | `suspend fun catalog(): CatalogResponseDto` |
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | **Novo.** Fetch + cache em `filesDir` |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | Construir o repositório no `AppContainer` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estado do catálogo, refresh e detalhe selecionado |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passar estado/callbacks para as telas |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseImage.kt` | **Novo.** Imagem + placeholder + `contentDescription` |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt` | **Novo.** Bottom sheet com imagem e instruções |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Miniatura + abrir detalhe |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Abrir detalhe ao tocar no exercício |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Botão “Atualizar catálogo” + status |
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | **Novo.** URL, parse e cache |

Não edite `shared/`, `wear/` nem `com.treinos.shared/`.

## Modelo de dados / contrato

O backend já expõe `GET /v1/catalog`:

```json
{
  "exercises": [
    {
      "id": "3_4_Sit-Up",
      "name": "3/4 Sit-Up",
      "primaryMuscles": ["abdominals"],
      "secondaryMuscles": [],
      "equipment": "body only",
      "level": "beginner",
      "instructions": ["...", "..."],
      "images": ["3_4_Sit-Up/0.jpg", "3_4_Sit-Up/1.jpg"]
    }
  ]
}
```

URL da imagem:

```
https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<path>
```

Regras:

- Cache local em `context.filesDir/exercise_catalog_v1.json`.
- `ExerciseMediaRepository` deve ter `suspend fun load(forceRefresh: Boolean = false): Map<String, ExerciseMedia>`.
- TTL: 7 dias. Se o fetch falhar e houver cache, usar o cache (stale-while-error).
- Se não houver cache e o fetch falhar, retornar mapa vazio; a UI mostra
  placeholder. Nunca lançar exceção para a tela.
- Limite de leitura do cache: 5 MB; acima disso, ignorar e rebaixar.
- `ExerciseMedia(id, name, primaryMuscles, equipment, instructions, imageUrl: String?)`.
- Usar a primeira imagem como principal; a segunda, se existir, no detalhe.
- Sem migration Room.
- Coil já tem cache de disco; não implementar cache de imagem próprio.

## Regras e casos de borda

1. Exercício sem `images` → `imageUrl = null` e placeholder com ícone.
2. Imagem que falha ao carregar (404/rede) → placeholder, sem crash.
3. Catálogo offline com cache → imagens já cacheadas pelo Coil continuam.
4. Catálogo offline sem cache → detalhe mostra nome/instruções vazios e
   placeholder; não bloquear o treino.
5. Nome do arquivo com espaços/acentos → usar a string exata de `images`
   concatenada à base, sem montar manualmente o nome.
6. Exercício não encontrado no mapa → placeholder e detalhe mínimo.
7. `forceRefresh` deve ser explícito no botão de Ajustes; não atualizar a cada
   abertura de tela.
8. `contentDescription`: “Imagem do exercício <nome>” quando houver imagem;
   `null` para imagem decorativa.
9. O detalhe não deve abrir durante o timer de descanso sem o usuário tocar.

## Critérios de aceite

1. **Dado** o catálogo carregado, **quando** o usuário abre um exercício no
   treino, **então** vê a imagem principal e o nome.
2. **Dado** um exercício com duas imagens, **quando** abre o detalhe, **então**
   vê a primeira como principal e consegue ver a segunda (ou indicador de
   várias).
3. **Dado** que o app está offline mas já carregou o catálogo antes, **quando**
   abre o detalhe, **então** a imagem em cache aparece e o app não trava.
4. **Dado** que o app está offline e nunca carregou o catálogo, **quando** abre
   o detalhe, **então** vê placeholder e consegue fechar.
5. **Dado** um exercício sem imagem, **então** placeholder com descrição
   acessível.
6. **Dado** o botão “Atualizar catálogo”, **quando** toca, **então** o arquivo de
   cache é reescrito e o status mostra a data/hora.
7. **Dado** que o backend está desligado, **quando** atualiza, **então** o cache
   antigo é mantido e aparece aviso.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/media/ExerciseMediaTest.kt` | `monta_url_da_primeira_imagem`, `sem_images_retorna_null`, `parse_do_catalogo_preserva_instrucoes`, `cache_expirado_e_aceito_quando_fetch_falha`, `cache_acima_do_limite_e_ignorado`, `exercicio_desconhecido_retorna_null` |

Testes de UI não entram no gate atual; a verificação visual é manual.

## Verificação manual

1. Suba o backend e abra um treino; confirme a miniatura no exercício atual.
2. Toque no exercício e confira imagem, músculos, equipamento e instruções.
3. Ative o modo avião, force o fechamento e reabra: imagem em cache deve
   aparecer.
4. Limpe os dados do app, mantenha o modo avião e abra o detalhe: placeholder.
5. Ajustes → “Atualizar catálogo” com backend ligado e desligado.
6. Confira no TalkBack que a imagem tem descrição e o sheet fecha.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Licença:** free-exercise-db é Unlicense; manter a atribuição no README.
- **Tamanho do cache:** limitar a 5 MB e usar o cache de disco do Coil.
- **Rede:** primeira visualização exige internet; o core do treino continua
  offline.
- **Rollback:** remover a dependência Coil, o repositório e os componentes de
  imagem; nenhum dado do usuário é alterado.
