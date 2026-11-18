from queryforge_orchestrator.retrieval.schema import (
    SAMPLE_SCHEMA,
    StaticSchemaProvider,
)


def test_static_provider_returns_full_sample_schema():
    context = StaticSchemaProvider().fetch()
    names = {table.name for table in context.tables}
    assert names == {"customers", "products", "orders", "order_items"}


def test_static_provider_can_limit_to_requested_tables():
    context = StaticSchemaProvider().fetch(tables=["orders"])
    assert [t.name for t in context.tables] == ["orders"]


def test_orders_customer_id_is_unindexed_in_sample():
    orders = SAMPLE_SCHEMA.table("orders")
    assert orders is not None
    assert "customer_id" in orders.foreign_keys
    indexed_columns = {col for index in orders.indexes for col in index.columns}
    assert "customer_id" not in indexed_columns


def test_prompt_text_renders_tables_keys_and_indexes_without_rows():
    text = SAMPLE_SCHEMA.to_prompt_text()
    assert "TABLE orders" in text
    assert "FK: customer_id -> customers.id" in text
    assert "UNIQUE INDEX customers_email_key (email)" in text
    # No row data should ever appear in schema context (SPEC.md §8).
    assert "@" not in text  # a crude check that no sample email leaked in
