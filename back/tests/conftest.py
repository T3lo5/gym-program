"""Configuração dos testes do backend.

Define variáveis de ambiente antes de importar `app.main`, porque o módulo
resolve caminhos e configuração no momento do import.
"""

import os

# Atribuição (não setdefault): `back/.env` traz uma chave real e `load_dotenv`
# roda no import de `app.main`. Sem zerar a chave aqui, os testes que exigem o
# plano local determinístico podem chamar a IA e mudar de resultado a cada run.
os.environ["SENSENOVA_API_KEY"] = ""

os.environ.setdefault("DATABASE_PATH", "/tmp/treinos-test.db")
os.environ.setdefault("MDNS_ENABLED", "false")
os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")
