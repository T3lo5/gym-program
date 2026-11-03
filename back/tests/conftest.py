"""Configuração dos testes do backend.

Define variáveis de ambiente antes de importar `app.main`, porque o módulo
resolve caminhos e configuração no momento do import.
"""

import os

os.environ.setdefault("DATABASE_PATH", "/tmp/treinos-test.db")
os.environ.setdefault("MDNS_ENABLED", "false")
os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")
