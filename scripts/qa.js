'use strict';

const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const root = path.resolve(__dirname, '..');
const qaRoot = path.join(root, '.qa-tmp');
const assetsRoot = path.join(qaRoot, 'assets');
const siteRoot = path.join(qaRoot, 'site');
const version = '9.8.7-t.6';
const tag = `v${version}`;
const names = {
  windows: `Dabolang-${version}-Windows-x64.exe`,
  arm64: `Dabolang-${version}-macOS-arm64.zip`,
  x64: `Dabolang-${version}-macOS-x64.zip`
};
const macDriveUrl = 'https://drive.google.com/drive/folders/1ycUT2dFEl6kkjgpZHcshUJSaTqAwLaWz';

function dummy(filePath, header, byte) {
  const descriptor = fs.openSync(filePath, 'w');
  try {
    fs.writeSync(descriptor, header, 0, header.length, 0);
    fs.writeSync(descriptor, Buffer.from([byte]), 0, 1, 1024 * 1024 + 127);
  } finally {
    fs.closeSync(descriptor);
  }
}

function hash(filePath) {
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function build(output = siteRoot) {
  return spawnSync(process.execPath, [
    path.join(root, 'scripts', 'build-site.js'),
    '--tag', tag,
    '--repository', 'ihansuru-gif/ihansuru-gif.github.io',
    '--assets', path.relative(root, assetsRoot),
    '--notes-file', path.relative(root, path.join(qaRoot, 'notes.txt')),
    '--output', path.relative(root, output)
  ], { cwd: root, encoding: 'utf8' });
}

try {
  fs.rmSync(qaRoot, { recursive: true, force: true });
  fs.mkdirSync(assetsRoot, { recursive: true });
  dummy(path.join(assetsRoot, names.windows), Buffer.from('MZ', 'ascii'), 0x57);
  dummy(path.join(assetsRoot, names.arm64), Buffer.from('PK\x03\x04', 'binary'), 0x41);
  dummy(path.join(assetsRoot, names.x64), Buffer.from('PK\x03\x04', 'binary'), 0x58);
  fs.writeFileSync(path.join(qaRoot, 'notes.txt'), '- 새 기능\n- 오류 수정\n', 'utf8');

  const result = build();
  assert.strictEqual(result.status, 0, result.stderr || result.stdout);
  const channelRoot = path.join(siteRoot, 'daborang-jitsi-screen-gallery');
  const windowsManifest = JSON.parse(fs.readFileSync(path.join(channelRoot, 'update', 'latest.json'), 'utf8'));
  const macManifest = JSON.parse(fs.readFileSync(path.join(channelRoot, 'mac', 'latest.json'), 'utf8'));
  assert.strictEqual(windowsManifest.version, version);
  assert.deepStrictEqual(windowsManifest.notes, ['새 기능', '오류 수정']);
  assert.strictEqual(windowsManifest.assets['windows-x64'].url, `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/update/${names.windows}`);
  assert.strictEqual(windowsManifest.assets['windows-x64'].sha256, hash(path.join(assetsRoot, names.windows)));
  assert.strictEqual(windowsManifest.assets['windows-x64'].size, fs.statSync(path.join(assetsRoot, names.windows)).size);
  assert.deepStrictEqual(
    fs.readFileSync(path.join(channelRoot, 'update', names.windows)),
    fs.readFileSync(path.join(assetsRoot, names.windows))
  );
  assert.strictEqual(macManifest.assets.arm64.url, `https://github.com/ihansuru-gif/ihansuru-gif.github.io/releases/download/${tag}/${names.arm64}`);
  assert.strictEqual(macManifest.assets.x64.url, `https://github.com/ihansuru-gif/ihansuru-gif.github.io/releases/download/${tag}/${names.x64}`);
  assert.strictEqual(macManifest.assets.arm64.sha256, hash(path.join(assetsRoot, names.arm64)));
  assert.strictEqual(macManifest.assets.x64.sha256, hash(path.join(assetsRoot, names.x64)));
  assert.strictEqual(macManifest.manualDownloadUrl, macDriveUrl);
  assert.strictEqual(fs.existsSync(path.join(channelRoot, 'mac', names.arm64)), false);
  assert.strictEqual(fs.existsSync(path.join(channelRoot, 'mac', names.x64)), false);
  assert.strictEqual(fs.readFileSync(path.join(siteRoot, 'robots.txt'), 'utf8'), 'User-agent: *\nDisallow: /daborang-jitsi-screen-gallery/\n');
  for (const page of [
    path.join(channelRoot, 'index.html'),
    path.join(channelRoot, 'update', 'index.html'),
    path.join(channelRoot, 'mac', 'index.html')
  ]) {
    const html = fs.readFileSync(page, 'utf8');
    assert.match(html, /noindex,nofollow,noarchive,nosnippet,noimageindex/);
  }
  const macPage = fs.readFileSync(path.join(channelRoot, 'mac', 'index.html'), 'utf8');
  assert.match(macPage, /http-equiv="refresh"/);
  assert.match(macPage, /window\.location\.replace/);
  assert.match(macPage, /Google Drive 열기/);
  assert.ok(macPage.includes(macDriveUrl));
  assert.doesNotMatch(macPage, /Windows|\.exe|업데이트 확인/);

  const workflow = fs.readFileSync(path.join(root, '.github', 'workflows', 'deploy-daborang.yml'), 'utf8').replace(/\r\n/g, '\n');
  assert.match(workflow, /push:\s*\n\s+branches: \[main\]/);
  assert.match(workflow, /release:\s*\n\s+types: \[published\]/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /deploy:\s*\n\s+description:/);
  assert.match(workflow, /compat_probe_release_id:\s*\n\s+description:/);
  assert.match(workflow, /compat_probe_asset_name:\s*\n\s+description:/);
  assert.match(workflow, /dispatch-release:\s*\n\s+if:.*github\.event_name == 'release'/);
  assert.match(workflow, /actions: write/);
  assert.match(workflow, /gh workflow run deploy-daborang\.yml/);
  assert.match(workflow, /--ref main/);
  assert.match(workflow, /-f release_tag=/);
  assert.match(workflow, /-f deploy=true/);
  assert.match(workflow, /github\.event_name == 'workflow_dispatch' && inputs\.deploy/);
  assert.match(workflow, /qa:\s*\n\s+if:.*github\.event_name != 'release'.*\n\s+runs-on:/);
  assert.match(workflow, /build:\s*\n\s+needs: qa/);
  assert.match(workflow, /Require a stable published release/);
  assert.match(workflow, /--json isDraft,isPrerelease/);
  assert.match(workflow, /\.isDraft == false and \.isPrerelease == false/);
  assert.match(workflow, /actions\/checkout@v7/);
  assert.match(workflow, /actions\/setup-node@v7/);
  assert.match(workflow, /node-version: 24/);
  assert.match(workflow, /actions\/configure-pages@v6/);
  assert.match(workflow, /actions\/upload-pages-artifact@v5/);
  assert.match(workflow, /actions\/deploy-pages@v5/);
  assert.match(workflow, /pages: write/);
  assert.match(workflow, /id-token: write/);
  assert.match(workflow, /--json isDraft,isPrerelease/);
  assert.match(workflow, /false\\tfalse/);
  assert.match(workflow, /Add optional unpublished legacy compatibility probe/);
  assert.match(workflow, /inputs\.compat_probe_release_id != '' \|\| inputs\.compat_probe_asset_name != ''/);
  assert.match(workflow, /\^Dabolang-compat-probe-\[0-9A-Za-z\._-\]\+\\\.exe\$/);
  assert.match(workflow, /releases\/assets\/\$asset_id/);
  assert.match(workflow, /compat-probe/);
  assert.strictEqual((workflow.match(/gh release download/g) || []).length, 1);

  fs.writeFileSync(path.join(assetsRoot, names.windows), Buffer.from('NO', 'ascii'));
  const rejected = build(path.join(qaRoot, 'rejected-site'));
  assert.notStrictEqual(rejected.status, 0);
  assert.match(rejected.stderr, /Windows EXE 파일 크기|Windows EXE 파일 헤더/);
  process.stdout.write('PASS: public update channel validates three assets and redirects existing macOS clients to the fixed Google Drive folder\n');
} finally {
  fs.rmSync(qaRoot, { recursive: true, force: true });
}
