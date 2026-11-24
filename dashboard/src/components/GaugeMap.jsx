import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import { useTheme } from "@mui/material/styles";
import { sideColor } from "./SideChip";
import { SIDE_LABEL } from "../format";
import { HEIGHT, LAND, SCALE, TRANSLATE, WIDTH } from "../map/mediterranean";

const RAD = Math.PI / 180;

// same Mercator the path was generated with
function project(lat, lon) {
  return [
    SCALE * lon * RAD + TRANSLATE[0],
    TRANSLATE[1] - SCALE * Math.log(Math.tan(Math.PI / 4 + (lat * RAD) / 2)),
  ];
}

let ctx;
function textWidth(text, bold) {
  ctx = ctx || document.createElement("canvas").getContext("2d");
  ctx.font = `${bold ? 700 : 600} 11.84px "Archivo Variable", system-ui, sans-serif`;
  return ctx.measureText(text).width;
}

// Each label tries eight spots around its dot and takes the one that overlaps
// least with labels already placed, other dots and the frame edge. Eight
// gauges don't need anything smarter than that.
function placeLabels(points, k, frameW, frameH) {
  const placed = [];
  const dots = points.map((p) => ({ x: p.x * k - 8, y: p.y * k - 8, w: 16, h: 16 }));
  const overlap = (a, b) =>
    Math.max(0, Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x)) *
    Math.max(0, Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y));
  const outside = (r) =>
    Math.max(0, 4 - r.x) +
    Math.max(0, r.x + r.w - frameW + 4) +
    Math.max(0, 4 - r.y) +
    Math.max(0, r.y + r.h - frameH + 4);

  return points.map((pt) => {
    const w = Math.ceil(textWidth(pt.text, pt.bold)) + 18;
    const h = 22;
    const [x, y] = [pt.x * k, pt.y * k];
    const tries = [
      [x + 10, y - h / 2],
      [x - 10 - w, y - h / 2],
      [x - w / 2, y - h - 10],
      [x - w / 2, y + 10],
      [x + 6, y - h - 6],
      [x + 6, y + 6],
      [x - w - 6, y + 6],
      [x - w - 6, y - h - 6],
    ];
    let best = null;
    tries.forEach(([cx, cy], i) => {
      const r = { x: cx, y: cy, w, h };
      const cost =
        placed.reduce((n, p) => n + overlap(r, p), 0) * 4 +
        dots.reduce((n, d) => n + overlap(r, d), 0) * 2 +
        outside(r) * 400 +
        i; // prefer earlier spots on a tie
      if (!best || cost < best.cost) best = { ...r, cost };
    });
    placed.push(best);
    return { ...pt, label: best };
  });
}

export default function GaugeMap({ matches: all, focus }) {
  // a match without coordinates can't be drawn; it still shows everywhere else
  const matches = useMemo(
    () => all.filter((m) => m.latitude != null && m.longitude != null),
    [all]
  );
  const t = useTheme().tokens;
  const navigate = useNavigate();
  const box = useRef(null);
  const [width, setWidth] = useState(0);

  useLayoutEffect(() => {
    const el = box.current;
    const ro = new ResizeObserver(([e]) => setWidth(e.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const k = width / WIDTH;
  // below this the map is too small for name tags; the dots still open the gauge
  const showLabels = width >= 420 || (focus && width > 0);
  const points = useMemo(() => {
    // on a gauge page only the gauge itself is labelled; the rest are context
    const pts = matches
      .filter((m) => !focus || m.id === focus.id)
      .map((m) => {
        const [x, y] = project(m.latitude, m.longitude);
        return { m, x, y, bold: focus?.id === m.id, text: m.name };
      });
    // place the focused gauge first so it keeps the best spot
    pts.sort((a, b) => (b.m.id === focus?.id) - (a.m.id === focus?.id) || a.y - b.y);
    return placeLabels(pts, k, width, width * (HEIGHT / WIDTH));
  }, [matches, focus, k, width]);

  return (
    <Box
      ref={box}
      sx={{
        position: "relative",
        width: "100%",
        aspectRatio: `${WIDTH} / ${HEIGHT}`,
        bgcolor: t.sea,
        borderRadius: "10px",
        overflow: "hidden",
      }}
    >
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        width="100%"
        height="100%"
        style={{ display: "block" }}
        aria-hidden
      >
        <path d={LAND} fill={t.land} stroke={t.coast} strokeWidth="0.8" strokeLinejoin="round" />
      </svg>
      {width > 0 &&
        matches.map((m) => {
          const [x, y] = project(m.latitude, m.longitude);
          const color = sideColor(t, m.activeSide);
          return (
            <ButtonBase
              key={m.id}
              title={m.name}
              aria-label={`${m.name}, ${SIDE_LABEL[m.activeSide]}. Open gauge`}
              tabIndex={showLabels ? -1 : 0}
              onClick={() => navigate(`/gauge/${m.id}`)}
              sx={{
                position: "absolute",
                left: x * k,
                top: y * k,
                width: 12,
                height: 12,
                ml: "-6px",
                mt: "-6px",
                // a 28px hit area around the 12px dot
                "&::after": { content: '""', position: "absolute", inset: -8, borderRadius: "50%" },
                borderRadius: "50%",
                border: `2px solid ${color}`,
                boxShadow: `0 0 0 2px ${t.paper}`,
                bgcolor: m.activeSide === "PRIMARY" ? color : t.paper,
                backgroundImage:
                  m.activeSide === "SECONDARY"
                    ? `linear-gradient(90deg, transparent 50%, ${color} 50%)`
                    : "none",
                opacity: focus && focus.id !== m.id ? 0.5 : 1,
              }}
            />
          );
        })}
      {showLabels &&
        points.map(({ m, text, label }) => {
          const color = sideColor(t, m.activeSide);
          return (
            <ButtonBase
              key={m.id}
              onClick={() => navigate(`/gauge/${m.id}`)}
              aria-label={`${m.name}, ${SIDE_LABEL[m.activeSide]}. Open gauge`}
              sx={{
                position: "absolute",
                left: label.x,
                top: label.y,
                height: label.h,
                width: label.w,
                borderRadius: "6px",
                bgcolor: t.paper,
                border: `1px solid ${t.hairline}`,
                fontSize: "0.74rem",
                fontWeight: focus?.id === m.id ? 700 : 600,
                color: t.ink,
                whiteSpace: "pre",
                "&:hover, &:focus-visible": { borderColor: color },
              }}
            >
              {text}
            </ButtonBase>
          );
        })}
      <Box
        component="span"
        sx={{ position: "absolute", right: 8, bottom: 4, fontSize: "0.65rem", color: t.muted }}
      >
        Coastline: Natural Earth
      </Box>
    </Box>
  );
}
