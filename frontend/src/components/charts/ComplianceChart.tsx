import { ResponsiveContainer, RadialBarChart, RadialBar, PolarAngleAxis } from "recharts";
import type { Violation } from "../../types";

type Props = {
    violations: Violation[];
    loading?: boolean;
};

const ComplianceChart = ({ violations, loading = false }: Props) => {
    const total = violations.length;
    const fixed = violations.filter((item) => item.status === "FIXED").length;
    const compliantPercent = total > 0 ? Math.round((fixed / total) * 100) : 100;

    const chartData = [{ name: "Compliance", value: compliantPercent, fill: "#3b82f6" }];

    return (
        <div className="flex h-full flex-col rounded-[28px] border border-white/10 bg-[linear-gradient(180deg,rgba(15,26,43,0.95),rgba(10,20,33,0.82))] p-6 shadow-[0_24px_70px_-36px_rgba(2,8,23,0.95)]">
            <div className="mb-4 flex items-start justify-between gap-4">
                <div>
                    <p className="text-[11px] uppercase tracking-[0.24em] text-slate-500">
                        Snapshot
                    </p>
                    <h2 className="mt-2 text-sm font-semibold text-gray-200">
                        Compliance Posture
                    </h2>
                </div>
                <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-xs text-emerald-200">
                    Live from visible findings
                </span>
            </div>

            <div className="grid min-h-0 flex-1 gap-4 md:grid-cols-[1.05fr_0.95fr]">
                <div className="relative min-h-[200px]">
                    {loading ? (
                        <div className="flex h-full items-center justify-center text-gray-400">
                            Loading...
                        </div>
                    ) : (
                        <>
                            <ResponsiveContainer width="100%" height="100%">
                                <RadialBarChart
                                    innerRadius="68%"
                                    outerRadius="100%"
                                    data={chartData}
                                    startAngle={210}
                                    endAngle={-30}
                                >
                                    <PolarAngleAxis
                                        type="number"
                                        domain={[0, 100]}
                                        dataKey="value"
                                        tick={false}
                                    />
                                    <RadialBar
                                        background={{ fill: "rgba(148,163,184,0.12)" }}
                                        dataKey="value"
                                        cornerRadius={12}
                                    />
                                </RadialBarChart>
                            </ResponsiveContainer>

                            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                                <p className="text-4xl font-semibold text-white">
                                    {compliantPercent}%
                                </p>
                                <p className="mt-1 text-xs uppercase tracking-[0.22em] text-slate-500">
                                    Compliant
                                </p>
                            </div>
                        </>
                    )}
                </div>

                <div className="grid min-h-0 content-center">
                    <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
                        <p className="text-[11px] uppercase tracking-[0.22em] text-slate-500">
                            Fixed Findings
                        </p>
                        <div className="mt-3 flex items-end justify-between gap-3">
                            <p className="text-3xl font-semibold text-white">{fixed}</p>
                            <p className="text-[11px] uppercase tracking-[0.18em] text-emerald-300">
                                Verified
                            </p>
                        </div>
                        <p className="mt-2 text-xs leading-5 text-slate-400">
                            Findings verified as fixed in the current live dataset.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ComplianceChart;
