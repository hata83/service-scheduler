/**
 * Renames the Bruno v1 environment helpers to the Bruno v2 names.
 *
 * v1: bru.setEnv(name, value) / bru.getEnv(name)
 * v2: bru.setEnvVar(name, value) / bru.getEnvVar(name)
 *
 * Run: node tools/rename-bru-env-v2.mjs
 */
import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = process.argv[2] ?? 'service-scheduler-server/src/test/bruno';

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full));
    } else if (entry.endsWith('.bru')) {
      out.push(full);
    }
  }
  return out;
}

let changed = 0;
for (const file of walk(root)) {
  const before = readFileSync(file, 'utf8');
  const after = before
    .replace(/bru\.setEnv\(/g, 'bru.setEnvVar(')
    .replace(/bru\.getEnv\(/g, 'bru.getEnvVar(');
  if (before !== after) {
    writeFileSync(file, after);
    changed++;
  }
}
console.log(`${changed} files updated`);
