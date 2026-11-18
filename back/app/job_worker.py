"""Helpers puros para recovery e lease de jobs de geração (H5-2)."""

from datetime import datetime, timezone
from typing import Optional


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def lease_expired(lease_expires_at: Optional[str], now: Optional[str] = None) -> bool:
    if not lease_expires_at:
        return True
    now_value = datetime.fromisoformat((now or iso_now()).replace("Z", "+00:00"))
    expires_value = datetime.fromisoformat(lease_expires_at.replace("Z", "+00:00"))
    return expires_value <= now_value


def backoff_seconds(attempt: int) -> int:
    if attempt <= 1:
        return 5
    return 30
