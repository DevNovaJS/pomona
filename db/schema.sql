-- pomona 스키마
--
-- 마이그레이션 도구를 쓰지 않는다. 이 파일이 원본이고 손으로 적용한다.
-- 엔티티를 고치면 여기도 같이 고쳐야 한다. 어긋나면 테스트에서 잡힌다
-- (테스트 프로파일만 spring.jpa.hibernate.ddl-auto=validate).
--
-- 적용:
--   docker exec -i pomona-postgres psql -U pomona -d pomona < db/schema.sql
--
-- 모든 테이블은 bigserial 인조키를 PK 로 두고, 자연키는 유니크 제약으로 강제한다.
-- 재수집은 두 방식이다. 품종 마스터는 도매 행이 FK 로 참조하므로 ON CONFLICT 로 upsert 하고,
-- 도매·소매 일별 테이블은 날짜(소매는 품목 x 기간) 단위로 지우고 다시 넣는다.
-- 일별 테이블의 유니크 제약은 ON CONFLICT 용이 아니라, 집계 버그로 같은 키가 두 번 들어가는 걸 막고
-- 날짜 조회를 맡는 용도다 (날짜 컬럼으로 시작하므로 날짜 단독 인덱스가 따로 필요 없다).


-- ---------------------------------------------------------------------------
-- variety_master : 품종 마스터
-- 출처 [정산] katSale/trades. 실거래에 등장한 품종만 쌓는다.
-- ---------------------------------------------------------------------------
create table if not exists variety_master (
    id            bigserial   primary key,
    lclsf_cd      varchar(2)  not null,
    lclsf_nm      varchar(20) not null,
    mclsf_cd      varchar(2)  not null,
    mclsf_nm      varchar(20) not null,
    sclsf_cd      varchar(2)  not null,
    -- 실측: 12,175행 중 22행이 null. 코드는 오는데 이름이 비어 있다.
    sclsf_nm      varchar(20),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),

    -- 소분류코드는 단독으로 유니크하지 않다. 01 = 신고배 | 홍옥사과 | 캠벨얼리포도.
    -- 소분류명도 마찬가지라 이름 기반 매핑은 금지. 세 코드가 모두 있어야 품종이 식별된다.
    constraint uq_variety_master unique (lclsf_cd, mclsf_cd, sclsf_cd)
);

comment on table  variety_master               is '품종 마스터. [정산] 실거래에 등장한 품종';
comment on column variety_master.lclsf_cd      is '대분류. 06 과실류 / 08 과일과채류';
comment on column variety_master.mclsf_cd      is '중분류 = 품목. 과실류의 01 = 사과';
comment on column variety_master.sclsf_cd      is '소분류 = 품종. 사과의 17 = 홍로';
comment on column variety_master.sclsf_nm      is '품종명. 실측 12,175행 중 22행이 null 로 온다';
-- 품종의 첫/마지막 등장일은 컬럼으로 두지 않는다. wholesale_daily 의
-- ix_wholesale_variety_date 인덱스에 MIN/MAX 를 걸면 인덱스 양 끝만 읽고 끝난다.


-- ---------------------------------------------------------------------------
-- wholesale_daily : 도매 일별 집계 (주력 가격)
-- 출처 [정산] katSale/trades. 원본 거래 건을 아래 자연키로 접어서 저장한다.
-- 실측 2,544행 -> 598행 (23.5%).
-- ---------------------------------------------------------------------------
create table if not exists wholesale_daily (
    id            bigserial     primary key,
    trd_clcln_ymd date          not null,
    whsl_mrkt_cd  varchar(6)    not null,
    variety_id    bigint        not null references variety_master (id),
    trd_se        varchar(20)   not null,
    grd_cd        varchar(2)    not null,
    grd_nm        varchar(20)   not null,
    plor_cd       varchar(6)    not null,
    plor_nm       varchar(30),
    unit_nm       varchar(10)   not null,
    tot_prc       bigint        not null,
    tot_qty       numeric(14,3) not null,
    low_prc_per_kg  numeric(12,2) not null,
    high_prc_per_kg numeric(12,2) not null,
    trade_count   integer       not null,
    -- 날짜 단위로 지우고 다시 넣으므로 행이 수정되는 일이 없다. updated_at 을 두지 않는다.
    -- created_at = 이 날짜를 마지막으로 다시 수집한 시각.
    created_at    timestamptz   not null default now(),

    -- unit_nm 이 자연키에 들어가는 이유: 단위가 다른 행이 같은 키로 접히면
    -- SUM(tot_qty) 가 kg 와 '개' 를 더해버린다. 표본은 전부 kg 이라 행 수는 늘지 않는다.
    constraint uq_wholesale_daily unique (
        trd_clcln_ymd, whsl_mrkt_cd, variety_id, trd_se, grd_cd, plor_cd, unit_nm
    ),

    -- tot_qty 는 대표가의 분모다. 실측에서 물량 0 인 원본 행이 0.67% 있고
    -- 그것만으로 이뤄진 묶음이 0.5% 나온다. 배치가 unit_tot_qty = 0 행을 걸러내고,
    -- 묶음 합이 0 이면 아예 넣지 않는다. 이 제약은 그 규칙이 지켜지는지 감시한다.
    constraint ck_wholesale_qty_positive check (tot_qty > 0)
);

comment on table  wholesale_daily               is '도매 일별 집계. 대표가 = tot_prc / tot_qty (원/kg)';
comment on column wholesale_daily.trd_clcln_ymd is '정산일자. 경매 대금이 정산된 날';
comment on column wholesale_daily.whsl_mrkt_cd  is '도매시장. 110001 = 서울가락. 지금은 가락만 수집';
comment on column wholesale_daily.trd_se        is '거래 방식. 실측 5종 — 경매 / 정가수의 / 정가수의(예약형) / 전자거래 / -. 코드 없이 한글로만 온다';
comment on column wholesale_daily.grd_cd        is '품질 등급. 실측 10종 — 11 특 / 12 상 / 13 중 / 19 등외 등. 1Z 처럼 문자가 섞인 값도 있다';
comment on column wholesale_daily.plor_cd       is '산지 시군 코드. 산지명에 꼬리 공백이 붙어 오므로 코드를 키로 쓴다';
comment on column wholesale_daily.plor_nm       is '산지명. 실측 12% 에 꼬리 공백, 17행은 null. 적재 시 trim 한다';
comment on column wholesale_daily.unit_nm       is '거래 단위. 실측 전부 kg. kg 이 아닌 행은 배치가 경고를 남긴다';
comment on column wholesale_daily.tot_prc       is 'SUM(totprc). 이 묶음으로 오간 거래대금 총액(원)';
comment on column wholesale_daily.tot_qty       is 'SUM(unit_tot_qty). 실제 거래 중량 합(kg). 소수가 실제로 온다';
-- lwprc/hgprc 는 kg 당이 아니라 '포장 단위(상자) 하나당' 가격이다. 12,175행 실측:
--   lwprc <= (상자당 실제단가) <= hgprc   99% 성립
--   lwprc <= (kg당 실제단가)   <= hgprc    7% 성립
-- 게다가 한 묶음에 규격이 다른 상자가 섞이므로(20%) 원값을 MIN/MAX 하면
-- 1kg 상자값과 30kg 상자값을 비교하게 된다. 접기 전에 kg 당으로 바꾼 뒤 MIN/MAX 한다.
comment on column wholesale_daily.low_prc_per_kg  is 'MIN(lwprc / unit_qty). 최저 낙찰가의 kg 환산. 저장 시 내림 — 반올림하면 경계가 안쪽으로 밀려 대표가가 범위를 벗어난다';
comment on column wholesale_daily.high_prc_per_kg is 'MAX(hgprc / unit_qty). 최고 낙찰가의 kg 환산. 저장 시 올림';
comment on column wholesale_daily.trade_count   is '이 한 행으로 접힌 원본 거래 건수. 거래가 희박한 행을 가려내는 데 쓴다';

-- 품종 페이지의 12개월 시계열
create index if not exists ix_wholesale_variety_date on wholesale_daily (variety_id, trd_clcln_ymd);


-- ---------------------------------------------------------------------------
-- retail_daily : 소매 조사 원본
-- 출처 [가격] perDay/price. 접지 않고 점포 단위 그대로 넣는다.
-- 용도는 과일 물가 지수. 리뷰의 비교 대상으로도 검토 중.
-- ---------------------------------------------------------------------------
create table if not exists retail_daily (
    id               bigserial   primary key,
    exmn_ymd         date        not null,
    se_cd            varchar(2)  not null,
    se_nm            varchar(20) not null,
    ctgry_cd         varchar(3)  not null,
    ctgry_nm         varchar(20) not null,
    item_cd          varchar(3)  not null,
    item_nm          varchar(20) not null,
    vrty_cd          varchar(2)  not null,
    vrty_nm          varchar(30) not null,
    grd_cd           varchar(2)  not null,
    grd_nm           varchar(20) not null,
    sgg_cd           varchar(4)  not null,
    sgg_nm           varchar(20) not null,
    mrkt_cd          varchar(7)  not null,
    mrkt_nm          varchar(30) not null,
    unit             varchar(10) not null,
    unit_sz          varchar(10) not null,
    exmn_dd_prc      bigint      not null,
    exmn_dd_cnvs_prc bigint      not null,
    orgnl_reg_dt     timestamptz,
    -- 품목 x 기간 단위로 지우고 다시 넣으므로 updated_at 을 두지 않는다.
    created_at       timestamptz not null default now(),

    -- se_cd 가 자연키에 있어야 나중에 중도매(02)를 추가해도 소매 행을 덮어쓰지 않는다.
    constraint uq_retail_daily unique (
        exmn_ymd, se_cd, ctgry_cd, item_cd, vrty_cd, grd_cd, sgg_cd, mrkt_cd
    )
);

comment on table  retail_daily                  is '소매 조사 원본. 한 행 = 점포 한 곳의 그날 그 품목 가격';
comment on column retail_daily.exmn_ymd         is '조사일자. 조사원이 점포에서 가격을 적은 날. 평일만 조사한다';
comment on column retail_daily.se_cd            is '유통 단계. 01 소매 / 02 중도매 / 03 친환경. 지금은 01만 수집';
comment on column retail_daily.ctgry_cd         is '부류. 400 과일류 / 200 채소류(딸기 수박 참외 토마토 멜론)';
comment on column retail_daily.item_cd          is '품목. 411 = 사과';
comment on column retail_daily.vrty_cd          is '품종. 06 = 쓰가루(아오리). [정산]과 코드 체계가 다르다';
comment on column retail_daily.grd_cd           is '등급. 실측 4종 — 04 상품 / 05 중품(품질축), 15 M과 / 16 S과(크기축). 품목마다 축이 다르다';
comment on column retail_daily.sgg_cd           is '조사 지역 시군구. 22곳';
comment on column retail_daily.mrkt_cd          is '조사한 점포. 50곳. 지역당 2곳 안팎이라 지역별 대표값으로는 표본이 얇다';
comment on column retail_daily.unit             is '판매 단위. 실측 3종 — 개 / kg / g. 체리는 100g 단위로 조사된다';
comment on column retail_daily.unit_sz          is '묶음 크기. unit 과 합쳐 해석한다 — 개 + 10 = 10개 묶음';
comment on column retail_daily.exmn_dd_prc      is '그 점포의 그날 가격(원). 위 묶음 하나 값';
comment on column retail_daily.exmn_dd_cnvs_prc is 'kg 환산가. unit 이 kg 일 때만 실제로 환산된다. 개 단위는 원가를 복사해 온다';
comment on column retail_daily.orgnl_reg_dt     is '원본 시스템 등록일시. 소매에도 확정 지연이 있는지 나중에 여기서 본다';

-- 물가 지수·품종 페이지의 시계열
create index if not exists ix_retail_item_date on retail_daily (item_cd, vrty_cd, exmn_ymd);


-- ---------------------------------------------------------------------------
-- batch_run : 수집 배치 실행 이력
-- API 출처 없음. 백오피스 "배치 관리" 화면이 이 테이블만 읽는다.
-- 같은 날짜를 여러 번 재수집하고 그 시도를 전부 남기므로 자연키가 없다 (append only).
-- ---------------------------------------------------------------------------
create table if not exists batch_run (
    id          bigserial   primary key,
    job_name    varchar(40) not null,
    target_date date        not null,
    status      varchar(10) not null,
    params      jsonb       not null,
    row_count   integer     not null default 0,
    message     text,
    started_at  timestamptz not null default now(),
    finished_at timestamptz,

    constraint ck_batch_run_status check (status in ('RUNNING', 'SUCCESS', 'EMPTY', 'FAILED'))
);

comment on table  batch_run             is '수집 배치 실행 이력. append only';
comment on column batch_run.job_name    is 'wholesale-daily / retail-daily';
comment on column batch_run.target_date is '수집 대상 날짜';
comment on column batch_run.status      is 'RUNNING / SUCCESS / EMPTY(휴장·미조사라 0행) / FAILED. EMPTY 는 실패가 아니다';
comment on column batch_run.params      is '그때 보낸 호출 파라미터. 재실행 버튼이 이 값을 그대로 다시 쓴다';
comment on column batch_run.row_count   is '적재한 행 수';
comment on column batch_run.message     is '실패 사유. 유일하게 NULL 을 허용하는 값 컬럼';
comment on column batch_run.finished_at is '종료 시각. 진행 중이면 NULL';

-- 백오피스의 "최신 수집일" 과 "최근 실행 이력" 이 이 인덱스 하나로 해결된다
create index if not exists ix_batch_run_job_date on batch_run (job_name, target_date desc);


-- ---------------------------------------------------------------------------
-- review : 직접 먹은 과일 리뷰
-- API 출처 없음. 백오피스에서 쓰고, 공개면 빌드가 전부 가져가 목록·상세 페이지를 만든다.
-- 임시저장이 없다 — 저장하면 다음 빌드에 공개된다.
-- 리뷰는 많아야 수백 건이라 품종별로 찾아도 테이블 전체를 읽는 게 빠르므로 인덱스를 걸지 않는다.
-- ---------------------------------------------------------------------------
create table if not exists review (
    id          bigserial    primary key,
    variety_id  bigint       not null references variety_master (id),
    eaten_date  date         not null,
    title       varchar(100) not null,
    store       varchar(100) not null,
    origin      varchar(100),
    price       integer      not null,
    weight_gram integer,
    rating      smallint     not null,
    body        text         not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint ck_review_price  check (price > 0),
    constraint ck_review_weight check (weight_gram > 0),
    constraint ck_review_rating check (rating between 0 and 5)
);

comment on table  review            is '직접 먹은 과일 리뷰';
comment on column review.variety_id is '품종. 상세에 붙는 그날 도매 시세를 이 품종으로 찾는다';
comment on column review.eaten_date is '먹은 날. 도매 시세는 이날 또는 그 전 마지막 거래일 값을 붙인다';
comment on column review.title      is '목록과 검색 결과에 나오는 제목';
comment on column review.store      is '산 곳';
comment on column review.origin     is '포장에 적힌 산지. 안 적혀 있으면 NULL';
comment on column review.price      is '산 가격(원)';
comment on column review.weight_gram is '산 무게(g). 바나나 한 송이처럼 모르면 NULL. 있으면 kg당 가격을 계산해 도매가와 나란히 보여준다';
comment on column review.rating     is '별점 0~5';


-- ---------------------------------------------------------------------------
-- retail_variety : 소매 품종 마스터
-- 출처 [가격] perDay/price. 소매 수집 때 응답에 나온 품종을 upsert 한다 (정산 쪽 variety_master 와 같은 방식).
-- retail_daily 한 행은 "어느 날 어느 점포의 어느 등급 가격" 이라 품종을 가리킬 수 없고,
-- 재수집 때 지우고 다시 넣어 id 가 바뀌므로 FK 대상이 될 수 없다. 그래서 품종만 따로 둔다.
-- retail_daily 에 이 테이블의 id 를 넣지 않는다. 조회 때 코드 3개로 조인한다.
-- ---------------------------------------------------------------------------
create table if not exists retail_variety (
    id         bigserial   primary key,
    ctgry_cd   varchar(3)  not null,
    ctgry_nm   varchar(20) not null,
    item_cd    varchar(3)  not null,
    item_nm    varchar(20) not null,
    vrty_cd    varchar(2)  not null,
    vrty_nm    varchar(30) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint uq_retail_variety unique (ctgry_cd, item_cd, vrty_cd)
);

comment on table  retail_variety          is '소매 품종 마스터. [가격] 조사에 등장한 품종';
comment on column retail_variety.ctgry_cd is '부류. 400 과일류 / 200 채소류';
comment on column retail_variety.item_cd  is '품목. 411 = 사과';
comment on column retail_variety.vrty_cd  is '품종. 사과의 07 = 홍로. 00 은 품종 구분 없음';


-- ---------------------------------------------------------------------------
-- variety_retail_mapping : 정산 품종 ↔ 소매 품종
-- API 출처 없음. 백오피스에서 손으로 짝짓는다. 연결하면 그 품종 페이지에 소매가가 붙는다.
-- 정산 여러 품종이 소매 하나를 가리킬 수 있다 (후지·로얄후지·로얄부사 → 소매 후지).
-- 정산 품종 하나에는 매핑이 하나뿐이다 (uq_mapping_variety).
-- 소매 품종이 NULL 인 행 = "확인했는데 소매에 없음". 품종 대부분은 소매 짝이 없어서, 행이 아예 없는
-- 품종만 "미연결(아직 안 본 것)" 로 띄워야 새 품종이 묻히지 않는다.
-- ---------------------------------------------------------------------------
create table if not exists variety_retail_mapping (
    id                bigserial   primary key,
    variety_id        bigint      not null references variety_master (id),
    retail_variety_id bigint      references retail_variety (id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),

    constraint uq_mapping_variety unique (variety_id)
);

comment on table  variety_retail_mapping                   is '정산 품종 ↔ 소매 품종. 백오피스에서 짝짓는다';
comment on column variety_retail_mapping.retail_variety_id is '짝지은 소매 품종. NULL 이면 확인했는데 소매에 없음';
