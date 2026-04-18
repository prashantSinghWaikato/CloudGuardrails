import { API_BASE_URL, apiFetch } from "./api";

type AuthPayload = {
    email: string;
    password: string;
};

type SignupPayload = AuthPayload & {
    name: string;
    organizationName: string;
};

const postAuth = async (path: string, payload: AuthPayload | SignupPayload) => {
    const res = await fetch(`${API_BASE_URL}${path}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
    });

    const token = await res.text();

    if (!res.ok) {
        throw new Error(token || "Authentication failed");
    }

    return token;
};

export const login = (payload: AuthPayload) => postAuth("/auth/login", payload);

export const signup = (payload: SignupPayload) =>
    postAuth("/auth/signup", payload);

export const fetchUser = async () => {
    const res = await apiFetch("/auth/me");
    return res.json();
};
