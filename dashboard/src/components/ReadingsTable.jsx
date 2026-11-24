import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TablePagination from "@mui/material/TablePagination";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import { api } from "../api";
import { dayTime, level } from "../format";
import Panel from "./Panel";

export default function ReadingsTable({ match }) {
  const [feed, setFeed] = useState("primary");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState(null);
  const station = match[feed] || match.primary;

  useEffect(() => {
    let live = true;
    api.history(station.sourceType, station.externalId, page, size).then((r) => live && setData(r));
    return () => {
      live = false;
    };
  }, [station, page, size]);

  return (
    <Panel
      title="Raw readings"
      endpoint={`GET /api/v2/sources/${station.sourceType}/stations/${station.externalId}/readings/period?period=DAILY&page=${page}&size=${size}`}
      action={
        match.secondary && (
          <ToggleButtonGroup
            size="small"
            exclusive
            value={feed}
            onChange={(_, v) => {
              if (v) {
                setFeed(v);
                setPage(0);
              }
            }}
          >
            <ToggleButton value="primary">Primary</ToggleButton>
            <ToggleButton value="secondary">Backup</ToggleButton>
          </ToggleButtonGroup>
        )
      }
    >
      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" sx={{ "& td, & th": { borderColor: "divider" } }}>
          <TableHead>
            <TableRow>
              <TableCell>Time (UTC)</TableCell>
              <TableCell>Station</TableCell>
              <TableCell align="right">Level (m)</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.content.map((r) => (
              <TableRow key={r.timestamp} hover>
                <TableCell>{dayTime(r.timestamp)}</TableCell>
                <TableCell sx={{ color: "text.secondary" }}>{r.stationExternalId}</TableCell>
                <TableCell align="right">{level(r.primaryValue)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      {data && (
        <TablePagination
          component="div"
          count={data.totalElements}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 25, 100]}
          onPageChange={(_, p) => setPage(p)}
          sx={{
            "& .MuiTablePagination-selectLabel, & .MuiTablePagination-input": {
              display: { xs: "none", sm: "inline-flex" },
            },
          }}
          onRowsPerPageChange={(e) => {
            setSize(+e.target.value);
            setPage(0);
          }}
        />
      )}
      <Typography variant="caption" color="text.secondary">
        Newest first. The v2 endpoints cap a page at 1000 rows and a range at 366 days.
      </Typography>
    </Panel>
  );
}
