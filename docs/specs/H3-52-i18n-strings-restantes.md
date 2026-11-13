# H3-52 — i18n: extrair as strings hardcoded restantes

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-52` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `pendente` |

**Origem:** varredura de polimento UX/UI de 2026-11-13. A H3-37 criou a
infraestrutura de idioma (385 chaves + `values-en` + `StringParityTest`), mas
~88 literais em português continuam hardcoded em 9 arquivos de UI. Com o
idioma English selecionado, o usuário ainda vê diálogos do treino ("Excluir
série?", "Finalizar treino?"), rótulos do Histórico ("Treinos", "Séries",
"Volume", "Duração") e quase toda a tela de Ajustes em português.

## Objetivo

Com o idioma English selecionado, nenhuma tela, diálogo ou mensagem do app
mostra português — a paridade prometida pela H3-37 fica completa.

## Fora de escopo

- Wear OS (i18n e TalkBack do relógio são a H3-59).
- Backend e catálogo (tradução de exercícios é a H3-36, concluída).
- Mudar a copy em português: os textos de PT continuam idênticos aos de hoje.
- Novos idiomas além de PT/EN; suporte a RTL.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | ~22 literais → `stringResource`: diálogos "Excluir série?"/"Finalizar treino?"/"Abandonar treino?", "Voltar", "Pular", "Atualizar catálogo", "Buscar substituto", "Só hoje"/"Todos os treinos", "Trocar", campos "Repetições"/"Carga (kg)"/"RPE (opcional)", "Excluir série", "Cancelar" |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | maior bloco: títulos "Ajustes"/"Treino"/"Sincronização", "Testar conexão"/"Testando…", "Conectar"/"Conectando…", "Conectado", dicas de servidor e descoberta, "Sincronizar com o servidor", "nunca", "versão não informada" |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | "Histórico", "Nenhum treino registrado", rótulos de métrica, "Piscina %d m", "Sem séries registradas" |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | diálogo "Excluir plano?" ("Excluir"/"Cancelar") |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | literais remanescentes |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | "Editar registro"/"Registrar atividade" e remanescentes |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | literais remanescentes (snackbar/labels) |
| `app/src/main/java/com/treinos/app/ui/UiError.kt` | `title = "Não foi possível $contexto"` (linha ~52) → recurso com placeholder resolvido onde há Context |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt` | "Músculos principais:", "Equipamento:" |
| `app/src/main/res/values/strings.xml` | novas chaves (PT com o texto atual, byte a byte) |
| `app/src/main/res/values-en/strings.xml` | mesmas chaves em inglês |
| `app/src/test/java/com/treinos/app/ui/StringParityTest.kt` | existente; valida a paridade das novas chaves automaticamente |

## Modelo de dados / contrato

Sem schema. Contrato de chaves: toda chave nova existe nos dois `strings.xml`
com o mesmo nome; strings com variável usam placeholders posicionais
(`%1$s`, `%1$d`); contagens que flexionam em PT ("1 série"/"2 séries") usam
`<plurals>` nos dois idiomas.

## Regras e casos de borda

- Composables usam `stringResource(R.string.…)`; textos montados fora de
  composição (mensagens do `AppViewModel`, `UiError`) resolvem via
  `application.getString(R.string.…, args)`.
- Interpolação vira placeholder — proibido concatenar pedaços de tradução
  com `+`.
- Nomes de exercício/equipamento vêm do catálogo bilíngue (H3-36) e não são
  literais de UI — não tocar.
- A chave nasce com o literal atual em PT: nenhuma mudança visível para quem
  já usa português.

## Critérios de aceite

1. **Dado** idioma English em Ajustes, **quando** percorrer Home, Geração,
   Treino (incl. diálogos de série/excluir/finalizar/abandonar e a folha de
   troca), Histórico, Registro avulso e Ajustes, **então** nenhum texto em
   português aparece.
2. **Dado** idioma Português, **quando** percorrer as mesmas telas,
   **então** o texto é idêntico ao atual (nenhuma mudança de copy).
3. **Dado** o build, **quando** rodar a verificação completa, **então**
   `StringParityTest` fica verde com as novas chaves nos dois idiomas.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/StringParityTest.kt` | existente cobre as novas chaves (nenhum teste novo obrigatório) |

## Verificação manual

1. Ajustes → Idioma do app → English; percorrer todas as telas e abrir cada
   diálogo (editar série, excluir série, finalizar, abandonar, trocar
   exercício, excluir plano).
2. Anotar qualquer português restante e corrigir antes do gate.
3. Voltar para Português e conferir que nenhum texto mudou.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Mudança mecânica e reversível por `git revert`. Riscos reais: placeholder
faltando ou com índice errado (crash na formatação — `StringParityTest` não
formata; a verificação manual cobre as telas tocadas) e chave esquecida
(paridade não pega literal de código, só de recursos — por isso a varredura
manual do passo 1 é parte do aceite).
