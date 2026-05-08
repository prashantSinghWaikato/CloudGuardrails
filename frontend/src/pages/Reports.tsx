import { useEffect, useState } from "react";
import jsPDF from "jspdf";
import {
  fetchExecutiveReportRuns,
  fetchExecutiveReportSchedule,
  generateExecutiveReport,
  updateExecutiveReportSchedule,
} from "../api/reportApi";
import type {
  ExecutiveReportRun,
  ExecutiveReportSchedule,
  ExecutiveReportScheduleRequest,
} from "../types";

const dayOptions = [
  { value: 1, label: "Monday" },
  { value: 2, label: "Tuesday" },
  { value: 3, label: "Wednesday" },
  { value: 4, label: "Thursday" },
  { value: 5, label: "Friday" },
  { value: 6, label: "Saturday" },
  { value: 7, label: "Sunday" },
];

const defaultDateTimeValue = (date: Date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
};

const inputValueToIso = (value: string) => {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
};

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return "Not available";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const normalizeSummaryText = (value: string | null | undefined) => {
  if (!value) {
    return "No summary generated.";
  }

  return value
    .replaceAll(/\*\*/g, "")
    .replaceAll(/^>\s?/gm, "")
    .replaceAll(/^#{1,6}\s*/gm, "")
    .replaceAll(/^["']+|["']+$/gm, "")
    .replaceAll(/^Executive Overview\s*$/gim, "")
    .replaceAll(/^Key Metrics\s*$/gim, "")
    .replaceAll(/^Highest-Risk Accounts\s*$/gim, "")
    .replaceAll(/^Top Recurring Issues\s*$/gim, "")
    .replaceAll(/^Recommended Actions\s*$/gim, "")
    .replaceAll(/\r/g, "")
    .replaceAll(/\n{3,}/g, "\n\n")
    .trim();
};

const parseSummarySections = (value: string | null | undefined) => {
  const cleaned = normalizeSummaryText(value);
  const lines = cleaned.split("\n").map((line) => line.trim()).filter(Boolean);
  const sections: Record<string, string[]> = {
    overview: [],
    risks: [],
    actions: [],
    other: [],
  };

  let current: keyof typeof sections = "overview";

  for (const line of lines) {
    const normalized = line.toLowerCase().replace(/:$/, "");

    if (normalized === "executive overview") {
      current = "overview";
      continue;
    }
    if (normalized === "key risks") {
      current = "risks";
      continue;
    }
    if (normalized === "recommended actions") {
      current = "actions";
      continue;
    }
    if (normalized === "system note") {
      current = "other";
      continue;
    }

    sections[current].push(line.replace(/^- /, "").trim());
  }

  return {
    overview: sections.overview.join(" "),
    risks: sections.risks,
    actions: sections.actions,
    other: sections.other,
  };
};

const ReportsPage = () => {
  const [runs, setRuns] = useState<ExecutiveReportRun[]>([]);
  const [schedule, setSchedule] = useState<ExecutiveReportSchedule | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [savingSchedule, setSavingSchedule] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [from, setFrom] = useState(defaultDateTimeValue(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)));
  const [to, setTo] = useState(defaultDateTimeValue(new Date()));
  const [scheduleForm, setScheduleForm] = useState<ExecutiveReportScheduleRequest>({
    enabled: false,
    dayOfWeek: 1,
    scheduledTime: "09:00",
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    recipients: [],
  });
  const [recipientsText, setRecipientsText] = useState("");

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const [scheduleResponse, runResponse] = await Promise.all([
          fetchExecutiveReportSchedule(),
          fetchExecutiveReportRuns(),
        ]);

        setSchedule(scheduleResponse);
        setRuns(runResponse);
        setScheduleForm({
          enabled: scheduleResponse.enabled,
          dayOfWeek: scheduleResponse.dayOfWeek,
          scheduledTime: scheduleResponse.scheduledTime || "09:00",
          timeZone: scheduleResponse.timeZone || "UTC",
          recipients: scheduleResponse.recipients,
        });
        setRecipientsText(scheduleResponse.recipients.join(", "));
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : "Failed to load reports");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  const handleGenerate = async () => {
    try {
      setGenerating(true);
      setError(null);
      setSuccess(null);
      const run = await generateExecutiveReport({
        from: inputValueToIso(from),
        to: inputValueToIso(to),
      });

      setRuns((current) => [run, ...current.filter((item) => item.id !== run.id)].slice(0, 10));
      setSuccess("Executive summary generated.");
    } catch (generationError) {
      setError(generationError instanceof Error ? generationError.message : "Failed to generate report");
    } finally {
      setGenerating(false);
    }
  };

  const handleScheduleSave = async () => {
    try {
      setSavingSchedule(true);
      setScheduleError(null);
      setSuccess(null);

      const recipients = recipientsText
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean);

      const updated = await updateExecutiveReportSchedule({
        ...scheduleForm,
        recipients,
      });

      setSchedule(updated);
      setScheduleForm({
        enabled: updated.enabled,
        dayOfWeek: updated.dayOfWeek,
        scheduledTime: updated.scheduledTime || "09:00",
        timeZone: updated.timeZone || "UTC",
        recipients: updated.recipients,
      });
      setRecipientsText(updated.recipients.join(", "));
      setSuccess("Weekly report schedule updated.");
    } catch (saveError) {
      setScheduleError(saveError instanceof Error ? saveError.message : "Failed to update schedule");
    } finally {
      setSavingSchedule(false);
    }
  };

  const handlePdfExport = (run: ExecutiveReportRun) => {
    setError(null);
    try {
      const pdf = new jsPDF({
        orientation: "portrait",
        unit: "pt",
        format: "a4",
      });

      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const margin = 48;
      const contentWidth = pageWidth - margin * 2;
      const colors = {
        ink: [15, 23, 42] as const,
        body: [51, 65, 85] as const,
        mute: [100, 116, 139] as const,
        line: [203, 213, 225] as const,
        panel: [248, 250, 252] as const,
        accent: [14, 116, 144] as const,
        accentSoft: [236, 254, 255] as const,
      };
      let y = margin;
      let pageNumber = 1;
      const summarySections = parseSummarySections(run.summaryText);
      const summaryText = summarySections.overview || normalizeSummaryText(run.summaryText);

      const ensureSpace = (needed = 24) => {
        if (y + needed > pageHeight - margin) {
          pdf.addPage();
          pageNumber += 1;
          y = margin;
          drawFooter();
        }
      };

      const addWrappedText = (text: string, x: number, fontSize: number, lineHeight: number, width = contentWidth - (x - margin)) => {
        pdf.setFontSize(fontSize);
        const lines = pdf.splitTextToSize(text, width);
        lines.forEach((line: string) => {
          ensureSpace(lineHeight);
          pdf.text(line, x, y);
          y += lineHeight;
        });
      };

      const estimateWrappedHeight = (text: string, fontSize: number, lineHeight: number, width: number) => {
        pdf.setFontSize(fontSize);
        const lines = pdf.splitTextToSize(text, width);
        return {
          lines,
          height: Math.max(lineHeight, lines.length * lineHeight),
        };
      };

      const drawFooter = () => {
        pdf.setDrawColor(...colors.line);
        pdf.line(margin, pageHeight - 28, pageWidth - margin, pageHeight - 28);
        pdf.setFont("helvetica", "normal");
        pdf.setFontSize(9);
        pdf.setTextColor(...colors.mute);
        pdf.text("Cloud Guardrails", margin, pageHeight - 12);
        pdf.text(`Page ${pageNumber}`, pageWidth - margin, pageHeight - 12, { align: "right" });
      };

      const drawSectionTitle = (title: string) => {
        ensureSpace(28);
        pdf.setFont("helvetica", "bold");
        pdf.setFontSize(15);
        pdf.setTextColor(...colors.ink);
        pdf.text(title, margin, y);
        y += 10;
        pdf.setDrawColor(...colors.line);
        pdf.line(margin, y, pageWidth - margin, y);
        y += 18;
      };

      const drawMetaChip = (x: number, width: number, label: string, value: string) => {
        pdf.setFillColor(...colors.panel);
        pdf.setDrawColor(...colors.line);
        pdf.roundedRect(x, y, width, 44, 10, 10, "FD");
        pdf.setFont("helvetica", "bold");
        pdf.setFontSize(9);
        pdf.setTextColor(...colors.mute);
        pdf.text(label.toUpperCase(), x + 12, y + 14);
        pdf.setFont("helvetica", "normal");
        pdf.setFontSize(10);
        pdf.setTextColor(...colors.ink);
        const lines = pdf.splitTextToSize(value, width - 24);
        lines.slice(0, 2).forEach((line: string, index: number) => {
          pdf.text(line, x + 12, y + 29 + index * 12);
        });
      };

      pdf.setFillColor(...colors.ink);
      pdf.rect(0, 0, pageWidth, 116, "F");
      pdf.setFillColor(...colors.accent);
      pdf.rect(0, 0, 12, 116, "F");
      pdf.setTextColor(255, 255, 255);
      pdf.setFont("helvetica", "bold");
      pdf.setFontSize(24);
      pdf.text("Cloud Guardrails Executive Summary", margin, 56);
      pdf.setFont("helvetica", "normal");
      pdf.setFontSize(11);
      pdf.setTextColor(203, 213, 225);
      pdf.text("Weekly security posture and remediation review", margin, 76);

      y = 138;
      const chipGap = 10;
      const chipWidth = (contentWidth - chipGap * 2) / 3;
      drawMetaChip(margin, chipWidth, "Generated", formatDateTime(run.createdAt));
      drawMetaChip(margin + chipWidth + chipGap, chipWidth, "Period", `${formatDateTime(run.periodStart)} to ${formatDateTime(run.periodEnd)}`);
      drawMetaChip(margin + (chipWidth + chipGap) * 2, chipWidth, "Delivery", `${run.triggerType} • ${run.emailStatus || "Not sent"}`);
      y += 64;

      const metricWidth = (contentWidth - 18) / 4;
      const metrics = [
        { label: "Findings", value: run.metrics.totalFindings },
        { label: "Open", value: run.metrics.openFindings },
        { label: "Critical", value: run.metrics.criticalFindings },
        { label: "Closed", value: run.metrics.closedFindings },
      ];

      metrics.forEach((metric, index) => {
        const x = margin + index * (metricWidth + 6);
        pdf.setDrawColor(...colors.line);
        pdf.setFillColor(...colors.panel);
        pdf.roundedRect(x, y, metricWidth, 70, 10, 10, "FD");
        pdf.setTextColor(...colors.mute);
        pdf.setFont("helvetica", "bold");
        pdf.setFontSize(10);
        pdf.text(metric.label.toUpperCase(), x + 12, y + 20);
        pdf.setTextColor(...colors.accent);
        pdf.setFontSize(24);
        pdf.text(String(metric.value), x + 12, y + 50);
      });
      y += 94;

      drawSectionTitle("Executive Overview");
      const overviewBoxWidth = contentWidth - 32;
      const overviewLayout = estimateWrappedHeight(summaryText, 11, 16, overviewBoxWidth);
      const overviewBoxHeight = Math.max(40, overviewLayout.height + 18);
      ensureSpace(overviewBoxHeight + 10);
      const overviewTop = y - 4;
      pdf.setFillColor(...colors.accentSoft);
      pdf.setDrawColor(190, 242, 100);
      pdf.roundedRect(margin, overviewTop, contentWidth, overviewBoxHeight, 12, 12, "FD");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      addWrappedText(summaryText, margin + 16, 11, 16, overviewBoxWidth);
      y = Math.max(y + 8, overviewTop + overviewBoxHeight + 12);

      drawSectionTitle("Key Risks");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      if (summarySections.risks.length === 0) {
        addWrappedText("No explicit AI risk summary was generated for this period.", margin, 11, 16);
      } else {
        summarySections.risks.forEach((risk) => {
          ensureSpace(28);
          pdf.setDrawColor(...colors.line);
          pdf.setFillColor(255, 255, 255);
          pdf.roundedRect(margin, y - 10, contentWidth, 24, 6, 6, "FD");
          pdf.setFillColor(...colors.accent);
          pdf.circle(margin + 12, y + 2, 3, "F");
          addWrappedText(risk, margin + 24, 11, 16, contentWidth - 36);
          y += 8;
        });
      }

      drawSectionTitle("Recommended Actions");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      if (summarySections.actions.length === 0) {
        addWrappedText("No explicit AI actions were generated for this period.", margin, 11, 16);
      } else {
        summarySections.actions.forEach((action, index) => {
          ensureSpace(34);
          pdf.setFillColor(...colors.panel);
          pdf.setDrawColor(...colors.line);
          pdf.roundedRect(margin, y - 12, contentWidth, 28, 8, 8, "FD");
          pdf.setFont("helvetica", "bold");
          pdf.setTextColor(...colors.accent);
          pdf.text(`${index + 1}.`, margin + 12, y + 4);
          pdf.setFont("helvetica", "normal");
          pdf.setTextColor(...colors.body);
          addWrappedText(action, margin + 28, 11, 16, contentWidth - 40);
          y += 6;
        });
      }

      drawSectionTitle("Highest-Risk Accounts");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      if (run.metrics.topAccounts.length === 0) {
        addWrappedText("No account concentration for this period.", margin, 11, 16);
      } else {
        run.metrics.topAccounts.forEach((account) => {
          ensureSpace(62);
          pdf.setDrawColor(...colors.line);
          pdf.setFillColor(255, 255, 255);
          pdf.roundedRect(margin, y - 12, contentWidth, 52, 8, 8, "FD");
          pdf.setFillColor(...colors.accent);
          pdf.roundedRect(margin, y - 12, 6, 52, 8, 8, "F");
          pdf.setFont("helvetica", "bold");
          pdf.setTextColor(...colors.ink);
          pdf.text(`${account.accountName} (${account.accountId})`, margin + 12, y + 2);
          pdf.setFont("helvetica", "normal");
          pdf.setTextColor(...colors.body);
          pdf.text(
            `${account.openFindings} open findings`,
            margin + 12,
            y + 20
          );
          pdf.text(
            `${account.criticalFindings} critical issues | ${account.findingsCreated} created this period`,
            margin + 12,
            y + 36
          );
          y += 64;
        });
      }

      drawSectionTitle("Top Rules");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      if (run.metrics.topRules.length === 0) {
        addWrappedText("No repeated rules for this period.", margin, 11, 16);
      } else {
        run.metrics.topRules.forEach((rule) => {
          ensureSpace(28);
          pdf.setDrawColor(...colors.line);
          pdf.roundedRect(margin, y - 10, contentWidth, 24, 6, 6);
          pdf.setFont("helvetica", "normal");
          pdf.setTextColor(...colors.ink);
          const ruleLines = pdf.splitTextToSize(rule.ruleName, contentWidth - 90);
          pdf.text(ruleLines[0], margin + 12, y + 4);
          pdf.setFont("helvetica", "bold");
          pdf.setTextColor(...colors.accent);
          pdf.text(String(rule.count), pageWidth - margin - 12, y + 4, { align: "right" });
          y += 32;
        });
      }
      y += 6;

      drawSectionTitle("Coverage Notes");
      pdf.setFont("helvetica", "normal");
      pdf.setTextColor(...colors.body);
      run.metrics.coverageNotes.forEach((note) => {
        addWrappedText(`• ${note}`, margin, 11, 16);
      });

      drawFooter();
      const safeDate = (run.createdAt || new Date().toISOString()).slice(0, 10);
      pdf.save(`cloud-guardrails-executive-summary-${safeDate}.pdf`);
    } catch {
      setError("Failed to generate the PDF.");
    }
  };

  return (
    <main className="mx-auto flex w-full max-w-[1380px] flex-col gap-6 px-6 py-8">
      <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-[28px] border border-white/10 bg-[linear-gradient(135deg,rgba(8,20,37,0.96),rgba(17,36,58,0.92))] p-7 shadow-[0_32px_80px_-36px_rgba(2,8,23,0.95)]">
          <p className="text-xs uppercase tracking-[0.26em] text-cyan-200/80">Executive Summary</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white">
            Generate leadership-ready weekly security reports.
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
            Generate the report on demand, then export it directly as PDF for stakeholders. Weekly delivery remains
            available through the schedule card.
          </p>

          <div className="mt-8 grid gap-4 md:grid-cols-2">
            <label className="space-y-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Period Start</span>
              <input
                type="datetime-local"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              />
            </label>

            <label className="space-y-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Period End</span>
              <input
                type="datetime-local"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              />
            </label>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleGenerate}
              disabled={generating}
              className="rounded-2xl bg-cyan-400 px-5 py-3 text-sm font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:bg-cyan-500/40"
            >
              {generating ? "Generating..." : "Generate Report"}
            </button>
            {runs[0] ? (
              <button
                type="button"
                onClick={() => handlePdfExport(runs[0])}
                className="rounded-2xl border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-white transition hover:border-white/25 hover:bg-white/10"
              >
                Export Latest PDF
              </button>
            ) : null}
            {success ? <p className="text-sm text-emerald-300">{success}</p> : null}
            {error ? <p className="text-sm text-rose-300">{error}</p> : null}
          </div>
        </div>

        <div className="rounded-[28px] border border-white/10 bg-white/5 p-6 shadow-[0_20px_60px_-36px_rgba(2,8,23,0.9)]">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Scheduled Delivery</p>
              <h2 className="mt-2 text-xl font-semibold text-white">Weekly Email</h2>
            </div>
            <label className="flex items-center gap-3 text-sm text-slate-300">
              <span>Enabled</span>
              <input
                type="checkbox"
                checked={scheduleForm.enabled}
                onChange={(event) =>
                  setScheduleForm((current) => ({
                    ...current,
                    enabled: event.target.checked,
                  }))
                }
                className="h-4 w-4 rounded border-white/20 bg-slate-900 text-cyan-400 focus:ring-cyan-400"
              />
            </label>
          </div>

          <div className="mt-6 grid gap-4 md:grid-cols-2">
            <label className="space-y-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Day</span>
              <select
                value={scheduleForm.dayOfWeek}
                onChange={(event) =>
                  setScheduleForm((current) => ({
                    ...current,
                    dayOfWeek: Number(event.target.value),
                  }))
                }
                className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              >
                {dayOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Time</span>
              <input
                type="time"
                value={scheduleForm.scheduledTime}
                onChange={(event) =>
                  setScheduleForm((current) => ({
                    ...current,
                    scheduledTime: event.target.value,
                  }))
                }
                className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              />
            </label>

            <label className="space-y-2 md:col-span-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Timezone</span>
              <input
                type="text"
                value={scheduleForm.timeZone}
                onChange={(event) =>
                  setScheduleForm((current) => ({
                    ...current,
                    timeZone: event.target.value,
                  }))
                }
                className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              />
            </label>

            <label className="space-y-2 md:col-span-2">
              <span className="text-xs uppercase tracking-[0.24em] text-slate-500">Recipients</span>
              <textarea
                value={recipientsText}
                onChange={(event) => setRecipientsText(event.target.value)}
                rows={4}
                placeholder="security@example.com, leadership@example.com"
                className="w-full rounded-2xl border border-white/10 bg-slate-950/40 px-4 py-3 text-sm text-white outline-none transition focus:border-cyan-400/40"
              />
            </label>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleScheduleSave}
              disabled={savingSchedule}
              className="rounded-2xl border border-cyan-400/30 bg-cyan-400/10 px-5 py-3 text-sm font-semibold text-cyan-100 transition hover:border-cyan-300/50 hover:bg-cyan-400/20 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {savingSchedule ? "Saving..." : "Save Schedule"}
            </button>
            {schedule?.nextRunAt ? (
              <p className="text-sm text-slate-400">Next run: {formatDateTime(schedule.nextRunAt)}</p>
            ) : null}
            {scheduleError ? <p className="text-sm text-rose-300">{scheduleError}</p> : null}
          </div>
        </div>
      </section>

      <section className="rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(8,16,28,0.96),rgba(9,18,32,0.92))] p-6 shadow-[0_26px_70px_-46px_rgba(2,8,23,0.95)]">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.24em] text-slate-500">History</p>
            <h2 className="mt-2 text-2xl font-semibold text-white">Recent Reports</h2>
          </div>
          {loading ? <span className="text-sm text-slate-500">Loading...</span> : null}
        </div>

        <div className="mt-6 overflow-hidden rounded-3xl border border-white/10">
          {runs.length === 0 ? (
            <div className="px-5 py-8 text-sm text-slate-400">No executive reports generated yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-white/10 text-sm">
                <thead className="bg-white/5">
                  <tr className="text-left text-xs uppercase tracking-[0.22em] text-slate-500">
                    <th className="px-5 py-4 font-medium">Generated</th>
                    <th className="px-5 py-4 font-medium">Period</th>
                    <th className="px-5 py-4 font-medium">Source</th>
                    <th className="px-5 py-4 font-medium">Status</th>
                    <th className="px-5 py-4 font-medium">Email</th>
                    <th className="px-5 py-4 font-medium text-right">PDF</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/10">
                  {runs.map((run) => (
                    <tr key={run.id} className="bg-slate-950/15 text-slate-200">
                      <td className="px-5 py-4">{formatDateTime(run.createdAt)}</td>
                      <td className="px-5 py-4 text-slate-400">
                        {formatDateTime(run.periodStart)} to {formatDateTime(run.periodEnd)}
                      </td>
                      <td className="px-5 py-4">{run.triggerType}</td>
                      <td className="px-5 py-4">
                        <span className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] font-medium text-slate-300">
                          {run.status}
                        </span>
                      </td>
                      <td className="px-5 py-4">{run.emailStatus || "Not sent"}</td>
                      <td className="px-5 py-4 text-right">
                        <button
                          type="button"
                          onClick={() => handlePdfExport(run)}
                          className="rounded-2xl border border-cyan-400/20 bg-cyan-400/10 px-4 py-2 text-xs font-semibold text-cyan-100 transition hover:border-cyan-300/40 hover:bg-cyan-400/20"
                        >
                          Export PDF
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </main>
  );
};

export default ReportsPage;
