const { test } = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { registerSecret, redactOutput, sanitize } = require('./run.cjs');
test('P1 variable-length UTF8 boundary values are redacted in nested errors and console text', () => {
  const value = 'A1!' + '가'.repeat(23);
  registerSecret(value);
  assert.equal(Buffer.byteLength(value),72);
  assert.equal(redactOutput('fill: '+value).includes(value),false);
  assert.deepEqual(sanitize({ error: { message: value } }), { error: { message: '[REDACTED]' } });
});
test('malformed P1 selectors stop before filesystem or Docker preparation', () => {
  for (const args of [['--aus-p1','extra'], ['--aus-p1-recheck'], ['--aus-p1-recheck','../p0']]) {
    const r=spawnSync(process.execPath,[require.resolve('./run.cjs'),...args],{encoding:'utf8',windowsHide:true});
    assert.notEqual(r.status,0); assert.equal(r.stdout.includes('[e2e] Evidence:'),false);
  }
});
