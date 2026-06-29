// Assemble a clean web directory for Capacitor (no node_modules/tests/docs).
// Buildless: this just copies the app shell + assets into dist-web/.
import { cp, rm, mkdir } from 'node:fs/promises';

const OUT = 'dist-web';
const ITEMS = ['index.html', 'manifest.webmanifest', 'sw.js', 'assets'];

await rm(OUT, { recursive: true, force: true });
await mkdir(OUT, { recursive: true });
for (const item of ITEMS) {
  await cp(item, `${OUT}/${item}`, { recursive: true });
}
console.log(`built ${OUT}/ with: ${ITEMS.join(', ')}`);
