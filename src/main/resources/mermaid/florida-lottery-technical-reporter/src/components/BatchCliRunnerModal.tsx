import React, { useState } from 'react';
import { Terminal, Play, X, CheckCircle2, ShieldCheck, Database, FileSpreadsheet, Sparkles, Clock } from 'lucide-react';

interface BatchCliRunnerModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const BatchCliRunnerModal: React.FC<BatchCliRunnerModalProps> = ({ isOpen, onClose }) => {
  const [targetJob, setTargetJob] = useState('StoredProcInventoryMasterJob');
  const [schemasArg, setSchemasArg] = useState('ARS,TRANSLATOR,COMMON,GL');
  const [isRunning, setIsRunning] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [isCompleted, setIsCompleted] = useState(false);

  if (!isOpen) return null;

  const handleRunJob = () => {
    setIsRunning(true);
    setIsCompleted(false);
    const traceId = `TRC-${Math.random().toString(36).substring(2, 9).toUpperCase()}`;
    setLogs([
      `[IWS_RUNNER] Bootstrapping Spring Boot 3.4.0 CLI Context...`,
      `[IWS_RUNNER] Invoking: java -jar fllottery-etl-doc-master.jar -job=${targetJob} -schemas=${schemasArg} -date=2026-10-08`,
      `[AbstractJob] >>> Initializing JobContext | Trace ID: ${traceId} | Host: oci-batch-01 | PID: 48912`
    ]);

    setTimeout(() => {
      if (targetJob.includes('SecurityGrants')) {
        setLogs(prev => [
          ...prev,
          `[ServiceConfigDispatcher] Dynamic lookup for 'doc-report_config' on master etldb schema -> RESOLVED`,
          `[OracleSecuritySpecialist] Connecting to Oracle DEVOLTP (10.229.2.57) via HikariCP pool...`,
          `[OracleSecuritySpecialist] Querying ALL_TAB_PRIVS & ALL_COL_PRIVS for schemas: [${schemasArg}]...`
        ]);
      } else if (targetJob.includes('UnifiedTechnical')) {
        setLogs(prev => [
          ...prev,
          `[ServiceConfigDispatcher] Dynamic lookup for 'doc-report_config' on master etldb schema -> RESOLVED`,
          `[OracleDocSpecialist] Connecting to Oracle DEVOLTP (10.229.2.57)...`,
          `[OracleDocSpecialist] Inspecting ALL_TABLES, ALL_EXTERNAL_TABLES, ALL_EXTERNAL_LOCATIONS for [${schemasArg}]...`
        ]);
      } else {
        setLogs(prev => [
          ...prev,
          `[ServiceConfigDispatcher] Dynamic lookup for 'doc-report_config' on master etldb schema -> RESOLVED (targetDb=DEVOLTP, outDir=C:\\ocioldapps1\\Cached\\DbCache\\Schemas)`,
          `[OracleProcExtractorSpecialist] Connecting to Oracle instance DEVOLTP (10.229.2.57) via HikariCP...`,
          `[OracleProcExtractorSpecialist] Querying ALL_PROCEDURES, ALL_ARGUMENTS, ALL_DEPENDENCIES for [${schemasArg}]...`
        ]);
      }
    }, 600);

    setTimeout(() => {
      if (targetJob.includes('SecurityGrants')) {
        setLogs(prev => [
          ...prev,
          `[GenerateSchemaSecurityGrantsWordDocs] Found 412 Object Grants in ALL_TAB_PRIVS across target schemas.`,
          `[GenerateSchemaSecurityGrantsWordDocs] Analyzed ALL_COL_PRIVS: 0 restricted column grants (Full table authorization compliant).`,
          `[GenerateSchemaSecurityGrantsWordDocs] Building Pivot Table: 18 Distinct Grantees / Roles mapped.`,
          `[GenerateSchemaSecurityGrantsWordDocs] Rendering Formal Word Specs (.docx) with Web Team Executive Cover Page...`,
          `[GenerateSchemaSecurityGrantsWordDocs] Wrote: C:\\ocioldapps1\\Cached\\DbCache\\Schemas\\TRANSLATOR\\TRANSLATOR_Security_Grants_Specification.docx`
        ]);
      } else if (targetJob.includes('UnifiedTechnical')) {
        setLogs(prev => [
          ...prev,
          `[GenerateUnifiedTechnicalWordDocs] Detected External Tables via ALL_EXTERNAL_TABLES (Driver: ORACLE_LOADER, Directory: TRANSLATOR_DATA).`,
          `[GenerateUnifiedTechnicalWordDocs] Appended External File Ingestion subsections & raw access parameter code blocks.`,
          `[GenerateUnifiedTechnicalWordDocs] Generating individual & consolidated technical specifications with high-DPI ERDs...`,
          `[GenerateUnifiedTechnicalWordDocs] Successfully generated Word documents with authentic ORGANIZATION EXTERNAL DDL blocks.`
        ]);
      } else {
        setLogs(prev => [
          ...prev,
          `[ExtractProcsInventorySpecialist] Processing Schemas: Extracted 244 Routines & 220 Tables.`,
          `[ExtractProcsInventorySpecialist] Generating multi-tab workbook: Database_Routines_Inventory.xlsx`,
          `[ExtractProcsInventorySpecialist] Configured Named Ranges: ARS_TYPE, ARS_DESC, TRANSLATOR_TYPE...`,
          `[ExtractProcsInventorySpecialist] Generating chunk files (<450 KB): ARS.json, TRANSLATOR_Part_01.json...`
        ]);
      }
    }, 1300);

    setTimeout(() => {
      setLogs(prev => [
        ...prev,
        `[AuditSpecialist] Flight recorder heartbeat dispatched to etldb.SERVICE_AUDIT [STATUS=SUCCESS].`,
        `[NotificationSpecialist] Microsoft 365 Teams alert card dispatched to Architecture channel.`,
        `[JobTelemetryAspect] <<< Job execution finished in 1842ms with 0 failures. Exit code: 0.`
      ]);
      setIsRunning(false);
      setIsCompleted(true);
    }, 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-4 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Terminal className="w-5 h-5 text-amber-400" />
            <h3 className="font-bold text-white text-sm">IBM Workload Scheduler (IWS) CLI Runner</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 space-y-4 overflow-y-auto">
          <div className="flex flex-wrap gap-2 text-xs">
            <span className="text-slate-400 self-center">Presets:</span>
            <button
              type="button"
              onClick={() => {
                setTargetJob('StoredProcInventoryMasterJob');
                setSchemasArg('ARS,TRANSLATOR,COMMON,GL');
              }}
              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 font-mono text-[11px] cursor-pointer"
            >
              Inventory Job
            </button>
            <button
              type="button"
              onClick={() => {
                setTargetJob('GenerateSchemaSecurityGrantsWordDocs');
                setSchemasArg('TRANSLATOR,ARS,COMMON');
              }}
              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-amber-300 font-mono text-[11px] cursor-pointer"
            >
              Security Grants Matrix
            </button>
            <button
              type="button"
              onClick={() => {
                setTargetJob('GenerateUnifiedTechnicalWordDocs');
                setSchemasArg('TRANSLATOR');
              }}
              className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-sky-300 font-mono text-[11px] cursor-pointer"
            >
              Unified Docs (External Tables)
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Target Job Bean:</label>
              <input
                type="text"
                value={targetJob}
                onChange={(e) => setTargetJob(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-amber-300 focus:outline-none focus:border-amber-500"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">-schemas Switch:</label>
              <input
                type="text"
                value={schemasArg}
                onChange={(e) => setSchemasArg(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs font-mono text-sky-300 focus:outline-none focus:border-amber-500"
              />
            </div>
          </div>

          <button
            onClick={handleRunJob}
            disabled={isRunning}
            className="w-full py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs flex items-center justify-center space-x-2 transition-all shadow cursor-pointer disabled:opacity-50"
          >
            <Play className="w-4 h-4" />
            <span>{isRunning ? 'Executing Batch Job Pipeline...' : 'Trigger CLI Job Execution'}</span>
          </button>

          {/* Console Output */}
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 font-mono text-[11px] text-slate-300 h-64 overflow-y-auto space-y-1">
            {logs.length === 0 ? (
              <span className="text-slate-600">Press 'Trigger CLI Job Execution' to simulate unattended IWS run...</span>
            ) : (
              logs.map((log, i) => (
                <div key={i} className="leading-relaxed">
                  <span className="text-slate-600 mr-2">&gt;</span>
                  <span className={log.includes('SUCCESS') ? 'text-emerald-400 font-bold' : log.includes('CRITICAL') ? 'text-rose-400' : 'text-slate-300'}>
                    {log}
                  </span>
                </div>
              ))
            )}
          </div>

          {isCompleted && (
            <div className="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl flex items-center justify-between text-xs text-emerald-300 font-bold">
              <div className="flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                <span>Job Completed Successfully • Excel & AI Chunks Ready</span>
              </div>
              <span className="font-mono text-slate-400">Exit Code: 0</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
