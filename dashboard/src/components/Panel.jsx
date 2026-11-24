import Paper from "@mui/material/Paper";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

// A card with a title and, when the data comes from one endpoint, that endpoint.
// Showing the path is the point of the demo: every panel maps to a real route.
export default function Panel({ title, endpoint, action, children, sx, bodySx }) {
  return (
    <Paper sx={{ p: { xs: 2, md: 2.5 }, borderRadius: "14px", ...sx }}>
      {(title || action) && (
        <Box
          display="flex"
          alignItems="flex-start"
          justifyContent="space-between"
          gap={2}
          mb={1.75}
        >
          <Box minWidth={0}>
            {title && <Typography variant="h3">{title}</Typography>}
            {endpoint && (
              <Typography
                component="code"
                sx={{
                  display: "block",
                  mt: 0.25,
                  fontSize: "0.72rem",
                  color: "text.secondary",
                  fontFamily: 'ui-monospace, "SFMono-Regular", Menlo, monospace',
                  overflowWrap: "anywhere",
                }}
              >
                {endpoint}
              </Typography>
            )}
          </Box>
          {action}
        </Box>
      )}
      <Box sx={bodySx}>{children}</Box>
    </Paper>
  );
}
