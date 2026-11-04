import { describe, expect, it, vi } from 'vitest'
import { analyzeQuery, flattenPlan, type PlanNode } from './analyses'
import { createApiClient } from './client'

function node(id: number, nodeType: string, children: PlanNode[] = []): PlanNode {
  return {
    id,
    nodeType,
    parallelAware: false,
    relationName: null,
    alias: null,
    parentRelationship: null,
    startupCost: 0,
    totalCost: 0,
    planRows: 1,
    actualRows: null,
    actualLoops: null,
    actualTotalTimeMs: null,
    selfShare: 0,
    details: {},
    children,
  }
}

describe('analyzeQuery', () => {
  it('posts the query and mode as JSON', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(
      new Response('{"mode":"estimate"}', { headers: { 'Content-Type': 'application/json' } }),
    )
    const client = createApiClient({ baseUrl: 'http://api.test', fetch })

    await analyzeQuery(client, { sql: 'SELECT 1', mode: 'estimate' })

    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('http://api.test/api/v1/analyses')
    expect(init).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"sql":"SELECT 1","mode":"estimate"}',
    })
  })
})

describe('flattenPlan', () => {
  it('lists nodes in EXPLAIN reading order with their depth', () => {
    const plan = node(0, 'Hash Join', [node(1, 'Seq Scan'), node(2, 'Hash', [node(3, 'Seq Scan')])])

    expect(flattenPlan(plan).map(({ node, depth }) => [node.id, depth])).toEqual([
      [0, 0],
      [1, 1],
      [2, 1],
      [3, 2],
    ])
  })
})
