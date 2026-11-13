import numpy as np

from athena_ingest.embedding import l2_normalise, mean_pool
from athena_ingest.store import vector_literal


def test_mean_pool_ignores_padding():
    tokens = np.array([[[1.0, 2.0], [3.0, 4.0], [100.0, 100.0]]])
    mask = np.array([[1, 1, 0]])
    assert np.allclose(mean_pool(tokens, mask), [[2.0, 3.0]])


def test_l2_normalise():
    v = l2_normalise(np.array([[3.0, 4.0], [0.0, 0.0]]))
    assert np.allclose(v[0], [0.6, 0.8])
    assert np.allclose(v[1], [0.0, 0.0])          # zero vector stays zero, no division by zero


def test_vector_literal_is_pgvector_text_format():
    assert vector_literal(np.array([0.5, -1.0, 1e-8])) == "[0.5,-1,1e-08]"
