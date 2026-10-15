import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { newProgramId } from '../src/identity.js';

test('offline CEF without randomUUID still creates distinct RFC 4122 version 4 IDs', () => {
  const offlineCrypto = { getRandomValues: bytes => webcrypto.getRandomValues(bytes) };
  const first = newProgramId(offlineCrypto), second = newProgramId(offlineCrypto);
  assert.match(first, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.notEqual(first, second);
});
