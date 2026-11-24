import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Alert from "@mui/material/Alert";
import { api } from "../api";
import Panel from "../components/Panel";
import { SOURCE_LABEL } from "../format";

const CONNECTORS = {
  REST_API: {
    cls: "RestPollingConnector",
    text: "Asks each active station for the last completed hour over HTTP, then sweeps back over the previous hours to pick up anything a slow response missed.",
  },
  CSV_FEED: {
    cls: "CsvFeedConnector",
    text: "Downloads one wide CSV per run: a timestamp column and one value column per station. New columns become new stations.",
  },
  FTP_DELIMITED: {
    cls: "DelimitedFtpConnector",
    text: "Reads per-station files from an FTP server: a header, a few metadata rows, then comma-separated readings.",
  },
  FTP_BINARY: {
    cls: "BinaryFeedConnector",
    text: "Fetches hourly GCF files, a compressed binary seismic and sensor format, and decodes the difference-encoded blocks.",
  },
};

const OPTIONS = [15_000, 30_000, 60_000, 120_000, 300_000];
const label = (ms) =>
  ms < 60_000 ? `${ms / 1000} seconds` : ms === 60_000 ? "1 minute" : `${ms / 60_000} minutes`;

export default function Sources() {
  const [intervals, setIntervals] = useState(null);
  const [matches, setMatches] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.intervals().then(setIntervals);
    api.matches().then(setMatches);
  }, []);

  const change = async (source, ms) => {
    try {
      await api.setInterval(source, ms);
      setIntervals((prev) => ({ ...prev, [source]: ms }));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  };

  const used = (source) => ({
    primary: matches.filter((m) => m.primary.sourceType === source).map((m) => m.name),
    backup: matches.filter((m) => m.secondary?.sourceType === source).map((m) => m.name),
  });

  return (
    <Box>
      <Box maxWidth={760} mb={3}>
        <Typography variant="h1" mb={1}>
          Sources
        </Typography>
        <Typography color="text.secondary">
          Four ways the readings arrive, one connector each. All of them write into the same station
          and reading tables, so gap filling, downsampling and failover are written once. Polling
          intervals live in the database and change without a restart.
        </Typography>
      </Box>

      {!api.live && (
        <Alert severity="info" variant="outlined" sx={{ mb: 2, maxWidth: 760 }}>
          The demo feeds send one reading a minute. Set an interval shorter than that and the gap
          scanner starts counting slots that were never going to be filled; open a gauge afterwards
          to see it.
        </Alert>
      )}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box display="grid" gridTemplateColumns={{ xs: "1fr", md: "1fr 1fr" }} gap={2}>
        {Object.entries(CONNECTORS).map(([source, c]) => {
          const u = used(source);
          return (
            <Panel
              key={source}
              title={SOURCE_LABEL[source]}
              endpoint={`PUT /api/scheduler/intervals/${source}`}
            >
              <Typography variant="body2" color="text.secondary" mb={2}>
                {c.text}
              </Typography>
              <Box
                display="flex"
                flexWrap="wrap"
                alignItems="center"
                justifyContent="space-between"
                gap={2}
              >
                <FormControl size="small" sx={{ minWidth: 180 }}>
                  <InputLabel id={`${source}-iv`}>Poll every</InputLabel>
                  <Select
                    labelId={`${source}-iv`}
                    label="Poll every"
                    value={intervals ? intervals[source] : ""}
                    onChange={(e) => change(source, e.target.value)}
                  >
                    {OPTIONS.map((ms) => (
                      <MenuItem key={ms} value={ms}>
                        {label(ms)}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  component="code"
                  sx={{ fontFamily: "ui-monospace, Menlo, monospace" }}
                >
                  {c.cls}
                </Typography>
              </Box>
              <Typography variant="body2" mt={2}>
                <b>Primary for</b> {u.primary.length ? u.primary.join(", ") : "none"}
                <br />
                <b>Backup for</b> {u.backup.length ? u.backup.join(", ") : "none"}
              </Typography>
            </Panel>
          );
        })}
      </Box>
    </Box>
  );
}
