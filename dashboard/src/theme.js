import { createTheme } from "@mui/material/styles";

// Series colours were checked for colour-blind separation in both modes.
// Primary is always blue and backup always orange, in charts, chips and on the map.
export const TOKENS = {
  light: {
    page: "#EDF2F3",
    paper: "#FFFFFF",
    ink: "#0E2430",
    ink2: "#4A5D66",
    muted: "#748690",
    grid: "#E1E8EA",
    axis: "#C5D0D4",
    hairline: "rgba(14,36,48,0.10)",
    primary: "#2A78D6",
    backup: "#EB6834",
    none: "#D03B3B",
    good: "#006300",
    brand: "#0D4D66",
    water: "#D7E7EC",
    sea: "#EEF6F9",
    land: "#DCE3E2",
    coast: "#B4C3C6",
  },
  dark: {
    page: "#081318",
    paper: "#0F1D23",
    ink: "#E6EFF1",
    ink2: "#A8B9C0",
    muted: "#74878F",
    grid: "#1B2C33",
    axis: "#2B3E46",
    hairline: "rgba(255,255,255,0.09)",
    primary: "#3987E5",
    backup: "#D95926",
    none: "#E05555",
    good: "#0CA30C",
    brand: "#7FC4DB",
    water: "#12313C",
    sea: "#0B1A20",
    land: "#1D2D33",
    coast: "#33474F",
  },
};

export const FONT = '"Archivo Variable", system-ui, -apple-system, "Segoe UI", sans-serif';

export function makeTheme(mode) {
  const t = TOKENS[mode];
  return createTheme({
    palette: {
      mode,
      primary: { main: t.brand },
      background: { default: t.page, paper: t.paper },
      text: { primary: t.ink, secondary: t.ink2 },
      divider: t.hairline,
      error: { main: t.none },
    },
    tokens: t,
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: FONT,
      h1: {
        fontSize: "2rem",
        fontWeight: 650,
        fontStretch: "118%",
        letterSpacing: "-0.01em",
        lineHeight: 1.15,
      },
      h2: { fontSize: "1.25rem", fontWeight: 650, fontStretch: "112%", lineHeight: 1.25 },
      h3: { fontSize: "1rem", fontWeight: 650, fontStretch: "108%" },
      body1: { fontSize: "0.95rem", lineHeight: 1.55 },
      body2: { fontSize: "0.85rem", lineHeight: 1.5 },
      caption: { fontSize: "0.75rem" },
      button: { textTransform: "none", fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { fontFeatureSettings: '"tnum" 1' },
          "::selection": { background: t.water },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: { root: { backgroundImage: "none", border: `1px solid ${t.hairline}` } },
      },
      MuiButtonBase: { defaultProps: { disableRipple: false } },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            background: mode === "dark" ? "#E6EFF1" : "#0E2430",
            color: mode === "dark" ? "#0E2430" : "#fff",
            fontSize: "0.75rem",
          },
        },
      },
    },
  });
}
