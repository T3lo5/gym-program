# ⚠️ Código legado — não editar

Este pacote (`com.treinos.shared`) vive dentro do módulo `app`, mas **não é
referenciado pelo app atual**. Ele é uma cópia antiga de entidades, DAOs e
repositórios.

O código em uso está em:

- `app/src/main/java/com/treinos/app/data/local/` — Room (fonte de verdade)
- `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt`
- `app/src/main/java/com/treinos/app/ui/`

Não implemente features aqui. Se precisar remover este diretório, faça em um
commit isolado e rode `./scripts/verify.sh`.
