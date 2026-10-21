const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');

// Explicit allowlist: never touch existing test evidence, uploads, migrations or other build outputs.
for (const relative of ['frontend/test-results', 'frontend/playwright-report', 'frontend/.cache/e2e',
  'backend/build/e2e', 'backend/build/classes/java/e2e', 'backend/build/tmp/compileE2eJava',
  'backend/build/generated/sources/annotationProcessor/java/e2e', 'backend/build/generated/sources/headers/java/e2e']) {
  const target = path.resolve(root, relative);
  if (!target.startsWith(root + path.sep)) throw new Error('Cleanup target escaped the repository.');
  if (!fs.existsSync(target)) continue;
  if (fs.lstatSync(target).isSymbolicLink() || fs.realpathSync(target) !== target) {
    throw new Error(`Refusing cleanup through a link: ${relative}`);
  }
  fs.rmSync(target, { recursive: true });
  console.log(`Removed ${relative}`);
}
