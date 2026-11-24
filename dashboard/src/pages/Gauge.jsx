import { useCallback, useEffect, useRef, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Skeleton from "@mui/material/Skeleton";
import Switch from "@mui/material/Switch";
import FormControlLabel from "@mui/material/FormControlLabel";
import Link from "@mui/material/Link";
import { useTheme } from "@mui/material/styles";
import { api } from "../api";
import useInterval from "../useInterval";
import Panel from "../components/Panel";
import SideChip from "../components/SideChip";
import SeriesChart from "../components/SeriesChart";
import GapStrip from "../components/GapStrip";
import UsageList from "../components/UsageList";
import ReadingsTable from "../components/ReadingsTable";
import GaugeMap from "../components/GaugeMap";
import { duration, level, SOURCE_LABEL } from "../format";

const DAY = 86_400_000;

function Feed({ label, station }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      {station ? (
        <Typography variant="body2" fontWeight={600}>
          {SOURCE_LABEL[station.sourceType]}
          <Box component="span" sx={{ color: "text.secondary", fontWeight: 400 }}>
            {" "}
            {station.externalId}
          </Box>
        </Typography>
      ) : (
        <Typography variant="body2" color="text.secondary">
          not configured
        </Typography>
      )}
    </Box>
  );
}

export default function Gauge() {
  const { id } = useParams();
  const t = useTheme().tokens;
  const [state, setState] = useState(null);
  const [error, setError] = useState(null);
  const [showRaw, setShowRaw] = useState(false);

  // the id this page is showing now; a response for an earlier id is dropped
  const current = useRef(id);
  current.current = id;

  const load = useCallback(async () => {
    try {
      const [match, all, points, raw, gaps, usage] = await Promise.all([
        api.match(id),
        api.matches(),
        api.series24h(id),
        api.sides24h(id),
        api.gaps(id),
        api.usage(id),
      ]);
      if (current.current !== id) return;
      setState({ match, all, points, raw, gaps, usage, end: Date.now() });
      setError(null);
    } catch (e) {
      if (current.current === id) setError(e.message);
    }
  }, [id]);

  useEffect(() => {
    setState(null);
    load();
  }, [load]);
  useInterval(load, 60_000);

  if (error && !state) {
    return (
      <Panel title="Gauge not found">
        <Typography mb={1}>{error}</Typography>
        <Link component={RouterLink} to="/">
          Back to all gauges
        </Link>
      </Panel>
    );
  }

  const m = state?.match;
  const last = state?.points[state.points.length - 1];
  const backupShare =
    state && state.points.length
      ? state.points.filter((p) => p.side === "SECONDARY").length / state.points.length
      : 0;
  // time covered by backup points, counting only steps that weren't breaks
  const onBackupMs = (state?.points ?? []).reduce((n, p, i, arr) => {
    const prev = arr[i - 1];
    return prev && p.side === "SECONDARY" && p.time - prev.time <= 90_000
      ? n + (p.time - prev.time)
      : n;
  }, 0);

  return (
    <Box>
      <Link
        component={RouterLink}
        to="/"
        underline="hover"
        variant="body2"
        sx={{ color: "text.secondary" }}
      >
        All gauges
      </Link>
      <Box
        display="flex"
        flexWrap="wrap"
        alignItems="flex-end"
        justifyContent="space-between"
        gap={2}
        mt={1}
        mb={3}
      >
        <Box>
          <Box display="flex" alignItems="center" gap={1.5} flexWrap="wrap">
            <Typography
              variant="h1"
              sx={{ fontSize: { xs: "2rem", md: "2.6rem" }, fontStretch: "125%" }}
            >
              {m ? m.name : <Skeleton width={220} />}
            </Typography>
            {m && <SideChip side={m.activeSide} />}
          </Box>
          {m && m.latitude != null && m.longitude != null && (
            <Typography color="text.secondary" mt={0.5}>
              {Math.abs(m.latitude).toFixed(3)}° {m.latitude < 0 ? "S" : "N"},{" "}
              {Math.abs(m.longitude).toFixed(3)}° {m.longitude < 0 ? "W" : "E"}
            </Typography>
          )}
        </Box>
        {m && (
          <Box display="flex" gap={4} flexWrap="wrap">
            <Feed label="Primary feed" station={m.primary} />
            <Feed label="Backup feed" station={m.secondary} />
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">
                Latest
              </Typography>
              <Typography
                sx={{ fontSize: "1.6rem", fontWeight: 600, fontStretch: "125%", lineHeight: 1.1 }}
              >
                {level(last?.value)}
                <Typography component="span" sx={{ ml: 0.5, color: "text.secondary" }}>
                  m
                </Typography>
              </Typography>
            </Box>
          </Box>
        )}
      </Box>

      <Box
        display="grid"
        gridTemplateColumns={{ xs: "1fr", lg: "minmax(0, 1fr) 360px" }}
        gap={2}
        alignItems="start"
      >
        <Box display="grid" gap={2} minWidth={0}>
          <Panel
            title="Merged series"
            endpoint={`GET /api/matches/${id}/series/last-24h`}
            action={
              <FormControlLabel
                sx={{
                  mr: 0,
                  "& .MuiFormControlLabel-label": { fontSize: "0.8rem", color: "text.secondary" },
                }}
                control={
                  <Switch
                    size="small"
                    checked={showRaw}
                    onChange={(e) => setShowRaw(e.target.checked)}
                  />
                }
                label="Show both feeds"
              />
            }
          >
            {state ? (
              <SeriesChart points={state.points} raw={state.raw} showRaw={showRaw} match={m} />
            ) : (
              <Skeleton variant="rounded" height={340} />
            )}
            {state && (
              <Typography variant="body2" color="text.secondary" mt={1}>
                {backupShare > 0
                  ? `${Math.round(backupShare * 100)}% of today's points came from the backup feed, about ${duration(onBackupMs)} in total. Drag across the chart to zoom.`
                  : "Every point today came from the primary feed. Drag across the chart to zoom."}
              </Typography>
            )}
          </Panel>

          <Panel title="Missed polling slots" endpoint={`GET /api/matches/${id}/gaps`}>
            {state ? (
              <GapStrip
                gaps={state.gaps}
                start={state.end - DAY}
                end={state.end}
                hasBackup={!!m.secondary}
              />
            ) : (
              <Skeleton variant="rounded" height={90} />
            )}
          </Panel>

          {m && <ReadingsTable match={m} />}
        </Box>

        <Box display="grid" gap={2}>
          <Panel title="Source switches" endpoint={`GET /api/matches/${id}/usage`}>
            {state ? (
              <UsageList rows={state.usage} now={state.end} />
            ) : (
              <Skeleton variant="rounded" height={160} />
            )}
          </Panel>
          <Panel title="Nearby">
            {state ? (
              <GaugeMap matches={state.all} focus={m} />
            ) : (
              <Skeleton variant="rounded" height={160} />
            )}
          </Panel>
        </Box>
      </Box>
    </Box>
  );
}
