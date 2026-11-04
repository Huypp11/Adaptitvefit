"""Doc file .env o thu muc goc du an (cung file voi Spring Boot) vao bien moi truong, khong can thu vien ngoai."""
import os
from pathlib import Path


def load_dotenv(path: Path | None = None) -> None:
    p = path or Path(__file__).resolve().parents[2] / ".env"
    if not p.exists():
        return
    for line in p.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))
