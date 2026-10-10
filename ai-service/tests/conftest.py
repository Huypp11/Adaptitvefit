import pytest

from app.ml import dataset_registry, predict, registry, train


@pytest.fixture
def isolated_dirs(tmp_path, monkeypatch):
    """Ghi dataset/model vao thu muc tam, khong dung vao du lieu that."""
    monkeypatch.setattr(dataset_registry, "VERSIONS_DIR", tmp_path / "versions")
    monkeypatch.setattr(registry, "MODELS_DIR", tmp_path / "models")
    monkeypatch.setattr(train, "MODELS_DIR", tmp_path / "models")
    monkeypatch.setattr(predict, "MODELS_DIR", tmp_path / "models")
    predict.load_model.cache_clear()
    yield tmp_path
    predict.load_model.cache_clear()
