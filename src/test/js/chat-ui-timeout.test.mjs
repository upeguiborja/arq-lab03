import assert from 'node:assert/strict'
import { test } from 'node:test'
import { fetchChat } from '../../../ui/src/fetchChat.mjs'

test('a stalled chat request times out even with an SDK cancellation signal', async t => {
  const deadline = new AbortController()
  t.mock.method(AbortSignal, 'timeout', ms => {
    assert.equal(ms, 60_000)
    return deadline.signal
  })
  t.mock.method(globalThis, 'fetch', (_input, { signal }) => new Promise((_, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason))
  }))
  const request = fetchChat('/api/chat/stream', { signal: new AbortController().signal })
  deadline.abort(new DOMException('Timeout', 'TimeoutError'))
  await assert.rejects(request, { name: 'TimeoutError' })
})
