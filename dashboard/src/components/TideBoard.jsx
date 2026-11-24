import { useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";
import SideChip, { sideColor } from "./SideChip";
import { hhmm, level, SOURCE_LABEL } from "../format";

const W = 1000;
const H = 56;
const PAD = 6;
const BREAK_MS = 90_000; // a jump longer than this is drawn as a break

function segments(points) {
  const out = [];
  let cur = null;
  for (const p of points) {
    const prev = cur && cur.points[cur.points.length - 1];
    if (!cur || cur.side !== p.side || p.time - prev.time > BREAK_MS) {
      // keep the line continuous across a side switch by starting from the last point
      cur = { side: p.side, points: prev && p.time - prev.time <= BREAK_MS ? [prev] : [] };
      out.push(cur);
    }
    cur.points.push(p);
  }
  return out;
}

// Coverage strip under each line: which side served each stretch of the day.
function coverage(points, start, end) {
  const out = [];
  let run = null;
  const close = (t) => {
    if (run) {
      run.to = t;
      out.push(run);
      run = null;
    }
  };
  let prev = null;
  for (const p of points) {
    if (prev && p.time - prev.time > BREAK_MS) {
      close(prev.time);
      out.push({ side: "NONE", from: prev.time, to: p.time });
    }
    if (!run || run.side !== p.side) {
      close(p.time);
      run = { side: p.side, from: p.time };
    }
    prev = p;
  }
  close(prev ? prev.time : start);
  if (!points.length) out.push({ side: "NONE", from: start, to: end });
  else {
    if (points[0].time - start > BREAK_MS)
      out.unshift({ side: "NONE", from: start, to: points[0].time });
    if (end - prev.time > BREAK_MS) out.push({ side: "NONE", from: prev.time, to: end });
  }
  return out;
}

function nearest(points, t) {
  let lo = 0,
    hi = points.length - 1;
  if (hi < 0) return null;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (points[mid].time < t) lo = mid + 1;
    else hi = mid;
  }
  const a = points[Math.max(0, lo - 1)],
    b = points[lo];
  const best = Math.abs(a.time - t) <= Math.abs(b.time - t) ? a : b;
  return Math.abs(best.time - t) <= BREAK_MS ? best : null;
}

function Row({ match, points, start, end, hover, setHover }) {
  const t = useTheme().tokens;
  const x = (time) => ((time - start) / (end - start)) * W;
  const { segs, y, strip } = useMemo(() => {
    const vals = points.map((p) => p.value);
    const lo = Math.min(...vals),
      hi = Math.max(...vals);
    const span = hi - lo || 0.01;
    return {
      segs: segments(points),
      y: (v) => PAD + (1 - (v - lo) / span) * (H - PAD * 2),
      strip: coverage(points, start, end),
    };
  }, [points, start, end]);

  const at = hover != null ? nearest(points, hover) : points[points.length - 1];
  const onMove = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    setHover(start + ((e.clientX - r.left) / r.width) * (end - start));
  };

  return (
    <Box
      component={RouterLink}
      to={`/gauge/${match.id}`}
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "1fr auto", md: "170px 1fr 104px 92px" },
        gridTemplateAreas: {
          xs: '"name value" "spark spark" "chip chip"',
          md: '"name spark chip value"',
        },
        alignItems: "center",
        columnGap: 2,
        rowGap: 0.75,
        px: { xs: 1.5, md: 2 },
        py: 1.25,
        color: "inherit",
        textDecoration: "none",
        borderTop: `1px solid ${t.hairline}`,
        transition: "background-color 120ms",
        "&:hover, &:focus-visible": { bgcolor: t.water + "55", outline: "none" },
        "&:focus-visible": { boxShadow: `inset 3px 0 0 ${t.brand}` },
      }}
    >
      <Box gridArea="name" minWidth={0}>
        <Typography variant="h3" sx={{ fontStretch: "115%" }}>
          {match.name}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap component="div">
          {SOURCE_LABEL[match.primary.sourceType]}
          {match.secondary ? `, backup ${SOURCE_LABEL[match.secondary.sourceType]}` : ", no backup"}
        </Typography>
      </Box>

      <Box
        gridArea="spark"
        position="relative"
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <svg
          viewBox={`0 0 ${W} ${H + 8}`}
          preserveAspectRatio="none"
          width="100%"
          height={H + 8}
          style={{ display: "block", overflow: "visible" }}
          aria-hidden
        >
          {[0.25, 0.5, 0.75].map((f) => (
            <line
              key={f}
              x1={W * f}
              x2={W * f}
              y1={0}
              y2={H}
              stroke={t.grid}
              vectorEffect="non-scaling-stroke"
            />
          ))}
          {segs.map((s, i) => (
            <polyline
              key={i}
              points={s.points
                .map((p) => `${x(p.time).toFixed(1)},${y(p.value).toFixed(1)}`)
                .join(" ")}
              fill="none"
              stroke={sideColor(t, s.side)}
              strokeWidth="2"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          {strip.map((r, i) => (
            <rect
              key={i}
              x={x(r.from)}
              width={Math.max(x(r.to) - x(r.from), 2)}
              y={H + 3}
              height={4}
              fill={sideColor(t, r.side)}
              opacity={r.side === "NONE" ? 1 : 0.55}
            />
          ))}
          {hover != null && hover >= start && hover <= end && (
            <line
              x1={x(hover)}
              x2={x(hover)}
              y1={0}
              y2={H + 8}
              stroke={t.ink2}
              vectorEffect="non-scaling-stroke"
            />
          )}
        </svg>
        {at && (
          <Box
            sx={{
              position: "absolute",
              left: `${((at.time - start) / (end - start)) * 100}%`,
              top: y(at.value) * ((H + 8) / (H + 8)),
              width: 8,
              height: 8,
              ml: "-4px",
              mt: "-4px",
              borderRadius: "50%",
              bgcolor: sideColor(t, at.side),
              boxShadow: `0 0 0 2px ${t.paper}`,
              pointerEvents: "none",
            }}
          />
        )}
      </Box>

      <Box gridArea="chip">
        <SideChip side={match.activeSide} size="sm" />
      </Box>

      <Box gridArea="value" textAlign="right">
        <Typography
          sx={{ fontSize: "1.35rem", fontWeight: 600, fontStretch: "125%", lineHeight: 1.1 }}
        >
          {level(at?.value)}
          <Typography
            component="span"
            sx={{ fontSize: "0.75rem", ml: 0.4, color: "text.secondary", fontStretch: "100%" }}
          >
            m
          </Typography>
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {at ? hhmm(at.time) : "no reading"}
        </Typography>
      </Box>
    </Box>
  );
}

export default function TideBoard({ matches, series, start, end }) {
  const [hover, setHover] = useState(null);
  const t = useTheme().tokens;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => start + f * (end - start));

  return (
    <Box>
      <Box
        sx={{
          display: { xs: "none", md: "grid" },
          gridTemplateColumns: "170px 1fr 104px 92px",
          columnGap: 2,
          px: 2,
          pb: 1,
        }}
      >
        <Typography variant="caption" color="text.secondary">
          {hover != null ? `At ${hhmm(hover)} UTC` : "Last 24 hours, UTC"}
        </Typography>
        <Box position="relative" height={16}>
          {ticks.map((tick, i) => (
            <Typography
              key={i}
              variant="caption"
              sx={{
                position: "absolute",
                left: `${i * 25}%`,
                color: t.muted,
                transform: i === 0 ? "none" : i === 4 ? "translateX(-100%)" : "translateX(-50%)",
              }}
            >
              {i === 4 ? "now" : hhmm(tick)}
            </Typography>
          ))}
        </Box>
        <Typography variant="caption" color="text.secondary">
          Serving from
        </Typography>
        <Typography variant="caption" color="text.secondary" textAlign="right">
          Level
        </Typography>
      </Box>
      {matches.map((m) => (
        <Row
          key={m.id}
          match={m}
          points={series[m.id] || []}
          start={start}
          end={end}
          hover={hover}
          setHover={setHover}
        />
      ))}
    </Box>
  );
}
