import { useCallback, useEffect, useMemo, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Skeleton from "@mui/material/Skeleton";
import { useTheme } from "@mui/material/styles";
import { api } from "../api";
import useInterval from "../useInterval";
import TideBoard from "../components/TideBoard";
import GaugeMap from "../components/GaugeMap";
import Panel from "../components/Panel";
import Kpi from "../components/Kpi";
import Legend from "../components/Legend";
import { Glyph, sideColor } from "../components/SideChip";

const DAY = 86_400_000;

export default function Overview() {
  const t = useTheme().tokens;
  const [state, setState] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const matches = await api.matches();
      const [series, gaps] = await Promise.all([
        Promise.all(matches.map((m) => api.series24h(m.id))),
        Promise.all(matches.map((m) => api.gaps(m.id))),
      ]);
      const end = Date.now();
      setState({
        matches,
        series: Object.fromEntries(matches.map((m, i) => [m.id, series[i]])),
        missed: gaps.reduce((n, g) => n + g.length, 0),
        end,
      });
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useInterval(load, 60_000);

  const counts = useMemo(() => {
    const c = { PRIMARY: 0, SECONDARY: 0, NONE: 0 };
    state?.matches.forEach((m) => {
      c[m.activeSide || "NONE"] += 1;
    });
    return c;
  }, [state]);

  const marker = (side) => <Glyph side={side} color={sideColor(t, side)} />;

  return (
    <Box>
      <Box
        display="grid"
        gridTemplateColumns={{ xs: "1fr", lg: "minmax(0, 5fr) minmax(0, 6fr)" }}
        gap={{ xs: 3, lg: 4 }}
        mb={3}
      >
        <Box display="flex" flexDirection="column">
          <Typography
            variant="h1"
            component="h1"
            mb={1.25}
            sx={{ fontSize: { xs: "2rem", md: "2.5rem" } }}
          >
            Water level, last 24 hours
          </Typography>
          <Typography color="text.secondary" mb={3} maxWidth={560}>
            Eight coastal gauges, each reported by two different feeds. When the primary feed goes
            quiet for longer than one and a half polling intervals, readings from the backup fill
            the hole, and every missed slot is logged.
          </Typography>
          <Box display="grid" gridTemplateColumns="1fr 1fr" gap={2} mt="auto">
            <Kpi
              label="On primary"
              value={state ? counts.PRIMARY : "–"}
              unit="gauges"
              marker={marker("PRIMARY")}
              note="Primary feed is fresh"
            />
            <Kpi
              label="On backup"
              value={state ? counts.SECONDARY : "–"}
              unit="gauges"
              marker={marker("SECONDARY")}
              note="Primary silent, backup serving"
            />
            <Kpi
              label="No data"
              value={state ? counts.NONE : "–"}
              unit="gauges"
              marker={marker("NONE")}
              note="Neither feed is fresh"
            />
            <Kpi
              label="Missed slots"
              value={state ? state.missed.toLocaleString("en") : "–"}
              note="Both sides, last 24 h"
            />
          </Box>
        </Box>
        <Panel title="Where they are" endpoint="GET /api/matches">
          {state ? (
            <GaugeMap matches={state.matches} />
          ) : (
            <Skeleton variant="rounded" sx={{ aspectRatio: "1000 / 450", height: "auto" }} />
          )}
        </Panel>
      </Box>

      {error && (
        <Panel sx={{ mb: 2, borderColor: t.none }}>
          <Typography>Couldn't load the gauges: {error}. The page retries every minute.</Typography>
        </Panel>
      )}

      <Panel
        title="Gauges"
        endpoint="GET /api/matches/{id}/series/last-24h"
        action={
          <Legend
            items={[
              { label: "Primary feed", color: t.primary },
              { label: "Backup feed", color: t.backup },
              { label: "No data", color: t.none, kind: "bar" },
            ]}
          />
        }
        sx={{ pb: 0.5 }}
        bodySx={{ mx: { xs: -2, md: -2.5 } }}
      >
        {state ? (
          <TideBoard
            matches={state.matches}
            series={state.series}
            start={state.end - DAY}
            end={state.end}
          />
        ) : (
          <Box px={2}>
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} height={74} />
            ))}
          </Box>
        )}
      </Panel>
      <Typography variant="body2" color="text.secondary" mt={1.5} maxWidth={760}>
        Each line is scaled to its own gauge. The strip under it shows which feed served each
        stretch of the day, red where neither did. Hover a line to read every gauge at the same
        moment; open one for its gap log and raw readings.
      </Typography>
    </Box>
  );
}
