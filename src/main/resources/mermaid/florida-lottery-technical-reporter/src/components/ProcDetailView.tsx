import React, { useState } from 'react';
import { StoredProcedureDoc } from '../types/proc';
import { generateMarkdownTechnicalReport, generateHtmlLivingDoc } from '../services/reportGenerator';
import { generateTsvRowForResearchedData } from '../services/sqlParser';
import {
  FileText,
  Table,
  Database,
  GitFork,
  AlertTriangle,
  Code2,
  Copy,
  Check,
  Download,
  Share2,
  Sparkles,
  ExternalLink,
  ShieldAlert,
  Cpu
} from 'lucide-react';

interface ProcDetailViewProps {
  procedure: StoredProcedureDoc;
  onSelectAnother?: () => void;
}

export const ProcDetailView: React.FC<ProcDetailViewProps> = ({ procedure }) => {
  const [activeTab, setActiveTab] = useState<'overview' | 'params' | 'crud' | 'deps' | 'risks' | 'source' | 'java'>('overview');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleDownloadMarkdown = () => {
    const md = generateMarkdownTechnicalReport(procedure);
    const blob = new Blob([md], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${procedure.name}_TECHNICAL_SPEC.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDownloadHtml = () => {
    const html = generateHtmlLivingDoc(procedure);
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${procedure.name}_LIVING_DOC.html`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const tsvRow = generateTsvRowForResearchedData(procedure);

  return (
    <div className="space-y-6">
      {/* Procedure Header Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <div className="flex items-center space-x-2 flex-wrap gap-y-1 mb-2">
              <span className="px-2.5 py-0.5 rounded bg-sky-950 text-sky-400 font-mono text-xs font-semibold border border-sky-800/50">
                {procedure.schema}
              </span>
              {procedure.packageName && (
                <span className="px-2.5 py-0.5 rounded bg-purple-950 text-purple-400 font-mono text-xs font-semibold border border-purple-800/50">
                  {procedure.packageName}
                </span>
              )}
              <span className="px-2.5 py-0.5 rounded bg-amber-950 text-amber-400 font-mono text-xs font-semibold border border-amber-800/50">
                {procedure.dialect}
              </span>
              <span className="px-2.5 py-0.5 rounded bg-slate-800 text-slate-300 font-mono text-xs font-semibold border border-slate-700">
                {procedure.authIdType}
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold text-white font-mono tracking-tight">
              {procedure.name}
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Author: <span className="text-slate-200">{procedure.author}</span> • Last Modified: <span className="text-slate-200">{procedure.lastModifiedDate}</span> • IWS Schedule: <span className="text-amber-300 font-mono">{procedure.iwsJobSchedule || 'Manual / Triggered'}</span>
            </p>

            {/* Dynamic Operational Profile Tags */}
            <div className="flex items-center gap-1.5 flex-wrap mt-2.5">
              <span className="text-[11px] font-semibold text-slate-400 mr-0.5">Profile Tags:</span>
              {(() => {
                const tags: Array<{ label: string; color: string; desc: string }> = [];
                const rawUpper = (procedure.rawSql || '').toUpperCase();
                const hasBulk = rawUpper.includes('BULK COLLECT') || rawUpper.includes('FORALL');
                const hasUpdateDelete = procedure.crudMatrix.some(c => c.operations.some(op => ['UPDATE', 'DELETE'].includes(op)));

                if (procedure.metrics.linesOfCode > 350 || rawUpper.includes('SLEEP')) {
                  tags.push({ label: 'Long Process Time', color: 'bg-amber-950/80 text-amber-300 border-amber-800/60', desc: 'Extended batch execution cycle' });
                }
                if (procedure.crudMatrix.length > 3 || rawUpper.includes('INDEX') || rawUpper.includes('TABLE ACCESS FULL')) {
                  tags.push({ label: 'Heavy Disk Use', color: 'bg-rose-950/80 text-rose-300 border-rose-800/60', desc: 'Intensive table scan / disk read I/O' });
                }
                if (hasBulk || procedure.metrics.linesOfCode > 500) {
                  tags.push({ label: 'Heavy Data Processing', color: 'bg-blue-950/80 text-blue-300 border-blue-800/60', desc: 'Large batch volume array throughput' });
                }
                if (hasUpdateDelete && (procedure.metrics.linesOfCode > 200 || rawUpper.includes('FOR UPDATE'))) {
                  tags.push({ label: 'Heavy Record Lock Rates', color: 'bg-purple-950/80 text-purple-300 border-purple-800/60', desc: 'Row-level write locks & concurrency risk' });
                }
                if (procedure.metrics.linesOfCode < 120 && procedure.crudMatrix.length <= 2 && procedure.metrics.riskScore < 15) {
                  tags.push({ label: 'Low Resource Footprint', color: 'bg-emerald-950/80 text-emerald-300 border-emerald-800/60', desc: 'Minimal CPU/disk overhead; safe online' });
                }
                if (tags.length === 0) {
                  tags.push({ label: 'Standard Operational Profile', color: 'bg-slate-800 text-slate-300 border-slate-700', desc: 'Standard business transaction' });
                }
                return tags.map(t => (
                  <span
                    key={t.label}
                    title={t.desc}
                    className={`px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide border shadow-sm ${t.color}`}
                  >
                    🏷️ {t.label}
                  </span>
                ));
              })()}
            </div>
          </div>

          {/* Quick Action Export Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => copyToClipboard(tsvRow, 'tsv')}
              className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition-all shadow-md cursor-pointer"
              title="Copy 7-column TSV line to paste directly into Excel Researched Data tab"
            >
              {copiedKey === 'tsv' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              <span>{copiedKey === 'tsv' ? 'TSV Copied!' : 'Copy Excel TSV Line'}</span>
            </button>

            <button
              onClick={handleDownloadMarkdown}
              className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-all border border-slate-700 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5 text-sky-400" />
              <span>Export .MD Spec</span>
            </button>

            <button
              onClick={handleDownloadHtml}
              className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-all border border-slate-700 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5 text-emerald-400" />
              <span>Export .HTML Doc</span>
            </button>
          </div>
        </div>

        {/* Complexity & Health Score Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 mt-6 pt-6 border-t border-slate-800 text-xs">
          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Lines of Code</span>
            <span className="text-base font-bold text-white font-mono">{procedure.metrics.linesOfCode}</span>
            <span className="text-[10px] text-slate-500 block">({procedure.metrics.commentLines} comments)</span>
          </div>

          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Cyclomatic Complexity</span>
            <span className={`text-base font-bold font-mono ${procedure.metrics.cyclomaticComplexity > 10 ? 'text-amber-400' : 'text-emerald-400'}`}>
              {procedure.metrics.cyclomaticComplexity}
            </span>
            <span className="text-[10px] text-slate-500 block">Nesting: {procedure.metrics.nestingDepth}</span>
          </div>

          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Maintainability Index</span>
            <span className="text-base font-bold text-emerald-400 font-mono">{procedure.metrics.maintainabilityIndex} / 100</span>
          </div>

          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Parameters</span>
            <span className="text-base font-bold text-sky-400 font-mono">{procedure.parameters.length}</span>
            <span className="text-[10px] text-slate-500 block">IN: {procedure.parameters.filter(p => p.mode === 'IN').length} | OUT: {procedure.parameters.filter(p => p.mode === 'OUT').length}</span>
          </div>

          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Tables Touched</span>
            <span className="text-base font-bold text-purple-400 font-mono">{procedure.crudMatrix.length}</span>
          </div>

          <div className="bg-slate-950/70 p-3 rounded-xl border border-slate-800">
            <span className="text-slate-400 block mb-0.5">Risk Score</span>
            <span className={`text-base font-bold font-mono ${procedure.metrics.riskScore > 20 ? 'text-rose-400' : 'text-emerald-400'}`}>
              {procedure.metrics.riskScore} / 100
            </span>
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex border-b border-slate-800 overflow-x-auto gap-2">
        <button
          onClick={() => setActiveTab('overview')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'overview'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <FileText className="w-4 h-4" />
          <span>Executive Overview</span>
        </button>

        <button
          onClick={() => setActiveTab('params')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'params'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Table className="w-4 h-4" />
          <span>Parameters ({procedure.parameters.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('crud')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'crud'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Database className="w-4 h-4" />
          <span>CRUD & Lineage Matrix ({procedure.crudMatrix.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('deps')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'deps'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <GitFork className="w-4 h-4" />
          <span>Call Tree & Dependencies ({procedure.dependencies.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('risks')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'risks'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <AlertTriangle className="w-4 h-4 text-amber-400" />
          <span>Risk Audit ({procedure.riskFindings.length})</span>
        </button>

        <button
          onClick={() => setActiveTab('source')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'source'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Code2 className="w-4 h-4" />
          <span>SQL Source Code</span>
        </button>

        <button
          onClick={() => setActiveTab('java')}
          className={`flex items-center space-x-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'java'
              ? 'border-amber-500 text-amber-400 bg-slate-900/50'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Cpu className="w-4 h-4 text-emerald-400" />
          <span>Java 21 Fluent DSL</span>
        </button>
      </div>

      {/* Tab Contents */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div>
              <h3 className="text-base font-bold text-white mb-2">Business Purpose & Execution Flow</h3>
              <p className="text-sm text-slate-300 leading-relaxed bg-slate-950 p-4 rounded-xl border border-slate-800">
                {procedure.purpose}
              </p>
            </div>

            {/* Generated Excel TSV Preview Box */}
            <div className="bg-slate-950 p-4 rounded-xl border border-amber-500/30">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-bold text-amber-400 flex items-center space-x-1.5">
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Researched Data TSV Line (Excel Staging Tab Ready)</span>
                </span>
                <button
                  onClick={() => copyToClipboard(tsvRow, 'tsv-inner')}
                  className="text-xs text-slate-300 hover:text-amber-300 font-semibold flex items-center space-x-1 cursor-pointer"
                >
                  {copiedKey === 'tsv-inner' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedKey === 'tsv-inner' ? 'Copied' : 'Copy Row'}</span>
                </button>
              </div>
              <div className="bg-slate-900 p-3 rounded font-mono text-xs text-slate-300 overflow-x-auto border border-slate-800">
                {tsvRow}
              </div>
            </div>

            {/* Quick summary grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
                <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-2">Key Tables Touched</h4>
                <ul className="space-y-1.5 text-xs">
                  {procedure.crudMatrix.map((c, i) => (
                    <li key={i} className="flex items-center justify-between text-slate-300 font-mono">
                      <span>{c.tableName}</span>
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-900 text-amber-400 border border-slate-800">
                        {c.operations.join(', ')}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
                <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-2">External Call Dependencies</h4>
                {procedure.dependencies.length > 0 ? (
                  <ul className="space-y-1.5 text-xs">
                    {procedure.dependencies.map((d, i) => (
                      <li key={i} className="flex items-center justify-between text-slate-300 font-mono">
                        <span>{d.name}</span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-900 text-sky-400 border border-slate-800">
                          {d.type}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <span className="text-xs text-slate-500">No external dependencies.</span>
                )}
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: PARAMETERS */}
        {activeTab === 'params' && (
          <div className="space-y-4">
            <h3 className="text-base font-bold text-white mb-2">Technical Parameter Signatures</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400 uppercase font-semibold border-b border-slate-800">
                  <tr>
                    <th className="p-3">Parameter Name</th>
                    <th className="p-3">Mode</th>
                    <th className="p-3">Data Type</th>
                    <th className="p-3">Default</th>
                    <th className="p-3">Nullable</th>
                    <th className="p-3">Business Purpose</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 font-mono">
                  {procedure.parameters.map((p, i) => (
                    <tr key={i} className="hover:bg-slate-950/60 transition-colors">
                      <td className="p-3 font-bold text-amber-300">{p.name}</td>
                      <td className="p-3">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          p.mode === 'IN' ? 'bg-sky-950 text-sky-400' : p.mode === 'OUT' ? 'bg-emerald-950 text-emerald-400' : 'bg-purple-950 text-purple-400'
                        }`}>
                          {p.mode}
                        </span>
                      </td>
                      <td className="p-3 text-slate-300">
                        {p.dataType}
                        {p.length ? `(${p.length})` : ''}
                        {p.precision ? `(${p.precision},${p.scale || 0})` : ''}
                      </td>
                      <td className="p-3 text-slate-400">{p.defaultValue || '-'}</td>
                      <td className="p-3 text-slate-400">{p.isNullable ? 'YES' : 'NO'}</td>
                      <td className="p-3 font-sans text-slate-300">{p.businessDescription}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 3: CRUD & LINEAGE */}
        {activeTab === 'crud' && (
          <div className="space-y-4">
            <h3 className="text-base font-bold text-white mb-2">CRUD & Data Lineage Matrix</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400 uppercase font-semibold border-b border-slate-800">
                  <tr>
                    <th className="p-3">Table Name</th>
                    <th className="p-3">Schema</th>
                    <th className="p-3">Operations</th>
                    <th className="p-3">Columns Accessed</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 font-mono">
                  {procedure.crudMatrix.map((c, i) => (
                    <tr key={i} className="hover:bg-slate-950/60 transition-colors">
                      <td className="p-3 font-bold text-white">{c.tableName}</td>
                      <td className="p-3 text-slate-400">{c.schema || procedure.schema}</td>
                      <td className="p-3">
                        <div className="flex gap-1 flex-wrap">
                          {c.operations.map((op, oi) => (
                            <span
                              key={oi}
                              className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                op === 'SELECT'
                                  ? 'bg-sky-950 text-sky-400 border border-sky-800/40'
                                  : op === 'INSERT'
                                  ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/40'
                                  : op === 'UPDATE'
                                  ? 'bg-amber-950 text-amber-400 border border-amber-800/40'
                                  : 'bg-rose-950 text-rose-400 border border-rose-800/40'
                              }`}
                            >
                              {op}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="p-3 text-slate-300 font-sans text-xs">
                        {c.columnsAccessed.join(', ')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 4: DEPENDENCIES */}
        {activeTab === 'deps' && (
          <div className="space-y-4">
            <h3 className="text-base font-bold text-white mb-2">Routine Call Tree & Remote Link References</h3>
            {procedure.dependencies.length > 0 ? (
              <div className="space-y-3">
                {procedure.dependencies.map((d, i) => (
                  <div key={i} className="bg-slate-950 p-4 rounded-xl border border-slate-800 flex items-start justify-between">
                    <div>
                      <div className="flex items-center space-x-2">
                        <span className="font-mono font-bold text-amber-300 text-sm">{d.name}</span>
                        <span className="px-2 py-0.5 rounded bg-slate-900 text-sky-400 font-mono text-[10px] border border-slate-800">
                          {d.type}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-1">{d.description}</p>
                    </div>
                    <span className="text-[11px] font-mono text-slate-500 bg-slate-900 px-2 py-1 rounded border border-slate-800">
                      {d.direction}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-400">No external dependencies or package references identified.</p>
            )}
          </div>
        )}

        {/* TAB 5: RISKS */}
        {activeTab === 'risks' && (
          <div className="space-y-4">
            <h3 className="text-base font-bold text-white mb-2">Automated Risk & Anti-Pattern Detection</h3>
            {procedure.riskFindings.length > 0 ? (
              <div className="space-y-3">
                {procedure.riskFindings.map((r) => (
                  <div
                    key={r.id}
                    className={`p-4 rounded-xl border ${
                      r.severity === 'CRITICAL'
                        ? 'bg-rose-950/30 border-rose-800/60'
                        : r.severity === 'HIGH'
                        ? 'bg-orange-950/30 border-orange-800/60'
                        : 'bg-amber-950/30 border-amber-800/60'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <div className="flex items-center space-x-2">
                        <AlertTriangle className={`w-4 h-4 ${
                          r.severity === 'CRITICAL' ? 'text-rose-400' : r.severity === 'HIGH' ? 'text-orange-400' : 'text-amber-400'
                        }`} />
                        <h4 className="text-sm font-bold text-white">{r.title}</h4>
                      </div>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded font-bold uppercase bg-slate-900 text-slate-200">
                        {r.severity} • {r.category}
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 mt-1 leading-relaxed">{r.detail}</p>
                    <div className="mt-3 bg-slate-950/80 p-2.5 rounded text-xs border border-slate-800/80">
                      <span className="font-semibold text-amber-400 block mb-0.5">Action Required:</span>
                      <span className="text-slate-300">{r.recommendation}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-6 bg-emerald-950/20 border border-emerald-800/40 rounded-xl text-center">
                <span className="text-emerald-400 font-bold text-sm">✅ Zero High-Severity Risk Anti-Patterns Detected</span>
                <p className="text-xs text-slate-400 mt-1">This procedure complies with Florida Lottery transactional and error handling governance.</p>
              </div>
            )}
          </div>
        )}

        {/* TAB 6: SOURCE CODE */}
        {activeTab === 'source' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-white">Full SQL Source Text</h3>
              <button
                onClick={() => copyToClipboard(procedure.rawSql, 'raw-sql')}
                className="text-xs font-semibold text-slate-300 hover:text-white flex items-center space-x-1 cursor-pointer bg-slate-800 px-3 py-1.5 rounded-lg border border-slate-700"
              >
                {copiedKey === 'raw-sql' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedKey === 'raw-sql' ? 'Source Copied' : 'Copy SQL'}</span>
              </button>
            </div>
            <pre className="p-4 bg-slate-950 text-sky-300 font-mono text-xs rounded-xl overflow-x-auto border border-slate-800 max-h-[500px] leading-relaxed">
              {procedure.rawSql}
            </pre>
          </div>
        )}

        {/* TAB 7: JAVA 21 FLUENT DSL */}
        {activeTab === 'java' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-white">Java 21 / Spring Boot 3.4.0 Fluent Request DSL</h3>
                <p className="text-xs text-slate-400">Pure Java code block matching com.fllottery.etl.core architecture.</p>
              </div>
              <button
                onClick={() => copyToClipboard(procedure.sampleExecutionSnippet.javaFluentDsl, 'java-snippet')}
                className="text-xs font-semibold text-slate-300 hover:text-white flex items-center space-x-1 cursor-pointer bg-slate-800 px-3 py-1.5 rounded-lg border border-slate-700"
              >
                {copiedKey === 'java-snippet' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedKey === 'java-snippet' ? 'Copied' : 'Copy Java DSL'}</span>
              </button>
            </div>

            <pre className="p-4 bg-slate-950 text-emerald-300 font-mono text-xs rounded-xl overflow-x-auto border border-slate-800 leading-relaxed">
              {procedure.sampleExecutionSnippet.javaFluentDsl}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
};
