import { useEffect, useMemo, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api, clearSession, getToken, role, setSession, subscribe } from './api';

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const SEVERITY_COLORS = {
  CRITICAL: '#f07178',
  HIGH: '#f0a35e',
  MEDIUM: '#e8b84a',
  LOW: '#6cb6ff',
};
const EXPLAIN_COLORS = {
  READY: '#3dcf9a',
  PENDING: '#e8b84a',
  FAILED: '#f07178',
  SKIPPED: '#8fa3bf',
};
const PROMOTE_COLORS = {
  IMMEDIATE: '#f07178',
  AGGREGATE: '#6cb6ff',
};
const CHART_TOOLTIP = {
  backgroundColor: '#243552',
  border: '1px solid #6b84a6',
  borderRadius: 10,
  color: '#f2f6fc',
  boxShadow: '0 12px 32px rgba(0, 0, 0, 0.55)',
  padding: '10px 12px',
};
const CHART_TOOLTIP_LABEL = { color: '#d5e2f5', marginBottom: 4, fontWeight: 600 };
const CHART_TOOLTIP_ITEM = { color: '#f2f6fc' };
const CHART_CURSOR = { fill: 'rgba(255, 255, 255, 0.08)' };
const TICK = { fill: '#8fa3bf', fontSize: 11 };

function ChartTooltip(props) {
  return (
    <Tooltip
      contentStyle={CHART_TOOLTIP}
      labelStyle={CHART_TOOLTIP_LABEL}
      itemStyle={CHART_TOOLTIP_ITEM}
      cursor={CHART_CURSOR}
      {...props}
    />
  );
}

export function App() {
  const [authed, setAuthed] = useState(() => Boolean(getToken()));

  if (!authed) {
    return (
      <Routes>
        <Route path="/login" element={<Login onLogin={() => setAuthed(true)} />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }
  return (
    <div className="shell">
      <nav className="nav">
        <div className="brand">
          <strong>WAF SOC</strong>
          <span>Giám sát tấn công web · Juice Shop lab</span>
        </div>
        <NavLink to="/" end>Tổng quan</NavLink>
        <NavLink to="/alerts">Cảnh báo</NavLink>
        <NavLink to="/incidents">Sự cố</NavLink>
        <div className="spacer" />
        <span className="role-chip">{role() || 'SOC'}</span>
        <button
          className="ghost"
          type="button"
          onClick={() => {
            clearSession();
            setAuthed(false);
            window.location.assign('/login');
          }}
        >
          Đăng xuất
        </button>
      </nav>
      <main className="main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/incidents" element={<Incidents />} />
          <Route path="/incidents/:id" element={<IncidentDetail />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

function Login({ onLogin }) {
  const navigate = useNavigate();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [error, setError] = useState('');

  async function onSubmit(event) {
    event.preventDefault();
    setError('');
    try {
      const body = await api('/api/oauth/tokens', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      });
      setSession(body.accessToken, body.role);
      onLogin();
      navigate('/');
    } catch {
      setError('Đăng nhập thất bại — kiểm tra tài khoản hoặc API.');
    }
  }

  return (
    <div className="login">
      <form onSubmit={onSubmit}>
        <h1>Đăng nhập SOC</h1>
        <p className="lede">Bảng điều khiển phát hiện tấn công trên access log Nginx → Kafka → Spring.</p>
        <label>Tài khoản<input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" /></label>
        <label>Mật khẩu<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
        {error ? <p className="error">{error}</p> : null}
        <button type="submit">Vào bảng điều khiển</button>
      </form>
    </div>
  );
}

function useApps() {
  const [apps, setApps] = useState([]);
  useEffect(() => {
    api('/api/apps').then(setApps).catch(() => setApps([]));
  }, []);
  return apps;
}

function AppFilter({ appId, setAppId }) {
  const apps = useApps();
  return (
    <label>
      Phạm vi ứng dụng
      <select value={appId} onChange={(e) => setAppId(e.target.value)}>
        <option value="">Tất cả host</option>
        {apps.map((app) => (
          <option key={app.id} value={app.id}>{app.name}</option>
        ))}
      </select>
    </label>
  );
}

function buildBriefItems(stats) {
  if (!stats) {
    return [{ tone: 'muted', label: 'Trạng thái', value: '…', note: 'Đang tải số liệu từ backend' }];
  }
  const items = [];
  const open = Number(stats.openIncidentCount || 0);
  items.push({
    tone: open > 0 ? 'warn' : 'ok',
    label: 'Hàng đợi triage',
    value: open > 0 ? `${open} OPEN` : '0 OPEN',
    note: open > 0 ? 'Cần analyst xem / ACK' : 'Không có sự cố đang mở',
  });

  const critical = Number(stats.criticalAlertCount || 0);
  if (critical > 0) {
    items.push({
      tone: 'danger',
      label: 'CRITICAL',
      value: String(critical),
      note: 'Promote IMMEDIATE ngay',
    });
  }

  const orphan = Number(stats.unattachedAlertCount || 0);
  items.push({
    tone: orphan > 0 ? 'warn' : 'ok',
    label: 'Chưa gắn sự cố',
    value: String(orphan),
    note: orphan > 0 ? 'MEDIUM/HIGH chưa đủ K=3' : 'Mọi cảnh báo đã gắn incident',
  });

  const lastHour = Number(stats.alertsLastHour || 0);
  items.push({
    tone: lastHour > 0 ? 'info' : 'muted',
    label: '1 giờ gần nhất',
    value: `${lastHour} alert`,
    note: lastHour > 0 ? `${fmt(stats.alertsLast24Hours)} trong 24 giờ` : 'Không có cảnh báo mới',
  });

  const topType = stats.topAttackTypes?.[0];
  if (topType) {
    items.push({
      tone: 'info',
      label: 'Pattern nổi bật',
      value: topType.name,
      note: `${fmt(topType.count)} hit rule`,
    });
  }

  const topIp = stats.topIps?.[0];
  if (topIp) {
    items.push({
      tone: 'info',
      label: 'IP đáng chú ý',
      value: topIp.name,
      note: `${fmt(topIp.count)} cảnh báo`,
    });
  }

  const ready = Number(stats.explanationByStatus?.READY || 0);
  const pending = Number(stats.explanationByStatus?.PENDING || 0);
  const failed = Number(stats.explanationByStatus?.FAILED || 0);
  if (ready || pending || failed) {
    items.push({
      tone: failed > 0 ? 'danger' : pending > 0 ? 'warn' : 'ok',
      label: 'AI explain',
      value: `${ready} READY`,
      note: `${pending} PENDING · ${failed} FAILED`,
    });
  }

  return items;
}

function SituationBrief({ stats }) {
  const items = useMemo(() => buildBriefItems(stats), [stats]);
  const open = Number(stats?.openIncidentCount || 0);
  const critical = Number(stats?.criticalAlertCount || 0);
  const headline = !stats
    ? 'Đang tổng hợp tình hình lab…'
    : open > 0
      ? `${open} sự cố OPEN cần xử lý`
      : critical > 0
        ? `Ổn định triage · ${critical} CRITICAL đã được promote`
        : 'Lab đang yên — chưa có tín hiệu nóng';

  return (
    <div className="situation">
      <div className="situation-head">
        <div>
          <div className="situation-kicker">Tóm tắt tình hình</div>
          <h2 className="situation-title">{headline}</h2>
          <p className="situation-sub">
            Tổng hợp từ số liệu overview (không phải Ollama) — giúp SOC đọc nhanh trước khi mở biểu đồ.
          </p>
        </div>
        <div className="situation-pulse">
          <span className={`dot ${open > 0 || critical > 10 ? 'hot' : 'calm'}`} />
          {open > 0 ? 'Cần chú ý' : 'Ổn định'}
        </div>
      </div>
      <div className="situation-grid">
        {items.map((item) => (
          <article key={item.label} className={`situation-card ${item.tone}`}>
            <span className="situation-label">{item.label}</span>
            <strong className="situation-value">{item.value}</strong>
            <span className="situation-note">{item.note}</span>
          </article>
        ))}
      </div>
    </div>
  );
}

function Dashboard() {
  const [appId, setAppId] = useState('');
  const [stats, setStats] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [live, setLive] = useState(false);

  function reload() {
    const query = appId ? `?appId=${appId}` : '';
    api(`/api/stats/overview${query}`).then(setStats).catch(() => setStats(null));
    api(`/api/alerts${query}`).then((rows) => setAlerts(rows.slice(0, 12))).catch(() => setAlerts([]));
    api(`/api/incidents${query}`).then((rows) => setIncidents(rows.slice(0, 6))).catch(() => setIncidents([]));
  }

  useEffect(() => { reload(); }, [appId]);

  useEffect(() => {
    const stopAlerts = subscribe('/api/stream/alerts', (event, data) => {
      if (event !== 'alert') return;
      setLive(true);
      if (appId && String(data.appId) !== appId) return;
      setAlerts((current) => [data, ...current.filter((row) => row.id !== data.id)].slice(0, 12));
      reload();
    });
    const stopIncidents = subscribe('/api/stream/incidents', (event, data) => {
      if (event !== 'incident') return;
      setLive(true);
      if (appId && String(data.appId) !== appId) return;
      setIncidents((current) => [data, ...current.filter((row) => row.id !== data.id)].slice(0, 6));
      reload();
    });
    return () => {
      stopAlerts();
      stopIncidents();
    };
  }, [appId]);

  const timeline = useMemo(
    () => (stats?.timeline || []).map((point) => ({
      ...point,
      label: formatBucket(point.bucket),
    })),
    [stats],
  );

  const severityTotal = useMemo(() => {
    const map = stats?.alertsBySeverity || {};
    return SEVERITIES.reduce((sum, key) => sum + Number(map[key] || 0), 0);
  }, [stats]);

  const severityPie = useMemo(
    () => SEVERITIES
      .map((name) => ({ name, value: Number(stats?.alertsBySeverity?.[name] || 0) }))
      .filter((row) => row.value > 0),
    [stats],
  );

  const promotePie = useMemo(
    () => (stats?.promoteReasons || [])
      .map((row) => ({ name: row.name, value: Number(row.count) || 0 }))
      .filter((row) => row.value > 0),
    [stats],
  );

  const explainPie = useMemo(() => {
    const map = stats?.explanationByStatus || {};
    return Object.keys(EXPLAIN_COLORS)
      .map((name) => ({ name, value: Number(map[name] || 0) }))
      .filter((row) => row.value > 0);
  }, [stats]);

  const incidentStatusBars = useMemo(() => ([
    { name: 'OPEN', count: Number(stats?.openIncidentCount || 0), fill: '#e8b84a' },
    { name: 'ACK', count: Number(stats?.ackIncidentCount || 0), fill: '#3dcf9a' },
    { name: 'CLOSED', count: Number(stats?.closedIncidentCount || 0), fill: '#8fa3bf' },
  ]), [stats]);

  const pipelineBars = useMemo(() => {
    const events = Number(stats?.eventCount || 0);
    const alertsCount = Number(stats?.alertCount || 0);
    const incidentsTotal = Number(stats?.openIncidentCount || 0)
      + Number(stats?.ackIncidentCount || 0)
      + Number(stats?.closedIncidentCount || 0);
    const linked = Math.max(0, alertsCount - Number(stats?.unattachedAlertCount || 0));
    return [
      { stage: 'Events', count: events },
      { stage: 'Alerts', count: alertsCount },
      { stage: 'Gắn incident', count: linked },
      { stage: 'Incidents', count: incidentsTotal },
    ];
  }, [stats]);

  const windowCompare = useMemo(() => ([
    { name: '1 giờ', count: Number(stats?.alertsLastHour || 0) },
    { name: '24 giờ', count: Number(stats?.alertsLast24Hours || 0) },
    { name: 'Toàn bộ', count: Number(stats?.alertCount || 0) },
  ]), [stats]);

  const topIpsChart = useMemo(
    () => [...(stats?.topIps || [])].slice(0, 6).reverse(),
    [stats],
  );
  const topPathsChart = useMemo(
    () => [...(stats?.topPaths || [])].slice(0, 6).map((row) => ({
      ...row,
      short: shortenPath(row.name),
    })).reverse(),
    [stats],
  );
  const topRulesChart = useMemo(
    () => [...(stats?.topRules || [])].slice(0, 6).reverse(),
    [stats],
  );

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Tổng quan giám sát</h1>
          <p className="sub">
            Đọc nhanh tình hình lab: traffic → cảnh báo → sự cố → giải thích AI.
            Lọc theo ứng dụng để so sánh scope Juice Shop.
          </p>
        </div>
        <div className="controls">
          <AppFilter appId={appId} setAppId={setAppId} />
          {live
            ? <span className="live">SSE đang nhận sự kiện</span>
            : <span className="wait">Đang chờ SSE</span>}
        </div>
      </div>

      <SituationBrief stats={stats} />

      <div className="kpis">
        <div className="kpi">
          <span className="label">Sự kiện đã ingest</span>
          <span className="value">{fmt(stats?.eventCount)}</span>
          <span className="hint">Mọi request qua Nginx đã chuẩn hóa vào PostgreSQL</span>
        </div>
        <div className="kpi">
          <span className="label">Cảnh báo</span>
          <span className="value">{fmt(stats?.alertCount)}</span>
          <span className="hint">
            {fmt(stats?.alertsLast24Hours)} / 24h · {fmt(stats?.alertsLastHour)} / 1h
          </span>
        </div>
        <div className={`kpi ${stats?.openIncidentCount ? 'emphasis' : 'ok'}`}>
          <span className="label">Sự cố OPEN</span>
          <span className="value">{fmt(stats?.openIncidentCount)}</span>
          <span className="hint">
            ACK {fmt(stats?.ackIncidentCount)} · CLOSED {fmt(stats?.closedIncidentCount)}
          </span>
        </div>
        <div className="kpi">
          <span className="label">CRITICAL</span>
          <span className="value">{fmt(stats?.criticalAlertCount)}</span>
          <span className="hint">
            Điểm TB {stats?.averageAlertScore ?? '—'} · max {stats?.maxAlertScore ?? '—'}
          </span>
        </div>
      </div>

      <div className="chips" style={{ marginBottom: 16 }}>
        <span className="stat-chip">Chưa gắn incident: <strong>{fmt(stats?.unattachedAlertCount)}</strong></span>
        <span className="stat-chip">IMMEDIATE: <strong>{countNamed(stats?.promoteReasons, 'IMMEDIATE')}</strong></span>
        <span className="stat-chip">AGGREGATE: <strong>{countNamed(stats?.promoteReasons, 'AGGREGATE')}</strong></span>
        <span className="stat-chip">Explain READY: <strong>{fmt(stats?.explanationByStatus?.READY)}</strong></span>
        <span className="stat-chip">Explain FAILED: <strong>{fmt(stats?.explanationByStatus?.FAILED)}</strong></span>
      </div>

      <div className="section-title">
        <div>
          <h2>Biểu đồ vận hành</h2>
          <p>Timeline, phễu detect → incident, và so sánh cửa sổ thời gian</p>
        </div>
      </div>

      <div className="grid-2">
        <div className="panel">
          <div className="panel-head">
            <h2>Dòng thời gian cảnh báo</h2>
            <span className="meta">Theo giờ · thấy đợt probe / tấn công</span>
          </div>
          {timeline.length === 0 ? <p className="empty">Chưa có dữ liệu timeline.</p> : (
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={timeline}>
                <defs>
                  <linearGradient id="alertFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#e8b84a" stopOpacity={0.45} />
                    <stop offset="100%" stopColor="#e8b84a" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#2a3850" strokeDasharray="3 3" />
                <XAxis dataKey="label" tick={TICK} minTickGap={24} />
                <YAxis allowDecimals={false} tick={TICK} width={36} />
                <ChartTooltip />
                <Area
                  type="monotone"
                  dataKey="count"
                  name="Cảnh báo"
                  stroke="#e8b84a"
                  strokeWidth={2.5}
                  fill="url(#alertFill)"
                />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>
        <div className="panel">
          <div className="panel-head">
            <h2>Phễu pipeline</h2>
            <span className="meta">Events → alerts → gắn incident → incidents</span>
          </div>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={pipelineBars} layout="vertical" margin={{ left: 8, right: 12 }}>
              <CartesianGrid stroke="#2a3850" strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tick={TICK} />
              <YAxis type="category" dataKey="stage" width={96} tick={TICK} />
              <ChartTooltip />
              <Bar dataKey="count" name="Số lượng" fill="#6cb6ff" radius={[0, 8, 8, 0]} barSize={22} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid-3" style={{ marginTop: 12 }}>
        <div className="panel">
          <div className="panel-head">
            <h2>Mức nghiêm trọng</h2>
            <span className="meta">{severityTotal} cảnh báo</span>
          </div>
          {severityPie.length === 0 ? <p className="empty">Chưa có cảnh báo.</p> : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie
                    data={severityPie}
                    dataKey="value"
                    nameKey="name"
                    innerRadius={48}
                    outerRadius={72}
                    paddingAngle={2}
                  >
                    {severityPie.map((row) => (
                      <Cell key={row.name} fill={SEVERITY_COLORS[row.name] || '#8fa3bf'} />
                    ))}
                  </Pie>
                  <ChartTooltip />
                </PieChart>
              </ResponsiveContainer>
              <div className="sev-row">
                {SEVERITIES.map((name) => {
                  const count = Number(stats?.alertsBySeverity?.[name] || 0);
                  const pct = severityTotal ? Math.round((count * 100) / severityTotal) : 0;
                  return (
                    <div className="sev-item" key={name}>
                      <span className={`badge ${name}`}>{name}</span>
                      <div className="bar"><div className={`fill ${name}`} style={{ width: `${pct}%` }} /></div>
                      <span className="count">{count}</span>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>

        <div className="panel">
          <div className="panel-head">
            <h2>Cách promote sự cố</h2>
            <span className="meta">IMMEDIATE vs AGGREGATE</span>
          </div>
          {promotePie.length === 0 ? <p className="empty">Chưa có sự cố.</p> : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={promotePie}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="45%"
                  outerRadius={74}
                  label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                >
                  {promotePie.map((row) => (
                    <Cell key={row.name} fill={PROMOTE_COLORS[row.name] || '#8fa3bf'} />
                  ))}
                </Pie>
                <ChartTooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="panel">
          <div className="panel-head">
            <h2>Trạng thái AI explain</h2>
            <span className="meta">READY / PENDING / FAILED / SKIPPED</span>
          </div>
          {explainPie.length === 0 ? <p className="empty">Chưa có trạng thái explain.</p> : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={explainPie}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={42}
                  outerRadius={70}
                  paddingAngle={3}
                >
                  {explainPie.map((row) => (
                    <Cell key={row.name} fill={EXPLAIN_COLORS[row.name] || '#8fa3bf'} />
                  ))}
                </Pie>
                <ChartTooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="grid-2" style={{ marginTop: 12 }}>
        <div className="panel">
          <div className="panel-head">
            <h2>Loại tấn công (rule hits)</h2>
            <span className="meta">SQLi / XSS / Path traversal</span>
          </div>
          {(stats?.topAttackTypes || []).length === 0 ? <p className="empty">Chưa có hit.</p> : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={stats.topAttackTypes}>
                <CartesianGrid stroke="#2a3850" strokeDasharray="3 3" />
                <XAxis dataKey="name" tick={TICK} />
                <YAxis allowDecimals={false} tick={TICK} width={32} />
                <ChartTooltip />
                <Bar dataKey="count" name="Hits" fill="#3dcf9a" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
        <div className="panel">
          <div className="panel-head">
            <h2>Cửa sổ thời gian & trạng thái sự cố</h2>
            <span className="meta">So sánh volume + lifecycle triage</span>
          </div>
          <div className="grid-2" style={{ gap: 8 }}>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={windowCompare}>
                <CartesianGrid stroke="#2a3850" strokeDasharray="3 3" />
                <XAxis dataKey="name" tick={TICK} />
                <YAxis allowDecimals={false} tick={TICK} width={28} />
                <ChartTooltip />
                <Bar dataKey="count" name="Cảnh báo" fill="#e8b84a" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={incidentStatusBars}>
                <CartesianGrid stroke="#2a3850" strokeDasharray="3 3" />
                <XAxis dataKey="name" tick={TICK} />
                <YAxis allowDecimals={false} tick={TICK} width={28} />
                <ChartTooltip />
                <Bar dataKey="count" name="Sự cố" radius={[8, 8, 0, 0]}>
                  {incidentStatusBars.map((row) => (
                    <Cell key={row.name} fill={row.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="section-title">
        <div>
          <h2>Xếp hạng mục tiêu</h2>
          <p>IP, path và rule — xem ai/đâu đang bị nhắm nhiều nhất</p>
        </div>
      </div>

      <div className="grid-3">
        <div className="panel">
          <div className="panel-head">
            <h2>Top IP nguồn</h2>
            <span className="meta">Client IP nhiều cảnh báo</span>
          </div>
          {topIpsChart.length === 0 ? <p className="empty">Chưa có IP cảnh báo</p> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={topIpsChart} layout="vertical" margin={{ left: 4, right: 8 }}>
                <XAxis type="number" allowDecimals={false} tick={TICK} />
                <YAxis type="category" dataKey="name" width={100} tick={{ ...TICK, fontSize: 10 }} />
                <ChartTooltip />
                <Bar dataKey="count" name="Alerts" fill="#f0a35e" radius={[0, 6, 6, 0]} barSize={14} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
        <div className="panel">
          <div className="panel-head">
            <h2>Top đường dẫn</h2>
            <span className="meta">Endpoint bị nhắm</span>
          </div>
          {topPathsChart.length === 0 ? <p className="empty">Chưa có path cảnh báo</p> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={topPathsChart} layout="vertical" margin={{ left: 4, right: 8 }}>
                <XAxis type="number" allowDecimals={false} tick={TICK} />
                <YAxis type="category" dataKey="short" width={110} tick={{ ...TICK, fontSize: 10 }} />
                <ChartTooltip
                  formatter={(value) => [value, 'Alerts']}
                  labelFormatter={(_, payload) => payload?.[0]?.payload?.name || ''}
                />
                <Bar dataKey="count" name="Alerts" fill="#6cb6ff" radius={[0, 6, 6, 0]} barSize={14} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
        <div className="panel">
          <div className="panel-head">
            <h2>Top rule</h2>
            <span className="meta">Signature kích hoạt nhiều</span>
          </div>
          {topRulesChart.length === 0 ? <p className="empty">Chưa có detection hit</p> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={topRulesChart} layout="vertical" margin={{ left: 4, right: 8 }}>
                <XAxis type="number" allowDecimals={false} tick={TICK} />
                <YAxis type="category" dataKey="name" width={120} tick={{ ...TICK, fontSize: 10 }} />
                <ChartTooltip />
                <Bar dataKey="count" name="Hits" fill="#3dcf9a" radius={[0, 6, 6, 0]} barSize={14} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="section-title">
        <div>
          <h2>Sự cố gần đây</h2>
          <p>Ưu tiên OPEN / ACK — bấm vào để triage và xem giải thích AI</p>
        </div>
        <NavLink to="/incidents">Xem tất cả →</NavLink>
      </div>
      <IncidentTable rows={incidents} compact />

      <div className="section-title">
        <div>
          <h2>Cảnh báo mới nhất</h2>
          <p>Feed realtime qua SSE — gồm cả cảnh báo chưa promote thành sự cố</p>
        </div>
        <NavLink to="/alerts">Xem tất cả →</NavLink>
      </div>
      <AlertTable rows={alerts} />
    </section>
  );
}

function RankPanel({ title, meta, rows, empty }) {
  const max = Math.max(1, ...(rows || []).map((row) => Number(row.count) || 0));
  return (
    <div className="panel">
      <div className="panel-head">
        <h2>{title}</h2>
        <span className="meta">{meta}</span>
      </div>
      {(rows || []).length === 0 ? <p className="empty">{empty}</p> : (
        <div className="rank-list">
          {(rows || []).map((row) => {
            const pct = Math.round((Number(row.count) * 100) / max);
            return (
              <div className="rank-row" key={row.name}>
                <div className="top">
                  <span className="name" title={row.name}>{row.name}</span>
                  <span className="count">{row.count}</span>
                </div>
                <div className="track"><div className="fill" style={{ width: `${pct}%` }} /></div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Alerts() {
  const [appId, setAppId] = useState('');
  const [rows, setRows] = useState([]);
  const [live, setLive] = useState(false);
  const [severity, setSeverity] = useState('ALL');
  const [attach, setAttach] = useState('ALL');
  const [query, setQuery] = useState('');

  useEffect(() => {
    const q = appId ? `?appId=${appId}` : '';
    api(`/api/alerts${q}`).then(setRows).catch(() => setRows([]));
  }, [appId]);

  useEffect(() => {
    return subscribe('/api/stream/alerts', (event, data) => {
      if (event !== 'alert') return;
      setLive(true);
      if (appId && String(data.appId) !== appId) return;
      setRows((current) => [data, ...current.filter((row) => row.id !== data.id)]);
    });
  }, [appId]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return rows.filter((row) => {
      if (severity !== 'ALL' && row.severity !== severity) return false;
      if (attach === 'LINKED' && !row.incidentId) return false;
      if (attach === 'ORPHAN' && row.incidentId) return false;
      if (!needle) return true;
      const hay = `${row.title || ''} ${row.summary || ''} ${row.clientIp || ''} ${row.appName || ''}`.toLowerCase();
      return hay.includes(needle);
    });
  }, [rows, severity, attach, query]);

  const summary = useMemo(() => summarizeAlerts(rows), [rows]);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Cảnh báo</h1>
          <p className="sub">
            Mỗi cảnh báo = 1 request vượt ngưỡng. CRITICAL promote ngay; MEDIUM/HIGH chờ đủ K=3 cùng IP mới thành sự cố.
          </p>
        </div>
        <div className="controls">
          <AppFilter appId={appId} setAppId={setAppId} />
          {live ? <span className="live">SSE đang nhận</span> : <span className="wait">Đang chờ SSE</span>}
        </div>
      </div>

      <div className="brief">
        <div className="label">Đọc nhanh feed cảnh báo</div>
        <p>{summary.brief}</p>
      </div>

      <div className="mini-kpis">
        <div className="mini-kpi">
          <div className="label">Tổng trong phạm vi</div>
          <div className="value">{fmt(rows.length)}</div>
          <div className="hint">Sau filter app</div>
        </div>
        <div className={`mini-kpi ${summary.critical ? 'danger' : ''}`}>
          <div className="label">CRITICAL</div>
          <div className="value">{fmt(summary.critical)}</div>
          <div className="hint">Promote IMMEDIATE</div>
        </div>
        <div className={`mini-kpi ${summary.orphan ? 'warn' : 'ok'}`}>
          <div className="label">Chưa gắn sự cố</div>
          <div className="value">{fmt(summary.orphan)}</div>
          <div className="hint">Thường MEDIUM/HIGH &lt; K</div>
        </div>
        <div className="mini-kpi">
          <div className="label">Điểm trung bình</div>
          <div className="value">{summary.avgScore ?? '—'}</div>
          <div className="hint">Max {summary.maxScore ?? '—'}</div>
        </div>
      </div>

      <div className="filter-bar">
        <label>
          Mức
          <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
            <option value="ALL">Tất cả</option>
            {SEVERITIES.map((name) => <option key={name} value={name}>{name}</option>)}
          </select>
        </label>
        <label>
          Gắn sự cố
          <select value={attach} onChange={(e) => setAttach(e.target.value)}>
            <option value="ALL">Tất cả</option>
            <option value="LINKED">Đã gắn incident</option>
            <option value="ORPHAN">Chưa gắn</option>
          </select>
        </label>
        <label className="grow">
          Tìm IP / tiêu đề / app
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="vd. 172.18.0.1 hoặc SQLI"
          />
        </label>
        <span className="count-note">Hiển thị {filtered.length}/{rows.length}</span>
      </div>

      <AlertList rows={filtered} />
    </section>
  );
}

function AlertList({ rows }) {
  if (!rows?.length) {
    return <p className="empty">Không có cảnh báo khớp bộ lọc hiện tại.</p>;
  }
  return (
    <div className="list-stack">
      {rows.map((row) => {
        const tone = String(row.severity || '').toLowerCase();
        return (
          <article key={row.id} className={`list-card ${tone}`}>
            <div>
              <div className="headline">
                <strong>{row.title}</strong>
                <span className={`badge ${row.severity}`}>{row.severity}</span>
                {row.appName ? <span className="badge">{row.appName}</span> : null}
              </div>
              <div className="meta-line">
                <span className="mono">{row.clientIp}</span>
                <span>event #{row.eventId}</span>
                <span>alert #{row.id}</span>
                {row.incidentId
                  ? <NavLink to={`/incidents/${row.incidentId}`}>sự cố #{row.incidentId}</NavLink>
                  : <span className="badge">chưa gắn sự cố</span>}
              </div>
              {row.summary ? <p className="summary">{row.summary}</p> : null}
            </div>
            <div className="side">
              <div className="score-lg">{row.score}</div>
              <div className="when">{formatTime(row.createdAt)}</div>
              {row.incidentId
                ? <NavLink to={`/incidents/${row.incidentId}`}>Mở sự cố →</NavLink>
                : <span className="when">Chờ đủ K hoặc CRITICAL</span>}
            </div>
          </article>
        );
      })}
    </div>
  );
}

function AlertTable({ rows }) {
  return <AlertList rows={rows} />;
}

function Incidents() {
  const [appId, setAppId] = useState('');
  const [rows, setRows] = useState([]);
  const [live, setLive] = useState(false);
  const [status, setStatus] = useState('ALL');
  const [reason, setReason] = useState('ALL');
  const [explain, setExplain] = useState('ALL');
  const [query, setQuery] = useState('');

  useEffect(() => {
    const q = appId ? `?appId=${appId}` : '';
    api(`/api/incidents${q}`).then(setRows).catch(() => setRows([]));
  }, [appId]);

  useEffect(() => {
    return subscribe('/api/stream/incidents', (event, data) => {
      if (event !== 'incident') return;
      setLive(true);
      if (appId && String(data.appId) !== appId) return;
      setRows((current) => [data, ...current.filter((row) => row.id !== data.id)]);
    });
  }, [appId]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return rows.filter((row) => {
      if (status !== 'ALL' && row.status !== status) return false;
      if (reason !== 'ALL' && row.promoteReason !== reason) return false;
      if (explain !== 'ALL' && row.explanationStatus !== explain) return false;
      if (!needle) return true;
      const hay = `${row.title || ''} ${row.clientIp || ''} ${row.appName || ''}`.toLowerCase();
      return hay.includes(needle);
    });
  }, [rows, status, reason, explain, query]);

  const summary = useMemo(() => summarizeIncidents(rows), [rows]);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Sự cố</h1>
          <p className="sub">
            1 sự cố gom N cảnh báo theo (app, IP). Triage trên sự cố — ACK / CLOSED — và xem giải thích AI.
          </p>
        </div>
        <div className="controls">
          <AppFilter appId={appId} setAppId={setAppId} />
          {live ? <span className="live">SSE đang nhận</span> : <span className="wait">Đang chờ SSE</span>}
        </div>
      </div>

      <div className="brief">
        <div className="label">Hàng đợi triage</div>
        <p>{summary.brief}</p>
      </div>

      <div className="mini-kpis">
        <div className={`mini-kpi ${summary.open ? 'warn' : 'ok'}`}>
          <div className="label">OPEN</div>
          <div className="value">{fmt(summary.open)}</div>
          <div className="hint">Cần analyst xem</div>
        </div>
        <div className="mini-kpi">
          <div className="label">ACK</div>
          <div className="value">{fmt(summary.ack)}</div>
          <div className="hint">Đã xác nhận</div>
        </div>
        <div className="mini-kpi">
          <div className="label">CLOSED</div>
          <div className="value">{fmt(summary.closed)}</div>
          <div className="hint">Đã đóng</div>
        </div>
        <div className="mini-kpi">
          <div className="label">AI READY / FAILED</div>
          <div className="value">{fmt(summary.ready)}/{fmt(summary.failed)}</div>
          <div className="hint">SKIPPED {fmt(summary.skipped)}</div>
        </div>
      </div>

      <div className="filter-bar">
        <label>
          Trạng thái
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="ALL">Tất cả</option>
            <option value="OPEN">OPEN</option>
            <option value="ACK">ACK</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </label>
        <label>
          Promote
          <select value={reason} onChange={(e) => setReason(e.target.value)}>
            <option value="ALL">Tất cả</option>
            <option value="IMMEDIATE">IMMEDIATE</option>
            <option value="AGGREGATE">AGGREGATE</option>
          </select>
        </label>
        <label>
          AI explain
          <select value={explain} onChange={(e) => setExplain(e.target.value)}>
            <option value="ALL">Tất cả</option>
            <option value="READY">READY</option>
            <option value="PENDING">PENDING</option>
            <option value="FAILED">FAILED</option>
            <option value="SKIPPED">SKIPPED</option>
          </select>
        </label>
        <label className="grow">
          Tìm IP / tiêu đề / app
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="vd. juice hoặc 172.18"
          />
        </label>
        <span className="count-note">Hiển thị {filtered.length}/{rows.length}</span>
      </div>

      <div style={{ marginBottom: 12 }}>
        <div className="seg">
          {['ALL', 'OPEN', 'ACK', 'CLOSED'].map((value) => (
            <button
              key={value}
              type="button"
              className={status === value ? 'active' : ''}
              onClick={() => setStatus(value)}
            >
              {value === 'ALL' ? 'Tất cả' : value}
            </button>
          ))}
        </div>
      </div>

      <IncidentList rows={filtered} />
    </section>
  );
}

function IncidentList({ rows, compact = false }) {
  if (!rows?.length) {
    return <p className="empty">Không có sự cố khớp bộ lọc hiện tại.</p>;
  }
  return (
    <div className="list-stack">
      {rows.map((row) => {
        const tone = row.status === 'OPEN' ? 'open' : String(row.severity || '').toLowerCase();
        return (
          <article key={row.id} className={`list-card ${tone}`}>
            <div>
              <div className="headline">
                <strong>{compact ? `#${row.id} · ${row.clientIp}` : row.title}</strong>
                <span className={`badge ${row.status}`}>{row.status}</span>
                <span className={`badge ${row.severity}`}>{row.severity}</span>
                <span className="badge">{row.promoteReason}</span>
                <span className={`badge ${row.explanationStatus}`}>{row.explanationStatus}</span>
              </div>
              <div className="meta-line">
                <span className="mono">{row.clientIp}</span>
                {row.appName ? <span>{row.appName}</span> : null}
                <span>{row.alertCount} cảnh báo</span>
                <span>sự cố #{row.id}</span>
              </div>
              {!compact && row.title ? (
                <p className="summary">
                  {row.promoteReason === 'IMMEDIATE'
                    ? 'Promote ngay vì có cảnh báo CRITICAL.'
                    : 'Gom từ đủ K cảnh báo MEDIUM/HIGH cùng IP trong cửa sổ thời gian.'}
                </p>
              ) : null}
            </div>
            <div className="side">
              <div className="score-lg">{row.score}</div>
              <div className="when">{formatTime(row.openedAt)}</div>
              <NavLink to={`/incidents/${row.id}`}>Chi tiết triage →</NavLink>
            </div>
          </article>
        );
      })}
    </div>
  );
}

function IncidentTable({ rows, compact = false }) {
  return <IncidentList rows={rows} compact={compact} />;
}

function IncidentDetail() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');

  function load() {
    api(`/api/incidents/${id}`).then(setDetail).catch((err) => setError(String(err.message || err)));
  }

  useEffect(() => { load(); }, [id]);
  useEffect(() => {
    return subscribe('/api/stream/incidents', (event, data) => {
      if (event === 'incident' && String(data.id) === String(id)) {
        load();
      }
    });
  }, [id]);

  const categories = useMemo(() => {
    const counts = {};
    for (const item of detail?.alerts || []) {
      for (const hit of item.hits || []) {
        counts[hit.category] = (counts[hit.category] || 0) + 1;
      }
    }
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
  }, [detail]);

  async function triage(status) {
    setBusy(status);
    try {
      await api(`/api/incidents/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) });
      load();
    } finally {
      setBusy('');
    }
  }

  async function retry() {
    setBusy('explain');
    try {
      await api(`/api/incidents/${id}/explain`, { method: 'POST' });
      load();
    } finally {
      setBusy('');
    }
  }

  if (!detail) {
    return <p className="empty">{error || 'Đang tải chi tiết sự cố…'}</p>;
  }
  const incident = detail.incident;

  return (
    <section>
      <div className="incident-hero">
        <NavLink to="/incidents">← Danh sách sự cố</NavLink>
        <h1>{incident.title}</h1>
        <div className="incident-meta">
          <span className={`badge ${incident.status}`}>{incident.status}</span>
          <span className={`badge ${incident.severity}`}>{incident.severity}</span>
          <span className="badge">{incident.promoteReason}</span>
          <span className={`badge ${incident.explanationStatus}`}>{incident.explanationStatus}</span>
          <span className="score">
            điểm {incident.score} · {incident.alertCount} cảnh báo · IP {incident.clientIp}
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={() => triage('ACK')} disabled={incident.status !== 'OPEN' || busy === 'ACK'}>
            {busy === 'ACK' ? 'Đang ACK…' : 'Xác nhận (ACK)'}
          </button>
          <button type="button" className="ghost" onClick={() => triage('CLOSED')} disabled={incident.status === 'CLOSED' || busy === 'CLOSED'}>
            {busy === 'CLOSED' ? 'Đang đóng…' : 'Đóng sự cố'}
          </button>
          <button type="button" className="ghost" onClick={retry} disabled={busy === 'explain'}>
            {busy === 'explain' ? 'Đang enqueue…' : 'Giải thích lại'}
          </button>
        </div>
      </div>

      <div className="detail-grid">
        <div className="panel">
          <div className="panel-head">
            <h2>Thông tin sự cố</h2>
            <span className="meta">Correlation key = app + IP</span>
          </div>
          <div className="kv">
            <div className="kv-row"><span className="k">Ứng dụng</span><span className="v">{incident.appName || '—'}</span></div>
            <div className="kv-row"><span className="k">Client IP</span><span className="v">{incident.clientIp}</span></div>
            <div className="kv-row"><span className="k">Mở lúc</span><span className="v">{formatTime(incident.openedAt)}</span></div>
            <div className="kv-row"><span className="k">ACK lúc</span><span className="v">{formatTime(incident.ackedAt)}</span></div>
            <div className="kv-row"><span className="k">Đóng lúc</span><span className="v">{formatTime(incident.closedAt)}</span></div>
            <div className="kv-row">
              <span className="k">Promote</span>
              <span className="v">
                {incident.promoteReason === 'IMMEDIATE' ? 'IMMEDIATE (CRITICAL)' : 'AGGREGATE (đủ K)'}
              </span>
            </div>
          </div>
        </div>
        <div className="panel">
          <div className="panel-head">
            <h2>Bằng chứng theo loại</h2>
            <span className="meta">{(detail.alerts || []).length} cảnh báo thành viên</span>
          </div>
          {categories.length === 0 ? <p className="empty">Chưa có rule hit.</p> : (
            <div className="rank-list">
              {categories.map(([name, count]) => {
                const max = categories[0][1] || 1;
                const pct = Math.round((count * 100) / max);
                return (
                  <div className="rank-row" key={name}>
                    <div className="top">
                      <span className="name">{name}</span>
                      <span className="count">{count} hit</span>
                    </div>
                    <div className="track"><div className="fill" style={{ width: `${pct}%` }} /></div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      <div className="explain-box">
        <div className="panel-head">
          <h2>Giải thích AI</h2>
          <span className={`badge ${incident.explanationStatus}`}>{incident.explanationStatus}</span>
        </div>
        <p className="sub" style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>
          AI hỗ trợ, có thể không chính xác — chỉ giải thích dựa trên rule hits (vì sao mở, bằng chứng, hành động đề xuất), không phát hiện tấn công.
          {detail.explanationConfidence ? ` · độ tin cậy model: ${detail.explanationConfidence}` : ''}
        </p>
        <ExplanationBody text={detail.explanation} />
        {detail.explanationError ? <p className="error">{detail.explanationError}</p> : null}
      </div>

      <div className="section-title">
        <div>
          <h2>Cảnh báo & evidence</h2>
          <p>Request gốc + rule hits — dùng để demo drill-down và triage</p>
        </div>
      </div>
      {(detail.alerts || []).map((item) => (
        <article key={item.alert.id} className="hit-card">
          <div className="headline" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
            <strong style={{ margin: 0 }}>{item.alert.title}</strong>
            <span className={`badge ${item.alert.severity}`}>{item.alert.severity}</span>
            <span className="badge">điểm {item.alert.score}</span>
          </div>
          <p className="req">
            {item.method} {item.path}{item.query ? `?${item.query}` : ''} · HTTP {item.httpStatus}
          </p>
          <ul className="hits">
            {(item.hits || []).map((hit) => (
              <li key={hit.id}>
                <code>{hit.ruleCode}</code> ({hit.category}, w={hit.weight}): {hit.evidence}
              </li>
            ))}
            {(item.hits || []).length === 0 ? <li>Không có hit (bất thường)</li> : null}
          </ul>
        </article>
      ))}
    </section>
  );
}

const EXPLAIN_HEADINGS = [
  { match: /^tom tat$/i, title: 'Tóm tắt', tone: 'info' },
  { match: /^vi sao xay ra$/i, title: 'Vì sao xảy ra', tone: 'warn' },
  { match: /^bang chung chinh$/i, title: 'Bằng chứng chính', tone: 'danger' },
  { match: /^hanh dong de xuat$/i, title: 'Hành động đề xuất', tone: 'ok' },
];

function ExplanationBody({ text }) {
  if (!text) {
    return <p className="body">Chưa có nội dung giải thích. Bấm «Giải thích lại» nếu status READY/FAILED cần refresh.</p>;
  }
  const sections = splitExplanation(text);
  if (!sections.length) {
    return <ExplanationBlock text={text} />;
  }
  return (
    <div className="explain-sections">
      {sections.map((section) => (
        <article key={section.title} className={`explain-section ${section.tone}`}>
          <h3>{section.title}</h3>
          <ExplanationBlock text={section.body} />
        </article>
      ))}
    </div>
  );
}

function ExplanationBlock({ text }) {
  const lines = String(text || '').split('\n').map((line) => line.trim()).filter(Boolean);
  if (!lines.length) return null;
  const bullets = lines.every((line) => /^[-*•]\s+/.test(line) || /^\d+[.)]\s+/.test(line));
  if (bullets) {
    return (
      <ul className="explain-list">
        {lines.map((line, index) => (
          <li key={`${index}-${line.slice(0, 24)}`}>
            {renderInlineMarkdown(line.replace(/^([-*•]\s+|\d+[.)]\s+)/, ''))}
          </li>
        ))}
      </ul>
    );
  }
  return (
    <div className="body explain-prose">
      {lines.map((line, index) => (
        <p key={`${index}-${line.slice(0, 24)}`}>{renderInlineMarkdown(line)}</p>
      ))}
    </div>
  );
}

/** Renders **bold** and `code` from model text; escapes everything else as text. */
function renderInlineMarkdown(text) {
  const source = String(text || '');
  const parts = [];
  const tokenRe = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  let match = tokenRe.exec(source);
  let key = 0;
  while (match) {
    if (match.index > last) {
      parts.push(source.slice(last, match.index));
    }
    const token = match[0];
    if (token.startsWith('**') && token.endsWith('**')) {
      parts.push(<strong key={`b-${key++}`} className="explain-strong">{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('`') && token.endsWith('`')) {
      parts.push(<code key={`c-${key++}`} className="explain-code">{token.slice(1, -1)}</code>);
    } else {
      parts.push(token);
    }
    last = match.index + token.length;
    match = tokenRe.exec(source);
  }
  if (last < source.length) {
    parts.push(source.slice(last));
  }
  return parts.length ? parts : source;
}

function splitExplanation(text) {
  const source = String(text || '').replace(/\r\n/g, '\n').trim();
  if (!source) return [];
  const headingRe = /(?:^|\n)\s*(?:#{1,3}\s*)?(TÓM TẮT|TOM TAT|VÌ SAO XẢY RA|VI SAO XAY RA|BẰNG CHỨNG CHÍNH|BANG CHUNG CHINH|HÀNH ĐỘNG ĐỀ XUẤT|HANH DONG DE XUAT)\s*:?\s*(?=\n|$)/gi;
  const hits = [...source.matchAll(headingRe)];
  if (!hits.length) return [];
  const sections = [];
  for (let i = 0; i < hits.length; i += 1) {
    const hit = hits[i];
    const rawTitle = hit[1];
    const start = hit.index + hit[0].length;
    const end = i + 1 < hits.length ? hits[i + 1].index : source.length;
    const body = source.slice(start, end).trim();
    const meta = EXPLAIN_HEADINGS.find((item) => item.match.test(normalizeHeading(rawTitle)));
    sections.push({
      title: meta?.title || rawTitle,
      tone: meta?.tone || 'info',
      body,
    });
  }
  return sections.filter((section) => section.body);
}

function normalizeHeading(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function summarizeAlerts(rows) {
  const critical = rows.filter((row) => row.severity === 'CRITICAL').length;
  const orphan = rows.filter((row) => !row.incidentId).length;
  const linked = rows.length - orphan;
  let avgScore = null;
  let maxScore = null;
  if (rows.length) {
    const sum = rows.reduce((acc, row) => acc + Number(row.score || 0), 0);
    avgScore = Math.round((sum * 10) / rows.length) / 10;
    maxScore = Math.max(...rows.map((row) => Number(row.score || 0)));
  }
  const topIp = topKey(rows.map((row) => row.clientIp));
  const parts = [
    `${rows.length} cảnh báo trong phạm vi`,
    critical ? `${critical} CRITICAL` : 'không có CRITICAL',
    orphan ? `${orphan} chưa gắn sự cố` : 'mọi cảnh báo đã gắn sự cố',
    linked ? `${linked} đã gắn incident` : null,
    topIp ? `IP nổi bật ${topIp.name} (${topIp.count})` : null,
  ].filter(Boolean);
  return { brief: `${parts.join(' · ')}.`, critical, orphan, avgScore, maxScore };
}

function summarizeIncidents(rows) {
  const open = rows.filter((row) => row.status === 'OPEN').length;
  const ack = rows.filter((row) => row.status === 'ACK').length;
  const closed = rows.filter((row) => row.status === 'CLOSED').length;
  const ready = rows.filter((row) => row.explanationStatus === 'READY').length;
  const failed = rows.filter((row) => row.explanationStatus === 'FAILED').length;
  const skipped = rows.filter((row) => row.explanationStatus === 'SKIPPED').length;
  const immediate = rows.filter((row) => row.promoteReason === 'IMMEDIATE').length;
  const aggregate = rows.filter((row) => row.promoteReason === 'AGGREGATE').length;
  const parts = [
    open ? `${open} OPEN cần triage` : 'không có OPEN',
    `${ack} ACK · ${closed} CLOSED`,
    `${immediate} IMMEDIATE · ${aggregate} AGGREGATE`,
    `AI ${ready} READY / ${failed} FAILED / ${skipped} SKIPPED`,
  ];
  return { brief: `${parts.join(' · ')}.`, open, ack, closed, ready, failed, skipped };
}

function topKey(values) {
  const counts = {};
  for (const value of values) {
    if (!value) continue;
    counts[value] = (counts[value] || 0) + 1;
  }
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  if (!entries.length) return null;
  return { name: entries[0][0], count: entries[0][1] };
}

function countNamed(rows, name) {
  const hit = (rows || []).find((row) => row.name === name);
  return hit ? hit.count : 0;
}

function fmt(value) {
  if (value == null) return '—';
  return Number(value).toLocaleString('vi-VN');
}

function formatTime(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatBucket(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN', { month: '2-digit', day: '2-digit', hour: '2-digit' });
}

function shortenPath(path) {
  if (!path) return '—';
  if (path.length <= 22) return path;
  return `…${path.slice(-20)}`;
}
