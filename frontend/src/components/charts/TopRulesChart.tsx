import {
    Bar,
    BarChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import type { Violation } from "../../types";

type Props = {
    violations: Violation[];
    loading?: boolean;
};

type RuleDatum = {
    name: string;
    shortName: string;
    value: number;
    share: number;
};

const MAX_RULES = 2;

const truncateLabel = (value: string, max = 30) => {
    if (value.length <= max) {
        return value;
    }

    return `${value.slice(0, max - 1)}...`;
};

const TopRulesChart = ({ violations, loading = false }: Props) => {
    const ruleMap: Record<string, number> = {};

    violations.forEach((violation) => {
        const rule = violation.ruleName || "Unknown Rule";
        ruleMap[rule] = (ruleMap[rule] || 0) + 1;
    });

    const total = Object.values(ruleMap).reduce((sum, count) => sum + count, 0);

    const chartData: RuleDatum[] = Object.entries(ruleMap)
        .map(([name, value]) => ({
            name,
            shortName: truncateLabel(name),
            value,
            share: total > 0 ? (value / total) * 100 : 0,
        }))
        .sort((a, b) => b.value - a.value || a.name.localeCompare(b.name))
        .slice(0, MAX_RULES);

    const distinctRules = chartData.length;
    const allValuesEqual = chartData.every((item) => item.value === chartData[0]?.value);
    const useRankedList = distinctRules <= 3 || allValuesEqual;
    return (
        <div className="flex h-full flex-col rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(15,26,43,0.95),rgba(10,20,33,0.82))] p-6 shadow-[0_24px_70px_-36px_rgba(2,8,23,0.95)]">
            <div className="mb-5 flex items-start justify-between gap-4">
                <div>
                    <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                        Rule Noise
                    </p>
                    <h2 className="mt-2 text-sm font-semibold text-gray-200">
                        Top Violated Rules
                    </h2>
                </div>
                <div className="text-right">
                    <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">
                        Distinct Rules
                    </p>
                    <p className="mt-1 text-2xl font-semibold leading-none text-slate-100">
                        {distinctRules}
                    </p>
                </div>
            </div>

            {loading ? (
                <div className="flex flex-1 items-center justify-center text-gray-400">
                    Loading...
                </div>
            ) : chartData.length === 0 ? (
                <div className="flex flex-1 items-center justify-center text-gray-400">
                    No data available
                </div>
            ) : (
                <>
                    <div className="mb-5 rounded-2xl border border-cyan-400/10 bg-cyan-400/[0.04] px-4 py-3 text-sm text-slate-300">
                        <span className="font-semibold text-slate-100">{chartData[0].name}</span>{" "}
                        is the top noisy rule right now, contributing{" "}
                        <span className="font-semibold text-cyan-200">{chartData[0].share.toFixed(0)}%</span> of visible findings.
                    </div>

                    {useRankedList ? (
                        <div className="flex flex-1 flex-col gap-3">
                            {chartData.map((item, index) => (
                                <div
                                    key={item.name}
                                    className="border-b border-white/8 pb-3 last:border-b-0 last:pb-0"
                                >
                                    <div className="flex items-center justify-between gap-4">
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-3">
                                                <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-full border border-cyan-400/20 bg-cyan-400/10 px-2 text-[11px] font-semibold text-cyan-200">
                                                    {index + 1}
                                                </span>
                                                <p className="truncate text-sm font-medium text-slate-100" title={item.name}>
                                                    {item.name}
                                                </p>
                                            </div>
                                            <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-slate-800/90">
                                                <div
                                                    className="h-full rounded-full bg-[linear-gradient(90deg,#67d4ff,#3b82f6)]"
                                                    style={{ width: `${Math.max(item.share, 12)}%` }}
                                                />
                                            </div>
                                        </div>
                                        <div className="min-w-[64px] text-right">
                                            <p className="text-lg font-semibold leading-none text-white">{item.value}</p>
                                            <p className="mt-1 text-[11px] uppercase tracking-[0.12em] text-slate-500">
                                                {item.share.toFixed(0)}%
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="min-h-[220px] flex-1">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart
                                    data={chartData}
                                    layout="vertical"
                                    margin={{ top: 4, right: 16, left: 12, bottom: 4 }}
                                >
                                    <CartesianGrid horizontal={true} vertical={false} stroke="#233247" strokeDasharray="3 3" />
                                    <XAxis
                                        type="number"
                                        allowDecimals={false}
                                        stroke="#7c8aa5"
                                        tickLine={false}
                                        axisLine={false}
                                    />
                                    <YAxis
                                        type="category"
                                        dataKey="shortName"
                                        width={120}
                                        stroke="#cbd5e1"
                                        tickLine={false}
                                        axisLine={false}
                                    />
                                    <Tooltip
                                        cursor={{ fill: "rgba(148, 163, 184, 0.08)" }}
                                        formatter={(value) => {
                                            const numericValue = Number(value ?? 0);
                                            return [`${numericValue} violation${numericValue === 1 ? "" : "s"}`, "Count"];
                                        }}
                                        labelFormatter={(_, payload) => {
                                            const item = payload?.[0]?.payload as RuleDatum | undefined;
                                            return item?.name ?? "";
                                        }}
                                        contentStyle={{
                                            backgroundColor: "#020617",
                                            border: "1px solid #1f2937",
                                            borderRadius: "10px",
                                            fontSize: "12px",
                                        }}
                                    />
                                    <Bar dataKey="value" fill="#56b8ff" radius={[0, 8, 8, 0]} barSize={18} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    )}

                    <div className="mt-4 text-xs text-slate-500">
                        {total} visible violation{total === 1 ? "" : "s"} across {distinctRules} rule{distinctRules === 1 ? "" : "s"} in this workspace.
                    </div>
                </>
            )}
        </div>
    );
};

export default TopRulesChart;
