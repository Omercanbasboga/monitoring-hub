import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";
import SideChip from "./SideChip";
import { dayTime, duration, hhmm } from "../format";

export default function UsageList({ rows, now }) {
  const t = useTheme().tokens;
  if (!rows.length)
    return (
      <Typography variant="body2" color="text.secondary">
        No switches recorded yet.
      </Typography>
    );
  return (
    <Box component="ol" sx={{ listStyle: "none", m: 0, p: 0 }}>
      {rows.map((r, i) => (
        <Box
          component="li"
          key={r.startedAt}
          sx={{
            display: "grid",
            gridTemplateColumns: "92px 1fr auto",
            alignItems: "center",
            gap: 1.5,
            py: 1,
            borderTop: i ? `1px solid ${t.hairline}` : "none",
          }}
        >
          <SideChip side={r.side} size="sm" />
          <Typography variant="body2">
            {dayTime(r.startedAt)}
            <Box component="span" sx={{ color: "text.secondary" }}>
              {r.endedAt == null
                ? " until now"
                : ` to ${new Date(r.endedAt).getUTCDate() === new Date(r.startedAt).getUTCDate() ? hhmm(r.endedAt) : dayTime(r.endedAt)}`}
            </Box>
          </Typography>
          <Typography variant="body2" color="text.secondary" textAlign="right">
            {duration((r.endedAt ?? now) - r.startedAt)}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}
