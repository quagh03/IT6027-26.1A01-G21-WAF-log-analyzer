import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, clearSession, getToken, role, setSession, subscribe } from './api';

export function App() {
  if (!getToken()) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }
  return (
    <div className="shell">
      <nav className="nav">
        <strong>SOC lab</strong>
        <NavLink to="/" end>Tổng quan</NavLink>
        <NavLink to="/alerts">Cảnh báo</NavLink>
        <NavLink to="/incidents">Sự cố</NavLink>
        <span className="disclaimer">{role()}</span>
        <button className="ghost" type="button" onClick={() => { clearSession(); window.location.assign('/login'); }}>
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

function Login() {
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
      navigate('/');
    } catch {
      setError('Đăng nhập thất bại');
    }
  }

  return (
    <div className="login">
      <form onSubmit={onSubmit}>
        <h1>Đăng nhập SOC</h1>
        <label>Tài khoản<input value={username} onChange={(e) => setUsername(e.target.value)} /></label>
        <label>Mật khẩu<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
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
      Ứng dụng
      <select value={appId} onChange={(e) => setAppId(e.target.value)}>
        <option value="">Tất cả</option>
        {apps.map((app) => (
          <option key={app.id} value={app.id}>{app.name}</option>
        ))}
      </select>
    </label>
  );
}

function Dashboard() {
  const [appId, setAppId] = useState('');
  const [stats, setStats] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [live, setLive] = useState(false);

  useEffect(() => {
    const query = appId ? `?appId=${appId}` : '';
    api(`/api/stats/overview${query}`).then(setStats).catch(() => setStats(null));
    api(`/api/alerts${query}`).then((rows) => setAlerts(rows.slice(0, 8))).catch(() => setAlerts([]));
  }, [appId]);

  useEffect(() => {
    const stopAlerts = subscribe('/api/stream/alerts', (event, data) => {
      if (event !== 'alert') return;
      setLive(true);
      if (appId && String(data.appId) !== appId) return;
      setAlerts((current) => [data, ...current.filter((row) => row.id !== data.id)].slice(0, 8));
      setStats((current) => current ? { ...current, alertCount: current.alertCount + 1 } : current);
    });
    const stopIncidents = subscribe('/api/stream/incidents', () => setLive(true));
    return () => {
      stopAlerts();
      stopIncidents();
    };
  }, [appId]);

  return (
    <section>
      <div className="row">
        <h1>Tổng quan</h1>
        <AppFilter appId={appId} setAppId={setAppId} />
        {live ? <span className="live">SSE đang nhận sự kiện</span> : <span className="disclaimer">Đang chờ SSE</span>}
      </div>
      <div className="kpis">
        <div className="panel kpi"><span>Sự kiện</span><strong>{stats?.eventCount ?? '—'}</strong></div>
        <div className="panel kpi"><span>Cảnh báo</span><strong>{stats?.alertCount ?? '—'}</strong></div>
        <div className="panel kpi"><span>Sự cố mở</span><strong>{stats?.openIncidentCount ?? '—'}</strong></div>
        <div className="panel kpi"><span>Mức nghiêm trọng</span><strong>{stats ? Object.values(stats.alertsBySeverity || {}).reduce((a, b) => a + b, 0) : '—'}</strong></div>
      </div>
      <div className="charts">
        <div className="panel">
          <h2>Dòng thời gian cảnh báo</h2>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={stats?.timeline || []}>
              <CartesianGrid stroke="#243140" />
              <XAxis dataKey="bucket" hide />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="#f0b429" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="panel">
          <h2>Loại tấn công</h2>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={stats?.topAttackTypes || []}>
              <XAxis dataKey="name" />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" fill="#3ddc97" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <div className="charts" style={{ marginTop: 12 }}>
        <Rank title="IP nhiều cảnh báo" rows={stats?.topIps} />
        <Rank title="Đường dẫn nhiều cảnh báo" rows={stats?.topPaths} />
      </div>
      <h2>Cảnh báo mới</h2>
      <AlertTable rows={alerts} />
    </section>
  );
}

function Rank({ title, rows }) {
  return (
    <div className="panel">
      <h2>{title}</h2>
      <table className="table">
        <tbody>
          {(rows || []).map((row) => (
            <tr key={row.name}><td>{row.name}</td><td>{row.count}</td></tr>
          ))}
          {(rows || []).length === 0 ? <tr><td>Chưa có dữ liệu</td></tr> : null}
        </tbody>
      </table>
    </div>
  );
}

function Alerts() {
  const [appId, setAppId] = useState('');
  const [rows, setRows] = useState([]);
  useEffect(() => {
    const query = appId ? `?appId=${appId}` : '';
    api(`/api/alerts${query}`).then(setRows).catch(() => setRows([]));
  }, [appId]);
  useEffect(() => {
    return subscribe('/api/stream/alerts', (event, data) => {
      if (event !== 'alert') return;
      if (appId && String(data.appId) !== appId) return;
      setRows((current) => [data, ...current.filter((row) => row.id !== data.id)]);
    });
  }, [appId]);
  return (
    <section>
      <div className="row"><h1>Cảnh báo</h1><AppFilter appId={appId} setAppId={setAppId} /></div>
      <AlertTable rows={rows} />
    </section>
  );
}

function AlertTable({ rows }) {
  return (
    <table className="table">
      <thead>
        <tr><th>Thời điểm</th><th>Mức</th><th>Điểm</th><th>IP</th><th>Tiêu đề</th><th>Sự cố</th></tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id}>
            <td>{formatTime(row.createdAt)}</td>
            <td><span className={`badge ${row.severity}`}>{row.severity}</span></td>
            <td>{row.score}</td>
            <td>{row.clientIp}</td>
            <td>{row.title}</td>
            <td>{row.incidentId ? <NavLink to={`/incidents/${row.incidentId}`}>#{row.incidentId}</NavLink> : 'chưa gắn'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Incidents() {
  const [appId, setAppId] = useState('');
  const [rows, setRows] = useState([]);
  useEffect(() => {
    const query = appId ? `?appId=${appId}` : '';
    api(`/api/incidents${query}`).then(setRows).catch(() => setRows([]));
  }, [appId]);
  useEffect(() => {
    return subscribe('/api/stream/incidents', (event, data) => {
      if (event !== 'incident') return;
      if (appId && String(data.appId) !== appId) return;
      setRows((current) => {
        const rest = current.filter((row) => row.id !== data.id);
        return [data, ...rest];
      });
    });
  }, [appId]);
  return (
    <section>
      <div className="row"><h1>Sự cố</h1><AppFilter appId={appId} setAppId={setAppId} /></div>
      <table className="table">
        <thead>
          <tr><th>Mở lúc</th><th>Trạng thái</th><th>Lý do</th><th>Điểm</th><th>Số cảnh báo</th><th>Giải thích</th><th></th></tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{formatTime(row.openedAt)}</td>
              <td><span className={`badge ${row.status}`}>{row.status}</span></td>
              <td>{row.promoteReason}</td>
              <td>{row.score}</td>
              <td>{row.alertCount}</td>
              <td><span className={`badge ${row.explanationStatus}`}>{row.explanationStatus}</span></td>
              <td><NavLink to={`/incidents/${row.id}`}>Chi tiết</NavLink></td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function IncidentDetail() {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');

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

  async function triage(status) {
    await api(`/api/incidents/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) });
    load();
  }

  async function retry() {
    await api(`/api/incidents/${id}/explain`, { method: 'POST' });
    load();
  }

  if (!detail) {
    return <p>{error || 'Đang tải…'}</p>;
  }
  const incident = detail.incident;
  return (
    <section>
      <h1>{incident.title}</h1>
      <p>
        <span className={`badge ${incident.status}`}>{incident.status}</span>{' '}
        <span className={`badge ${incident.severity}`}>{incident.severity}</span>{' '}
        điểm {incident.score} · {incident.alertCount} cảnh báo · {incident.promoteReason}
      </p>
      <div className="row">
        <button type="button" onClick={() => triage('ACK')} disabled={incident.status !== 'OPEN'}>Xác nhận</button>
        <button type="button" className="ghost" onClick={() => triage('CLOSED')} disabled={incident.status === 'CLOSED'}>Đóng</button>
        <button type="button" className="ghost" onClick={retry}>Giải thích lại</button>
      </div>
      <div className="panel" style={{ marginTop: 16 }}>
        <h2>Giải thích <span className={`badge ${incident.explanationStatus}`}>{incident.explanationStatus}</span></h2>
        <p className="disclaimer">AI hỗ trợ, có thể không chính xác.</p>
        <p>{detail.explanation || 'Chưa có nội dung giải thích.'}</p>
        {detail.explanationError ? <p className="error">{detail.explanationError}</p> : null}
      </div>
      <h2>Cảnh báo trong sự cố</h2>
      {(detail.alerts || []).map((item) => (
        <article key={item.alert.id} className="panel" style={{ marginBottom: 12 }}>
          <strong>{item.alert.title}</strong>
          <p>Điểm {item.alert.score} · {item.method} {item.path}{item.query ? `?${item.query}` : ''} · HTTP {item.httpStatus}</p>
          <ul className="hits">
            {(item.hits || []).map((hit) => (
              <li key={hit.id}>{hit.ruleCode} ({hit.category}) weight {hit.weight}: {hit.evidence}</li>
            ))}
          </ul>
        </article>
      ))}
    </section>
  );
}

function formatTime(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN');
}
