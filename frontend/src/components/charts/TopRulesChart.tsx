import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    Tooltip,
    ResponsiveContainer,
    CartesianGrid,
} from "recharts";
import type { Violation } from "../../types";

type Props = {
    violations: Violation[];
    loading?: boolean;
};

const TopRulesChart = ({ violations, loading = false }: Props) => {

    // 🔥 Aggregate rule counts
    const ruleMap: Record<string, number> = {};

    violations.forEach((v) => {
        const rule = v.ruleName || "Unknown";
        ruleMap[rule] = (ruleMap[rule] || 0) + 1;
    });

    const chartData = Object.keys(ruleMap).map((key) => ({
        name: key,
        value: ruleMap[key],
    }));

    return (
        <div className="h-full rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(15,26,43,0.95),rgba(10,20,33,0.82))] p-6 shadow-[0_24px_70px_-36px_rgba(2,8,23,0.95)]">
            <div className="mb-4 flex items-start justify-between gap-4">
                <div>
                    <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                        Rule Noise
                    </p>
                    <h2 className="mt-2 text-sm font-semibold text-gray-200">
                        Top Violated Rules
                    </h2>
                </div>
                <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-300">
                    Prioritize tuning
                </span>
            </div>

            {loading ? (
                <div className="text-center text-gray-400 py-10">
                    Loading...
                </div>
            ) : chartData.length === 0 ? (
                <div className="text-center text-gray-400 py-10">
                    No data available
                </div>
            ) : (
                <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                        <XAxis dataKey="name" stroke="#aaa" />
                        <YAxis stroke="#aaa" />
                        <Tooltip
                            contentStyle={{
                                backgroundColor: "#020617",
                                border: "1px solid #1f2937",
                                borderRadius: "8px",
                                fontSize: "12px",
                            }}
                        />
                        <Bar dataKey="value" fill="#56b8ff" radius={[8, 8, 0, 0]} />
                    </BarChart>
                </ResponsiveContainer>
            )}
        </div>
    );
};

export default TopRulesChart;
