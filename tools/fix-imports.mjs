/**
 * Adds the missing imports after the package move.
 *
 * The script builds the map of every class simple name to its package, then
 * adds the import for each referenced class that the file does not import and
 * that does not live in the same package.
 *
 * Run: node tools/fix-imports.mjs
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';

const mainRoot = 'service-scheduler-server/src/main/java/dev/hata/scheduler';
const testRoot = 'service-scheduler-server/src/test/java/dev/hata/scheduler';

/** Walks the tree and returns every Java source. */
function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full));
    } else if (entry.endsWith('.java')) {
      out.push(full);
    }
  }
  return out;
}

const mainFiles = walk(mainRoot);
const testFiles = walk(testRoot);

/** The simple name to package map of every production class. */
const packages = new Map();
for (const file of mainFiles) {
  const text = readFileSync(file, 'utf8');
  const packageMatch = text.match(/^package\s+([\w.]+);/m);
  const classMatch = text.match(/^(?:public\s+)?(?:final\s+)?(?:abstract\s+)?(?:class|interface|enum|record)\s+(\w+)/m);
  if (packageMatch && classMatch) {
    packages.set(classMatch[1], packageMatch[1]);
  }
}

/** The simple name to package map of every test class. */
const testPackages = new Map();
for (const file of testFiles) {
  const text = readFileSync(file, 'utf8');
  const packageMatch = text.match(/^package\s+([\w.]+);/m);
  const classMatch = text.match(/^(?:public\s+)?(?:final\s+)?(?:abstract\s+)?(?:class|interface|enum|record)\s+(\w+)/m);
  if (packageMatch && classMatch) {
    testPackages.set(classMatch[1], packageMatch[1]);
  }
}

let changed = 0;
for (const file of [...mainFiles, ...testFiles]) {
  let text = readFileSync(file, 'utf8');
  const ownPackage = text.match(/^package\s+([\w.]+);/m)?.[1] ?? '';
  const imported = new Set([...text.matchAll(/^import\s+(?:static\s+)?([\w.]+);/gm)].map((m) => m[1]));

  const body = text.replace(/^import\s+.*;$/gm, '');
  const needed = new Set();
  for (const [simple, packageName] of [...packages, ...testPackages]) {
    if (packageName === ownPackage) continue;
    if (imported.has(`${packageName}.${simple}`)) continue;
    if (!new RegExp(`\\b${simple}\\b`).test(body)) continue;
    needed.add(`${packageName}.${simple}`);
  }
  if (needed.size === 0) continue;

  const importBlock = [...needed].sort().map((fqn) => `import ${fqn};`).join('\n');
  const lines = text.split('\n');
  const importStart = lines.findIndex((line) => line.startsWith('import '));
  const lastImport = lines.reduce((last, line, index) => (line.startsWith('import ') ? index : last), -1);
  const packageLine = lines.findIndex((line) => line.startsWith('package '));
  const insertAt = packageLine + 1;
  lines.splice(insertAt, 0, '', importBlock);
  writeFileSync(file, lines.join('\n'), 'utf8');
  changed++;
  console.log(`imports added to ${file}: ${[...needed].join(', ')}`);
}

console.log(`${changed} files updated`);
