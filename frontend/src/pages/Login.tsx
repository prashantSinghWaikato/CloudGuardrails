import { ArrowRight, Lock, Mail } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { fetchUser, login } from "../api/authService";
import AuthShell from "../components/AuthShell";

type Props = {
    setIsLoggedIn: (value: boolean) => void;
};

const Login = ({ setIsLoggedIn }: Props) => {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const [notice] = useState(() => {
        const authNotice = sessionStorage.getItem("authNotice") ?? "";
        if (authNotice) {
            sessionStorage.removeItem("authNotice");
        }
        return authNotice;
    });

    const navigate = useNavigate();

    const handleLogin = async () => {
        setError("");

        if (!email || !password) {
            setError("Please enter your email and password.");
            return;
        }

        try {
            setLoading(true);

            const token = await login({ email, password });
            localStorage.setItem("token", token);

            const user = await fetchUser();
            localStorage.setItem("user", JSON.stringify(user));
            window.dispatchEvent(new Event("userUpdated"));

            setIsLoggedIn(true);
            navigate("/dashboard", { replace: true });
        } catch (loginError) {
            setError(loginError instanceof Error ? loginError.message : "Login failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthShell
            eyebrow="Guardrails Access"
            title="Operate cloud compliance from one realtime command surface."
            description="Review violations, monitor remediation progress, and keep account-level posture visible without switching between tooling."
            sideTitle="Built for operational security teams"
            sideBody="The platform combines policy findings, remediation workflows, and verification history in one workspace so your team can move from alert to resolution quickly."
            highlights={[
                {
                    label: "Realtime",
                    value: "STOMP",
                    detail: "Push updates for violations, remediations, and notifications.",
                },
                {
                    label: "Workflow",
                    value: "Verify",
                    detail: "Track execution outcomes and compliance verification state.",
                },
                {
                    label: "Coverage",
                    value: "AWS",
                    detail: "Monitor onboarded cloud accounts through rule-driven guardrails.",
                },
            ]}
        >
            <div className="space-y-8">
                <div className="space-y-4">
                    <div className="inline-flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3">
                        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10 p-2">
                            <img
                                src="/favicon.svg"
                                alt="Guardrails"
                                className="h-full w-full object-contain"
                            />
                        </div>
                        <div>
                            <p className="text-sm font-semibold text-white">Guardrails</p>
                            <p className="text-xs uppercase tracking-[0.22em] text-slate-500">
                                Security Command Center
                            </p>
                        </div>
                    </div>

                    <div>
                        <h2 className="text-3xl font-semibold tracking-tight text-white">
                            Sign in
                        </h2>
                        <p className="mt-2 text-sm leading-6 text-slate-400">
                            Access your organization workspace and continue monitoring posture and remediation activity.
                        </p>
                    </div>
                </div>

                <div className="space-y-5">
                    <label className="block space-y-2">
                        <span className="text-sm text-slate-300">Email</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <Mail size={18} className="text-slate-500" />
                            <input
                                type="email"
                                placeholder="you@company.com"
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && void handleLogin()}
                            />
                        </div>
                    </label>

                    <label className="block space-y-2">
                        <span className="text-sm text-slate-300">Password</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <Lock size={18} className="text-slate-500" />
                            <input
                                type="password"
                                placeholder="Enter your password"
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && void handleLogin()}
                            />
                        </div>
                    </label>
                </div>

                {(notice || error) && (
                    <div className="space-y-3">
                        {notice && (
                            <div className="rounded-2xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">
                                {notice}
                            </div>
                        )}

                        {error && (
                            <div className="rounded-2xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                                {error}
                            </div>
                        )}
                    </div>
                )}

                <div className="space-y-4">
                    <button
                        type="button"
                        onClick={() => void handleLogin()}
                        disabled={loading}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white shadow-[0_18px_40px_-20px_rgba(37,99,235,0.95)] transition hover:-translate-y-0.5 hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        {loading ? "Signing in..." : "Sign In"}
                        {!loading && <ArrowRight size={16} />}
                    </button>

                    <p className="text-center text-sm text-slate-400">
                        Need a workspace?{" "}
                        <Link to="/signup" className="font-medium text-cyan-300 transition hover:text-cyan-200">
                            Create an account
                        </Link>
                    </p>
                </div>
            </div>
        </AuthShell>
    );
};

export default Login;
