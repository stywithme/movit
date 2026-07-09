import fs from 'fs';
import path from 'path';

const modulesDir = 'src/modules';
const appModule = fs.readFileSync('src/app.module.ts', 'utf8');
const importedModules = [
  ...new Set(
    [...appModule.matchAll(/from '\.\/modules\/([^']+)/g)].map((m) => m[1].split('/')[0]),
  ),
];

function walk(dir, files = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, files);
    else if (e.name.endsWith('.controller.ts')) files.push(p);
  }
  return files;
}

const allControllers = walk(modulesDir);
const registered = allControllers.filter((f) => {
  const mod = f.replace(/\\/g, '/').match(/modules\/([^/]+)/)[1];
  return importedModules.includes(mod);
});

if (fs.existsSync('src/app.controller.ts')) registered.push('src/app.controller.ts');

const methodMap = {
  Get: 'GET',
  Post: 'POST',
  Put: 'PUT',
  Patch: 'PATCH',
  Delete: 'DELETE',
  Head: 'HEAD',
  Options: 'OPTIONS',
  All: 'ALL',
};

const routes = [];
for (const file of registered.sort()) {
  const content = fs.readFileSync(file, 'utf8');
  const ctrlMatch = content.match(/@Controller\(([^)]*)\)/);
  let base = '';
  if (ctrlMatch) {
    const raw = ctrlMatch[1].trim();
    if (raw.startsWith('{')) {
      const pathM = raw.match(/path:\s*['`]([^'`]+)['`]/);
      base = pathM ? pathM[1] : '';
    } else {
      const strM = raw.match(/['`]([^'`]+)['`]/);
      base = strM ? strM[1] : '';
    }
  }
  const re = /@(Get|Post|Put|Patch|Delete|Head|Options|All)\(([^)]*)\)/g;
  let m;
  while ((m = re.exec(content)) !== null) {
    let routePath = '';
    const arg = m[2].trim();
    if (arg) {
      const strM = arg.match(/['`]([^'`]*?)['`]/);
      routePath = strM ? strM[1] : '';
    }
    const full = (`/${base}/${routePath}`).replace(/\/+/g, '/').replace(/\/$/, '') || '/';
    routes.push({ method: methodMap[m[1]], path: full, file: file.replace(/\\/g, '/') });
  }
}

console.log('Total routes:', routes.length);
console.log('Controllers:', registered.length);
for (const r of routes) {
  console.log(`${r.method}\t${r.path}\t${r.file}`);
}
