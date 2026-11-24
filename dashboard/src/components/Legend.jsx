import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTheme } from "@mui/material/styles";

export default function Legend({ items }) {
  const t = useTheme().tokens;
  return (
    <Box
      display="flex"
      flexWrap="wrap"
      gap={1.75}
      component="ul"
      sx={{ listStyle: "none", m: 0, p: 0 }}
    >
      {items.map(({ label, color, kind = "line" }) => (
        <Box component="li" key={label} display="flex" alignItems="center" gap={0.75}>
          <Box
            sx={
              kind === "line"
                ? { width: 16, height: 2, borderRadius: 1, bgcolor: color }
                : { width: 12, height: 4, borderRadius: 1, bgcolor: color }
            }
          />
          <Typography variant="caption" sx={{ color: t.ink2 }}>
            {label}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}
