import { useMemo } from "react";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import { useTheme } from "@mui/material/styles";
import { FONT } from "../theme";
import { dayTime, level, SIDE_LABEL } from "../format";

const BREAK_MS = 90_000;

// The merged series becomes two Highcharts series so each side gets its own
// legend entry. At a side switch the new side starts from the last point of the old one, so the line
// doesn't visibly jump where the colour changes.
function split(points) {
  const primary = [];
  const backup = [];
  let prev = null;
  for (const p of points) {
    const gap = prev && p.time - prev.time > BREAK_MS;
    if (gap) {
      primary.push([prev.time + 1, null]);
      backup.push([prev.time + 1, null]);
    }
    const target = p.side === "PRIMARY" ? primary : backup;
    const other = p.side === "PRIMARY" ? backup : primary;
    if (prev && !gap && prev.side !== p.side) {
      other.push([prev.time + 1, null]); // end the side we're leaving
      target.push([prev.time, prev.value]); // start the new side from the boundary
    }
    target.push([p.time, p.value]);
    prev = p;
  }
  return { primary, backup };
}

export default function SeriesChart({ points, raw, showRaw, match }) {
  const theme = useTheme();
  const t = theme.tokens;

  const options = useMemo(() => {
    const { primary, backup } = split(points);
    const axis = {
      lineColor: t.axis,
      tickColor: t.axis,
      gridLineColor: t.grid,
      labels: { style: { color: t.muted, fontSize: "11px" } },
    };
    const series = [
      {
        name: `Primary, ${match.primary.sourceType}`,
        data: primary,
        color: t.primary,
        side: "PRIMARY",
      },
      {
        name: match.secondary ? `Backup, ${match.secondary.sourceType}` : "Backup (none)",
        data: backup,
        color: t.backup,
        side: "SECONDARY",
      },
    ];
    if (showRaw) {
      series.push(
        {
          name: "Primary feed, raw",
          data: raw.primary.map((p) => [p.time, p.value]),
          color: t.primary,
          lineWidth: 1,
          opacity: 0.35,
          enableMouseTracking: false,
          zIndex: -1,
          gapSize: 2,
          side: "RAW",
        },
        {
          name: "Backup feed, raw",
          data: raw.secondary.map((p) => [p.time, p.value]),
          color: t.backup,
          lineWidth: 1,
          opacity: 0.35,
          enableMouseTracking: false,
          zIndex: -1,
          gapSize: 2,
          side: "RAW",
        }
      );
    }
    return {
      chart: {
        type: "line",
        height: 340,
        backgroundColor: "transparent",
        spacing: [8, 4, 8, 0],
        style: { fontFamily: FONT },
        zooming: { type: "x" },
        resetZoomButton: { theme: { r: 6, style: { fontSize: "12px" } } },
      },
      title: { text: null },
      credits: { enabled: false },
      accessibility: { enabled: false },
      legend: {
        align: "left",
        verticalAlign: "top",
        itemStyle: { color: t.ink2, fontWeight: "500", fontSize: "12px" },
        itemHoverStyle: { color: t.ink },
        symbolWidth: 16,
      },
      xAxis: {
        ...axis,
        type: "datetime",
        crosshair: { color: t.axis },
        dateTimeLabelFormats: { hour: "%H:%M", day: "%e %b" },
      },
      yAxis: {
        ...axis,
        title: { text: "metres", style: { color: t.muted } },
        labels: { ...axis.labels, format: "{value:.2f}" },
      },
      time: { timezone: "UTC" },
      tooltip: {
        shared: false,
        backgroundColor: t.paper,
        borderColor: t.hairline,
        borderRadius: 8,
        shadow: false,
        style: { color: t.ink, fontSize: "12px" },
        formatter() {
          const side = this.series.userOptions.side;
          return `<span style="color:${t.ink2}">${dayTime(this.x)} UTC</span><br/><b>${level(this.y)} m</b> from ${SIDE_LABEL[side]?.toLowerCase()} feed`;
        },
      },
      plotOptions: {
        series: {
          lineWidth: 2,
          marker: {
            enabled: false,
            radius: 4,
            states: { hover: { lineColor: t.paper, lineWidth: 2 } },
          },
          states: { hover: { lineWidthPlus: 0 }, inactive: { opacity: 0.5 } },
          animation: false,
          connectNulls: false,
          turboThreshold: 0,
        },
      },
      series,
    };
  }, [points, raw, showRaw, match, t]);

  return <HighchartsReact highcharts={Highcharts} options={options} />;
}
