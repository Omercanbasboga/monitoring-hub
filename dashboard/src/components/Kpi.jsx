import Paper from "@mui/material/Paper";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

export default function Kpi({ label, value, unit, note, marker }) {
  return (
    <Paper sx={{ p: 2, borderRadius: "14px", display: "flex", flexDirection: "column", gap: 0.5 }}>
      <Box display="flex" alignItems="center" gap={0.75}>
        {marker}
        <Typography variant="body2" color="text.secondary" fontWeight={600}>
          {label}
        </Typography>
      </Box>
      <Typography
        sx={{ fontSize: "2.1rem", fontWeight: 600, fontStretch: "125%", lineHeight: 1.05 }}
      >
        {value}
        {unit && (
          <Typography
            component="span"
            sx={{ fontSize: "0.9rem", ml: 0.5, color: "text.secondary", fontStretch: "100%" }}
          >
            {unit}
          </Typography>
        )}
      </Typography>
      {note && (
        <Typography variant="caption" color="text.secondary">
          {note}
        </Typography>
      )}
    </Paper>
  );
}
