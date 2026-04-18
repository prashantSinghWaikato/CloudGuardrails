// types/UserResponse.ts

export type UserResponse = {
    name: string;
    email: string;
    organization?: string;
    accounts?: string[];
};