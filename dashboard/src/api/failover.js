// Same rules as FailoverMerger and GapScanner on the server, so the demo
// behaves like the real service. Times are epoch milliseconds here.

export const TOLERANCE = 1.5;

export function merge(primary, secondary, windowStart, windowEnd, thresholdMs) {
  const p = primary.filter((x) => x.value != null).sort((a, b) => a.time - b.time);
  const backup = secondary.filter((x) => x.value != null).sort((a, b) => a.time - b.time);
  const between = (from, to, incFrom, incTo) =>
    backup.filter(
      (x) => (incFrom ? x.time >= from : x.time > from) && (incTo ? x.time <= to : x.time < to)
    );
  const out = [];
  const splice = (pts) =>
    pts.forEach((x) => out.push({ time: x.time, value: x.value, side: "SECONDARY" }));

  if (!p.length) {
    splice(between(windowStart, windowEnd, true, true));
    return out;
  }
  if (p[0].time - windowStart > thresholdMs) splice(between(windowStart, p[0].time, true, false));
  let prev = null;
  for (const pt of p) {
    if (prev != null && pt.time - prev > thresholdMs) splice(between(prev, pt.time, false, false));
    out.push({ time: pt.time, value: pt.value, side: "PRIMARY" });
    prev = pt.time;
  }
  if (windowEnd - prev > thresholdMs) splice(between(prev, windowEnd, false, true));
  return out;
}

// Slots sit on a fixed grid (multiples of the interval since the epoch) so the
// same outage always yields the same instants, as GapScanner does.
const gridAtOrAfter = (t, step) => Math.ceil(t / step) * step;

export function missingSlots(times, stepMs, windowStart, now, watchedWholeWindow = true) {
  const tolerance = stepMs * TOLERANCE;
  const settled = now - tolerance;
  const real = times.filter((t) => t >= windowStart - tolerance);
  const slots = [];
  if (!real.length) {
    if (!watchedWholeWindow) return slots;
    for (let t = gridAtOrAfter(windowStart, stepMs); t < settled; t += stepMs) slots.push(t);
    return slots;
  }
  const half = Math.floor(stepMs / 2);
  const hole = (from, to) => {
    if (to - from <= tolerance) return;
    for (let t = gridAtOrAfter(from + half, stepMs); t <= to - half; t += stepMs) {
      if (t >= windowStart) slots.push(t);
    }
  };
  for (let i = 1; i < real.length; i++) hole(real[i - 1], real[i]);
  hole(real[real.length - 1], settled);
  return slots;
}
