Questions: 35 scored (29 English, 6 Thai); skipped (answer not in the KB): 28

| Mode | Programme filter | Subset | n | hit@1 | hit@3 | hit@5 | hit@10 | MRR@10 | p50 latency |
|---|---|---|---|---|---|---|---|---|---|
| lexical | no | all | 35 | 40% | 66% | 74% | 83% | 0.54 | 13 ms |
| lexical | no | en | 29 | 41% | 69% | 79% | 90% | 0.57 | 13 ms |
| lexical | no | th | 6 | 33% | 50% | 50% | 50% | 0.42 | 13 ms |
| semantic | no | all | 35 | 54% | 71% | 86% | 97% | 0.66 | 53 ms |
| semantic | no | en | 29 | 62% | 76% | 86% | 97% | 0.72 | 53 ms |
| semantic | no | th | 6 | 17% | 50% | 83% | 100% | 0.42 | 53 ms |
| hybrid | no | all | 35 | 60% | 77% | 91% | 94% | 0.71 | 58 ms |
| hybrid | no | en | 29 | 62% | 79% | 97% | 97% | 0.74 | 58 ms |
| hybrid | no | th | 6 | 50% | 67% | 67% | 83% | 0.58 | 58 ms |
| lexical | yes | all | 35 | 51% | 71% | 77% | 89% | 0.63 | 9 ms |
| lexical | yes | en | 29 | 55% | 76% | 83% | 97% | 0.67 | 9 ms |
| lexical | yes | th | 6 | 33% | 50% | 50% | 50% | 0.42 | 9 ms |
| semantic | yes | all | 35 | 54% | 77% | 91% | 97% | 0.69 | 46 ms |
| semantic | yes | en | 29 | 62% | 83% | 93% | 97% | 0.74 | 46 ms |
| semantic | yes | th | 6 | 17% | 50% | 83% | 100% | 0.43 | 46 ms |
| hybrid | yes | all | 35 | 66% | 86% | 91% | 94% | 0.76 | 37 ms |
| hybrid | yes | en | 29 | 69% | 90% | 97% | 97% | 0.80 | 37 ms |
| hybrid | yes | th | 6 | 50% | 67% | 67% | 83% | 0.58 | 37 ms |

Hybrid misses at k=5 (without programme filter): 6 (rank >10), th-5 (rank 8), th-26 (rank >10)

Hybrid misses at k=5 (with programme filter): 6 (rank >10), th-5 (rank 8), th-26 (rank >10)
