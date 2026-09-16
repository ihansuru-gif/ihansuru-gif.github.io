'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const EXPECTED_REPOSITORY = 'ihansuru-gif/ihansuru-gif.github.io';
const PAGES_ORIGIN = 'https://ihansuru-gif.github.io';
const CHANNEL_PATH = '/daborang-jitsi-screen-gallery/';
const MIN_BINARY_BYTES = 1024 * 1024;
const MAX_BINARY_BYTES = 1900 * 1024 * 1024;
const NOINDEX = '<meta name="robots" content="noindex,nofollow,noarchive,nosnippet,noimageindex">';

function fail(message) {
  throw new Error(`PUBLIC CHANNEL ERROR: ${message}`);
}

function argumentsFrom(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key?.startsWith('--') || value === undefined || value.startsWith('--')) fail('인수 형식이 올바르지 않습니다.');
    values.set(key.slice(2), value);
  }
  return values;
}

function required(values, name) {
  const value = String(values.get(name) || '').trim();
  if (!value) fail(`--${name} 값이 필요합니다.`);
  return value;
}

function insideRoot(root, candidate, label) {
  const resolved = path.resolve(root, candidate);
  const relative = path.relative(root, resolved);
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) fail(`${label} 경로는 저장소 내부여야 합니다.`);
  return resolved;
}

function inspectBinary(filePath, magic, label) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) fail(`${label} 파일이 없습니다.`);
  const size = fs.statSync(filePath).size;
  if (size < MIN_BINARY_BYTES || size > MAX_BINARY_BYTES) fail(`${label} 파일 크기가 허용 범위를 벗어났습니다.`);
  const descriptor = fs.openSync(filePath, 'r');
  try {
    const header = Buffer.alloc(magic.length);
    if (fs.readSync(descriptor, header, 0, header.length, 0) !== header.length || !header.equals(magic)) {
      fail(`${label} 파일 헤더가 올바르지 않습니다.`);
    }
  } finally {
    fs.closeSync(descriptor);
  }
  return size;
}

function sha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const input = fs.createReadStream(filePath);
    input.on('error', reject);
    input.on('data', (chunk) => hash.update(chunk));
    input.on('end', () => resolve(hash.digest('hex')));
  });
}

function cleanNotes(filePath) {
  if (!filePath || !fs.existsSync(filePath)) return [];
  return fs.readFileSync(filePath, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*(?:[-*+]\s+|#+\s*)/, '').trim())
    .filter(Boolean)
    .slice(0, 8)
    .map((line) => line.slice(0, 160));
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function component(value) {
  return encodeURIComponent(value).replace(/%2F/gi, '%252F');
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

async function main() {
  const root = path.resolve(__dirname, '..');
  const values = argumentsFrom(process.argv.slice(2));
  const tag = required(values, 'tag');
  const repository = required(values, 'repository');
  if (repository !== EXPECTED_REPOSITORY) fail(`공개 저장소는 ${EXPECTED_REPOSITORY}여야 합니다.`);
  if (!/^v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(tag)) fail('릴리스 태그 형식이 올바르지 않습니다.');
  const version = tag.slice(1);
  const assetsDirectory = insideRoot(root, required(values, 'assets'), '자산');
  const outputRoot = insideRoot(root, required(values, 'output'), '출력');
  const notesFile = values.get('notes-file') ? insideRoot(root, values.get('notes-file'), '릴리스 노트') : '';

  const names = {
    windows: `Dabolang-${version}-Windows-x64.exe`,
    arm64: `Dabolang-${version}-macOS-arm64.zip`,
    x64: `Dabolang-${version}-macOS-x64.zip`
  };
  const files = Object.fromEntries(Object.entries(names).map(([key, name]) => [key, path.join(assetsDirectory, name)]));
  const sizes = {
    windows: inspectBinary(files.windows, Buffer.from('MZ', 'ascii'), 'Windows EXE'),
    arm64: inspectBinary(files.arm64, Buffer.from('PK\x03\x04', 'binary'), 'Apple Silicon ZIP'),
    x64: inspectBinary(files.x64, Buffer.from('PK\x03\x04', 'binary'), 'Intel ZIP')
  };
  const [windowsHash, arm64Hash, x64Hash] = await Promise.all([
    sha256(files.windows), sha256(files.arm64), sha256(files.x64)
  ]);

  fs.rmSync(outputRoot, { recursive: true, force: true });
  const channelRoot = path.join(outputRoot, 'daborang-jitsi-screen-gallery');
  const updateRoot = path.join(channelRoot, 'update');
  const macRoot = path.join(channelRoot, 'mac');
  fs.mkdirSync(updateRoot, { recursive: true });
  fs.mkdirSync(macRoot, { recursive: true });
  fs.writeFileSync(path.join(outputRoot, '.nojekyll'), '', 'utf8');
  fs.writeFileSync(path.join(outputRoot, 'robots.txt'), `User-agent: *\nDisallow: ${CHANNEL_PATH}\n`, 'utf8');

  fs.copyFileSync(files.windows, path.join(updateRoot, names.windows));
  const publishedAt = new Date().toISOString();
  const windowsUrl = `${PAGES_ORIGIN}${CHANNEL_PATH}update/${component(names.windows)}`;
  writeJson(path.join(updateRoot, 'latest.json'), {
    schemaVersion: 1,
    version,
    publishedAt,
    notes: cleanNotes(notesFile),
    assets: {
      'windows-x64': { url: windowsUrl, sha256: windowsHash, size: sizes.windows }
    }
  });

  const releaseBase = `https://github.com/${repository}/releases/download/${component(tag)}/`;
  const macAssets = {
    arm64: {
      label: 'Apple Silicon (M1 이상)',
      url: `${releaseBase}${component(names.arm64)}`,
      sha256: arm64Hash,
      size: sizes.arm64
    },
    x64: {
      label: 'Intel Mac',
      url: `${releaseBase}${component(names.x64)}`,
      sha256: x64Hash,
      size: sizes.x64
    }
  };
  writeJson(path.join(macRoot, 'latest.json'), {
    schemaVersion: 1,
    version,
    publishedAt,
    assets: macAssets
  });

  const hiddenPage = `<!doctype html><html lang="ko"><head><meta charset="utf-8">${NOINDEX}<meta name="googlebot" content="noindex,nofollow,noarchive,nosnippet,noimageindex"><title>다볼랭</title></head><body></body></html>\n`;
  fs.writeFileSync(path.join(channelRoot, 'index.html'), hiddenPage, 'utf8');
  fs.writeFileSync(path.join(updateRoot, 'index.html'), hiddenPage, 'utf8');
  const macPage = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">${NOINDEX}<meta name="googlebot" content="noindex,nofollow,noarchive,nosnippet,noimageindex"><title>다볼랭 macOS</title>
<style>*{box-sizing:border-box}body{margin:0;background:#f6f7fa;color:#171b24;font-family:-apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Noto Sans KR",sans-serif}main{width:min(440px,calc(100% - 32px));margin:14vh auto;padding:26px;background:#fff;border-radius:16px;box-shadow:0 12px 34px #17203318}h1{margin:0 0 18px;font-size:21px}.downloads{display:grid;gap:10px}a{padding:13px 15px;border-radius:10px;background:#172033;color:#fff;text-align:center;text-decoration:none;font-weight:700}a:hover,a:focus-visible{background:#2b3850}small{display:block;margin-top:16px;color:#858c99;text-align:center}</style></head>
<body><main><h1>다볼랭 macOS</h1><div class="downloads"><a href="${escapeHtml(macAssets.arm64.url)}">Apple Silicon (M1 이상)</a><a href="${escapeHtml(macAssets.x64.url)}">Intel Mac</a></div><small>${escapeHtml(version)}</small></main></body></html>\n`;
  fs.writeFileSync(path.join(macRoot, 'index.html'), macPage, 'utf8');

  process.stdout.write(`READY ${version} windows=${windowsHash} arm64=${arm64Hash} x64=${x64Hash}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.message || error}\n`);
  process.exitCode = 1;
});
