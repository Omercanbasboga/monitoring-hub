import { merge, missingSlots, TOLERANCE } from "./failover";

// The same eight gauges as the server's demo profile (DemoFeed.java):
// two sources each, a day of tide-shaped readings and a few outages.
// Everything is generated in the browser, nothing is fetched.

const MIN = 60_000;
const DAY = 24 * 60 * MIN;
const M2_HOURS = 12.42;

const GAUGES = [
  ["Valletta", 35.897, 14.512, "REST_API", "CSV_FEED", 0.42, 0.11, [[620, 540]], []],
  ["Piraeus", 37.937, 23.627, "REST_API", "FTP_DELIMITED", 0.31, 0.09, [[95, 0]], [[900, 860]]],
  ["Split", 43.507, 16.441, "CSV_FEED", "REST_API", 0.55, 0.18, [], []],
  [
    "Bodrum",
    37.032,
    27.423,
    "FTP_BINARY",
    "REST_API",
    0.28,
    0.08,
    [
      [1200, 1080],
      [300, 270],
    ],
    [[1150, 1120]],
  ],
  ["Alicante", 38.338, -0.477, "REST_API", "CSV_FEED", 0.37, 0.12, [[40, 0]], [[40, 0]]],
  ["Naples", 40.84, 14.269, "FTP_DELIMITED", "FTP_BINARY", 0.46, 0.15, [[480, 420]], []],
  ["Heraklion", 35.344, 25.137, "REST_API", null, 0.25, 0.07, [[200, 185]], []],
  ["Limassol", 34.672, 33.044, "CSV_FEED", "REST_API", 0.33, 0.1, [], [[700, 0]]],
].map(([name, lat, lon, primary, backup, mean, amp, pOut, bOut], i) => ({
  id: `m-${String(i + 1).padStart(2, "0")}`,
  name,
  lat,
  lon,
  primary,
  backup,
  mean,
  amp,
  pOut,
  bOut,
}));

export const SOURCES = ["REST_API", "CSV_FEED", "FTP_DELIMITED", "FTP_BINARY"];

const started = Math.floor(Date.now() / MIN) * MIN;
const intervals = Object.fromEntries(SOURCES.map((s) => [s, MIN]));

// deterministic noise so a reload draws the same lines
function noise(seed) {
  const x = Math.sin(seed * 12.9898) * 43758.5453;
  return (x - Math.floor(x) - 0.5) * 0.005;
}

function level(g, backup, t) {
  const h = t / 3_600_000;
  const tide = g.amp * Math.sin((2 * Math.PI * h) / M2_HOURS + g.lon);
  const seiche = 0.006 * Math.sin((2 * Math.PI * h) / 0.9);
  const v =
    g.mean + tide + seiche + (backup ? 0.012 : 0) + noise(t / MIN + g.lat * (backup ? 7 : 3));
  return Math.round(v * 1000) / 1000;
}

function down(g, backup, t) {
  const minsAgo = (started - t) / MIN;
  return (backup ? g.bOut : g.pOut).some(([a, b]) => minsAgo <= a && minsAgo >= b);
}

function readings(g, backup, from, to) {
  const out = [];
  if (backup && !g.backup) return out;
  for (let t = Math.ceil(from / MIN) * MIN; t <= to; t += MIN) {
    if (!down(g, backup, t)) out.push({ time: t, value: level(g, backup, t) });
  }
  return out;
}

const now = () => Math.floor(Date.now() / MIN) * MIN;
const threshold = (source) => intervals[source] * TOLERANCE;
const fresh = (times, at, thr) => times.length > 0 && at - times[times.length - 1] <= thr;

function station(g, backup) {
  const source = backup ? g.backup : g.primary;
  if (!source) return null;
  const externalId = `${g.name.toLowerCase()}-${backup ? "b" : "p"}`;
  return { id: `${g.id}-${backup ? "b" : "p"}`, sourceType: source, externalId, name: g.name };
}

function activeSideAt(g, pTimes, bTimes, at) {
  const upTo = (arr) => {
    let lo = 0,
      hi = arr.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (arr[mid] <= at) lo = mid + 1;
      else hi = mid;
    }
    return arr.slice(Math.max(0, lo - 1), lo);
  };
  if (fresh(upTo(pTimes), at, threshold(g.primary))) return "PRIMARY";
  if (g.backup && fresh(upTo(bTimes), at, threshold(g.backup))) return "SECONDARY";
  return "NONE";
}

function usage(g) {
  const end = now();
  const start = end - DAY;
  const pTimes = readings(g, false, start - DAY, end).map((x) => x.time);
  const bTimes = readings(g, true, start - DAY, end).map((x) => x.time);
  const rows = [];
  for (let t = start; t <= end; t += MIN) {
    const side = activeSideAt(g, pTimes, bTimes, t);
    const open = rows[rows.length - 1];
    if (open && open.side === side) continue;
    if (open) open.endedAt = t;
    rows.push({ side, startedAt: t, endedAt: null });
  }
  return rows.reverse();
}

function toDto(g) {
  const end = now();
  const pTimes = readings(g, false, end - 10 * MIN, end).map((x) => x.time);
  const bTimes = readings(g, true, end - 10 * MIN, end).map((x) => x.time);
  return {
    id: g.id,
    name: g.name,
    latitude: g.lat,
    longitude: g.lon,
    primary: station(g, false),
    secondary: station(g, true),
    activeSide: activeSideAt(g, pTimes, bTimes, end),
  };
}

const wait = (v) =>
  new Promise((r) => setTimeout(() => r(structuredClone(v)), 60 + Math.random() * 120));
const find = (id) => {
  const g = GAUGES.find((x) => x.id === id);
  if (!g) throw new Error(`Unknown match: ${id}`);
  return g;
};

export const mockApi = {
  live: false,

  matches: () => wait(GAUGES.map(toDto)),

  match: (id) => wait(toDto(find(id))),

  series24h: (id) => {
    const g = find(id);
    const end = now();
    const start = end - DAY;
    return wait(
      merge(
        readings(g, false, start, end),
        readings(g, true, start, end),
        start,
        end,
        threshold(g.primary)
      )
    );
  },

  // both sides, unmerged: what the merged series was built from
  sides24h: (id) => {
    const g = find(id);
    const end = now();
    return wait({
      primary: readings(g, false, end - DAY, end),
      secondary: readings(g, true, end - DAY, end),
    });
  },

  gaps: (id) => {
    const g = find(id);
    const end = now();
    const start = end - DAY;
    const side = (backup, source) =>
      missingSlots(
        readings(g, backup, start - DAY, end).map((x) => x.time),
        intervals[source],
        start,
        end
      ).map((slotTime) => ({ slotTime, side: backup ? "SECONDARY" : "PRIMARY" }));
    const rows = [...side(false, g.primary), ...(g.backup ? side(true, g.backup) : [])];
    return wait(rows.sort((a, b) => b.slotTime - a.slotTime));
  },

  usage: (id) => wait(usage(find(id))),

  history: (sourceType, externalId, page = 0, size = 20) => {
    const g = GAUGES.find((x) =>
      [station(x, false), station(x, true)].some(
        (s) => s && s.sourceType === sourceType && s.externalId === externalId
      )
    );
    const backup = externalId.endsWith("-b");
    const end = now();
    const all = readings(g, backup, end - DAY, end)
      .reverse()
      .map((r) => ({
        stationExternalId: externalId,
        timestamp: r.time,
        primaryValue: r.value,
        secondaryValue: null,
      }));
    const content = all.slice(page * size, page * size + size);
    const totalPages = Math.ceil(all.length / size);
    return wait({
      content,
      page,
      size,
      totalElements: all.length,
      totalPages,
      first: page === 0,
      last: page >= totalPages - 1,
    });
  },

  intervals: () => wait({ ...intervals }),

  setInterval: (source, ms) => {
    if (ms < 5000 || ms > 600000)
      return Promise.reject(new Error("Interval must be between 5000ms and 600000ms"));
    intervals[source] = ms;
    return wait(ms);
  },
};
