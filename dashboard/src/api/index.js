import { mockApi } from "./mock";

// With VITE_API_URL set the dashboard talks to a running monitoring-hub;
// without it (the GitHub Pages build) it uses the in-browser simulation.
const BASE = import.meta.env.VITE_API_URL;

async function get(path) {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return res.json();
}

const ms = (t) => (t == null ? null : new Date(t).getTime());
const points = (arr) => arr.map((p) => ({ ...p, time: ms(p.time) }));

const liveApi = {
  live: true,
  matches: () => get("/api/matches"),
  match: (id) => get(`/api/matches/${id}`),
  series24h: async (id) => points(await get(`/api/matches/${id}/series/last-24h`)),
  sides24h: async (id) => {
    const m = await get(`/api/matches/${id}`);
    const side = async (s) => {
      if (!s) return [];
      const rows = await get(
        `/api/sources/${s.sourceType}/stations/${s.externalId}/readings/chart/last-24h`
      );
      return rows
        .filter((r) => r.primaryValue != null)
        .map((r) => ({ time: ms(r.timestamp), value: r.primaryValue }));
    };
    return { primary: await side(m.primary), secondary: await side(m.secondary) };
  },
  gaps: async (id) =>
    (await get(`/api/matches/${id}/gaps`)).map((g) => ({ ...g, slotTime: ms(g.slotTime) })),
  usage: async (id) =>
    (await get(`/api/matches/${id}/usage`)).map((u) => ({
      ...u,
      startedAt: ms(u.startedAt),
      endedAt: ms(u.endedAt),
    })),
  history: async (sourceType, externalId, page = 0, size = 20) => {
    const r = await get(
      `/api/v2/sources/${sourceType}/stations/${externalId}/readings/period?period=DAILY&page=${page}&size=${size}`
    );
    return { ...r, content: r.content.map((x) => ({ ...x, timestamp: ms(x.timestamp) })) };
  },
  intervals: () => get("/api/scheduler/intervals"),
  setInterval: async (source, value) => {
    const res = await fetch(`${BASE}/api/scheduler/intervals/${source}?intervalMs=${value}`, {
      method: "PUT",
    });
    if (!res.ok) throw new Error((await res.json()).error || res.statusText);
    return value;
  },
};

export const api = BASE ? liveApi : mockApi;
