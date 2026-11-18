"""Retrieval-augmented context for the rewrite agent (SPEC.md §5).

Two retrieval paths:
- **Structured**: database schema metadata (tables, columns, keys, indexes, stats), fetched
  through the Java API — see :mod:`.schema`.
- **Lexical**: optimization rules and curated examples with stable ids, scored by a small,
  dependency-free TF-IDF index — see :mod:`.rules` and :mod:`.index`.

All retrieved text is treated as untrusted **data**, never as instructions (SPEC.md §8). We
never retrieve benchmark answers or measured target runtimes as optimization context.
"""
