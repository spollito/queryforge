"""Structured schema-metadata retrieval (SPEC.md §5, §10).

Schema metadata (tables, columns, types, keys, indexes, row estimates) is read from the
target database by the **Java API**, in the same read-only transaction it uses for EXPLAIN —
the Python service never connects to a database. This module defines the typed context and a
provider interface:

- :class:`StaticSchemaProvider` serves a fixed context (the sample e-commerce schema), used
  for offline development and deterministic tests.
- :class:`HttpSchemaProvider` fetches it from the Java API. The Java endpoint is added in the
  Phase 3 integration task; until then the HTTP provider is wired but unused by tests.

All of this is untrusted **data** for the prompt, never instructions (SPEC.md §8).
"""

from __future__ import annotations

import abc

from pydantic import BaseModel, Field


class ColumnInfo(BaseModel):
    model_config = {"extra": "forbid"}

    name: str
    data_type: str
    nullable: bool = True


class IndexInfo(BaseModel):
    model_config = {"extra": "forbid"}

    name: str
    columns: list[str]
    unique: bool = False


class TableInfo(BaseModel):
    model_config = {"extra": "forbid"}

    name: str
    columns: list[ColumnInfo] = Field(default_factory=list)
    primary_key: list[str] = Field(default_factory=list)
    foreign_keys: dict[str, str] = Field(
        default_factory=dict, description="column -> referenced table.column"
    )
    indexes: list[IndexInfo] = Field(default_factory=list)
    estimated_rows: float | None = None


class SchemaContext(BaseModel):
    model_config = {"extra": "forbid"}

    tables: list[TableInfo] = Field(default_factory=list)

    def table(self, name: str) -> TableInfo | None:
        for table in self.tables:
            if table.name == name:
                return table
        return None

    def to_prompt_text(self) -> str:
        """Compact textual rendering for the LLM prompt (schema only, no rows)."""
        lines: list[str] = []
        for table in self.tables:
            cols = ", ".join(
                f"{c.name} {c.data_type}{'' if c.nullable else ' NOT NULL'}" for c in table.columns
            )
            lines.append(f"TABLE {table.name} ({cols})")
            if table.primary_key:
                lines.append(f"  PK: {', '.join(table.primary_key)}")
            for col, ref in table.foreign_keys.items():
                lines.append(f"  FK: {col} -> {ref}")
            for index in table.indexes:
                kind = "UNIQUE INDEX" if index.unique else "INDEX"
                lines.append(f"  {kind} {index.name} ({', '.join(index.columns)})")
            if table.estimated_rows is not None:
                lines.append(f"  ~rows: {int(table.estimated_rows)}")
        return "\n".join(lines)


class SchemaProvider(abc.ABC):
    @abc.abstractmethod
    def fetch(self, tables: list[str] | None = None) -> SchemaContext:
        """Return schema context, optionally limited to ``tables``."""
        raise NotImplementedError


#: The sample e-commerce schema (infrastructure/sample-db/sql/schema.sql), with the indexes
#: that schema deliberately omits left out, so retrieval reflects the real target.
SAMPLE_SCHEMA = SchemaContext(
    tables=[
        TableInfo(
            name="customers",
            columns=[
                ColumnInfo(name="id", data_type="bigint", nullable=False),
                ColumnInfo(name="email", data_type="text", nullable=False),
                ColumnInfo(name="country_code", data_type="char(2)", nullable=False),
                ColumnInfo(name="created_at", data_type="timestamptz", nullable=False),
            ],
            primary_key=["id"],
            indexes=[IndexInfo(name="customers_email_key", columns=["email"], unique=True)],
        ),
        TableInfo(
            name="products",
            columns=[
                ColumnInfo(name="id", data_type="bigint", nullable=False),
                ColumnInfo(name="sku", data_type="text", nullable=False),
                ColumnInfo(name="category", data_type="text", nullable=False),
                ColumnInfo(name="price_cents", data_type="integer", nullable=False),
            ],
            primary_key=["id"],
            indexes=[IndexInfo(name="products_sku_key", columns=["sku"], unique=True)],
        ),
        TableInfo(
            name="orders",
            columns=[
                ColumnInfo(name="id", data_type="bigint", nullable=False),
                ColumnInfo(name="customer_id", data_type="bigint", nullable=False),
                ColumnInfo(name="status", data_type="text", nullable=False),
                ColumnInfo(name="created_at", data_type="timestamptz", nullable=False),
                ColumnInfo(name="total_cents", data_type="integer", nullable=False),
            ],
            primary_key=["id"],
            foreign_keys={"customer_id": "customers.id"},
            # orders.customer_id and orders.created_at are intentionally unindexed.
        ),
        TableInfo(
            name="order_items",
            columns=[
                ColumnInfo(name="order_id", data_type="bigint", nullable=False),
                ColumnInfo(name="product_id", data_type="bigint", nullable=False),
                ColumnInfo(name="quantity", data_type="integer", nullable=False),
                ColumnInfo(name="unit_price_cents", data_type="integer", nullable=False),
            ],
            primary_key=["order_id", "product_id"],
            foreign_keys={"order_id": "orders.id", "product_id": "products.id"},
            # order_items.product_id is intentionally unindexed.
        ),
    ]
)


class StaticSchemaProvider(SchemaProvider):
    """Serves a fixed :class:`SchemaContext` (defaults to the sample schema)."""

    def __init__(self, context: SchemaContext = SAMPLE_SCHEMA) -> None:
        self._context = context

    def fetch(self, tables: list[str] | None = None) -> SchemaContext:
        if tables is None:
            return self._context
        wanted = set(tables)
        return SchemaContext(tables=[t for t in self._context.tables if t.name in wanted])


class HttpSchemaProvider(SchemaProvider):
    """Fetches schema context from the Java API (endpoint added in Phase 3)."""

    def __init__(self, base_url: str, timeout_s: float = 5.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_s = timeout_s

    def fetch(self, tables: list[str] | None = None) -> SchemaContext:  # pragma: no cover
        import httpx

        params = {"tables": ",".join(tables)} if tables else None
        response = httpx.get(
            f"{self._base_url}/api/v1/schema", params=params, timeout=self._timeout_s
        )
        response.raise_for_status()
        return SchemaContext.model_validate(response.json())
