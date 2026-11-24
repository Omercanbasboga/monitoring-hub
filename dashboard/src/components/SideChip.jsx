import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import { SIDE_LABEL } from "../format";

// Shape as well as colour: a filled dot for primary, a half dot for backup,
// a cross for no data. The label is always there too.
export function Glyph({ side, color }) {
  if (side === "NONE") {
    return (
      <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden>
        <path d="M2 2l6 6M8 2l-6 6" stroke={color} strokeWidth="2" strokeLinecap="round" />
      </svg>
    );
  }
  return (
    <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden>
      <circle
        cx="5"
        cy="5"
        r="4"
        fill={side === "PRIMARY" ? color : "none"}
        stroke={color}
        strokeWidth="2"
      />
      {side === "SECONDARY" && <path d="M5 1a4 4 0 0 1 0 8z" fill={color} />}
    </svg>
  );
}

export function sideColor(tokens, side) {
  return side === "PRIMARY" ? tokens.primary : side === "SECONDARY" ? tokens.backup : tokens.none;
}

export default function SideChip({ side, size = "md" }) {
  const t = useTheme().tokens;
  if (!side) return null;
  const color = sideColor(t, side);
  return (
    <Box
      component="span"
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: 0.75,
        px: size === "sm" ? 0.75 : 1,
        py: size === "sm" ? 0.1 : 0.25,
        borderRadius: 99,
        border: `1px solid ${color}`,
        color: "text.primary",
        fontSize: size === "sm" ? "0.72rem" : "0.78rem",
        fontWeight: 600,
        whiteSpace: "nowrap",
        lineHeight: 1.6,
      }}
    >
      <Glyph side={side} color={color} />
      {SIDE_LABEL[side]}
    </Box>
  );
}
