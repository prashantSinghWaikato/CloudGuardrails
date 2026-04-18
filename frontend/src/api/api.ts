export const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

export const clearAuthState = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    window.dispatchEvent(new Event("userUpdated"));
};

export const requireAuthRefresh = (message: string) => {
    clearAuthState();
    sessionStorage.setItem("authNotice", message);
    window.location.href = "/";
};

export const apiFetch = async (
    url: string,
    options: RequestInit = {}
) => {
    const token = localStorage.getItem("token");
    const headers = new Headers(options.headers);

    if (!headers.has("Content-Type") && options.body) {
        headers.set("Content-Type", "application/json");
    }

    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    const res = await fetch(`${API_BASE_URL}${url}`, {
        ...options,
        headers,
    });

    if (res.status === 401) {
        clearAuthState();
        window.location.href = "/";
        throw new Error("Unauthorized");
    }

    return res;
};

export const readApiError = async (
    res: Response,
    fallbackMessage: string
) => {
    try {
        const data = (await res.json()) as { message?: string; error?: string };
        return data.message || data.error || fallbackMessage;
    } catch {
        try {
            const text = await res.text();
            return text || fallbackMessage;
        } catch {
            return fallbackMessage;
        }
    }
};
