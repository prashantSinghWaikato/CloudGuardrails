import ViolationTable from "../components/ViolationTable";

const ViolationsPage = () => {
    return (
        <div className="p-6 space-y-6">
            <h1 className="text-xl font-semibold text-gray-200">
                Violations
            </h1>

            <ViolationTable fullView />
        </div>
    );
};

export default ViolationsPage;