"""Z3-based semantic verification over a supported SQL fragment (SPEC.md §7).

**Supported fragment (v1).** A single ``SELECT`` over exactly one table, with:
- projection of plain columns (or ``*``) — the two queries must project identically;
- a ``WHERE`` predicate built from ``AND`` / ``OR`` / ``NOT`` / parentheses over comparisons
  (``= <> < <= > >=``) between a column and an **integer** literal, or between two columns;
  plus ``BETWEEN``, ``IN (int, ...)``, ``IS [NOT] NULL``, and boolean literals.

Anything else — joins, aggregation, ``GROUP BY``/``HAVING``/``DISTINCT``/``ORDER BY``/``LIMIT``,
subqueries, non-integer literals, functions, differing tables or projections — yields
``UNSUPPORTED``. ``UNSUPPORTED`` and ``UNKNOWN`` are never treated as verified (SPEC.md §7).

**NULL semantics.** Columns are modelled with three-valued logic: each column has a Boolean
``is null`` flag and an integer value, and a row is kept only when the predicate evaluates to
*TRUE* (not FALSE, not UNKNOWN). So ``a = a`` is correctly NOT equivalent to ``TRUE`` (they
differ when ``a`` is NULL), and the verifier returns that row as a counterexample.

**Bag semantics.** Because the fragment has no ``DISTINCT``/``GROUP BY`` and identical
projection over one table, two queries return the same multiset of rows iff they keep exactly
the same rows — i.e. iff their predicates are equivalent as TRUE-sets. That equivalence is
what Z3 checks: ``SAT(passes_A XOR passes_B)`` means not equivalent (counterexample), ``UNSAT``
means equivalent.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..models import VerificationResult, VerificationStatus
from . import Verifier


class _Unsupported(Exception):
    """Raised internally when a query leaves the supported fragment."""


@dataclass(frozen=True)
class _SupportedQuery:
    table: str
    projection: tuple[str, ...]
    predicate: object | None  # a sqlglot expression, or None for no WHERE


class Z3Verifier(Verifier):
    """Verifies predicate equivalence of two single-table queries with Z3."""

    def __init__(self, timeout_ms: int = 2000) -> None:
        # Import here so the base install (without the 'verification' extra) still works;
        # default_verifier() falls back to UnknownVerifier when these are missing.
        import sqlglot  # noqa: F401
        import z3  # noqa: F401

        self._timeout_ms = timeout_ms

    def verify(self, original_sql: str, candidate_sql: str) -> VerificationResult:
        import z3

        try:
            a = _parse_supported(original_sql)
            b = _parse_supported(candidate_sql)
        except _Unsupported as exc:
            return _result(VerificationStatus.UNSUPPORTED, detail=str(exc))

        if a.table != b.table:
            return _result(VerificationStatus.UNSUPPORTED, detail="queries read different tables")
        if a.projection != b.projection:
            return _result(VerificationStatus.UNSUPPORTED, detail="queries project different columns")

        env = _Env()
        try:
            passes_a, _ = _encode(a.predicate, env)
            passes_b, _ = _encode(b.predicate, env)
        except _Unsupported as exc:
            return _result(VerificationStatus.UNSUPPORTED, detail=str(exc))

        solver = z3.Solver()
        solver.set("timeout", self._timeout_ms)
        solver.add(passes_a != passes_b)
        outcome = solver.check()
        if outcome == z3.unsat:
            return _result(VerificationStatus.VERIFIED, detail=f"predicates equivalent over {a.table}")
        if outcome == z3.sat:
            counterexample = _counterexample(env, solver.model())
            return VerificationResult(
                status=VerificationStatus.INVALID,
                method="z3",
                counterexample=counterexample,
                detail="a row is kept by one query but not the other",
            )
        return _result(VerificationStatus.UNKNOWN, detail="solver returned unknown (timeout)")


def _result(status: VerificationStatus, detail: str) -> VerificationResult:
    return VerificationResult(status=status, method="z3", detail=detail)


# --- sqlglot parsing into the supported fragment -------------------------------------------

_DISALLOWED_CLAUSES = (
    "with", "group", "having", "qualify", "order", "limit", "offset", "distinct", "windows",
    "laterals", "pivots",
)


def _parse_supported(sql: str) -> _SupportedQuery:
    import sqlglot
    from sqlglot import exp

    try:
        tree = sqlglot.parse_one(sql, read="postgres")
    except Exception as exc:  # sqlglot raises various ParseError subtypes
        raise _Unsupported(f"parse error: {exc}") from exc

    if not isinstance(tree, exp.Select):
        raise _Unsupported("not a single SELECT statement")
    for clause in _DISALLOWED_CLAUSES:
        if tree.args.get(clause):
            raise _Unsupported(f"unsupported clause: {clause}")
    if tree.args.get("joins"):
        raise _Unsupported("joins are outside the supported fragment")

    tables = list(tree.find_all(exp.Table))
    if len(tables) != 1:
        raise _Unsupported("exactly one table is required")
    table = tables[0].name.lower()

    projection: list[str] = []
    for item in tree.expressions:
        if isinstance(item, exp.Star):
            projection.append("*")
        elif isinstance(item, exp.Column):
            projection.append(item.name.lower())
        else:
            raise _Unsupported("only plain column or * projections are supported")

    where = tree.args.get("where")
    predicate = where.this if where is not None else None
    return _SupportedQuery(table=table, projection=tuple(projection), predicate=predicate)


# --- three-valued-logic encoding -----------------------------------------------------------


class _Env:
    """Symbolic columns shared across both queries, so the comparison is over the same row."""

    def __init__(self) -> None:
        self.columns: dict = {}

    def column(self, name: str):
        import z3

        if name not in self.columns:
            self.columns[name] = (z3.Bool(f"{name}__null"), z3.Int(f"{name}__val"))
        return self.columns[name]


# op name -> function on two z3 Int terms
def _apply(op: str, left, right):
    return {
        "=": left == right,
        "<>": left != right,
        "<": left < right,
        "<=": left <= right,
        ">": left > right,
        ">=": left >= right,
    }[op]


def _maybe_int(node) -> int | None:
    from sqlglot import exp

    if isinstance(node, exp.Paren):
        return _maybe_int(node.this)
    if isinstance(node, exp.Neg):
        inner = _maybe_int(node.this)
        return -inner if inner is not None else None
    if isinstance(node, exp.Literal) and node.is_int:
        return int(node.name)
    return None


def _term(node, env):
    """Return (defined_bool, int_term) for a column or integer-literal operand."""
    import z3
    from sqlglot import exp

    if isinstance(node, exp.Paren):
        return _term(node.this, env)
    value = _maybe_int(node)
    if value is not None:
        return z3.BoolVal(True), z3.IntVal(value)
    if isinstance(node, exp.Column):
        null_flag, int_val = env.column(node.name.lower())
        return z3.Not(null_flag), int_val
    raise _Unsupported(f"unsupported operand: {type(node).__name__}")


def _encode_cmp(left, op: str, right, env):
    import z3

    left_defined, left_term = _term(left, env)
    right_defined, right_term = _term(right, env)
    defined = z3.And(left_defined, right_defined)
    base = _apply(op, left_term, right_term)
    return z3.And(defined, base), z3.And(defined, z3.Not(base))


_CMP_OPS = {"EQ": "=", "NEQ": "<>", "LT": "<", "LTE": "<=", "GT": ">", "GTE": ">="}


def _encode(node, env):
    """Return (passes_true, is_false) z3 Booleans for a predicate under three-valued logic."""
    import z3
    from sqlglot import exp

    if node is None:
        return z3.BoolVal(True), z3.BoolVal(False)
    if isinstance(node, exp.Paren):
        return _encode(node.this, env)
    if isinstance(node, exp.And):
        lt, lf = _encode(node.left, env)
        rt, rf = _encode(node.right, env)
        return z3.And(lt, rt), z3.Or(lf, rf)
    if isinstance(node, exp.Or):
        lt, lf = _encode(node.left, env)
        rt, rf = _encode(node.right, env)
        return z3.Or(lt, rt), z3.And(lf, rf)
    if isinstance(node, exp.Not):
        true_part, false_part = _encode(node.this, env)
        return false_part, true_part
    if isinstance(node, exp.Boolean):
        return (z3.BoolVal(True), z3.BoolVal(False)) if node.this else (z3.BoolVal(False), z3.BoolVal(True))
    if isinstance(node, exp.Is):
        if isinstance(node.expression, exp.Null) and isinstance(node.this, exp.Column):
            null_flag, _ = env.column(node.this.name.lower())
            # IS NULL is a two-valued test: TRUE iff null, FALSE otherwise (never UNKNOWN).
            return null_flag, z3.Not(null_flag)
        raise _Unsupported("unsupported IS expression")
    if isinstance(node, exp.Between):
        low = node.args.get("low")
        high = node.args.get("high")
        lt, lf = _encode_cmp(node.this, ">=", low, env)
        rt, rf = _encode_cmp(node.this, "<=", high, env)
        return z3.And(lt, rt), z3.Or(lf, rf)
    if isinstance(node, exp.In):
        if node.args.get("query"):
            raise _Unsupported("IN subquery is not supported")
        options = node.args.get("expressions") or []
        if not options:
            raise _Unsupported("empty IN list")
        trues = []
        falses = []
        for option in options:
            t, f = _encode_cmp(node.this, "=", option, env)
            trues.append(t)
            falses.append(f)
        return z3.Or(*trues), z3.And(*falses)

    op = _CMP_OPS.get(type(node).__name__)
    if op is not None:
        return _encode_cmp(node.left, op, node.right, env)
    raise _Unsupported(f"unsupported predicate: {type(node).__name__}")


def _counterexample(env, model) -> dict:
    import z3

    row: dict = {}
    for name, (null_flag, int_val) in env.columns.items():
        if z3.is_true(model.eval(null_flag, model_completion=True)):
            row[name] = None
        else:
            row[name] = model.eval(int_val, model_completion=True).as_long()
    return row
