import pytest

from queryforge_orchestrator.models import VerificationStatus
from queryforge_orchestrator.verification.z3_verifier import Z3Verifier

verifier = Z3Verifier()


def verify(a, b):
    return verifier.verify(a, b)


# --- VERIFIED: genuinely equivalent predicates ---------------------------------------------


@pytest.mark.parametrize(
    "a, b",
    [
        ("SELECT id FROM orders WHERE customer_id = 42", "SELECT id FROM orders WHERE 42 = customer_id"),
        ("SELECT id FROM orders WHERE a >= 10 AND a <= 20", "SELECT id FROM orders WHERE a BETWEEN 10 AND 20"),
        ("SELECT id FROM orders WHERE a = 1 OR a = 2 OR a = 3", "SELECT id FROM orders WHERE a IN (1, 2, 3)"),
        ("SELECT id FROM orders WHERE a > 5 AND b < 3", "SELECT id FROM orders WHERE b < 3 AND a > 5"),
        ("SELECT id FROM orders WHERE customer_id = 42", "SELECT id FROM orders WHERE customer_id = 42"),
    ],
)
def test_equivalent_predicates_are_verified(a, b):
    result = verify(a, b)
    assert result.status is VerificationStatus.VERIFIED
    assert result.status.is_trustworthy_equivalence


# --- INVALID: non-equivalent, with a counterexample ----------------------------------------


def test_strict_vs_nonstrict_inequality_is_invalid_with_boundary_counterexample():
    result = verify("SELECT id FROM orders WHERE a > 5", "SELECT id FROM orders WHERE a >= 5")
    assert result.status is VerificationStatus.INVALID
    assert result.counterexample == {"a": 5}


def test_self_equality_is_not_true_under_null_semantics():
    # a = a is UNKNOWN (not TRUE) when a IS NULL, so it differs from TRUE. The counterexample
    # must be the NULL row — this is the whole point of modelling three-valued logic.
    result = verify("SELECT id FROM orders WHERE a = a", "SELECT id FROM orders WHERE true")
    assert result.status is VerificationStatus.INVALID
    assert result.counterexample == {"a": None}


def test_different_constants_are_invalid():
    result = verify("SELECT id FROM orders WHERE a = 1", "SELECT id FROM orders WHERE a = 2")
    assert result.status is VerificationStatus.INVALID


# --- UNSUPPORTED: outside the fragment, never claimed as verified --------------------------


@pytest.mark.parametrize(
    "a, b, why",
    [
        ("SELECT status FROM orders WHERE status = 'paid'", "SELECT status FROM orders WHERE status = 'paid'",
         "string literal"),
        ("SELECT id FROM orders", "SELECT id, status FROM orders", "projection differs"),
        ("SELECT id FROM orders", "SELECT id FROM customers", "different table"),
        ("SELECT count(*) FROM orders", "SELECT count(*) FROM orders", "aggregation"),
        ("SELECT id FROM orders WHERE a = 1 ORDER BY a LIMIT 10", "SELECT id FROM orders WHERE a = 1", "order/limit"),
        ("SELECT o.id FROM orders o JOIN customers c ON o.customer_id = c.id",
         "SELECT o.id FROM orders o JOIN customers c ON o.customer_id = c.id", "join"),
    ],
)
def test_out_of_fragment_is_unsupported(a, b, why):
    result = verify(a, b)
    assert result.status is VerificationStatus.UNSUPPORTED, why
    assert not result.status.is_trustworthy_equivalence


def test_identical_string_query_is_unsupported_not_verified():
    # Conservative: we cannot encode strings, so even identical string predicates are not
    # claimed as verified (safe — leads to fallback, never a false VERIFIED).
    result = verify("SELECT id FROM orders WHERE email = 'x@y.z'", "SELECT id FROM orders WHERE email = 'x@y.z'")
    assert result.status is VerificationStatus.UNSUPPORTED
