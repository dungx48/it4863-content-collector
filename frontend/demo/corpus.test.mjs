import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeCorpus } from './public/corpus.mjs';

test('normalizes one copied document and preserves a document array', () => {
  assert.deepEqual(JSON.parse(normalizeCorpus('{"text":"Một văn bản"}').json), [{ text: 'Một văn bản' }]);
  assert.equal(normalizeCorpus('[{"text":"Một"},{"text":"Hai"}]').count, 2);
});

test('rejects malformed JSON and documents without text', () => {
  assert.throws(() => normalizeCorpus('{'), /không phải JSON hợp lệ/);
  assert.throws(() => normalizeCorpus('{"title":"Thiếu nội dung"}'), /trường text/);
});
