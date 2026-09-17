# 다볼랭 공개 업데이트 채널

이 폴더 내용만 `ihansuru-gif/ihansuru-gif.github.io` 공개 저장소 루트에 넣습니다. 앱 소스는 넣지 않습니다.

1. 저장소 **Settings → Pages → Source**를 **GitHub Actions**로 설정합니다.
2. `v3.5.9-t.64` 형식의 태그로 Release를 만들고 아래 3개 파일을 올린 뒤 공개합니다.
   - `Dabolang-3.5.9-t.64-Windows-x64.exe`
   - `Dabolang-3.5.9-t.64-macOS-arm64.zip`
   - `Dabolang-3.5.9-t.64-macOS-x64.zip`
3. Release 공개 시 자동 배포됩니다. 기존 Release는 Actions에서 태그 입력 후 `deploy`를 선택해 다시 배포할 수 있습니다.

일반 push와 `deploy`를 끈 수동 실행은 더미 QA만 수행하며 사이트를 바꾸지 않습니다.

## 정식 승격 전 호환성 프로브

레거시 Windows 업데이터와 새 EXE의 호환성을 정식 배포 전에 확인할 수 있습니다.

1. 검사할 Windows EXE를 정확한 버전 이름(예: `Dabolang-3.6.0-rc.1-Windows-x64.exe`)으로 첨부한 GitHub prerelease를 공개합니다. prerelease 공개 이벤트는 정식 채널을 자동 배포하지 않습니다.
2. Actions에서 이 워크플로를 수동 실행하면서 현재 정식 태그를 `release_tag`에 넣고 `deploy`를 켭니다. `compat_probe_padding_bytes`에 `0`~`1000000` 값을, `compat_probe_source_tag`에는 공개한 prerelease 태그(예: `v3.6.0-rc.1`)를 넣습니다.
3. 배포 후 숨김 경로 `/daborang-jitsi-screen-gallery/compat-probe/Dabolang-compat-probe-p<바이트>.exe`로 레거시 업데이터 동작을 확인합니다. 이 프로브는 `update/latest.json`과 실제 정식 Windows 다운로드를 변경하지 않습니다.
4. 검증에 성공한 같은 빌드를 non-draft, non-prerelease 정식 Release로 공개합니다. 그때만 정식 채널 자동 배포가 실행됩니다.

`compat_probe_source_tag`를 비우면 프로브는 `release_tag`의 현재 정식 Windows EXE를 사용합니다. 소스 태그를 넣은 경우 워크플로는 그 Release가 draft가 아닌지, 태그에서 계산한 버전과 EXE의 정확한 파일명이 일치하는지 검사합니다. prerelease는 프로브 소스로 허용됩니다.

- Windows manifest: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/update/latest.json`
- macOS 다운로드: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/mac/` → 고정 Google Drive 폴더

macOS ZIP은 Drive 폴더에서 수동으로 교체합니다. 앱에 들어 있는 GitHub Pages 주소는 유지되므로 기존 사용자도 같은 버튼으로 새 Drive 폴더에 연결됩니다.

`noindex`와 `robots.txt`는 검색 노출을 줄이지만 URL 접근 자체를 막지는 않습니다.
