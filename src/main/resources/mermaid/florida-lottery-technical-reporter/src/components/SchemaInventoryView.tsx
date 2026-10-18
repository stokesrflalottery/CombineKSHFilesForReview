import React, { useState } from 'react';
import { FLORIDA_LOTTERY_SCHEMAS, SchemaSummary, TARGET_SCHEMAS_LIST } from '../data/schemasData';
import { FileSpreadsheet, Sparkles, FolderDown, ArrowRight, CheckCircle2, Clock, Layers, Database, Table } from 'lucide-react';

interface SchemaInventoryViewProps {
  onSelectSchema: (schemaName: string) => void;
  onOpenAiResearch: (schemaName: string) => void;
  onOpenExcelStaging: () => void;
  onOpenCliRunner: () => void;
}

export const SchemaInventoryView: React.FC<SchemaInventoryViewProps> = ({
  onSelectSchema,
  onOpenAiResearch,
  onOpenExcelStaging,
  onOpenCliRunner
}) => {
  const [searchTerm, setSearchTerm] = useState('');

  const schemas = Object.values(FLORIDA_LOTTERY_SCHEMAS);
  const totalRoutines = schemas.reduce((acc, s) => acc + s.totalRoutines, 0);
  const totalResearched = schemas.reduce((acc, s) => acc + s.researchedCount, 0);
  const totalTables = schemas.reduce((acc, s) => acc + s.tablesCount, 0);
  const totalProcs = schemas.reduce((acc, s) => acc + s.procsCount, 0);
  const totalPkgs = schemas.reduce((acc, s) => acc + s.pkgsCount, 0);
  const totalFuncs = schemas.reduce((acc, s) => acc + s.funcsCount, 0);
  const overallPercentage = ((totalResearched / totalRoutines) * 100).toFixed(1);

  const filteredSchemas = schemas.filter(s =>
    s.schemaName.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Executive Hero Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-amber-500/5 rounded-full blur-3xl pointer-events-none" />
        
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="flex items-center space-x-2 text-amber-400 text-xs font-semibold uppercase tracking-wider mb-2">
              <Layers className="w-4 h-4" />
              <span>Targeted Schema Governance • DEVOLTP Database</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              Florida Lottery Stored Procedure & Routine Inventory
            </h1>
            <p className="mt-2 text-sm text-slate-300 max-w-3xl leading-relaxed">
              Automated extraction, JSON chunking (<span className="text-amber-300 font-mono font-semibold">&lt;450 KB</span>), formula-linked Excel cataloging, and AI-driven technical reverse engineering across 12 mission-critical enterprise schemas.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={onOpenExcelStaging}
              className="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-lg shadow-emerald-900/30 transition-all cursor-pointer"
            >
              <FileSpreadsheet className="w-4 h-4" />
              <span>Open Excel Catalog Sandbox</span>
            </button>

            <button
              onClick={onOpenCliRunner}
              className="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold shadow-lg shadow-amber-500/20 transition-all cursor-pointer"
            >
              <Sparkles className="w-4 h-4" />
              <span>Run Batch Job Pipeline</span>
            </button>
          </div>
        </div>

        {/* Global Progress Metrics Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mt-6 pt-6 border-t border-slate-800/80">
          <div className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-400 block mb-1">Target Schemas</span>
            <span className="text-xl font-bold text-white">12 Schemas</span>
          </div>

          <div className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-400 block mb-1">Total Routines</span>
            <span className="text-xl font-bold text-amber-400">{totalRoutines}</span>
            <span className="text-[10px] text-slate-500 block mt-0.5">{totalProcs} Procs • {totalPkgs} Pkgs • {totalFuncs} Funcs</span>
          </div>

          <div className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-400 block mb-1">Database Tables</span>
            <span className="text-xl font-bold text-sky-400">{totalTables} Tables</span>
          </div>

          <div className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-400 block mb-1">Researched by AI</span>
            <span className="text-xl font-bold text-emerald-400">{totalResearched} / {totalRoutines}</span>
          </div>

          <div className="bg-slate-950/60 p-3.5 rounded-xl border border-slate-800 col-span-2">
            <div className="flex items-center justify-between text-xs mb-1.5">
              <span className="text-slate-400 font-medium">Research Completion</span>
              <span className="text-emerald-400 font-bold">{overallPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2.5 rounded-full overflow-hidden">
              <div
                className="bg-gradient-to-r from-amber-500 to-emerald-400 h-full rounded-full transition-all duration-500"
                style={{ width: `${overallPercentage}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Schema Filter & Search Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="relative w-full sm:w-80">
          <input
            type="text"
            placeholder="Search schemas (e.g., ARS, TRANSLATOR, GL)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50 focus:ring-1 focus:ring-amber-500/50"
          />
        </div>
        <div className="flex items-center space-x-2 text-xs text-slate-400">
          <span>Targeting Oracle OLTP Instances:</span>
          <span className="font-mono text-slate-200 bg-slate-900 px-2 py-1 rounded border border-slate-800">DEVOLTP (10.229.2.57)</span>
        </div>
      </div>

      {/* Schemas Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredSchemas.map((schema) => {
          const pct = Math.round((schema.researchedCount / schema.totalRoutines) * 100);
          return (
            <div
              key={schema.schemaName}
              className="bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-xl p-5 transition-all shadow hover:shadow-lg flex flex-col justify-between"
            >
              <div>
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <div className="flex items-center space-x-2">
                      <h3 className="text-lg font-bold text-white tracking-wide">{schema.schemaName}</h3>
                      <span className="text-[11px] font-mono text-slate-400 bg-slate-950 px-2 py-0.5 rounded border border-slate-800">
                        {schema.databaseName}
                      </span>
                    </div>
                    <span className="text-xs text-slate-400 mt-0.5 block">
                      {schema.totalRoutines} routines • {schema.tablesCount} tables
                    </span>
                  </div>

                  <span
                    className={`text-[11px] font-semibold px-2.5 py-1 rounded-full flex items-center space-x-1 ${
                      schema.status === 'COMPLETE'
                        ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-800/40'
                        : 'bg-amber-950/60 text-amber-400 border border-amber-800/40'
                    }`}
                  >
                    {schema.status === 'COMPLETE' ? (
                      <>
                        <CheckCircle2 className="w-3 h-3" />
                        <span>100% Done</span>
                      </>
                    ) : (
                      <>
                        <Clock className="w-3 h-3" />
                        <span>{pct}% Researched</span>
                      </>
                    )}
                  </span>
                </div>

                {/* Breakdown Pills */}
                <div className="grid grid-cols-4 gap-2 text-center my-3 text-[11px]">
                  <div className="bg-slate-950/80 p-2 rounded border border-slate-800/80">
                    <span className="text-slate-500 block text-[10px]">Procedures</span>
                    <span className="font-bold text-slate-200">{schema.procsCount}</span>
                  </div>
                  <div className="bg-slate-950/80 p-2 rounded border border-slate-800/80">
                    <span className="text-slate-500 block text-[10px]">Packages</span>
                    <span className="font-bold text-amber-300">{schema.pkgsCount}</span>
                  </div>
                  <div className="bg-slate-950/80 p-2 rounded border border-slate-800/80">
                    <span className="text-slate-500 block text-[10px]">Functions</span>
                    <span className="font-bold text-sky-300">{schema.funcsCount}</span>
                  </div>
                  <div className="bg-slate-950/80 p-2 rounded border border-slate-800/80">
                    <span className="text-slate-500 block text-[10px]">Tables</span>
                    <span className="font-bold text-emerald-300">{schema.tablesCount}</span>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="mt-3">
                  <div className="flex items-center justify-between text-[11px] text-slate-400 mb-1">
                    <span>AI Research Progress</span>
                    <span className="font-mono text-slate-300">{schema.researchedCount} / {schema.totalRoutines}</span>
                  </div>
                  <div className="w-full bg-slate-950 h-2 rounded-full overflow-hidden border border-slate-800">
                    <div
                      className="bg-amber-500 h-full rounded-full transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="grid grid-cols-2 gap-2 mt-4 pt-3 border-t border-slate-800/80">
                <button
                  onClick={() => onSelectSchema(schema.schemaName)}
                  className="flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-all cursor-pointer border border-slate-700"
                >
                  <Database className="w-3.5 h-3.5 text-sky-400" />
                  <span>Inspect Procs</span>
                </button>

                <button
                  onClick={() => onOpenAiResearch(schema.schemaName)}
                  className="flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 text-xs font-semibold transition-all cursor-pointer border border-amber-500/30"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Research AI</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
