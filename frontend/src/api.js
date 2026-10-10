const API = import.meta.env.VITE_API_BASE_URL || '';

export function getToken() {
  return sessionStorage.getItem('socToken') || '';
}

export function setSession(token, role) {
  sessionStorage.setItem('socToken', token);
  sessionStorage.setItem('socRole', role || '');
}

export function clearSession() {
  sessionStorage.removeItem('socToken');
  sessionStorage.removeItem('socRole');
}

export function role() {
  return sessionStorage.getItem('socRole') || '';
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(`${API}${path}`, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    throw new Error('unauthorized');
  }
  if (!response.ok) {
    throw new Error(await response.text());
  }
  if (response.status === 204) {
    return null;
  }
  const type = response.headers.get('content-type') || '';
  return type.includes('application/json') ? response.json() : response.text();
}

export function subscribe(path, onEvent) {
  const controller = new AbortController();
  const token = getToken();
  let stopped = false;

  async function read() {
    while (!stopped) {
      try {
        const response = await fetch(`${API}${path}`, {
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'text/event-stream',
          },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`sse ${response.status}`);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (!stopped) {
          const { value, done } = await reader.read();
          if (done) {
            break;
          }
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split('\n\n');
          buffer = frames.pop() || '';
          for (const frame of frames) {
            let event = 'message';
            let data = '';
            for (const line of frame.split('\n')) {
              if (line.startsWith('event:')) {
                event = line.slice(6).trim();
              } else if (line.startsWith('data:')) {
                data += line.slice(5).trim();
              }
            }
            if (!data) {
              continue;
            }
            onEvent(event, JSON.parse(data));
          }
        }
      } catch (error) {
        if (stopped || controller.signal.aborted) {
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    }
  }

  read();
  return () => {
    stopped = true;
    controller.abort();
  };
}
