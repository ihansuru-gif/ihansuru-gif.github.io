# 인사앱 1.6.0 제작 프롬프트

목표: 검수 완료된 1.5.0 기능 엔진을 유지하면서, 첨부된 최종 디자인 와이어프레임을 시각적 Source of Truth로 사용해 UI 레이어를 전면 교체한다.

## 절대 규칙
- 기존 기능/저장/gesture/timer/overlay/알림/녹음 엔진을 다시 만들지 않는다.
- MainActivity, 카드 외관, 띠지, 버튼, switch, input, icon, spacing, typography, 상태 UI를 교체한다.
- Android 기본 Button/Switch 느낌이 남으면 불합격.
- 전체 화면 dim/검정 배경 금지. 카드 바깥은 투명.
- 와이어프레임의 편집디자인·책갈피·독서노트 분위기를 유지한다.
- 2026년 기준 책·기록·문구를 좋아하는 20~30대 여성 회사원이 회사에서도 매일 쓸 수 있는 차분한 디자인을 기준으로 한다.

## 디자인 토큰
- Paper #F7F5F1
- Surface #FFFFFF
- Ink #292B31
- Secondary #74777F
- Todo #7396D8
- Calendar #9985D4
- Memo #D39A76
- Image #7FA894
- 8dp spacing system
- Card radius 16~20dp
- 1dp border, shadow 최소
- 본문 14~15sp, 카드 제목 16~18sp

## 주요 화면
1. 설정: 띠지 관리 / 잠금화면 / 기능 / 이미지 / 권한을 조용한 그룹 구조로 정리
2. 띠지: 얇은 책 인덱스형, 네 기능 색 분리
3. 투두: 흰 종이 카드 + 작은 파란 accent, 날짜/개수/목록/빠른 추가
4. 일정: editorial calendar, 주간/월간/검색/필터/기간 바/겹침 lane
5. 메모: 상위 모드(텍스트/체크/그리기/첨부) 중심으로 기능 계층화
6. 이미지: 이미지가 주인공, move/collapse/resize 최소 UI
7. 공통: 상단 작은 move handle, 우하단 // resize, 접기 버튼 분리

## QA
- Unit Test
- Lint
- Build
- Emulator Interaction Test
- move → resize → move
- body touch와 move 충돌 없음
- collapse
- tab order / ON/OFF
- 위치·크기 저장
- 투명 window
- 기존 투두/일정/메모/이미지 기능 회귀 없음
- 실패 시 수정 후 전체 재검수
