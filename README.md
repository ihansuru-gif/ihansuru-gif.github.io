# 다볼랭 공개 업데이트 채널

이 폴더 내용만 `ihansuru-gif/ihansuru-gif.github.io` 공개 저장소 루트에 넣습니다. 앱 소스는 넣지 않습니다.

1. 저장소 **Settings → Pages → Source**를 **GitHub Actions**로 설정합니다.
2. `v3.5.9-t.64` 형식의 태그로 Release를 만들고 아래 3개 파일을 올린 뒤 공개합니다.
   - `Dabolang-3.5.9-t.64-Windows-x64.exe`
   - `Dabolang-3.5.9-t.64-macOS-arm64.zip`
   - `Dabolang-3.5.9-t.64-macOS-x64.zip`
3. Release 공개 시 자동 배포됩니다. 기존 Release는 Actions에서 태그 입력 후 `deploy`를 선택해 다시 배포할 수 있습니다.

일반 push와 `deploy`를 끈 수동 실행은 더미 QA만 수행하며 사이트를 바꾸지 않습니다.

- Windows manifest: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/update/latest.json`
- macOS 다운로드: `https://ihansuru-gif.github.io/daborang-jitsi-screen-gallery/mac/`

`noindex`와 `robots.txt`는 검색 노출을 줄이지만 URL 접근 자체를 막지는 않습니다.
