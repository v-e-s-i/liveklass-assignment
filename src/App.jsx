import { useMemo, useState } from 'react';
import axios from 'axios';
import './App.css';

const initialSettlement = {
  creatorId: 'creator-1',
  yearMonth: '2025-03',
};

const initialSale = {
  id: '',
  courseId: 'course-1',
  studentId: '',
  amount: '',
  paidAt: '2025-03-05T10:00',
};

const initialSaleList = {
  creatorId: 'creator-1',
  from: '2025-03-01',
  to: '2025-03-31',
  page: '0',
  size: '20',
};

const initialCancel = {
  saleRecordId: '',
  refundAmount: '',
  canceledAt: '2025-03-20T10:00',
};

const initialAdminSummary = {
  from: '2025-03-01',
  to: '2025-03-31',
};

const won = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

function formatWon(value) {
  return won.format(Number(value || 0));
}

function App() {
  const [settlement, setSettlement] = useState(initialSettlement);
  const [sale, setSale] = useState(initialSale);
  const [saleList, setSaleList] = useState(initialSaleList);
  const [cancel, setCancel] = useState(initialCancel);
  const [adminSummary, setAdminSummary] = useState(initialAdminSummary);
  const [settlementResult, setSettlementResult] = useState(null);
  const [saleResult, setSaleResult] = useState(null);
  const [saleListResult, setSaleListResult] = useState(null);
  const [cancelResult, setCancelResult] = useState(null);
  const [adminResult, setAdminResult] = useState(null);
  const [saleErrors, setSaleErrors] = useState({});
  const [latestLog, setLatestLog] = useState(null);
  const [loading, setLoading] = useState('');

  const settlementData = settlementResult?.ok ? settlementResult.data : null;
  const saleListData = saleListResult?.ok ? saleListResult.data : null;
  const adminData = adminResult?.ok ? adminResult.data : null;
  const saleMessage = saleResult?.ok ? '판매 내역이 정상적으로 등록되었습니다.' : null;
  const cancelMessage = cancelResult?.ok ? '판매 취소가 정상적으로 등록되었습니다.' : null;
  const visibleError = latestLog && !latestLog.ok ? latestLog.data : null;

  const summaryItems = useMemo(() => {
    if (!settlementData) return [];
    return [
      ['총 판매금액', formatWon(settlementData.totalSalesAmount), 'positive'],
      ['총 환불금액', formatWon(settlementData.totalRefundAmount), 'negative'],
      ['순매출', formatWon(settlementData.netSalesAmount), 'strong'],
      ['수수료', formatWon(settlementData.feeAmount), 'muted'],
      ['정산 예정금액', formatWon(settlementData.payoutAmount), 'primary'],
      ['판매 / 취소 건수', `${settlementData.saleCount}건 / ${settlementData.cancelCount}건`, 'muted'],
    ];
  }, [settlementData]);

  const showError = (error) => error.response?.data || { message: error.message };

  const toKstOffsetDateTime = (value) => {
    const withSeconds = value.length === 16 ? `${value}:00` : value;
    return `${withSeconds}+09:00`;
  };

  const validateSale = () => {
    const errors = {};
    const amount = Number(sale.amount);

    if (!sale.id.trim()) errors.id = '판매 ID를 입력해 주세요.';
    if (!sale.courseId.trim()) errors.courseId = '강의 ID를 입력해 주세요.';
    if (!sale.studentId.trim()) errors.studentId = '수강생 ID를 입력해 주세요.';
    if (!sale.amount) {
      errors.amount = '판매 금액을 입력해 주세요.';
    } else if (!Number.isFinite(amount) || amount <= 0) {
      errors.amount = '판매 금액은 1원 이상의 숫자로 입력해 주세요.';
    }
    if (!sale.paidAt) {
      errors.paidAt = '결제 시각을 입력해 주세요.';
    } else if (Number.isNaN(Date.parse(sale.paidAt))) {
      errors.paidAt = '결제 시각을 올바른 날짜와 시간으로 입력해 주세요.';
    }

    setSaleErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const setResult = (type, result) => {
    if (type === 'settlement') {
      setSettlementResult(result);
    } else if (type === 'saleList') {
      setSaleListResult(result);
    } else if (type === 'cancel') {
      setCancelResult(result);
    } else if (type === 'admin') {
      setAdminResult(result);
    } else {
      setSaleResult(result);
    }
    setLatestLog({ type, ...result, at: new Date().toLocaleString('ko-KR') });
  };

  const fetchSettlement = async () => {
    setLoading('settlement');
    setSettlementResult(null);
    try {
      const response = await axios.get('/api/v1/settlements/monthly', {
        params: settlement,
        headers: { 'X-Creator-Id': settlement.creatorId },
      });
      setResult('settlement', { ok: true, data: response.data });
    } catch (error) {
      setResult('settlement', { ok: false, data: showError(error) });
    } finally {
      setLoading('');
    }
  };

  const createSale = async () => {
    if (!validateSale()) {
      setSaleResult({ ok: false, data: { message: '입력값을 확인해 주세요.' } });
      return;
    }

    setLoading('sale');
    setSaleResult(null);
    try {
      const response = await axios.post('/api/v1/sale-records', {
        ...sale,
        amount: Number(sale.amount),
        paidAt: toKstOffsetDateTime(sale.paidAt),
      });
      setResult('sale', { ok: true, data: response.data });
    } catch (error) {
      setResult('sale', { ok: false, data: showError(error) });
    } finally {
      setLoading('');
    }
  };

  const fetchSaleList = async () => {
    setLoading('saleList');
    setSaleListResult(null);
    try {
      const response = await axios.get('/api/v1/sale-records', {
        params: saleList,
        headers: saleList.creatorId ? { 'X-Creator-Id': saleList.creatorId } : {},
      });
      setResult('saleList', { ok: true, data: response.data });
    } catch (error) {
      setResult('saleList', { ok: false, data: showError(error) });
    } finally {
      setLoading('');
    }
  };

  const cancelSale = async () => {
    setLoading('cancel');
    setCancelResult(null);
    try {
      const response = await axios.post(`/api/v1/sale-records/${encodeURIComponent(cancel.saleRecordId)}/cancel`, {
        refundAmount: Number(cancel.refundAmount),
        canceledAt: toKstOffsetDateTime(cancel.canceledAt),
      });
      setResult('cancel', { ok: true, data: response.data });
    } catch (error) {
      setResult('cancel', { ok: false, data: showError(error) });
    } finally {
      setLoading('');
    }
  };

  const fetchAdminSummary = async () => {
    setLoading('admin');
    setAdminResult(null);
    try {
      const response = await axios.get('/api/v1/admin/settlements/summary', {
        params: adminSummary,
        headers: { 'X-Role': 'ADMIN' },
      });
      setResult('admin', { ok: true, data: response.data });
    } catch (error) {
      setResult('admin', { ok: false, data: showError(error) });
    } finally {
      setLoading('');
    }
  };

  const updateSettlement = (event) => {
    setSettlement({ ...settlement, [event.target.name]: event.target.value });
  };

  const updateSale = (event) => {
    setSale({ ...sale, [event.target.name]: event.target.value });
    setSaleErrors({ ...saleErrors, [event.target.name]: '' });
  };

  const updateSaleList = (event) => {
    setSaleList({ ...saleList, [event.target.name]: event.target.value });
  };

  const updateCancel = (event) => {
    setCancel({ ...cancel, [event.target.name]: event.target.value });
  };

  const updateAdminSummary = (event) => {
    setAdminSummary({ ...adminSummary, [event.target.name]: event.target.value });
  };

  return (
    <main className="app">
      <header className="topbar" aria-label="서비스 내비게이션">
        <div className="brand">
          <span className="brand-mark">LC</span>
          <strong>Liveklass 정산 관리</strong>
        </div>
        <nav className="nav-links" aria-label="주요 메뉴">
          <a href="#settlement">정산</a>
          <a href="#sale">판매 등록</a>
          <a href="#sale-list">판매 목록</a>
          <a href="#cancel">취소</a>
          <a href="#admin-summary">관리자</a>
          <a href="#api-log">API 테스트</a>
        </nav>
        <span className="dashboard-badge">크리에이터 대시보드</span>
      </header>

      <section className="hero-card">
        <div className="hero-copy">
          <p className="eyebrow">Creator revenue operations</p>
          <h1>크리에이터의 수익 정산을 더 쉽고 투명하게</h1>
          <p className="subtitle">판매, 환불, 수수료, 정산 예정 금액을 한 화면에서 확인하세요.</p>
          <div className="feature-chips" aria-label="주요 기능">
            <span>월별 정산</span>
            <span>판매 관리</span>
            <span>환불 추적</span>
            <span>관리자 집계</span>
          </div>
        </div>
        <div className="hero-panel" aria-label="정산 요약 미리보기">
          <span>이번 달 정산 예정금액</span>
          <strong>{settlementData ? formatWon(settlementData.payoutAmount) : '조회 전'}</strong>
          <small>
            {settlementData
              ? `${settlementData.creatorName} · ${settlementData.yearMonth}`
              : '크리에이터 ID와 정산 월을 입력해 주세요.'}
          </small>
        </div>
      </section>

      <div className="layout">
        <section className="card settlement-card" id="settlement">
          <div className="card-header">
            <div>
              <p className="section-kicker">Monthly settlement</p>
              <h2>월별 정산 조회</h2>
              <p className="card-description">크리에이터별 월간 매출과 환불, 수수료, 정산 예정 금액을 확인합니다.</p>
            </div>
          </div>

          <div className="form-grid two-columns">
            <label className="field">
              <span>크리에이터 ID</span>
              <input name="creatorId" value={settlement.creatorId} onChange={updateSettlement} placeholder="creator-1" />
              <small>X-Creator-Id 헤더에도 동일하게 전송됩니다.</small>
            </label>
            <label className="field">
              <span>정산 월</span>
              <input name="yearMonth" value={settlement.yearMonth} onChange={updateSettlement} placeholder="2025-03" />
              <small>YYYY-MM 형식으로 입력해 주세요.</small>
            </label>
          </div>

          <button className="primary-button" onClick={fetchSettlement} disabled={loading === 'settlement'}>
            {loading === 'settlement' ? '조회 중...' : '조회'}
          </button>

          {settlementResult && !settlementResult.ok && (
            <Alert tone="error" title="정산 조회에 실패했습니다." data={settlementResult.data} />
          )}

          {!settlementResult && (
            <div className="empty-state">조회 조건을 입력하고 정산 데이터를 불러와 주세요.</div>
          )}

          {settlementData && (
            <>
              <div className="period-strip">
                <span>정산 기간</span>
                <strong>{settlementData.period.from} ~ {settlementData.period.to}</strong>
              </div>
              <div className="summary-grid">
                {summaryItems.map(([label, value, tone]) => (
                  <div className={`summary-card ${tone}`} key={label}>
                    <span>{label}</span>
                    <strong>{value}</strong>
                  </div>
                ))}
              </div>
            </>
          )}
        </section>

        <section className="card" id="sale">
          <div className="card-header">
            <div>
              <p className="section-kicker">Sales input</p>
              <h2>판매 내역 등록</h2>
              <p className="card-description">새로운 결제 건을 등록해 정산 데이터에 반영되는 흐름을 확인합니다.</p>
            </div>
          </div>

          <div className="form-grid">
            <label className="field">
              <span>판매 ID</span>
              <input name="id" value={sale.id} onChange={updateSale} placeholder="sale-100" />
              <small>중복되지 않는 판매 식별자를 입력합니다.</small>
              {saleErrors.id && <em>{saleErrors.id}</em>}
            </label>
            <label className="field">
              <span>강의 ID</span>
              <input name="courseId" value={sale.courseId} onChange={updateSale} placeholder="course-1" />
              <small>등록된 강의 ID만 사용할 수 있습니다.</small>
              {saleErrors.courseId && <em>{saleErrors.courseId}</em>}
            </label>
            <label className="field">
              <span>수강생 ID</span>
              <input name="studentId" value={sale.studentId} onChange={updateSale} placeholder="student-100" />
              {saleErrors.studentId && <em>{saleErrors.studentId}</em>}
            </label>
            <label className="field">
              <span>판매 금액</span>
              <input name="amount" type="number" min="1" value={sale.amount} onChange={updateSale} placeholder="50000" />
              {saleErrors.amount && <em>{saleErrors.amount}</em>}
            </label>
            <label className="field wide">
              <span>결제 시각</span>
              <input name="paidAt" type="datetime-local" value={sale.paidAt} onChange={updateSale} />
              <small>제출 시 한국 시간 기준 +09:00 형식으로 전송됩니다.</small>
              {saleErrors.paidAt && <em>{saleErrors.paidAt}</em>}
            </label>
          </div>

          <button className="primary-button" onClick={createSale} disabled={loading === 'sale'}>
            {loading === 'sale' ? '생성 중...' : '판매 생성'}
          </button>

          {saleMessage && <Alert tone="success" title={saleMessage} data={saleResult.data} />}
          {saleResult && !saleResult.ok && <Alert tone="error" title="판매 등록에 실패했습니다." data={saleResult.data} />}
        </section>
      </div>

      <div className="operations-grid">
        <section className="card" id="sale-list">
          <div className="card-header">
            <div>
              <p className="section-kicker">Sales records</p>
              <h2>판매 내역 조회</h2>
              <p className="card-description">크리에이터와 기간 조건으로 판매 및 취소 상태를 확인합니다.</p>
            </div>
          </div>

          <div className="form-grid">
            <label className="field">
              <span>크리에이터 ID</span>
              <input name="creatorId" value={saleList.creatorId} onChange={updateSaleList} placeholder="creator-1" />
              <small>입력 시 X-Creator-Id 헤더로 함께 전송됩니다.</small>
            </label>
            <label className="field">
              <span>시작일</span>
              <input name="from" type="date" value={saleList.from} onChange={updateSaleList} />
            </label>
            <label className="field">
              <span>종료일</span>
              <input name="to" type="date" value={saleList.to} onChange={updateSaleList} />
            </label>
            <label className="field">
              <span>페이지</span>
              <input name="page" type="number" min="0" value={saleList.page} onChange={updateSaleList} />
            </label>
            <label className="field">
              <span>페이지 크기</span>
              <input name="size" type="number" min="1" value={saleList.size} onChange={updateSaleList} />
            </label>
          </div>

          <button className="primary-button" onClick={fetchSaleList} disabled={loading === 'saleList'}>
            {loading === 'saleList' ? '조회 중...' : '판매 내역 조회'}
          </button>

          {saleListResult && !saleListResult.ok && (
            <Alert tone="error" title="판매 내역 조회에 실패했습니다." data={saleListResult.data} />
          )}

          {!saleListResult && <div className="empty-state compact-empty">조회 조건을 입력하고 판매 내역을 불러와 주세요.</div>}

          {saleListData && (
            <>
              <div className="result-toolbar">
                <strong>총 {saleListData.totalElements}건</strong>
                <span>
                  페이지 {Number(saleListData.page) + 1} / {Math.max(Number(saleListData.totalPages || 0), 1)}
                </span>
              </div>
              {saleListData.content?.length ? (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>판매</th>
                        <th>수강생</th>
                        <th>금액</th>
                        <th>결제 시각</th>
                        <th>상태</th>
                        <th>환불 정보</th>
                      </tr>
                    </thead>
                    <tbody>
                      {saleListData.content.map((record) => (
                        <tr key={record.id}>
                          <td>
                            <strong>{record.id}</strong>
                            <small>{record.courseTitle || record.courseId}</small>
                          </td>
                          <td>{record.studentId}</td>
                          <td>{formatWon(record.amount)}</td>
                          <td>{record.paidAt}</td>
                          <td>
                            <span className={`status-pill ${record.canceled ? 'canceled' : 'active'}`}>
                              {record.canceled ? '취소' : '정상'}
                            </span>
                          </td>
                          <td>
                            {record.cancelInfo ? (
                              <>
                                <strong>{formatWon(record.cancelInfo.refundAmount)}</strong>
                                <small>{record.cancelInfo.canceledAt}</small>
                              </>
                            ) : (
                              '-'
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state compact-empty">조건에 맞는 판매 내역이 없습니다.</div>
              )}
            </>
          )}
        </section>

        <section className="card" id="cancel">
          <div className="card-header">
            <div>
              <p className="section-kicker">Refund input</p>
              <h2>판매 취소 등록</h2>
              <p className="card-description">기존 판매 건에 환불 금액과 취소 시각을 연결합니다.</p>
            </div>
          </div>

          <div className="form-grid">
            <label className="field">
              <span>판매 ID</span>
              <input name="saleRecordId" value={cancel.saleRecordId} onChange={updateCancel} placeholder="sale-100" />
            </label>
            <label className="field">
              <span>환불 금액</span>
              <input name="refundAmount" type="number" min="1" value={cancel.refundAmount} onChange={updateCancel} placeholder="30000" />
            </label>
            <label className="field wide">
              <span>취소 시각</span>
              <input name="canceledAt" type="datetime-local" value={cancel.canceledAt} onChange={updateCancel} />
              <small>제출 시 한국 시간 기준 +09:00 형식으로 전송됩니다.</small>
            </label>
          </div>

          <button className="primary-button" onClick={cancelSale} disabled={loading === 'cancel'}>
            {loading === 'cancel' ? '등록 중...' : '판매 취소 등록'}
          </button>

          {cancelMessage && <Alert tone="success" title={cancelMessage} data={cancelResult.data} />}
          {cancelResult && !cancelResult.ok && <Alert tone="error" title="판매 취소 등록에 실패했습니다." data={cancelResult.data} />}
        </section>

        <section className="card admin-card" id="admin-summary">
          <div className="card-header">
            <div>
              <p className="section-kicker">Admin settlement</p>
              <h2>관리자 정산 집계</h2>
              <p className="card-description">관리자 권한 헤더로 기간별 크리에이터 정산 합계를 조회합니다.</p>
            </div>
          </div>

          <div className="form-grid two-columns">
            <label className="field">
              <span>시작일</span>
              <input name="from" type="date" value={adminSummary.from} onChange={updateAdminSummary} />
            </label>
            <label className="field">
              <span>종료일</span>
              <input name="to" type="date" value={adminSummary.to} onChange={updateAdminSummary} />
              <small>X-Role: ADMIN 헤더로 요청합니다.</small>
            </label>
          </div>

          <button className="primary-button" onClick={fetchAdminSummary} disabled={loading === 'admin'}>
            {loading === 'admin' ? '조회 중...' : '관리자 집계 조회'}
          </button>

          {adminResult && !adminResult.ok && (
            <Alert tone="error" title="관리자 정산 집계 조회에 실패했습니다." data={adminResult.data} />
          )}

          {!adminResult && <div className="empty-state compact-empty">기간을 선택하고 관리자 정산 집계를 조회해 주세요.</div>}

          {adminData && (
            <>
              <div className="admin-summary-strip">
                <span>총 정산 예정금액</span>
                <strong>{formatWon(adminData.summary?.payoutAmount)}</strong>
                <small>
                  판매 {adminData.summary?.saleCount || 0}건 · 취소 {adminData.summary?.cancelCount || 0}건
                </small>
              </div>

              {adminData.items?.length ? (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>크리에이터</th>
                        <th>총 판매</th>
                        <th>총 환불</th>
                        <th>순매출</th>
                        <th>수수료</th>
                        <th>정산 예정</th>
                        <th>건수</th>
                      </tr>
                    </thead>
                    <tbody>
                      {adminData.items.map((item) => (
                        <tr key={item.creatorId}>
                          <td>
                            <strong>{item.creatorName || item.creatorId}</strong>
                            <small>{item.creatorId}</small>
                          </td>
                          <td>{formatWon(item.totalSalesAmount)}</td>
                          <td>{formatWon(item.totalRefundAmount)}</td>
                          <td>{formatWon(item.netSalesAmount)}</td>
                          <td>{formatWon(item.feeAmount)}</td>
                          <td>
                            <strong className="accent-text">{formatWon(item.payoutAmount)}</strong>
                          </td>
                          <td>{item.saleCount} / {item.cancelCount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state compact-empty">해당 기간의 정산 집계가 없습니다.</div>
              )}
            </>
          )}
        </section>
      </div>

      <section className="card log-card" id="api-log">
        <div className="card-header">
          <div>
            <p className="section-kicker">Developer panel</p>
            <h2>API 응답 로그</h2>
            <p className="card-description">최근 요청의 원본 응답을 확인하는 디버그 영역입니다.</p>
          </div>
          {latestLog && <span className={`log-badge ${latestLog.ok ? 'ok' : 'fail'}`}>{latestLog.ok ? 'SUCCESS' : 'ERROR'}</span>}
        </div>

        {visibleError && (
          <Alert tone="error" title={visibleError.message || '요청 처리 중 오류가 발생했습니다.'} data={visibleError} compact />
        )}

        {latestLog ? (
          <pre className={`json-log ${latestLog.ok ? '' : 'error'}`}>
            {JSON.stringify({ request: latestLog.type, at: latestLog.at, response: latestLog.data }, null, 2)}
          </pre>
        ) : (
          <div className="empty-state">조회 또는 등록 요청을 실행하면 API 응답 JSON이 여기에 표시됩니다.</div>
        )}
      </section>
    </main>
  );
}

function Alert({ tone, title, data, compact = false }) {
  return (
    <div className={`alert ${tone} ${compact ? 'compact' : ''}`}>
      <div>
        <strong>{title}</strong>
        {data?.message && <p>{data.message}</p>}
      </div>
      {data?.code && <span>{data.code}</span>}
    </div>
  );
}

export default App;
