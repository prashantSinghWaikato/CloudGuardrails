import RemediationTable from "../components/RemediationTable";

const RemediationsPage = () => {
    return (
        <div className="p-6 space-y-6">
            <h1 className="text-xl font-semibold text-gray-200">
                Remediations
            </h1>

            <RemediationTable />
        </div>
    );
};

export default RemediationsPage;