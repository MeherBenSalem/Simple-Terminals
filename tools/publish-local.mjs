import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const repo = path.resolve(process.argv[2] || '.');
const version = process.argv[3];
const dryRun = process.argv.includes('--dry-run');

// NightBeam publishes to CurseForge only (24 Nov 2026); the existing Modrinth project stays up as it is but gets
// no new versions.
if (!version) {
  throw new Error('Usage: node tools/publish-local.mjs <repo> <version> [--dry-run]');
}

function loadLocalEnvironment() {
  const candidates = [
    path.join(os.homedir(), 'NightBeam-Knowledge-Base', 'secrets', 'local.env'),
    path.join(os.homedir(), 'Desktop', 'local.env'),
  ];

  for (const envFile of candidates) {
    if (!fs.existsSync(envFile)) {
      continue;
    }

    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) {
        continue;
      }
      const equals = trimmed.indexOf('=');
      if (equals <= 0) {
        continue;
      }
      const name = trimmed.slice(0, equals).trim();
      if (!process.env[name]) {
        process.env[name] = trimmed.slice(equals + 1).trim();
      }
    }
  }
}

loadLocalEnvironment();

const curseForgeToken = process.env.CURSEFORGE_TOKEN;
const curseForgeApiKey = process.env.CURSEFORGE_API_KEY;
const curseForgeId = process.env.CURSEFORGE_ID || '1528758';

if (!curseForgeToken) {
  throw new Error('CURSEFORGE_TOKEN is not set');
}

const statePath = path.join(repo, 'build', 'publish-state.json');
const publishState = fs.existsSync(statePath)
  ? JSON.parse(fs.readFileSync(statePath, 'utf8'))
  : { curseforge: {} };

function savePublishState() {
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  fs.writeFileSync(statePath, `${JSON.stringify(publishState, null, 2)}\n`);
}

// One CurseForge file per jar; add targets here as ports land.
const targets = [
  { minecraft: '1.21.1', loader: 'fabric' },
  { minecraft: '1.21.1', loader: 'neoforge' },
];

function findJar(target) {
  const libs = path.join(repo, target.minecraft, target.loader, 'build', 'libs');
  if (!fs.existsSync(libs)) {
    throw new Error(`Missing build output directory: ${libs}`);
  }

  const suffix = `-${version}.jar`;
  const candidates = fs
    .readdirSync(libs)
    .filter((name) => name.startsWith(`omnivault-${target.loader}-${target.minecraft}-`) && name.endsWith(suffix))
    .filter((name) => !/(?:-sources|-javadoc|-dev|-dev-shadow)\.jar$/i.test(name))
    .map((name) => path.join(libs, name));

  if (candidates.length !== 1) {
    throw new Error(
      `Expected one ${target.minecraft} ${target.loader} ${version} jar, found ${candidates.length}`,
    );
  }
  return { ...target, jar: candidates[0] };
}

const releases = targets.map(findJar);
const changelog =
  fs
    .readFileSync(path.join(repo, 'CHANGELOG.md'), 'utf8')
    .split(/^## /m)
    .find((section) => section.startsWith(version))
    ?.replace(new RegExp(`^${version.replace(/\./g, '\\.')}[^\\n]*\\n+`), '')
    ?.trim() || `Release ${version}`;

async function responseText(response, service) {
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${service} ${response.status}: ${text.slice(0, 500)}`);
  }
  return text;
}

async function fetchCurseForgeVersions() {
  const response = await fetch('https://minecraft.curseforge.com/api/game/versions', {
    headers: { 'X-Api-Token': curseForgeToken },
  });
  return JSON.parse(await responseText(response, 'CurseForge versions'));
}

async function fetchCurseForgeFiles() {
  if (!curseForgeApiKey) {
    return [];
  }
  const response = await fetch(
    `https://api.curseforge.com/v1/mods/${curseForgeId}/files?pageSize=50`,
    { headers: { 'x-api-key': curseForgeApiKey } },
  );
  const result = JSON.parse(await responseText(response, 'CurseForge files'));
  return result.data || [];
}

function curseForgeVersionId(versions, name, preferredTypeId) {
  const named = versions.filter((item) => item.name.toLowerCase() === name.toLowerCase());
  const match = named.find((item) => item.gameVersionTypeID === preferredTypeId) || named[0];
  if (!match) {
    throw new Error(`CurseForge has no game version named ${name}`);
  }
  return match.id;
}

function curseForgeLoaderName(loader) {
  return { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge' }[loader];
}

async function publishCurseForge(release, versions, existingFiles) {
  const filename = path.basename(release.jar);
  if (publishState.curseforge[filename] || existingFiles.some((item) => item.fileName === filename)) {
    console.log(`CurseForge SKIP ${filename} (already exists)`);
    return;
  }

  if (dryRun) {
    console.log(`CurseForge DRY RUN ${filename}`);
    return;
  }

  const metadata = {
    changelog,
    changelogType: 'markdown',
    displayName: `OmniVault ${version} · ${release.loader} · ${release.minecraft}`,
    gameVersions: [
      curseForgeVersionId(versions, release.minecraft),
      curseForgeVersionId(versions, curseForgeLoaderName(release.loader), 68441),
      curseForgeVersionId(versions, 'Client', 75208),
      curseForgeVersionId(versions, 'Server', 75208),
    ],
    releaseType: 'release',
    ...(release.loader === 'fabric' ? { relations: { projects: [{ slug: 'fabric-api', type: 'requiredDependency' }] } } : {}),
  };
  const form = new FormData();
  form.append('metadata', JSON.stringify(metadata));
  form.append('file', new Blob([fs.readFileSync(release.jar)]), filename);

  const response = await fetch(
    `https://minecraft.curseforge.com/api/projects/${curseForgeId}/upload-file`,
    {
      method: 'POST',
      headers: { 'X-Api-Token': curseForgeToken },
      body: form,
    },
  );
  const result = JSON.parse(await responseText(response, 'CurseForge upload'));
  publishState.curseforge[filename] = result.id;
  savePublishState();
  console.log(`CurseForge OK ${filename} (${result.id})`);
}

console.log(`Publishing ${releases.length} separate ${version} releases from local jars:`);
for (const release of releases) {
  console.log(`- ${release.minecraft} ${release.loader}: ${path.basename(release.jar)}`);
}

const curseForgeVersions = await fetchCurseForgeVersions();
const existingCurseForge = await fetchCurseForgeFiles();
for (const release of releases) {
  await publishCurseForge(release, curseForgeVersions, existingCurseForge);
}

console.log('Local publishing complete.');
