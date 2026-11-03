# ⚠️ Módulo legado — fora do build

Este módulo **não está incluído** em `settings.gradle.kts`. Ele depende de
`:shared`, que também está fora do build. O relógio ainda não entra no APK.

Não implemente H2-2 (Wear OS) aqui sem uma spec própria que inclua:

- reativar `:shared` e `:wear` no `settings.gradle.kts`;
- ajustar o `Dockerfile`/CI para compilar os módulos;
- rodar `./scripts/verify.sh` com o novo build.

Até lá, o código ativo é somente `app/` + `back/`.
