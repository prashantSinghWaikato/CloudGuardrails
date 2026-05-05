import { useEffect, useState } from "react";
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

const escapeHtml = (value: string) =>
  value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

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
    const popup = window.open("", "_blank", "noopener,noreferrer,width=1024,height=900");

    if (!popup) {
      setError("Allow pop-ups to export the report as PDF.");
      return;
    }

    const summaryHtml = escapeHtml(run.summaryText || "No summary generated.").replaceAll("\n", "<br />");
    const coverageHtml = run.metrics.coverageNotes
      .map((note) => `<li>${escapeHtml(note)}</li>`)
      .join("");
    const accountsHtml = run.metrics.topAccounts
      .map(
        (account) => `
          <tr>
            <td>${escapeHtml(account.accountName)}</td>
            <td>${escapeHtml(account.accountId)}</td>
            <td>${account.openFindings}</td>
            <td>${account.criticalFindings}</td>
            <td>${account.findingsCreated}</td>
          </tr>`
      )
      .join("");
    const rulesHtml = run.metrics.topRules
      .map(
        (rule) => `
          <tr>
            <td>${escapeHtml(rule.ruleName)}</td>
            <td>${rule.count}</td>
          </tr>`
      )
      .join("");

    popup.document.write(`
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="utf-8" />
          <title>Cloud Guardrails Executive Summary</title>
          <style>
            body {
              font-family: Helvetica, Arial, sans-serif;
              color: #0f172a;
              margin: 0;
              padding: 40px;
              line-height: 1.55;
            }
            h1, h2, h3 {
              margin: 0 0 12px;
            }
            .meta {
              margin-top: 8px;
              color: #475569;
              font-size: 13px;
            }
            .section {
              margin-top: 28px;
            }
            .metrics {
              display: grid;
              grid-template-columns: repeat(4, 1fr);
              gap: 12px;
              margin-top: 20px;
            }
            .metric {
              border: 1px solid #cbd5e1;
              border-radius: 12px;
              padding: 14px;
            }
            .metric-label {
              font-size: 11px;
              text-transform: uppercase;
              letter-spacing: 0.08em;
              color: #64748b;
            }
            .metric-value {
              margin-top: 10px;
              font-size: 26px;
              font-weight: 700;
              color: #0f172a;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              margin-top: 12px;
            }
            th, td {
              border: 1px solid #cbd5e1;
              padding: 10px 12px;
              text-align: left;
              font-size: 13px;
            }
            th {
              background: #f8fafc;
            }
            ul {
              padding-left: 20px;
            }
            @media print {
              body {
                padding: 24px;
              }
            }
          </style>
        </head>
        <body>
          <h1>Cloud Guardrails Executive Summary</h1>
          <div class="meta">Generated: ${escapeHtml(formatDateTime(run.createdAt))}</div>
          <div class="meta">Period: ${escapeHtml(formatDateTime(run.periodStart))} to ${escapeHtml(formatDateTime(run.periodEnd))}</div>
          <div class="meta">Source: ${escapeHtml(run.triggerType)} | Email: ${escapeHtml(run.emailStatus || "Not sent")}</div>

          <div class="metrics">
            <div class="metric">
              <div class="metric-label">Findings</div>
              <div class="metric-value">${run.metrics.totalFindings}</div>
            </div>
            <div class="metric">
              <div class="metric-label">Open</div>
              <div class="metric-value">${run.metrics.openFindings}</div>
            </div>
            <div class="metric">
              <div class="metric-label">Critical</div>
              <div class="metric-value">${run.metrics.criticalFindings}</div>
            </div>
            <div class="metric">
              <div class="metric-label">Closed</div>
              <div class="metric-value">${run.metrics.closedFindings}</div>
            </div>
          </div>

          <div class="section">
            <h2>Executive Overview</h2>
            <div>${summaryHtml}</div>
          </div>

          <div class="section">
            <h2>Highest-Risk Accounts</h2>
            <table>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Account ID</th>
                  <th>Open Findings</th>
                  <th>Critical Findings</th>
                  <th>Created This Period</th>
                </tr>
              </thead>
              <tbody>
                ${accountsHtml || '<tr><td colspan="5">No account concentration for this period.</td></tr>'}
              </tbody>
            </table>
          </div>

          <div class="section">
            <h2>Top Rules</h2>
            <table>
              <thead>
                <tr>
                  <th>Rule</th>
                  <th>Count</th>
                </tr>
              </thead>
              <tbody>
                ${rulesHtml || '<tr><td colspan="2">No repeated rules for this period.</td></tr>'}
              </tbody>
            </table>
          </div>

          <div class="section">
            <h2>Coverage Notes</h2>
            <ul>${coverageHtml}</ul>
          </div>
        </body>
      </html>
    `);

    popup.document.close();
    popup.focus();
    popup.print();
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
