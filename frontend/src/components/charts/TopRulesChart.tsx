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

const MAX_RULES = 5;

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
    const topRule = chartData[0];

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
                <div className="rounded-2xl border border-cyan-400/10 bg-cyan-400/5 px-3 py-2 text-right">
                    <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">
                        Distinct Rules
                    </p>
                    <p className="mt-1 text-lg font-semibold text-slate-100">
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
                    <div className="mb-5 rounded-2xl border border-white/8 bg-black/20 p-4">
                        <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">
                            Most Common Rule
                        </p>
                        <div className="mt-2 flex items-end justify-between gap-4">
                            <div className="min-w-0">
                                <p className="truncate text-sm font-semibold text-slate-100" title={topRule.name}>
                                    {topRule.name}
                                </p>
                                <p className="mt-1 text-xs text-slate-400">
                                    {topRule.value} violation{topRule.value === 1 ? "" : "s"} generating {topRule.share.toFixed(0)}% of current noise
                                </p>
                            </div>
                            <div className="rounded-full border border-amber-400/20 bg-amber-400/10 px-3 py-1 text-xs font-medium text-amber-200">
                                Tune first
                            </div>
                        </div>
                    </div>

                    {useRankedList ? (
                        <div className="flex flex-1 flex-col gap-3">
                            {chartData.map((item, index) => (
                                <div
                                    key={item.name}
                                    className="rounded-2xl border border-white/8 bg-white/[0.03] px-4 py-3"
                                >
                                    <div className="flex items-center justify-between gap-4">
                                        <div className="min-w-0">
                                            <div className="flex items-center gap-3">
                                                <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-cyan-400/10 text-xs font-semibold text-cyan-200">
                                                    {index + 1}
                                                </span>
                                                <p className="truncate text-sm font-medium text-slate-100" title={item.name}>
                                                    {item.name}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <p className="text-sm font-semibold text-white">{item.value}</p>
                                            <p className="text-[11px] text-slate-500">{item.share.toFixed(0)}%</p>
                                        </div>
                                    </div>
                                    <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-800">
                                        <div
                                            className="h-full rounded-full bg-[linear-gradient(90deg,#67d4ff,#3b82f6)]"
                                            style={{ width: `${Math.max(item.share, 8)}%` }}
                                        />
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
                        Ranked by current visible violations across this workspace.
                    </div>
                </>
            )}
        </div>
    );
};

export default TopRulesChart;
