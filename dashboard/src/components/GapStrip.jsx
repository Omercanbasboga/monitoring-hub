import { useMemo, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";
import { sideColor } from "./SideChip";
import { duration, hhmm } from "../format";

const STEP_SLACK = 1.5;

// Consecutive missed slots collapse into one outage: a run continues while the
// next slot follows within one and a half polling steps.
function outages(slots, side) {
  const times = slots
    .filter((s) => s.side === side)
    .map((s) => s.slotTime)
    .sort((a, b) => a - b);
  if (!times.length) return [];
  const step = Math.min(...times.slice(1).map((t, i) => t - times[i]), 60_000);
  const out = [];
  let run = { from: times[0], to: times[0], slots: 1 };
  for (let i = 1; i < times.length; i++) {
    if (times[i] - run.to <= step * STEP_SLACK) {
      run.to = times[i];
      run.slots += 1;
    } else {
      out.push(run);
      run = { from: times[i], to: times[i], slots: 1 };
    }
  }
  out.push(run);
  return out.map((r) => ({ ...r, to: r.to + step }));
}

export default function GapStrip({ gaps, start, end, hasBackup }) {
  const t = useTheme().tokens;
  const [hover, setHover] = useState(null);
  const lanes = useMemo(
    () => [
      { side: "PRIMARY", label: "Primary", runs: outages(gaps, "PRIMARY") },
      ...(hasBackup
        ? [{ side: "SECONDARY", label: "Backup", runs: outages(gaps, "SECONDARY") }]
        : []),
    ],
    [gaps, hasBackup]
  );
  const pct = (time) => `${((time - start) / (end - start)) * 100}%`;

  return (
    <Box>
      {lanes.map((lane) => (
        <Box
          key={lane.side}
          display="grid"
          gridTemplateColumns={{ xs: "56px 1fr 60px", sm: "64px 1fr 96px" }}
          alignItems="center"
          gap={1.5}
          mb={1}
        >
          <Typography variant="body2" fontWeight={600}>
            {lane.label}
          </Typography>
          <Box
            position="relative"
            height={22}
            sx={{ bgcolor: t.water + "66", borderRadius: "5px" }}
          >
            {lane.runs.map((r, i) => (
              <Box
                key={i}
                tabIndex={0}
                onMouseEnter={() => setHover({ ...r, lane: lane.label })}
                onFocus={() => setHover({ ...r, lane: lane.label })}
                onMouseLeave={() => setHover(null)}
                onBlur={() => setHover(null)}
                aria-label={`${lane.label} missed ${r.slots} slots from ${hhmm(r.from)} to ${hhmm(r.to)}`}
                sx={{
                  position: "absolute",
                  top: 3,
                  bottom: 3,
                  left: pct(r.from),
                  width: `max(3px, calc(${pct(r.to)} - ${pct(r.from)}))`,
                  bgcolor: sideColor(t, lane.side),
                  borderRadius: "3px",
                  cursor: "default",
                  outlineOffset: 2,
                }}
              />
            ))}
          </Box>
          <Typography variant="caption" color="text.secondary" textAlign="right">
            {lane.runs.reduce((n, r) => n + r.slots, 0)} slots
          </Typography>
        </Box>
      ))}
      <Box
        display="grid"
        gridTemplateColumns={{ xs: "56px 1fr 60px", sm: "64px 1fr 96px" }}
        gap={1.5}
      >
        <span />
        <Box display="flex" justifyContent="space-between">
          {[0, 0.25, 0.5, 0.75, 1].map((f) => (
            <Typography
              key={f}
              variant="caption"
              sx={{
                color: t.muted,
                display: f === 0 || f === 1 ? "block" : { xs: "none", sm: "block" },
              }}
            >
              {f === 1 ? "now" : hhmm(start + f * (end - start))}
            </Typography>
          ))}
        </Box>
      </Box>
      <Typography variant="body2" color="text.secondary" mt={1.5} minHeight={22}>
        {hover
          ? `${hover.lane}: ${hover.slots} missed slots, ${hhmm(hover.from)} to ${hhmm(hover.to)} UTC (${duration(hover.to - hover.from)}).`
          : lanes.every((l) => !l.runs.length)
            ? "Nothing missed in the last 24 hours."
            : "Point at a bar for the exact window."}
      </Typography>
    </Box>
  );
}
