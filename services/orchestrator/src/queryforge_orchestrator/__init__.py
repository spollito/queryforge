"""QueryForge agentic SQL-optimization orchestrator.

This package is the Python service boundary described in SPEC.md §3/§10. It owns LangGraph
orchestration, retrieval, the pluggable LLM client, Z3 verification, and XGBoost ranking. It
never opens a database connection of its own: every plan capture, EXPLAIN, and benchmark run
goes back through the Java API, which keeps the single read-only, timeout-bounded path to
target databases.
"""

__version__ = "0.1.0"
