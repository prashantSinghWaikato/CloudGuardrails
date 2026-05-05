import { useEffect, useMemo, useState } from "react";
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

const ReportsPage = () => {
  const [runs, setRuns] = useState<ExecutiveReportRun[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null);
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
        setSelectedRunId(runResponse[0]?.id ?? null);
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

  const selectedRun = useMemo(
    () => runs.find((run) => run.id === selectedRunId) ?? runs[0] ?? null,
    [runs, selectedRunId]
  );

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
      setSelectedRunId(run.id);
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

  return (
    <main className="mx-auto flex w-full max-w-[1380px] flex-col gap-6 px-6 py-8">
      <section className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-[28px] border border-white/10 bg-[linear-gradient(135deg,rgba(8,20,37,0.96),rgba(17,36,58,0.92))] p-7 shadow-[0_32px_80px_-36px_rgba(2,8,23,0.95)]">
          <p className="text-xs uppercase tracking-[0.26em] text-cyan-200/80">Executive Summary</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white">
            Generate leadership-ready weekly security reports.
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
            Combine deterministic posture metrics with AI-written narrative, then deliver the summary on demand or
            on a weekly schedule.
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

      <section className="grid gap-6 xl:grid-cols-[0.82fr_1.18fr]">
        <div className="rounded-[28px] border border-white/10 bg-white/5 p-6">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.24em] text-slate-500">History</p>
              <h2 className="mt-2 text-xl font-semibold text-white">Recent Reports</h2>
            </div>
            {loading ? <span className="text-sm text-slate-500">Loading...</span> : null}
          </div>

          <div className="mt-6 space-y-3">
            {runs.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-white/10 px-4 py-6 text-sm text-slate-400">
                No executive reports generated yet.
              </div>
            ) : (
              runs.map((run) => (
                <button
                  key={run.id}
                  type="button"
                  onClick={() => setSelectedRunId(run.id)}
                  className={`w-full rounded-2xl border px-4 py-4 text-left transition ${
                    selectedRun?.id === run.id
                      ? "border-cyan-400/40 bg-cyan-400/10"
                      : "border-white/10 bg-slate-950/20 hover:border-white/20 hover:bg-white/5"
                  }`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-sm font-semibold text-white">{run.reportName}</p>
                      <p className="mt-1 text-xs uppercase tracking-[0.22em] text-slate-500">{run.triggerType}</p>
                    </div>
                    <span className="rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] font-medium text-slate-300">
                      {run.status}
                    </span>
                  </div>
                  <p className="mt-3 text-sm text-slate-400">{formatDateTime(run.createdAt)}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatDateTime(run.periodStart)} to {formatDateTime(run.periodEnd)}
                  </p>
                </button>
              ))
            )}
          </div>
        </div>

        <div className="rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(8,16,28,0.96),rgba(9,18,32,0.92))] p-6 shadow-[0_26px_70px_-46px_rgba(2,8,23,0.95)]">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Report Output</p>
              <h2 className="mt-2 text-2xl font-semibold text-white">
                {selectedRun?.reportName ?? "Executive Summary"}
              </h2>
            </div>
            {selectedRun ? (
              <div className="text-right text-sm text-slate-400">
                <p>Generated {formatDateTime(selectedRun.createdAt)}</p>
                <p>Email status: {selectedRun.emailStatus || "Not sent"}</p>
              </div>
            ) : null}
          </div>

          {!selectedRun ? (
            <div className="mt-6 rounded-2xl border border-dashed border-white/10 px-5 py-8 text-sm text-slate-400">
              Generate a report or select a previous run to view the summary and metrics.
            </div>
          ) : (
            <div className="mt-6 space-y-6">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                <MetricCard label="Findings This Period" value={selectedRun.metrics.totalFindings} />
                <MetricCard label="Open Findings" value={selectedRun.metrics.openFindings} />
                <MetricCard label="Critical Findings" value={selectedRun.metrics.criticalFindings} />
                <MetricCard label="Closed This Period" value={selectedRun.metrics.closedFindings} />
              </div>

              <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
                <section className="rounded-3xl border border-white/10 bg-white/5 p-5">
                  <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Narrative</p>
                  <pre className="mt-4 whitespace-pre-wrap font-sans text-sm leading-7 text-slate-200">
                    {selectedRun.summaryText || "No summary generated."}
                  </pre>
                </section>

                <div className="space-y-6">
                  <section className="rounded-3xl border border-white/10 bg-white/5 p-5">
                    <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Highest-Risk Accounts</p>
                    <div className="mt-4 space-y-3">
                      {selectedRun.metrics.topAccounts.length === 0 ? (
                        <p className="text-sm text-slate-400">No account concentrations for this period.</p>
                      ) : (
                        selectedRun.metrics.topAccounts.map((account) => (
                          <div key={`${account.accountId}-${account.accountName}`} className="rounded-2xl border border-white/10 bg-slate-950/30 px-4 py-3">
                            <p className="text-sm font-semibold text-white">{account.accountName}</p>
                            <p className="mt-1 text-xs text-slate-400">{account.accountId}</p>
                            <p className="mt-3 text-sm text-slate-300">
                              {account.openFindings} open, {account.criticalFindings} critical, {account.findingsCreated} created
                            </p>
                          </div>
                        ))
                      )}
                    </div>
                  </section>

                  <section className="rounded-3xl border border-white/10 bg-white/5 p-5">
                    <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Top Rules</p>
                    <div className="mt-4 space-y-3">
                      {selectedRun.metrics.topRules.length === 0 ? (
                        <p className="text-sm text-slate-400">No repeated rules for this period.</p>
                      ) : (
                        selectedRun.metrics.topRules.map((rule) => (
                          <div key={rule.ruleName} className="flex items-center justify-between gap-4 rounded-2xl border border-white/10 bg-slate-950/30 px-4 py-3">
                            <p className="text-sm text-slate-200">{rule.ruleName}</p>
                            <span className="rounded-full border border-cyan-400/20 bg-cyan-400/10 px-2.5 py-1 text-xs font-semibold text-cyan-100">
                              {rule.count}
                            </span>
                          </div>
                        ))
                      )}
                    </div>
                  </section>
                </div>
              </div>

              <section className="rounded-3xl border border-white/10 bg-white/5 p-5">
                <p className="text-xs uppercase tracking-[0.24em] text-slate-500">Coverage Notes</p>
                <div className="mt-4 flex flex-col gap-3">
                  {selectedRun.metrics.coverageNotes.map((note) => (
                    <div key={note} className="rounded-2xl border border-white/10 bg-slate-950/30 px-4 py-3 text-sm text-slate-300">
                      {note}
                    </div>
                  ))}
                </div>
              </section>
            </div>
          )}
        </div>
      </section>
    </main>
  );
};

const MetricCard = ({ label, value }: { label: string; value: number }) => (
  <div className="rounded-3xl border border-white/10 bg-white/5 px-5 py-4">
    <p className="text-xs uppercase tracking-[0.22em] text-slate-500">{label}</p>
    <p className="mt-3 text-3xl font-semibold text-white">{value}</p>
  </div>
);

export default ReportsPage;
