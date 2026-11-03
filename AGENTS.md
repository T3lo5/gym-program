# AGENTS.md

App Android (Kotlin/Compose) + backend FastAPI. Todo agente — humano ou IA —
que altera este repositório segue o **gate** abaixo. O gate é o mesmo do
GitHub Actions: não existe “funciona na minha máquina”.

## Gate obrigatório (antes de qualquer commit/push)

1. Rode `./scripts/verify.sh` a partir da raiz.
2. **Critério de conclusão:** exit code `0` e a linha
   `VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura` no final.
3. Se falhar, corrija a causa e rode de novo. Repetir até ficar verde.
4. Na resposta final, informe o comando executado e o resultado do gate.

Enquanto o gate não estiver verde, o trabalho está **incompleto**. Não peça
commit, não abra PR e não diga “pronto”.

`./scripts/verify.sh` constrói a imagem Docker do CI local e roda, contra o
working tree, com `clean`:

- `:app:testDebugUnitTest` — testes unitários JVM;
- `:app:lintDebug` — Android Lint;
- `:app:assembleDebug` — compilação limpa;
- `apksigner verify` — assinatura do APK;
- pacote `dist/treinos.apk` + SHA-256;
- backend: Ruff, import do FastAPI e Pytest em container limpo.

O `clean` é essencial: `app/build` e cache do host mascaram erros de
compilação. Foi assim que uma mudança quebrada passou para a `main`.

### Atalhos (nunca são prova de conclusão)

```bash
./scripts/verify.sh                 # gate completo — o único aceito
SKIP_BACKEND=1 ./scripts/verify.sh  # mudança só no Android
SKIP_LINT=1 ./scripts/verify.sh     # iteração local; rodar completo depois
./scripts/ci-android.sh             # roda o job Android fora do Docker
./scripts/ci-backend.sh             # só backend
```

`./scripts/build-docker.sh <tarefa>` é apenas build rápido. Ele avisa que
não substitui o gate.

## Regras por tipo de mudança

| Mudança | Obrigatório além do gate |
|---|---|
| Lógica pura em `app/src/main` (ex.: cálculo, progresso, parsing) | Teste JVM em `app/src/test/...` cobrindo o caso que motivou a mudança |
| UI/Compose/Room/Android | Compilar limpo; se extrair lógica, testá-la em JVM |
| Backend (`back/`) | Teste em `back/tests/` para o comportamento novo |
| Dependência nova | Justificar no PR; versão fixa; roda o gate completo |
| `.github/workflows/`, `Dockerfile`, `scripts/ci-*` | Rodar o gate e confirmar que o workflow continua chamando o script compartilhado |
| `uses:` novo/alterado no workflow | Confirmar a tag real (`gh api repos/<owner>/<action>/releases/latest`); nunca inventar versão |

### Fonte única do CI

`scripts/ci-android.sh` é a receita do CI. O workflow chama esse script; o
Docker local chama esse mesmo script. Ao mudar um passo de build, mude o
script — não duplique comandos Gradle no YAML.

### Item do roadmap exige spec

`ROADMAP.md` define o que fazer; `docs/specs/<ID>-*.md` define **como** e
**quando está pronto**. Antes de implementar um item:

1. Leia a spec em `docs/specs/`.
2. Se não existir, crie a partir de `docs/specs/_TEMPLATE.md` e peça aprovação.
3. Implemente exatamente o escopo da spec, incluindo os testes listados.
4. Atualize a spec (status) e mova o item para §4.1 do `ROADMAP.md`.

`docs/SPRINT-PLAN.md` é histórico e está superado; não use os IDs `S*` dele.

O gate é uma trava, não um obstáculo: **não** enfraqueça lint/teste para ficar
verde (remover teste, `@Ignore`, `-x lint`, `--no-verify`, baixar `minSdk` ou
comentar código). Corrija a causa.

## Definition of Done

- [ ] `./scripts/verify.sh` verde (saída colada na resposta)
- [ ] Spec do item em `docs/specs/` implementada e com status atualizado
- [ ] Mudança de lógica acompanhada do teste correspondente
- [ ] Diff pequeno e focado; sem arquivos gerados, `dist/`, `app/build/` ou segredos
- [ ] Backend, quando tocado, com Ruff + Pytest verdes
- [ ] Se o workflow mudou, ele ainda delega para `scripts/ci-*.sh`
- [ ] Commit no padrão `tipo: descrição curta` (`feat`, `fix`, `docs`, `ci`, `test`, `refactor`)

## Ambiente

- Pré-requisito: Docker com daemon acessível. O primeiro `verify.sh` baixa o
  Android SDK e pode demorar; os seguintes usam `.gradle-docker/`.
- Saídas: `dist/treinos.apk`, `dist/treinos.apk.sha256`,
  `app/build/reports/tests/...`, `app/build/reports/lint-results-debug.html`.
- Se o gate passa e o GitHub Actions falha, trate como bug de paridade: veja o
  log com `gh run view <id> --log-failed` e alinhe `scripts/ci-android.sh`.

## Mapa rápido

- `app/` — app Android (módulo incluído em `settings.gradle.kts`)
- `back/` — FastAPI + SQLite (`back/app`, testes em `back/tests`)
- `ROADMAP.md` — fonte de verdade do produto (o que fazer)
- `docs/specs/` — specs por item; `_TEMPLATE.md` é o formato obrigatório
- `docs/SPRINT-PLAN.md` — histórico superado (não usar)
- `shared/`, `wear/` — **legados**, fora do build (ver `LEGACY.md` em cada um)
- `app/src/main/java/com/treinos/shared/` — **legado**, não referenciado (ver `LEGACY.md`)
- `scripts/ci-android.sh` — CI Android (fonte única)
- `scripts/ci-backend.sh` — CI backend
- `scripts/verify.sh` — gate local completo
- `.github/workflows/android.yml` — workflow que chama os scripts acima
