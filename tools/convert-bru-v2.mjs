/**
 * Converts Bruno v1 request files to the Bruno v2 language.
 *
 * v1:  test "name" { expect(res.status)... }
 * v2:  tests { test("name", function() { expect(res.getStatus())... }); }
 *
 * Run: node tools/convert-bru-v2.mjs
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

function convert(text) {
  const lines = text.split(/\r?\n/);
  const out = [];
  let inTests = false;

  for (const line of lines) {
    const open = line.match(/^test\s+"(.*)"\s*\{\s*$/);
    if (open) {
      if (!inTests) {
        out.push('tests {');
        inTests = true;
      } else {
        out.push('');
      }
      out.push(`  test("${open[1]}", function() {`);
      continue;
    }
    if (inTests && line.trim() === '}') {
      out.push('  });');
      continue;
    }
    if (inTests) {
      out.push('  ' + line.replace(/res\.status/g, 'res.getStatus()').replace(/res\.body/g, 'res.getBody()'));
      continue;
    }
    out.push(line);
  }

  if (inTests) {
    out.push('}');
  }

  let result = out.join('\n');
  result = result.replace(/res\.headers\[([^\]]+)\]/g, 'res.getHeader($1)');
  result = result.replace(/res\.headers\b/g, 'res.getHeaders()');
  result = result.replace(/res\.body\b/g, 'res.getBody()');
  result = result.replace(/res\.status\b/g, 'res.getStatus()');
  return result;
}

let changed = 0;
for (const file of walk(root)) {
  const before = readFileSync(file, 'utf8');
  const after = convert(before);
  if (before !== after) {
    writeFileSync(file, after);
    changed++;
    console.log('converted ' + file);
  }
}
console.log(`${changed} files converted`);
