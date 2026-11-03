# ⚠️ Módulo legado — fora do build

Este módulo **não está incluído** em `settings.gradle.kts` (a linha está
comentada). Ele não entra no APK atual e não deve receber features.

O código ativo do app está em `app/`, e o backend em `back/`.

Se um dia for reativado, ele precisa de uma spec própria, entrar no build e
passar pelo gate `./scripts/verify.sh` (incluindo atualização do Dockerfile).
