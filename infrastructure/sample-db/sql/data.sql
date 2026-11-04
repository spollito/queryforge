-- Deterministic synthetic data. Row counts scale with :scale
-- (1 = 50k customers, 5k products, 500k orders, about 1M order items).
-- Distributions are skewed on purpose (order status, country) so that
-- selective predicates exist.

SELECT greatest(1, round(50000 * :scale))::int AS customer_count,
	greatest(1, round(5000 * :scale))::int AS product_count,
	greatest(1, round(500000 * :scale))::int AS order_count
\gset

SELECT setseed(0.42);

INSERT INTO customers (id, email, country_code, created_at)
SELECT g,
	'customer' || g || '@example.com',
	(ARRAY['US', 'US', 'US', 'US', 'GB', 'DE', 'FR', 'CA', 'JP', 'BR'])[1 + (g % 10)],
	timestamptz '2023-01-01' + (g % 1000) * interval '1 day'
FROM generate_series(1, :customer_count) AS g;

INSERT INTO products (id, sku, category, price_cents)
SELECT g,
	'SKU-' || lpad(g::text, 6, '0'),
	(ARRAY['books', 'electronics', 'home', 'toys', 'garden', 'sports'])[1 + (g % 6)],
	199 + (g * 37) % 20000
FROM generate_series(1, :product_count) AS g;

INSERT INTO orders (id, customer_id, status, created_at, total_cents)
SELECT g,
	1 + floor(random() * :customer_count)::bigint,
	CASE
		WHEN r < 0.90 THEN 'delivered'
		WHEN r < 0.97 THEN 'shipped'
		WHEN r < 0.995 THEN 'cancelled'
		ELSE 'pending'
	END,
	timestamptz '2023-01-01' + random() * interval '1095 days',
	500 + floor(random() * 50000)::int
FROM (SELECT g, random() AS r FROM generate_series(1, :order_count) AS g) AS s;

-- One to three distinct products per order (the offsets are distinct modulo
-- the product count, so the primary key holds).
INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents)
SELECT o.id,
	1 + (o.id * 7919 + n * 104729) % :product_count,
	1 + (o.id + n) % 4,
	199 + ((o.id + n) * 37) % 20000
FROM orders AS o
CROSS JOIN LATERAL generate_series(0, (o.id % 3)::int) AS n
WHERE n < :product_count;

ANALYZE;
