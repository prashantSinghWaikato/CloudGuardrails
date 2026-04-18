import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

type Props = {
    children: ReactNode;
};

const ProtectedRoute = ({ children }: Props) => {
    const token = localStorage.getItem("token");

    if (!token) {
        return <Navigate to="/" replace />;
    }

    return children;
};

export default ProtectedRoute;
