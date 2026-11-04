import type { ExplainMode } from './api'

export interface SampleQuery {
  label: string
  sql: string
  mode: ExplainMode
}

/**
 * Queries against the sample database (infrastructure/sample-db). Each one shows
 * a different plan shape; the ones whose problems only appear at run time use
 * execute mode.
 */
export const sampleQueries: SampleQuery[] = [
  {
    label: 'Orders for one customer',
    mode: 'estimate',
    sql: `SELECT id, status, total_cents
FROM orders
WHERE customer_id = 42;`,
  },
  {
    label: "Each customer's latest order",
    mode: 'estimate',
    sql: `SELECT c.id,
       (SELECT max(o.created_at)
        FROM orders o
        WHERE o.customer_id = c.id) AS last_order
FROM customers c
WHERE c.id <= 20;`,
  },
  {
    label: 'Newest orders first',
    mode: 'execute',
    sql: `SELECT id, customer_id, created_at
FROM orders
ORDER BY created_at DESC;`,
  },
  {
    label: 'Signups on one day in one country',
    mode: 'execute',
    sql: `SELECT id, email
FROM customers
WHERE country_code = 'GB'
  AND created_at = '2023-01-05 00:00:00+00';`,
  },
  {
    label: 'Order lines with several units',
    mode: 'execute',
    sql: `SELECT o.id, o.status, i.product_id, i.quantity
FROM order_items i
JOIN orders o ON o.id = i.order_id
WHERE i.quantity > 1;`,
  },
  {
    label: 'Revenue by country and status',
    mode: 'execute',
    sql: `SELECT c.country_code, o.status, sum(o.total_cents) AS revenue_cents
FROM orders o
JOIN customers c ON c.id = o.customer_id
GROUP BY c.country_code, o.status;`,
  },
]
