import { ArrowRight, Building2, Lock, Mail, User } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { fetchUser, signup } from "../api/authService";
import AuthShell from "../components/AuthShell";

type Props = {
    setIsLoggedIn: (value: boolean) => void;
};

const Signup = ({ setIsLoggedIn }: Props) => {
    const navigate = useNavigate();

    const [agree, setAgree] = useState(false);
    const [name, setName] = useState("");
    const [organizationName, setOrganizationName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSignup = async () => {
        setError("");

        if (!name || !organizationName || !email || !password) {
            setError("All fields are required.");
            return;
        }

        if (!agree) {
            setError("Please accept the terms to continue.");
            return;
        }

        try {
            setLoading(true);

            const token = await signup({
                name,
                email,
                password,
                organizationName,
            });

            localStorage.setItem("token", token);

            const user = await fetchUser();
            localStorage.setItem("user", JSON.stringify(user));
            window.dispatchEvent(new Event("userUpdated"));

            setIsLoggedIn(true);
            navigate("/dashboard", { replace: true });
        } catch (signupError) {
            setError(signupError instanceof Error ? signupError.message : "Signup failed");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthShell
            eyebrow="Organization Onboarding"
            title="Stand up a guardrails workspace that feels ready for a real security team."
            description="Create your organization, establish your admin account, and move straight into account onboarding, detection rules, and remediation workflows."
            sideTitle="What you unlock after signup"
            sideBody="Your admin workspace is provisioned immediately, which means you can start connecting cloud accounts, validating credentials, and reviewing live findings without extra setup steps."
            highlights={[
                {
                    label: "Setup",
                    value: "Instant",
                    detail: "Organization creation and admin access happen in one flow.",
                },
                {
                    label: "Accounts",
                    value: "Scoped",
                    detail: "Account visibility is derived from live backend user state.",
                },
                {
                    label: "Audit",
                    value: "Tracked",
                    detail: "Remediation execution and verification are preserved in the UI.",
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
                                Organization Setup
                            </p>
                        </div>
                    </div>

                    <div>
                        <h2 className="text-3xl font-semibold tracking-tight text-white">
                            Create account
                        </h2>
                        <p className="mt-2 text-sm leading-6 text-slate-400">
                            Start with your organization admin profile. You can add cloud accounts after access is created.
                        </p>
                    </div>
                </div>

                <div className="grid gap-5 sm:grid-cols-2">
                    <label className="block space-y-2">
                        <span className="text-sm text-slate-300">Full name</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <User size={18} className="text-slate-500" />
                            <input
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                placeholder="Ava Carter"
                            />
                        </div>
                    </label>

                    <label className="block space-y-2">
                        <span className="text-sm text-slate-300">Organization</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <Building2 size={18} className="text-slate-500" />
                            <input
                                value={organizationName}
                                onChange={(e) => setOrganizationName(e.target.value)}
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                placeholder="North Ridge Security"
                            />
                        </div>
                    </label>

                    <label className="block space-y-2 sm:col-span-2">
                        <span className="text-sm text-slate-300">Work email</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <Mail size={18} className="text-slate-500" />
                            <input
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                placeholder="you@company.com"
                            />
                        </div>
                    </label>

                    <label className="block space-y-2 sm:col-span-2">
                        <span className="text-sm text-slate-300">Password</span>
                        <div className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 transition focus-within:border-cyan-400/40 focus-within:bg-white/7">
                            <Lock size={18} className="text-slate-500" />
                            <input
                                type="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-slate-500"
                                placeholder="Create a strong password"
                                onKeyDown={(e) => e.key === "Enter" && void handleSignup()}
                            />
                        </div>
                    </label>
                </div>

                {error && (
                    <div className="rounded-2xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
                        {error}
                    </div>
                )}

                <div className="space-y-4">
                    <label
                        htmlFor="signup-agree"
                        className="flex cursor-pointer items-start gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-slate-300"
                    >
                        <input
                            id="signup-agree"
                            type="checkbox"
                            checked={agree}
                            onChange={() => setAgree((current) => !current)}
                            className="mt-1 accent-cyan-400"
                        />
                        <span>
                            I agree to the terms and understand this account becomes the initial admin for the organization workspace.
                        </span>
                    </label>

                    <button
                        onClick={() => void handleSignup()}
                        disabled={loading || !agree}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white shadow-[0_18px_40px_-20px_rgba(37,99,235,0.95)] transition hover:-translate-y-0.5 hover:bg-blue-500 disabled:cursor-not-allowed disabled:translate-y-0 disabled:bg-slate-700 disabled:shadow-none disabled:opacity-60"
                    >
                        {loading ? "Creating account..." : "Create Account"}
                        {!loading && <ArrowRight size={16} />}
                    </button>

                    <p className="text-center text-sm text-slate-400">
                        Already have access?{" "}
                        <Link to="/" className="font-medium text-cyan-300 transition hover:text-cyan-200">
                            Sign in
                        </Link>
                    </p>
                </div>
            </div>
        </AuthShell>
    );
};

export default Signup;
