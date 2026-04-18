import type { ReactNode } from "react";

type Props = {
    eyebrow: string;
    title: string;
    description: string;
    sideTitle: string;
    sideBody: string;
    highlights: Array<{
        label: string;
        value: string;
        detail: string;
    }>;
    children: ReactNode;
};

const AuthShell = ({
    eyebrow,
    title,
    description,
    sideTitle,
    sideBody,
    highlights,
    children,
}: Props) => {
    return (
        <div className="relative min-h-screen overflow-hidden px-6 py-10 text-white">
            <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(88,225,255,0.12),transparent_26%),radial-gradient(circle_at_bottom_right,rgba(47,125,255,0.18),transparent_32%)]" />

            <div className="relative mx-auto grid min-h-[calc(100vh-5rem)] w-full max-w-[1320px] gap-8 lg:grid-cols-[1.12fr_0.88fr]">
                <section className="relative overflow-hidden rounded-[32px] border border-white/10 bg-[linear-gradient(140deg,rgba(7,18,33,0.94),rgba(12,29,48,0.92))] p-8 shadow-[0_32px_90px_-40px_rgba(2,8,23,0.95)] sm:p-10 lg:p-12">
                    <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(88,225,255,0.16),transparent_24%),radial-gradient(circle_at_bottom_left,rgba(47,125,255,0.18),transparent_34%)]" />

                    <div className="relative flex h-full flex-col justify-between gap-10">
                        <div className="space-y-8">
                            <div className="inline-flex items-center rounded-full border border-cyan-400/20 bg-cyan-400/10 px-4 py-1.5 text-xs font-medium uppercase tracking-[0.28em] text-cyan-200">
                                {eyebrow}
                            </div>

                            <div className="space-y-4">
                                <h1 className="max-w-2xl text-4xl font-semibold tracking-tight text-white sm:text-5xl">
                                    {title}
                                </h1>
                                <p className="max-w-2xl text-base leading-7 text-slate-300">
                                    {description}
                                </p>
                            </div>

                            <div className="rounded-3xl border border-white/10 bg-white/5 p-6 backdrop-blur-sm">
                                <p className="text-xs uppercase tracking-[0.24em] text-slate-500">
                                    {sideTitle}
                                </p>
                                <p className="mt-3 max-w-xl text-sm leading-6 text-slate-300">
                                    {sideBody}
                                </p>
                            </div>
                        </div>

                        <div className="grid gap-4 sm:grid-cols-3">
                            {highlights.map((item) => (
                                <div
                                    key={item.label}
                                    className="rounded-2xl border border-white/10 bg-white/5 px-5 py-5 backdrop-blur-sm"
                                >
                                    <p className="text-xs uppercase tracking-[0.22em] text-slate-500">
                                        {item.label}
                                    </p>
                                    <p className="mt-3 text-2xl font-semibold text-white">
                                        {item.value}
                                    </p>
                                    <p className="mt-2 text-sm leading-6 text-slate-400">
                                        {item.detail}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="flex items-center justify-center">
                    <div className="w-full max-w-xl rounded-[30px] border border-white/10 bg-[rgba(7,15,27,0.9)] p-6 shadow-[0_28px_70px_-36px_rgba(2,8,23,0.92)] backdrop-blur-xl sm:p-8">
                        {children}
                    </div>
                </section>
            </div>
        </div>
    );
};

export default AuthShell;
