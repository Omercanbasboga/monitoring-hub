const pad = (n) => String(n).padStart(2, "0");

// UTC everywhere: gauges sit in four time zones and the server stores UTC.
export const hhmm = (t) => {
  const d = new Date(t);
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
};

export const dayTime = (t) => {
  const d = new Date(t);
  return `${pad(d.getUTCDate())}.${pad(d.getUTCMonth() + 1)} ${hhmm(t)}`;
};

export const level = (v) => (v == null ? "–" : v.toFixed(3));

export const duration = (ms) => {
  const m = Math.round(ms / 60000);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  return m % 60 ? `${h} h ${m % 60} min` : `${h} h`;
};

export const SIDE_LABEL = { PRIMARY: "Primary", SECONDARY: "Backup", NONE: "No data" };

export const SOURCE_LABEL = {
  REST_API: "REST polling",
  CSV_FEED: "CSV feed",
  FTP_DELIMITED: "FTP, delimited",
  FTP_BINARY: "FTP, GCF binary",
};
