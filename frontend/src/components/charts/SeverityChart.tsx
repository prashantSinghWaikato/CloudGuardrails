import {
    PieChart,
    Pie,
    Cell,
    Tooltip,
    ResponsiveContainer,
} from "recharts";
import type { Violation } from "../../types";

const COLORS: Record<string, string> = {
    CRITICAL: "#ef4444",
    HIGH: "#f97316",
    MEDIUM: "#eab308",
    LOW: "#22c55e",
};

type Props = {
    violations: Violation[];
    loading?: boolean;
};

type PieLabelProps = {
    cx?: number;
    cy?: number;
    midAngle?: number;
    outerRadius?: number;
    percent?: number;
};

const RADIAN = Math.PI / 180;

const renderLabel = ({ cx = 0, cy = 0, midAngle = 0, outerRadius = 0, percent = 0 }: PieLabelProps) => {
    if (percent <= 0) {
        return null;
    }

    const radius = outerRadius + 18;
    const x = cx + radius * Math.cos(-midAngle * RADIAN);
    const y = cy + radius * Math.sin(-midAngle * RADIAN);

    return (
        <text
            x={x}
            y={y}
            fill="#f8fafc"
            textAnchor={x > cx ? "start" : "end"}
            dominantBaseline="central"
            fontSize={12}
            fontWeight={600}
        >
            {`${(percent * 100).toFixed(0)}%`}
        </text>
    );
};

const SeverityChart = ({ violations, loading = false }: Props) => {

    // 🔥 aggregate
    const severityMap: Record<string, number> = {};

    violations.forEach((v) => {
        if (!v?.severity) return;
        severityMap[v.severity] =
            (severityMap[v.severity] || 0) + 1;
    });

    const chartData = Object.keys(severityMap).map((key) => ({
        name: key,
        value: severityMap[key],
    }));

    const total = chartData.reduce((sum, d) => sum + d.value, 0);

    return (
        <div className="flex h-full flex-col rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(15,26,43,0.95),rgba(10,20,33,0.82))] p-5 shadow-[0_24px_70px_-36px_rgba(2,8,23,0.95)]">
            <div className="mb-2">
                <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                    Severity Mix
                </p>
                <h2 className="mt-2 text-sm font-semibold text-gray-200">
                    Violations by Severity
                </h2>
            </div>

            <div className="flex-1 min-h-[220px] relative">

                {loading ? (
                    <div className="flex items-center justify-center h-full text-gray-400">
                        Loading...
                    </div>
                ) : chartData.length === 0 ? (
                    <div className="flex items-center justify-center h-full text-gray-400">
                        No data available
                    </div>
                ) : (
                    <>
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie
                                    data={chartData}
                                    dataKey="value"
                                    nameKey="name"
                                    cx="50%"
                                    cy="58%"
                                    outerRadius={76}
                                    innerRadius={48}
                                    paddingAngle={3}
                                    stroke="none"
                                    labelLine={false}
                                    label={renderLabel}
                                >
                                    {chartData.map((entry, i) => (
                                        <Cell
                                            key={i}
                                            fill={COLORS[entry.name] || "#64748b"}
                                        />
                                    ))}
                                </Pie>

                                <Tooltip
                                    formatter={(value, name) => {
                                        const numericValue = Number(value ?? 0);
                                        const percent =
                                            total > 0
                                                ? ((numericValue / total) * 100).toFixed(1)
                                                : "0.0";

                                        return [`${numericValue} (${percent}%)`, String(name)];
                                    }}
                                    contentStyle={{
                                        backgroundColor: "#020617",
                                        border: "1px solid #1f2937",
                                        borderRadius: "8px",
                                        fontSize: "12px",
                                    }}
                                />
                            </PieChart>
                        </ResponsiveContainer>

                        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                            <p className="text-2xl font-semibold text-white">
                                {total}
                            </p>
                            <p className="text-xs text-gray-400">
                                Total
                            </p>
                        </div>
                    </>
                )}
            </div>

            <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-gray-300">
                {chartData.map((item) => (
                    <div key={item.name} className="flex items-center gap-2">
                        <span
                            className="w-2.5 h-2.5 rounded-full"
                            style={{ backgroundColor: COLORS[item.name] }}
                        />
                        {item.name} ({item.value})
                    </div>
                ))}
            </div>
        </div>
    );
};

export default SeverityChart;
