import { useMemo, useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import Link from "@mui/material/Link";
import { makeTheme } from "./theme";
import { api } from "./api";
import useInterval from "./useInterval";
import { hhmm } from "./format";
import Overview from "./pages/Overview";
import Gauge from "./pages/Gauge";
import Sources from "./pages/Sources";

const REPO = "https://github.com/Omercanbasboga/monitoring-hub";

function readMode() {
  try {
    const saved = localStorage.getItem("mh:theme");
    if (saved === "light" || saved === "dark") return saved;
  } catch {
    /* storage blocked */
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function Mark({ color }) {
  // a tide curve over a datum line
  return (
    <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden>
      <path
        d="M2 14c3-6 6-6 9 0s6 6 9 0 4-4 4-4"
        fill="none"
        stroke={color}
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <path d="M2 21h22" stroke={color} strokeWidth="1.6" strokeLinecap="round" opacity="0.45" />
    </svg>
  );
}

function SunMoon({ dark }) {
  return dark ? (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden>
      <circle cx="12" cy="12" r="4.5" fill="currentColor" />
      {[0, 45, 90, 135, 180, 225, 270, 315].map((a) => (
        <path
          key={a}
          d="M12 2.5v2.5"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          transform={`rotate(${a} 12 12)`}
        />
      ))}
    </svg>
  ) : (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden>
      <path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z" fill="currentColor" />
    </svg>
  );
}

const navSx = (t) => ({
  px: 1.5,
  py: 0.75,
  borderRadius: "8px",
  color: t.ink2,
  textDecoration: "none",
  fontWeight: 600,
  fontSize: "0.9rem",
  "&:hover": { color: t.ink, bgcolor: t.water + "66" },
  "&.active": { color: t.ink, bgcolor: t.water },
});

export default function App() {
  const [mode, setMode] = useState(readMode);
  const theme = useMemo(() => makeTheme(mode), [mode]);
  const t = theme.tokens;
  const [clock, setClock] = useState(Date.now());
  useInterval(() => setClock(Date.now()), 15_000);

  const toggle = () => {
    const next = mode === "dark" ? "light" : "dark";
    setMode(next);
    try {
      localStorage.setItem("mh:theme", next);
    } catch {
      /* storage blocked */
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box
        component="header"
        sx={{
          position: "sticky",
          top: 0,
          zIndex: 1100,
          bgcolor: t.page + "E6",
          backdropFilter: "blur(8px)",
          borderBottom: `1px solid ${t.hairline}`,
        }}
      >
        <Box
          sx={{
            maxWidth: 1360,
            mx: "auto",
            px: { xs: 2, md: 3 },
            height: 64,
            display: "flex",
            alignItems: "center",
            gap: { xs: 1, md: 3 },
          }}
        >
          <Box
            component={NavLink}
            to="/"
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              color: t.ink,
              textDecoration: "none",
            }}
          >
            <Mark color={t.brand} />
            <Typography
              sx={{
                fontWeight: 700,
                fontStretch: "125%",
                fontSize: "1.05rem",
                display: { xs: "none", sm: "block" },
              }}
            >
              monitoring-hub
            </Typography>
          </Box>
          <Box component="nav" display="flex" gap={0.5}>
            <Box component={NavLink} to="/" end sx={navSx(t)}>
              Gauges
            </Box>
            <Box component={NavLink} to="/sources" sx={navSx(t)}>
              Sources
            </Box>
          </Box>
          <Box flex={1} />
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ display: { xs: "none", md: "block" } }}
          >
            {hhmm(clock)} UTC
          </Typography>
          <Tooltip title={mode === "dark" ? "Light theme" : "Dark theme"}>
            <IconButton onClick={toggle} aria-label="Switch theme" sx={{ color: t.ink2 }}>
              <SunMoon dark={mode === "dark"} />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {!api.live && (
        <Box sx={{ bgcolor: t.water, color: t.ink }}>
          <Typography
            variant="body2"
            sx={{ maxWidth: 1360, mx: "auto", px: { xs: 2, md: 3 }, py: 0.75 }}
          >
            Demo: the feeds are simulated in your browser with the same failover and gap rules as
            the server. New readings arrive every minute.{" "}
            <Link href={REPO} color="inherit" fontWeight={600}>
              Source on GitHub
            </Link>
          </Typography>
        </Box>
      )}

      <Box
        component="main"
        sx={{ maxWidth: 1360, mx: "auto", px: { xs: 2, md: 3 }, py: { xs: 3, md: 4 } }}
      >
        <Routes>
          <Route path="/" element={<Overview />} />
          <Route path="/gauge/:id" element={<Gauge />} />
          <Route path="/sources" element={<Sources />} />
          <Route path="*" element={<Overview />} />
        </Routes>
      </Box>

      <Box component="footer" sx={{ maxWidth: 1360, mx: "auto", px: { xs: 2, md: 3 }, pb: 4 }}>
        <Typography variant="caption" color="text.secondary">
          Spring Boot service with a React dashboard. Gauge positions are real harbours; every
          reading is generated. Coastline from Natural Earth. Charts by Highcharts (non-commercial
          licence).
        </Typography>
      </Box>
    </ThemeProvider>
  );
}
