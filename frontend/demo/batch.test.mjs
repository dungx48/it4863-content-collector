import { test } from 'node:test';
import assert from 'node:assert/strict';
import { collectBatch } from './public/batch.mjs';

test('batch continues after errors and removes duplicate text', async () => {
  const outcomes = [];
  const result = await collectBatch(['a', 'b', 'c'], async url => {
    if (url === 'c') throw new Error('fixture failed');
    return { url, text: 'same text' };
  }, { onResult: outcome => outcomes.push(outcome.status) });
  assert.deepEqual(outcomes, ['success', 'duplicate', 'failed']);
  assert.deepEqual(result.documents, [{ url: 'a', text: 'same text' }]);
});

test('batch stops before the next URL and keeps completed documents', async () => {
  const controller = new AbortController();
  const result = await collectBatch(['a', 'b'], async url => ({ url, text: url }), {
    signal: controller.signal,
    onResult: () => controller.abort(),
  });
  assert.equal(result.stopped, true);
  assert.deepEqual(result.documents, [{ url: 'a', text: 'a' }]);
});
