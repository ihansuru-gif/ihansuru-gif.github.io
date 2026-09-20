# 다볼랭 공개 업데이트 채널

이 폴더 내용만 `ihansuru-gif/ihansuru-gif.github.io` 공개 저장소 루트에 넣습니다. 앱 소스는 넣지 않습니다.

1. 저장소 **Settings → Pages → Source**를 **GitHub Actions**로 설정합니다.
2. 아래의 정식 승격 전 호환성 프로브를 통과한 prerelease를 그대로 정식 Release로 승격합니다. 정식 Release에는 다음 2개 Windows 파일이 있어야 합니다.
   - `Dabolang-3.5.9-t.69-Windows-x64.exe`
   - `Dabolang-3.5.9-t.69-Windows-x64.legacy-compat.json`
3. Release 공개 시 자동 배포됩니다. 기존 Release는 Actions에서 태그 입력 후 `deploy`를 선택해 다시 배포할 수 있지만, Windows EXE와 정확히 일치하는 호환성 증명 파일이 없으면 정식 채널 배포가 중단됩니다.

일반 push와 `deploy`를 끈 수동 실행은 더미 QA만 수행하며 사이트를 바꾸지 않습니다.

## 정식 승격 전 호환성 프로브

레거시 Windows 업데이터와 새 EXE의 호환성 검증은 정식 배포 전 필수입니다. `3.5.9-t.63`~`3.5.9-t.66`은 CDN의 압축 전송 `Content-Length`를 원본 EXE 크기와 비교하므로, 모든 향후 Windows 정식판은 아래 절차로 정확한 바이트를 실제 Pages에서 검증해야 합니다.

1. 최종 Windows EXE를 정확한 최종 버전 이름으로 첨부한 GitHub prerelease를 공개합니다. 예: 태그 `v3.5.9-t.69`, 파일 `Dabolang-3.5.9-t.69-Windows-x64.exe`. prerelease 공개 이벤트는 정식 채널을 자동 배포하지 않습니다.
2. Actions에서 이 워크플로를 수동 실행합니다. 현재 정식 태그를 `release_tag`에 넣고 `deploy`를 켠 뒤, `compat_probe_source_tag`에는 후보 prerelease 태그를, `compat_probe_padding_bytes`에는 정확한 최종 바이트를 검사한다는 뜻의 `0`을 넣습니다.
3. 워크플로는 숨김 Pages 프로브가 새 파일로 바뀔 때까지 제한 시간 동안 기다립니다. 그 뒤 gzip·identity `Content-Length`, `curl --compressed`로 받은 디코딩 크기·SHA-256·MZ 헤더가 후보 EXE와 모두 같은지 검사합니다.
4. 검증에 성공하면 `Dabolang-<버전>-Windows-x64.legacy-compat.json`을 후보 prerelease에 자동 첨부합니다. 이 JSON은 버전·파일명·크기·SHA-256·레거시 기준·Pages 주소·관측값·검증 시각·workflow 실행을 기록합니다.
5. EXE를 다시 만들거나 수정하지 말고, 그 prerelease를 non-draft, non-prerelease 정식 Release로 그대로 승격합니다. 정식 배포는 증명 JSON을 다시 내려받아 Release의 EXE 크기·SHA-256과 대조하며, 누락·불일치 시 Pages를 변경하지 않고 실패합니다.

`compat_probe_padding_bytes`의 1 이상 값은 패딩 후보를 탐색하는 용도로만 쓸 수 있으며 증명 파일을 만들지 않습니다. 최종 EXE 자체를 올린 `0` 프로브만 증명을 발급합니다. `compat_probe_source_tag`를 비우면 프로브는 `release_tag`의 현재 정식 Windows EXE를 사용하지만, 새 증명 발급은 prerelease를 대상으로 합니다.

기존 `v3.5.9-t.68`은 강제 게이트 도입 전에 이미 같은 전체 다운로드 검증을 통과했습니다. 이 태그에 한해 `release_tag`와 `compat_probe_source_tag`를 모두 `v3.5.9-t.68`, 패딩을 `0`으로 한 번 실행하면 고정 크기 `93789016`과 SHA-256 `626dc5276af72dc8585a26a05e431b43bb441b468677e506dac4bb403a3ad4ae`를 재검증한 뒤 증명을 부트스트랩할 수 있습니다. 그 이후 t.68 재배포도 다른 버전과 동일하게 증명 자산이 필수입니다.

프로브 배포는 숨김 파일만 추가하며 `update/latest.json`과 실제 정식 Windows 다운로드를 변경하지 않습니다. 정식 배포 뒤에는 숨김 프로브가 다시 제거됩니다.

- Windows manifest: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/update/latest.json`
- macOS 다운로드 안내: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/mac/`
- macOS 실제 파일: `https://drive.google.com/drive/folders/1ycUT2dFEl6kkjgpZHcshUJSaTqAwLaWz`

macOS 안내 페이지는 위 Google Drive 폴더로 자동 이동하며, 이동되지 않을 때를 위한 버튼도 제공합니다. macOS ZIP은 GitHub Release 자산으로 요구하거나 Pages에 복사하지 않습니다.

`noindex`와 `robots.txt`는 검색 노출을 줄이지만 URL 접근 자체를 막지는 않습니다.
