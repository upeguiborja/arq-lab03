import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';

test('a stalled chat request leaves the thinking state', async () => {
  const html = readFileSync(new URL('../../main/resources/static/index.html', import.meta.url), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'chat page should have a script');

  const elements = new Map();
  const messages = { children: [], appendChild(child) { this.children.push(child); }, scrollHeight: 0, scrollTop: 0 };
  const input = { value: '', focus() {}, addEventListener() {} };
  const form = { listeners: {}, addEventListener(name, listener) { this.listeners[name] = listener; } };
  const welcome = { remove() { elements.delete('welcome'); } };
  elements.set('messages', messages);
  elements.set('message', input);
  elements.set('send', { disabled: false });
  elements.set('chat-form', form);
  elements.set('welcome', welcome);
  elements.set('reset', { addEventListener() {} });

  const document = {
    getElementById: id => elements.get(id),
    querySelectorAll: () => [],
    createElement: () => ({
      textContent: '',
      className: '',
      classList: { add() {} }
    })
  };
  const fetch = (_url, options) => new Promise((_resolve, reject) => {
    options.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  });
  runInNewContext(script, {
    document,
    fetch,
    crypto: { randomUUID: () => '87623b29-0f6f-4502-87eb-c20ace85ab7f' },
    AbortController,
    setTimeout: callback => setTimeout(callback, 20),
    clearTimeout
  });

  input.value = 'hello';
  form.listeners.submit({ preventDefault() {} });
  await new Promise(resolve => setTimeout(resolve, 80));

  assert.equal(messages.children.length, 2);
  assert.equal(messages.children[1].textContent, 'The assistant took too long to reply. Please try again.');
});
