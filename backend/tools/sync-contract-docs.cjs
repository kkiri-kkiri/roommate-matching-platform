// Read-only declaration snapshot checker. Physical DDL and behavior need separate tests.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const api = fs.readFileSync(path.join(root, 'API.md'), 'utf8');
const erd = fs.readFileSync(path.join(root, 'ERD.md'), 'utf8');
const failures = [];
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const compact = value => value.replace(/\s+/g, '');

function closing(text, start) {
  let depth = 0, quote = null;
  for (let i = start; i < text.length; i++) {
    const c = text[i];
    if (quote) {
      if (c === '\\') i++;
      else if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === '(') depth++;
    else if (c === ')' && --depth === 0) return i;
  }
  throw new Error('Unclosed declaration');
}

function fields(text) {
  const annotations = [];
  text = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  let plain = '';
  for (let i = 0; i < text.length;) {
    if (text[i] !== '@') { plain += text[i++]; continue; }
    const name = text.slice(i + 1).match(/^[\w.]+/)[0];
    annotations.push(name.split('.').at(-1)); i += name.length + 1;
    while (/\s/.test(text[i] || '') && i < text.length) i++;
    if (text[i] === '(') i = closing(text, i) + 1;
  }
  let depth = 0, start = 0;
  const result = [];
  for (let i = 0; i <= plain.length; i++) {
    if (plain[i] === '<') depth++;
    if (plain[i] === '>') depth--;
    if (i === plain.length || (plain[i] === ',' && depth === 0)) {
      const field = plain.slice(start, i).trim().match(/^(.+?)\s+(\w+)$/s);
      if (field) result.push(compact(`${field[2]}: ${field[1]}`));
      start = i + 1;
    }
  }
  return { result, annotations };
}

let dtoCount = 0;
for (const row of api.split(/\r?\n/)) {
  const link = row.match(/^\| \[(\w+)\]\((src\/main\/java\/[^)]+)\) \|/);
  if (!link) continue;
  const source = read(link[2]);
  const record = new RegExp(`\\brecord\\s+${link[1]}\\s*\\(`).exec(source);
  if (!record) continue; // Class DTOs are outside the record declaration snapshot.
  const start = source.indexOf('(', record.index);
  const actual = fields(source.slice(start + 1, closing(source, start)));
  const documented = [...row.matchAll(/`(\w+: [^`]+)`/g)].map(match => compact(match[1]));
  if (JSON.stringify(actual.result) !== JSON.stringify(documented)) failures.push(`${link[1]} record fields/types differ`);
  if (actual.annotations.filter(name => name === 'PasswordByteLength').length !== (row.match(/PasswordByteLength/g) || []).length) failures.push(`${link[1]} byte limit is undocumented`);
  dtoCount++;
}

let endpointCount = 0;
for (const match of api.matchAll(/^\| (GET|POST|PUT|PATCH|DELETE) \| ([^|]+) \| `([^`]+)` \| \[(\w+)\.(\w+)\]\((src\/main\/java\/[^)]+)\) \|/gm)) {
  const source = read(match[6]);
  const base = source.match(/@RequestMapping\(\s*"([^"]+)"\s*\)/)?.[1] || '';
  const mapping = [...source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^)]*)\))?[\s\S]*?public\s+(?:ApiResponse|ResponseEntity)<([\s\S]*?)>\s+(\w+)\s*\(/g)]
    .find(item => item[4] === match[5]);
  if (!mapping || mapping[1].toUpperCase() !== match[1]
      || base + (mapping[2]?.match(/"([^"]*)"/)?.[1] || '') !== match[2].trim()
      || compact(mapping[3]) !== compact(match[3])) failures.push(`${match[4]}.${match[5]} endpoint declaration differs`);
  endpointCount++;
}

let entityCount = 0;
const entities = [...erd.matchAll(/^### ([A-Z_]+)\r?\n\r?\n근거: \[[^\]]+\]\((src\/main\/java\/[^)]+)\)/gm)];
for (let i = 0; i < entities.length; i++) {
  const section = erd.slice(entities[i].index, entities[i + 1]?.index || erd.length);
  const source = read(entities[i][2]);
  let previous = 0;
  for (const field of source.matchAll(/private\s+(\w+)\s+(\w+)\s*;/g)) {
    const block = source.slice(previous, field.index); previous = field.index + field[0].length;
    const annotation = [...block.matchAll(/@(Column|JoinColumn)\s*\(/g)].at(-1);
    if (!annotation) continue;
    const opening = block.indexOf('(', annotation.index);
    const declaration = block.slice(opening + 1, closing(block, opening));
    const name = declaration.match(/name\s*=\s*"([^"]+)"/)?.[1];
    if (!name) continue;
    const row = section.split(/\r?\n/).find(line => line.startsWith(`| ${name} |`));
    if (!row) { failures.push(`${entities[i][1]}.${name} missing`); continue; }
    if (/nullable\s*=\s*false/.test(declaration) && !row.includes('NOT NULL')) failures.push(`${entities[i][1]}.${name} nullability differs`);
    if (/unique\s*=\s*true/.test(declaration) && !row.includes('UNIQUE')) failures.push(`${entities[i][1]}.${name} UNIQUE differs`);
    const definition = declaration.match(/columnDefinition\s*=\s*"([^"]+)"/)?.[1];
    const length = declaration.match(/length\s*=\s*(\d+)/)?.[1];
    const sqlType = definition || (field[1] === 'String' && length ? `VARCHAR(${length})` : null);
    if (sqlType && !row.includes(sqlType)) failures.push(`${entities[i][1]}.${name} SQL declaration differs`);
  }
  entityCount++;
}

const statusCodes = { BAD_REQUEST: 400, UNAUTHORIZED: 401, FORBIDDEN: 403, CONFLICT: 409,
  TOO_MANY_REQUESTS: 429, INTERNAL_SERVER_ERROR: 500, BAD_GATEWAY: 502, SERVICE_UNAVAILABLE: 503 };
for (const item of read('src/main/java/com/kkirikkiri/global/exception/ErrorCode.java').matchAll(/(\w+)\(HttpStatus\.(\w+),\s*"([^"]+)"\)/g)) {
  const row = api.split(/\r?\n/).find(line => line.startsWith(`| ${item[1]} |`));
  if (!row || !row.includes(item[3]) || !row.includes(`| ${statusCodes[item[2]]} |`)) failures.push(`${item[1]} error contract missing or outdated`);
}
if (failures.length) { console.error(failures.join('\n')); process.exitCode = 1; }
else console.log(`Contract declarations checked: ${endpointCount} endpoints, ${dtoCount} record DTOs, ${entityCount} entities.`);
