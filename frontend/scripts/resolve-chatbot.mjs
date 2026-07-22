/**
 * Resolves optional chatbot/training module for the frontend.
 * Delegates to resolve-modules.mjs (supports --enable/--disable as chatbot toggles).
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2).map((a) => {
  if (a === '--enable') return '--enable-chatbot';
  if (a === '--disable') return '--disable-chatbot';
  return a;
});
const result = spawnSync(process.execPath, [path.join(__dirname, 'resolve-modules.mjs'), ...args], {
  stdio: 'inherit',
});
process.exit(result.status ?? 1);
