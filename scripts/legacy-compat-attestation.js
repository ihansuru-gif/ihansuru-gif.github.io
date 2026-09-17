'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const EXPECTED_REPOSITORY = 'ihansuru-gif/ihansuru-gif.github.io';
const PAGES_ORIGIN = 'https://ihansuru-gif.github.io';
const PROBE_PATH = '/daborang-jitsi-screen-gallery/compat-probe/Dabolang-compat-probe-p0.exe';
const LEGACY_BASELINES = Object.freeze([
  '3.5.9-t.63',
  '3.5.9-t.64',
  '3.5.9-t.65',
  '3.5.9-t.66'
]);
const MIN_BINARY_BYTES = 1024 * 1024;
const MAX_BINARY_BYTES = 1900 * 1024 * 1024;

function fail(message) {
  throw new Error(`LEGACY COMPAT ERROR: ${message}`);
}

function parseArguments(argv) {
  const command = String(argv[0] || '').trim();
  const values = new Map();
  for (let index = 1; index < argv.length; index += 2) {
    const key = argv[index];
    const value = argv[index + 1];
    if (!key?.startsWith('--') || value === undefined || value.startsWith('--')) fail('인수 형식이 올바르지 않습니다.');
    values.set(key.slice(2), value);
  }
  return { command, values };
}

function required(values, name) {
  const value = String(values.get(name) || '').trim();
  if (!value) fail(`--${name} 값이 필요합니다.`);
  return value;
}

function positiveInteger(value, label) {
  if (!/^\d+$/.test(String(value || ''))) fail(`${label} 값은 0 이상의 정수여야 합니다.`);
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) fail(`${label} 값이 안전한 정수가 아닙니다.`);
  return number;
}

function inspectExecutable(filePath) {
  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) fail(`Windows EXE가 없습니다: ${resolved}`);
  const size = fs.statSync(resolved).size;
  if (size < MIN_BINARY_BYTES || size > MAX_BINARY_BYTES) fail('Windows EXE 크기가 허용 범위를 벗어났습니다.');
  const descriptor = fs.openSync(resolved, 'r');
  try {
    const header = Buffer.alloc(2);
    if (fs.readSync(descriptor, header, 0, 2, 0) !== 2 || header.toString('ascii') !== 'MZ') {
      fail('Windows EXE의 MZ 헤더가 올바르지 않습니다.');
    }
  } finally {
    fs.closeSync(descriptor);
  }
  return { path: resolved, size };
}

function sha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', reject);
    stream.on('end', () => resolve(hash.digest('hex')));
  });
}

function validateVersion(value) {
  const version = String(value || '').trim();
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) fail('버전 형식이 올바르지 않습니다.');
  return version;
}

function expectedAssetName(version) {
  return `Dabolang-${version}-Windows-x64.exe`;
}

function expectedProbeUrl() {
  return `${PAGES_ORIGIN}${PROBE_PATH}`;
}

function validatePagesUrl(value) {
  let url;
  try { url = new URL(String(value || '')); } catch (_) { fail('Pages 프로브 주소가 올바르지 않습니다.'); }
  if (url.origin !== PAGES_ORIGIN || url.pathname !== PROBE_PATH || url.search || url.hash || url.username || url.password) {
    fail('Pages 프로브 주소가 고정된 p0 HTTPS 경로와 일치하지 않습니다.');
  }
  return url.href;
}

function validateWorkflowRun(value) {
  const text = String(value || '').trim();
  const escaped = EXPECTED_REPOSITORY.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (!new RegExp(`^https://github\\.com/${escaped}/actions/runs/\\d+(?:/attempts/\\d+)?$`).test(text)) {
    fail('검증 workflow 실행 주소가 올바르지 않습니다.');
  }
  return text;
}

function validateVerifiedAt(value) {
  const text = String(value || '').trim();
  const time = Date.parse(text);
  if (!Number.isFinite(time) || time < Date.UTC(2025, 0, 1) || time > Date.now() + 5 * 60 * 1000) {
    fail('검증 시각이 올바르지 않습니다.');
  }
  return new Date(time).toISOString();
}

async function validateAttestation(attestation, options) {
  const version = validateVersion(options.version);
  const executable = inspectExecutable(options.assetPath);
  const digest = await sha256(executable.path);
  if (!attestation || attestation.schemaVersion !== 1) fail('증명 파일 스키마가 올바르지 않습니다.');
  if (String(attestation.version || '') !== version) fail('증명 버전이 Windows EXE 버전과 다릅니다.');
  if (String(attestation.windowsAsset || '') !== expectedAssetName(version)) fail('증명 Windows 파일명이 올바르지 않습니다.');
  if (Number(attestation.size) !== executable.size) fail('증명 크기가 Windows EXE와 다릅니다.');
  if (String(attestation.sha256 || '').toLowerCase() !== digest) fail('증명 SHA-256이 Windows EXE와 다릅니다.');
  if (!Array.isArray(attestation.legacyBaselines) ||
      JSON.stringify(attestation.legacyBaselines) !== JSON.stringify(LEGACY_BASELINES)) {
    fail('레거시 기준 버전 목록이 올바르지 않습니다.');
  }
  const pagesUrl = validatePagesUrl(attestation.pagesUrl);
  const observed = attestation.observed;
  if (!observed || Number(observed.gzipContentLength) !== executable.size ||
      Number(observed.identityContentLength) !== executable.size ||
      Number(observed.decodedSize) !== executable.size || observed.mz !== true) {
    fail('Pages에서 관측한 gzip/identity/디코딩/MZ 결과가 Windows EXE와 일치하지 않습니다.');
  }
  const verifiedAt = validateVerifiedAt(attestation.verifiedAt);
  const workflowRun = validateWorkflowRun(attestation.workflowRun);
  return { version, executable, digest, pagesUrl, verifiedAt, workflowRun };
}

async function create(values) {
  const version = validateVersion(required(values, 'version'));
  const assetPath = required(values, 'asset-path');
  const executable = inspectExecutable(assetPath);
  const windowsAsset = required(values, 'windows-asset');
  if (windowsAsset !== expectedAssetName(version)) fail('Windows 파일명이 버전과 일치하지 않습니다.');
  const pagesUrl = validatePagesUrl(required(values, 'pages-url'));
  const gzipContentLength = positiveInteger(required(values, 'gzip-content-length'), 'gzip Content-Length');
  const identityContentLength = positiveInteger(required(values, 'identity-content-length'), 'identity Content-Length');
  const decodedSize = positiveInteger(required(values, 'decoded-size'), '디코딩 크기');
  const mz = required(values, 'mz') === 'true';
  if (!mz || gzipContentLength !== executable.size || identityContentLength !== executable.size || decodedSize !== executable.size) {
    fail('관측값이 원본 Windows EXE와 정확히 일치하지 않습니다.');
  }
  const output = path.resolve(required(values, 'output'));
  const workflowRun = validateWorkflowRun(required(values, 'workflow-run'));
  const digest = await sha256(executable.path);
  const value = {
    schemaVersion: 1,
    version,
    windowsAsset,
    size: executable.size,
    sha256: digest,
    legacyBaselines: [...LEGACY_BASELINES],
    pagesUrl,
    observed: { gzipContentLength, identityContentLength, decodedSize, mz: true },
    verifiedAt: new Date().toISOString(),
    workflowRun
  };
  await validateAttestation(value, { version, assetPath: executable.path });
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  process.stdout.write(`CREATED ${output} ${digest} ${executable.size}\n`);
}

async function verify(values) {
  const version = validateVersion(required(values, 'version'));
  const assetPath = required(values, 'asset-path');
  const attestationPath = path.resolve(required(values, 'attestation-path'));
  if (!fs.existsSync(attestationPath) || !fs.statSync(attestationPath).isFile()) fail('레거시 호환성 증명 파일이 없습니다.');
  let attestation;
  try { attestation = JSON.parse(fs.readFileSync(attestationPath, 'utf8')); }
  catch (_) { fail('레거시 호환성 증명 JSON을 읽지 못했습니다.'); }
  const result = await validateAttestation(attestation, { version, assetPath });
  process.stdout.write(`VERIFIED ${version} ${result.digest} ${result.executable.size} ${result.workflowRun}\n`);
}

async function main() {
  const { command, values } = parseArguments(process.argv.slice(2));
  if (command === 'create') return create(values);
  if (command === 'verify') return verify(values);
  fail('create 또는 verify 명령이 필요합니다.');
}

main().catch((error) => {
  process.stderr.write(`${error.message || error}\n`);
  process.exitCode = 1;
});

