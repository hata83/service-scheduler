/**
 * Extracts the inline SVG of an Archify viewer into a standalone SVG file.
 *
 * The viewer keeps the diagram as one inline SVG. The file uses the page
 * styles, so the script copies the style blocks into the SVG root. The
 * `viewBox` and the width keep the diagram scalable.
 *
 * Run: node tools/extract-diagram-svg.mjs <viewer.html> <output.svg>
 */
import { readFileSync, writeFileSync } from 'node:fs';

const [input, output] = process.argv.slice(2);

if (!input || !output) {
  console.error('usage: node tools/extract-diagram-svg.mjs <viewer.html> <output.svg>');
  process.exit(1);
}

const html = readFileSync(input, 'utf8');

const svgStart = html.indexOf('<svg');
if (svgStart < 0) {
  console.error(`no inline SVG in ${input}`);
  process.exit(1);
}

// The diagram SVG is the last one in the file. Match the tag that carries the
// diagram markers, so a decorative icon SVG never wins.
const marker = html.indexOf('data-node-ui');
const markerSvg = html.lastIndexOf('<svg', marker);
const start = markerSvg > svgStart ? markerSvg : svgStart;

const end = html.indexOf('</svg>', start);
if (end < 0) {
  console.error(`unterminated SVG in ${input}`);
  process.exit(1);
}

let svg = html.slice(start, end + '</svg>'.length);

// The viewer styles the diagram through the page CSS. Copy the style blocks
// into the SVG root so the file renders on its own. The CSS is plain text
// inside an XML element, so its angle brackets need an escape.
const styles = [...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) =>
  m[1].replace(/</g, '&lt;').replace(/>/g, '&gt;')
);
const styleBlock = styles.length ? `<style>${styles.join('\n')}</style>` : '';

// The SVG needs an explicit namespace to open as a standalone file.
svg = svg.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ');

// Insert the styles right after the opening tag.
svg = svg.replace(/(<svg[^>]*>)/, `$1${styleBlock}`);

// The viewer is HTML, so it holds bare ampersands. An SVG file is XML, so the
// ampersand needs an escape. An existing entity stays as it is.
svg = svg.replace(/&(?!#\d+;|#x[0-9a-fA-F]+;|[a-zA-Z][a-zA-Z0-9]*;)/g, '&amp;');

writeFileSync(output, svg, 'utf8');
console.log(`wrote ${output} (${svg.length} bytes, ${styles.length} style blocks)`);
