# 자체 호스팅 정적 자원 라이선스

외부 CDN(jsDelivr) 의존을 제거하기 위해 아래 자산을 버전 고정해 저장소에 포함했다.

| 자산 | 버전 | 원본 | 라이선스 |
| --- | --- | --- | --- |
| Bootstrap CSS/JS | 5.3.3 | https://github.com/twbs/bootstrap/releases/tag/v5.3.3 | MIT (`licenses/bootstrap-MIT.txt`) |
| Bootstrap Icons | 1.11.3 | https://github.com/twbs/icons/releases/tag/v1.11.3 | MIT (`licenses/bootstrap-icons-MIT.txt`) |
| Chart.js | 4.4.7 | https://github.com/chartjs/Chart.js/releases/tag/v4.4.7 | MIT (`licenses/chartjs-MIT.md`) |
| Pretendard Variable | 1.3.9 | https://github.com/orioncactus/pretendard/releases/tag/v1.3.9 | SIL Open Font License 1.1 (`licenses/pretendard-OFL.txt`) |

## 변경 사항

- `pretendard/pretendardvariable.css`의 `url(...)`을 원본 패키지 경로에서 실제 파일 위치인 `./woff2/PretendardVariable.woff2`로 수정했다.
- `bootstrap-icons/bootstrap-icons.min.css`의 `fonts/...` 상대 경로는 `bootstrap-icons/fonts/`에 맞춰 그대로 사용한다.
