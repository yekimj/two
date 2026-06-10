# 원서를읽고싶어요 — S펜 버튼 사전 PDF 앱 MVP

갤럭시 탭에서 PDF를 열고 필기하면서, S펜 버튼 더블클릭으로 선택된 단어를 사전 팝업으로 띄우는 Android/Kotlin 프로젝트입니다.

## 이번 버전의 핵심

- 독립 앱 방식
- PDF 표시/텍스트 선택: AndroidX PDF `PdfViewerFragment` / `PdfView`
- 선택 텍스트 수신: `PdfView.addOnSelectionChangedListener` + `TextSelection.text`
- S펜 버튼 더블클릭: Samsung Air Actions RemoteActions XML
- RemoteActions double click: `CTRL_LEFT+D` KeyEvent로 매핑
- fallback: 일부 펌웨어의 raw stylus button keycode 감지
- 사전 API: dictionaryapi.dev
- 필기 오버레이: 펜 / 형광펜 / 지우개 / 두께 / 6색
- 실행취소: 버튼 또는 두 손가락 더블탭
- 자동저장: 앱 pause 시 PDF별 `.ink.json` 저장
- 라이브러리: 앱 내부 `files/library/<PDF이름>/`

## 사용 시나리오

1. 앱에서 PDF 열기
2. PDF 텍스트를 길게 눌러 단어 선택
3. S펜 버튼 더블클릭
4. 선택된 단어를 사전 API로 검색
5. 앱 중앙에 작은 팝업 표시

## S펜 설정

설치 후 갤탭에서 다음 경로 확인:

Settings > Advanced features > S Pen > Air actions > App actions > 원서를읽고싶어요

Air actions가 꺼져 있으면 버튼 이벤트가 앱으로 들어오지 않습니다.

## 빌드

Android Studio에서 프로젝트 폴더 열기 → Gradle Sync → Build > Build APK(s)

또는 로컬에 Gradle/Android SDK가 있다면:

```bash
./gradlew assembleDebug
```

이 zip에는 Gradle Wrapper jar가 포함되어 있지 않습니다. Android Studio가 Gradle을 자동으로 잡게 하거나 로컬 Gradle을 사용하세요.

## 남은 작업

- 실제 PDF flatten export: 현재는 원본 PDF + 필기 JSON 저장까지만 연결되어 있습니다.
- AndroidX PDF alpha API 변동 대응: `TextSelectablePdfFragment.create()`의 URI argument key가 바뀌면 AndroidX PDF 샘플에 맞춰 수정해야 합니다.
- S펜 버튼 동작은 기기/One UI/S Pen Remote 지원 여부에 따라 다릅니다. Air actions 설정에서 앱 action을 켜야 합니다.
