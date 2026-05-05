import { apiFetch, readApiError } from "./api";
import type {
  ExecutiveReportGenerationRequest,
  ExecutiveReportRun,
  ExecutiveReportSchedule,
  ExecutiveReportScheduleRequest,
} from "../types";

export const generateExecutiveReport = async (payload: ExecutiveReportGenerationRequest) => {
  const res = await apiFetch("/reports/executive-summary/generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    throw new Error(await readApiError(res, "Failed to generate executive report"));
  }

  return (await res.json()) as ExecutiveReportRun;
};

export const fetchExecutiveReportSchedule = async () => {
  const res = await apiFetch("/reports/executive-summary/schedule");

  if (!res.ok) {
    throw new Error(await readApiError(res, "Failed to fetch report schedule"));
  }

  return (await res.json()) as ExecutiveReportSchedule;
};

export const updateExecutiveReportSchedule = async (payload: ExecutiveReportScheduleRequest) => {
  const res = await apiFetch("/reports/executive-summary/schedule", {
    method: "PUT",
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    throw new Error(await readApiError(res, "Failed to update report schedule"));
  }

  return (await res.json()) as ExecutiveReportSchedule;
};

export const fetchExecutiveReportRuns = async () => {
  const res = await apiFetch("/reports/executive-summary/runs");

  if (!res.ok) {
    throw new Error(await readApiError(res, "Failed to fetch report history"));
  }

  return (await res.json()) as ExecutiveReportRun[];
};
