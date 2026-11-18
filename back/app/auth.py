"""Autenticação mínima para exposição remota do backend."""

import secrets
from typing import Optional


def bearer_token(authorization: Optional[str]) -> str:
    if not authorization:
        return ""
    scheme, separator, token = authorization.strip().partition(" ")
    if separator != " " or scheme.lower() != "bearer":
        return ""
    return token.strip()


def is_authorized(authorization: Optional[str], api_token: Optional[str]) -> bool:
    configured = (api_token or "").strip()
    if not configured:
        return True
    return secrets.compare_digest(bearer_token(authorization), configured)


def validate_public_deploy(public_base_url: Optional[str], api_token: Optional[str]) -> None:
    public_url = (public_base_url or "").strip()
    token = (api_token or "").strip()
    if public_url.lower().startswith("https://") and not token:
        raise RuntimeError(
            "Deploy público inválido: PUBLIC_BASE_URL usa HTTPS mas API_TOKEN está vazio."
        )
