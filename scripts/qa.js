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
  attestation: `Dabolang-${version}-Windows-x64.legacy-compat.json`
};
const macDownloadFolderUrl = 'https://drive.google.com/drive/folders/1ycUT2dFEl6kkjgpZHcshUJSaTqAwLaWz';

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

function attestation(command, extraArguments = []) {
  return spawnSync(process.execPath, [
    path.join(root, 'scripts', 'legacy-compat-attestation.js'),
    command,
    ...extraArguments
  ], { cwd: root, encoding: 'utf8' });
}

try {
  fs.rmSync(qaRoot, { recursive: true, force: true });
  fs.mkdirSync(assetsRoot, { recursive: true });
  dummy(path.join(assetsRoot, names.windows), Buffer.from('MZ', 'ascii'), 0x57);
  fs.writeFileSync(path.join(qaRoot, 'notes.txt'), '- 새 기능\n- 오류 수정\n', 'utf8');

  const windowsPath = path.join(assetsRoot, names.windows);
  const attestationPath = path.join(assetsRoot, names.attestation);
  const windowsSize = fs.statSync(windowsPath).size;
  const createdAttestation = attestation('create', [
    '--version', version,
    '--windows-asset', names.windows,
    '--asset-path', windowsPath,
    '--pages-url', 'https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/compat-probe/Dabolang-compat-probe-p0.exe',
    '--gzip-content-length', String(windowsSize),
    '--identity-content-length', String(windowsSize),
    '--decoded-size', String(windowsSize),
    '--mz', 'true',
    '--workflow-run', 'https://github.com/ihansuru-gif/ihansuru-gif.github.io/actions/runs/123456789/attempts/1',
    '--output', attestationPath
  ]);
  assert.strictEqual(createdAttestation.status, 0, createdAttestation.stderr || createdAttestation.stdout);
  const verifiedAttestation = attestation('verify', [
    '--version', version,
    '--asset-path', windowsPath,
    '--attestation-path', attestationPath
  ]);
  assert.strictEqual(verifiedAttestation.status, 0, verifiedAttestation.stderr || verifiedAttestation.stdout);
  const attestationJson = JSON.parse(fs.readFileSync(attestationPath, 'utf8'));
  assert.deepStrictEqual(attestationJson.legacyBaselines, [
    '3.5.9-t.63', '3.5.9-t.64', '3.5.9-t.65', '3.5.9-t.66'
  ]);
  assert.strictEqual(attestationJson.size, windowsSize);
  assert.strictEqual(attestationJson.sha256, hash(windowsPath));
  assert.deepStrictEqual(attestationJson.observed, {
    gzipContentLength: windowsSize,
    identityContentLength: windowsSize,
    decodedSize: windowsSize,
    mz: true
  });

  const result = build();
  assert.strictEqual(result.status, 0, result.stderr || result.stdout);
  const channelRoot = path.join(siteRoot, 'daborang-jitsi-screen-gallery');
  const windowsManifest = JSON.parse(fs.readFileSync(path.join(channelRoot, 'update', 'latest.json'), 'utf8'));
  assert.strictEqual(windowsManifest.version, version);
  assert.strictEqual(windowsManifest.schemaVersion, 1);
  assert.match(windowsManifest.publishedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  assert.deepStrictEqual(windowsManifest.notes, ['새 기능', '오류 수정']);
  assert.deepStrictEqual(Object.keys(windowsManifest.assets), ['windows-x64']);
  assert.strictEqual(windowsManifest.assets['windows-x64'].url, `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/update/${names.windows}`);
  assert.match(windowsManifest.assets['windows-x64'].url, /^https:\/\//);
  assert.strictEqual(windowsManifest.assets['windows-x64'].sha256, hash(path.join(assetsRoot, names.windows)));
  assert.match(windowsManifest.assets['windows-x64'].sha256, /^[a-f0-9]{64}$/);
  assert.strictEqual(windowsManifest.assets['windows-x64'].size, fs.statSync(path.join(assetsRoot, names.windows)).size);
  assert.deepStrictEqual(
    fs.readFileSync(path.join(channelRoot, 'update', names.windows)),
    fs.readFileSync(path.join(assetsRoot, names.windows))
  );
  assert.strictEqual(fs.existsSync(path.join(channelRoot, 'mac', 'latest.json')), false);
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
  assert.match(macPage, new RegExp(macDownloadFolderUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  assert.match(macPage, /http-equiv="refresh"/);
  assert.match(macPage, /Google Drive에서 최신판 받기/);
  assert.match(macPage, /rel="noopener noreferrer external"/);
  assert.match(macPage, /referrerpolicy="no-referrer"/);
  assert.doesNotMatch(macPage, /Windows|\.exe|업데이트 확인/);
  assert.doesNotMatch(macPage, /releases\/download|macOS-(?:arm64|x64)\.zip/);

  const workflow = fs.readFileSync(path.join(root, '.github', 'workflows', 'deploy-daborang.yml'), 'utf8').replace(/\r\n/g, '\n');
  assert.match(workflow, /push:\s*\n\s+branches: \[main\]/);
  assert.match(workflow, /release:\s*\n\s+types: \[published\]/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /deploy:\s*\n\s+description:/);
  assert.match(workflow, /compat_probe_padding_bytes:\s*\n\s+description:/);
  assert.match(workflow, /compat_probe_source_tag:\s*\n\s+description:/);
  assert.match(workflow, /dispatch-release:\s*\n\s+if:.*github\.event_name == 'release'.*github\.event\.release\.draft == false.*github\.event\.release\.prerelease == false/);
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
  assert.match(workflow, /Download optional compatibility probe source/);
  assert.match(workflow, /inputs\.compat_probe_padding_bytes != ''/);
  assert.match(workflow, /inputs\.compat_probe_source_tag != ''/);
  assert.match(workflow, /PROBE_PADDING_BYTES.*inputs\.compat_probe_padding_bytes/);
  assert.match(workflow, /PROBE_SOURCE_TAG.*inputs\.compat_probe_source_tag/);
  assert.match(workflow, /\[\[ "\$PROBE_SOURCE_TAG" =~ \^v\[0-9\]\+/);
  assert.match(workflow, /probe_version="\$\{PROBE_SOURCE_TAG#v\}"/);
  assert.match(workflow, /probe_asset="Dabolang-\$\{probe_version\}-Windows-x64\.exe"/);
  assert.match(workflow, /--json isDraft,tagName,assets/);
  assert.match(workflow, /\.tagName == \\"\$PROBE_SOURCE_TAG\\"/);
  assert.match(workflow, /\.assets\[\]\.name \| select\(\. == \\"\$probe_asset\\"\)/);
  assert.match(workflow, /false\\ttrue\\t1/);
  assert.match(workflow, /compat-probe-source\/Dabolang-\$\{probe_version\}-Windows-x64\.exe/);
  assert.match(workflow, /incoming\/Dabolang-\$\{VERSION\}-Windows-x64\.exe/);
  assert.match(workflow, /truncate -s/);
  assert.match(workflow, /source_size \+ PROBE_PADDING_BYTES/);
  assert.match(workflow, /compat-probe/);
  assert.match(workflow, /Dabolang-\$\{VERSION\}-Windows-x64\.legacy-compat\.json/);
  assert.match(workflow, /Require matching legacy compatibility attestation/);
  assert.match(workflow, /legacy-compat-attestation\.js verify/);
  assert.match(workflow, /if:.*inputs\.compat_probe_padding_bytes == ''/);
  assert.match(workflow, /verify-compat-probe:\s*\n\s+needs: \[build, deploy\]/);
  assert.match(workflow, /Verify the deployed Pages probe/);
  assert.match(workflow, /for attempt in \$\(seq 1 36\)/);
  assert.match(workflow, /Accept-Encoding: identity/);
  assert.match(workflow, /Accept-Encoding: gzip/);
  assert.match(workflow, /--compressed/);
  assert.match(workflow, /identity_length.*EXPECTED_SIZE/);
  assert.match(workflow, /gzip_length.*EXPECTED_SIZE/);
  assert.match(workflow, /decoded_sha256.*EXPECTED_SHA256/);
  assert.match(workflow, /cmp --silent expected-probe\.exe downloaded-probe\.exe/);
  assert.match(workflow, /Create exact-byte compatibility attestation/);
  assert.match(workflow, /legacy-compat-attestation\.js create/);
  assert.match(workflow, /Attach compatibility attestation to the candidate release/);
  assert.match(workflow, /gh release upload.*\n[\s\S]*--clobber/);
  assert.match(workflow, /contents: write/);
  assert.match(workflow, /source_tag.*v3\.5\.9-t\.68/);
  assert.match(workflow, /93789016/);
  assert.match(workflow, /626dc5276af72dc8585a26a05e431b43bb441b468677e506dac4bb403a3ad4ae/);
  assert.strictEqual((workflow.match(/gh release download/g) || []).length, 3);
  assert.ok(workflow.indexOf('Require matching legacy compatibility attestation') < workflow.indexOf('node scripts/build-site.js'));
  assert.ok(workflow.indexOf('node scripts/build-site.js') < workflow.indexOf('Download optional compatibility probe source'));
  assert.ok(workflow.indexOf('Download optional compatibility probe source') < workflow.indexOf('Add optional unpublished legacy compatibility probe'));
  assert.doesNotMatch(workflow.slice(workflow.indexOf('Download optional compatibility probe source')), /latest\.json/);

  const readme = fs.readFileSync(path.join(root, 'README.md'), 'utf8');
  assert.match(readme, /compat_probe_source_tag/);
  assert.match(readme, /prerelease 공개 이벤트는 정식 채널을 자동 배포하지 않습니다/);
  assert.match(readme, /update\/latest\.json.*변경하지 않습니다/);
  assert.match(readme, /non-draft, non-prerelease 정식 Release/);
  assert.match(readme, /legacy-compat\.json/);
  assert.match(readme, /gzip·identity `Content-Length`/);
  assert.match(readme, /증명 JSON.*크기·SHA-256/);
  assert.match(readme, /누락·불일치 시 Pages를 변경하지 않고 실패/);
  assert.match(readme, /v3\.5\.9-t\.68.*부트스트랩/);

  const tamperedAttestation = { ...attestationJson, sha256: '0'.repeat(64) };
  const tamperedPath = path.join(assetsRoot, 'tampered-attestation.json');
  fs.writeFileSync(tamperedPath, `${JSON.stringify(tamperedAttestation)}\n`, 'utf8');
  const rejectedAttestation = attestation('verify', [
    '--version', version,
    '--asset-path', windowsPath,
    '--attestation-path', tamperedPath
  ]);
  assert.notStrictEqual(rejectedAttestation.status, 0);
  assert.match(rejectedAttestation.stderr, /증명 SHA-256/);
  assert.doesNotMatch(workflow, /macOS-(?:arm64|x64)\.zip/);
  assert.strictEqual((workflow.match(/gh release download/g) || []).length, 3);

  fs.writeFileSync(path.join(assetsRoot, names.windows), Buffer.from('NO', 'ascii'));
  const rejected = build(path.join(qaRoot, 'rejected-site'));
  assert.notStrictEqual(rejected.status, 0);
  assert.match(rejected.stderr, /Windows EXE 파일 크기|Windows EXE 파일 헤더/);
  process.stdout.write('PASS: public update channel validates Windows assets, enforces legacy attestation, and redirects macOS clients to Drive\n');
} finally {
  fs.rmSync(qaRoot, { recursive: true, force: true });
}
