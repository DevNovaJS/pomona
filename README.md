# pomona 백엔드

서울 가락시장 도매가·출하 물량으로 과일 가격을 보여주는 사이트의 백엔드. 공공데이터 API를 새벽마다 수집해 DB에 쌓고 API로 데이터를 제공한다.

- 프론트(공개면·백오피스): [pomona-web](https://github.com/DevNovaJS/pomona-web)

## 기술 스택

Kotlin 2.3 · Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Spring Data JPA + JdbcTemplate

## 데이터 출처 (공공데이터포털)

| API | 쓰는 곳 |
|---|---|
| [전국 공영도매시장 정산정보](https://www.data.go.kr/data/15141809/openapi.do) | 주 데이터. 가락시장(110001) 과실류(06)·과일과채류(08) 경매 실거래. 품종 목록의 기준 |
| [일별 도·소매 가격정보](https://www.data.go.kr/data/15156057/openapi.do) | 소매가. 과일 21개 품목 |

두 API는 코드 체계가 다르기 때문에(정산 사과 `06-01` 홍로 `17` / 가격 API 사과 `411` 홍로 `07`). 품종 이름 표기도 어긋나서(샤인마스캇 / 샤인머스켓) 백오피스에서 짝을 지어 둔 품종에만 소매가를 붙인다.

## API

공개 빌드용 `/api/public`

| 경로 | 내용 |
|---|---|
| `GET /period` | 기준일(DB의 마지막 거래일)과 12개월 범위 |
| `GET /items` · `/varieties` | 품목 목록, 페이지를 만들 품종 목록 |
| `GET /prices/latest` | 품종별 마지막 거래일 도매가(등급 합산 + 등급별) |
| `GET /prices/weekly` | 최근 7일 평균과 작년 같은 7일(364일 전) 평균 |
| `GET /prices/daily` | 품종별 최근 30일 거래일마다의 도매가 |
| `GET /retail-prices` | 매핑된 품종의 소매가(등급별, 점포별 7일 중앙값의 중앙값) |
| `GET /volumes` · `/volumes/items` | 품종·품목별 월별 물량. 12개월 + 작년 같은 달 |
| `GET /volumes/items/daily` | 품목별 일별 물량(과일 캘린더) |
| `GET /volumes/recent` | 최근 14일 물량과 평소 14일 물량(메인 "평소보다 많이 나오는 과일") |
| `GET /trading-days` | 달마다 거래일 수 |
| `GET /origins` · `/origins/items` | 주요 산지 상위 5곳(12개월 합계 + 달마다) |
| `GET /reviews` | 리뷰 전부와 그날 도매 시세 |

백오피스 `/api/admin`

| 경로 | 내용 |
|---|---|
| `GET /batch/status` | 수집 중인지, 작업별 마지막 성공일·안 풀린 실패 수 |
| `GET /batch/runs?before=` | 실행 이력. 수집 대상일 10일씩, 하루 안에서는 작업·품목마다 마지막 실행 |
| `GET /batch/failures` | 같은 조건으로 마지막에 돈 실행이 실패인 것 |
| `POST /batch/runs/{id}/retry` | 실패 재실행 |
| `POST /batch/collect?from=&to=` | 기간 재수집(202, 뒤에서 돈다) |
| `GET·POST /reviews` · `GET·PUT·DELETE /reviews/{id}` | 리뷰 작성·수정·삭제 |
| `GET /reviews/market-price` | 작성 중 그날 도매 시세 미리보기 |
| `GET /varieties` | 품종 마스터 전부(페이지 없는 소량 품종 포함) |
| `GET /mappings` · `/mappings/retail-varieties` | 품종 매핑 목록, 소매 품종 목록 |
| `PUT·DELETE /mappings/{varietyId}` | 짝 짓기·바꾸기·풀기 |

## 정책

- **대표가는 총액 ÷ 물량(kg당)**. API의 평균가(`avgprc`)는 거래 건별 단순평균이라 쓰지 않는다. 등급·기간을 합칠 때도 가격을 평균 내지 않고 총액·물량을 다시 더한다
- **국산·수입은 원산지 코드로 가른다**. `plor_cd`가 `800`으로 시작하면 수입. 품종 이름의 "(수입)"으로는 못 가린다(이름에 없는 수입 품종이 있다)
- **매매구분이 `-`인 거래는 수집에서 뺀다**. 산지 대량 거래가 kg당 200~300원대로 섞여 가격을 왜곡했다
- **결측은 실패가 아니다**. 주말·휴장·미조사로 0건이면 `EMPTY`로 남긴다
- **품종은 대·중·소분류 코드 3개로 식별한다**. 이름도 소분류 코드도 혼자서는 유일하지 않다(`01` = 신고배·홍옥·캠벨얼리)
- **품종 페이지는 조건에 맞는 품종만**: 기타(소분류 `99`, 중분류 `99`)가 아니고, 최근 12개월 거래일 10일 이상 · 물량 1톤 이상
- **소매가는 점포마다 7일 중앙값을 낸 뒤 점포끼리 중앙값**. 한 번에 중앙값을 내면 자주 조사된 점포 쪽으로 쏠린다
- 집계는 저장하지 않고 조회할 때마다 계산한다. 모든 조회가 날짜 구간을 정해 놓고 읽어서 데이터가 쌓여도 느려지지 않는다

## DB

`db/schema.sql`

| 테이블 | 내용 |
|---|---|
| `variety_master` | 정산 품종 마스터. 실거래에 나온 품종을 수집 때 올린다 |
| `wholesale_daily` | 도매 일별 집계(날짜·품종·거래방식·등급·산지·단위별 총액·물량) |
| `retail_daily` | 소매 조사 가격(날짜·점포·품종·등급별) |
| `retail_variety` | 소매 품종 마스터 |
| `variety_retail_mapping` | 정산 품종 ↔ 소매 품종 짝. 정산 품종 하나에 소매 하나 |
| `batch_run` | 수집 실행 이력. 재수집·재실행을 전부 남기는 append only |
| `review` | 직접 먹은 과일 리뷰. 가락시장 품종 연결은 선택 |
