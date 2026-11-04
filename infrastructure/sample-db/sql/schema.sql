-- Sample e-commerce schema used as a QueryForge target database.
--
-- Some indexes are deliberately missing so that common bottlenecks show up in
-- execution plans:
--   * orders.customer_id    (foreign key without an index)
--   * orders.created_at     (range filters and sorts)
--   * order_items.product_id

CREATE TABLE customers (
	id           bigint PRIMARY KEY,
	email        text        NOT NULL UNIQUE,
	country_code char(2)     NOT NULL,
	created_at   timestamptz NOT NULL
);

CREATE TABLE products (
	id          bigint  PRIMARY KEY,
	sku         text    NOT NULL UNIQUE,
	category    text    NOT NULL,
	price_cents integer NOT NULL
);

CREATE TABLE orders (
	id          bigint      PRIMARY KEY,
	customer_id bigint      NOT NULL REFERENCES customers (id),
	status      text        NOT NULL,
	created_at  timestamptz NOT NULL,
	total_cents integer     NOT NULL
);

CREATE TABLE order_items (
	order_id         bigint  NOT NULL REFERENCES orders (id),
	product_id       bigint  NOT NULL REFERENCES products (id),
	quantity         integer NOT NULL,
	unit_price_cents integer NOT NULL,
	PRIMARY KEY (order_id, product_id)
);
