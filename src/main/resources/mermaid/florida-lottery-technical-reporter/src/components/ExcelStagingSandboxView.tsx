import React, { useState } from 'react';
import { FLORIDA_LOTTERY_SCHEMAS } from '../data/schemasData';
import { SAMPLE_PROCEDURES } from '../data/sampleProcedures';
import { generateTsvRowForResearchedData } from '../services/sqlParser';
import { FileSpreadsheet, Download, Plus, Copy, Check, Filter, Search, Sparkles, Database, Layers } from 'lucide-react';

export interface ResearchedRow {
  lookupKey: string;
  schema: string;
  packageName: string;
  objectName: string;
  description: string;
  parameters: string;
  dependencies: string;
}

export const ExcelStagingSandboxView: React.FC = () => {
  const [activeSheet, setActiveSheet] = useState<'report' | 'researched' | 'all' | 'ars' | 'translator'>('report');
  const [searchTerm, setSearchTerm] = useState('');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Initial researched staging rows matching Ryan's 7-column Excel layout
  const [researchedRows, setResearchedRows] = useState<ResearchedRow[]>([
    {
      lookupKey: 'ARS.-.SP_ARS_RETAILER_DAILY_SETTLEMENT',
      schema: 'ARS',
      packageName: '-',
      objectName: 'SP_ARS_RETAILER_DAILY_SETTLEMENT',
      description: 'Performs nightly automated settlement calculation for all active Florida Lottery retail locations. Aggregates draw sales, instant ticket pack activations, validation redemptions, and commission adjustments to compute net EFT sweep amounts.',
      parameters: 'p_accounting_date IN DATE, p_retailer_category IN VARCHAR2, p_force_recalculation IN NUMBER, p_batch_id OUT NUMBER, p_processed_count OUT NUMBER, p_total_eft_amount OUT NUMBER, p_status_code OUT VARCHAR2',
      dependencies: 'Reads: [VW_ACTIVE_LICENSED_RETAILERS, GAMING_DRAW_TRANSACTIONS, INSTANT_PACK_SETTLEMENT] | Writes: [ARS_EFT_SWEEP_STAGING, ARS_RETAILER_LEDGER, ARS_SETTLEMENT_AUDIT_LOG] | Calls: [PKG_ARS_COMMISSION_CALC.CALCULATE_BONUS]'
    },
    {
      lookupKey: 'ARS.-.SP_GAMING_SCRATCH_INVENTORY_SYNC',
      schema: 'ARS',
      packageName: '-',
      objectName: 'SP_GAMING_SCRATCH_INVENTORY_SYNC',
      description: 'Synchronizes Scientific Games (SGI) warehouse consignment manifest records with Florida Lottery local central inventory balances. Updates pack status from RECEIVED to CONFIRMED, ACTIVE, or SETTLED.',
      parameters: 'p_game_number IN NUMBER, p_manifest_date IN DATE, p_packs_synchronized OUT NUMBER, p_discrepancy_count OUT NUMBER',
      dependencies: 'Reads: [SCRATCH_GAME_CATALOG, WAREHOUSE_CONSIGNMENT_LOG] | Writes: [SCRATCH_PACK_INVENTORY, SCRATCH_SECURITY_DISCREPANCIES] | Calls: [PKG_BARCODE_CIPHER.VERIFY_SECURITY_CHECKSUM]'
    },
    {
      lookupKey: 'TRANSLATOR.PKG_TRANS_CORE.PARSE_WAGERING_PACKET',
      schema: 'TRANSLATOR',
      packageName: 'PKG_TRANS_CORE',
      objectName: 'PARSE_WAGERING_PACKET',
      description: 'Deconstructs raw binary transaction streams from central gaming terminals into normalized lottery wager records.',
      parameters: 'p_raw_stream IN BLOB, p_transaction_id OUT VARCHAR2, p_game_id OUT NUMBER',
      dependencies: 'Reads: [RAW_STREAM_BUFFERS] | Writes: [TRANSACTION_STAGING] | Calls: [PKG_CRYPTO_TOKEN.VALIDATE]'
    },
    {
      lookupKey: 'GL.PKG_GENERAL_LEDGER.POST_DAILY_JOURNAL_VOUCHERS',
      schema: 'GL',
      packageName: 'PKG_GENERAL_LEDGER',
      objectName: 'POST_DAILY_JOURNAL_VOUCHERS',
      description: 'Generates double-entry general ledger voucher lines for statutory Florida State Treasury and sales receipts.',
      parameters: 'p_posting_date IN DATE, p_voucher_count OUT NUMBER, p_balance_check OUT VARCHAR2',
      dependencies: 'Reads: [ARS_RETAILER_LEDGER, PRIZE_PAYOUT_LOG] | Writes: [GL_JOURNAL_ENTRIES] | Calls: [None]'
    }
  ]);

  const [newTsvInput, setNewTsvInput] = useState('');

  const handlePasteTsv = () => {
    if (!newTsvInput.trim()) return;
    const lines = newTsvInput.trim().split(/\r?\n/);
    const newItems: ResearchedRow[] = [];

    for (const line of lines) {
      const parts = line.split('\t');
      if (parts.length >= 7) {
        newItems.push({
          lookupKey: parts[0].trim(),
          schema: parts[1].trim(),
          packageName: parts[2].trim(),
          objectName: parts[3].trim(),
          description: parts[4].replace(/^"|"$/g, '').trim(),
          parameters: parts[5].replace(/^"|"$/g, '').trim(),
          dependencies: parts[6].replace(/^"|"$/g, '').trim()
        });
      }
    }

    if (newItems.length > 0) {
      setResearchedRows(prev => [...prev, ...newItems]);
      setNewTsvInput('');
    }
  };

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const schemas = Object.values(FLORIDA_LOTTERY_SCHEMAS);
  const totalRoutines = schemas.reduce((acc, s) => acc + s.totalRoutines, 0);
  const totalResearched = schemas.reduce((acc, s) => acc + s.researchedCount, 0);

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2 text-emerald-400 text-xs font-semibold uppercase tracking-wider mb-1">
            <FileSpreadsheet className="w-4 h-4" />
            <span>Excel Multi-Tab Workbook Simulator • Database_Routines_Inventory.xlsx</span>
          </div>
          <h2 className="text-2xl font-extrabold text-white tracking-tight">
            Excel Catalog & TSV Staging Sandbox
          </h2>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl">
            Live simulation of Apache POI workbook structure. Demonstrates dynamic <span className="text-amber-300 font-mono">VLOOKUP</span> formula linkage from schema tabs into the central <span className="text-sky-300 font-mono">'Researched Data'</span> staging sheet.
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={() => {
              const allTsv = researchedRows.map(r =>
                `${r.lookupKey}\t${r.schema}\t${r.packageName}\t${r.objectName}\t"${r.description}"\t"${r.parameters}"\t"${r.dependencies}"`
              ).join('\n');
              const blob = new Blob([allTsv], { type: 'text/tab-separated-values' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = 'Researched_Data_Staging.tsv';
              a.click();
              URL.revokeObjectURL(url);
            }}
            className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition-all shadow cursor-pointer"
          >
            <Download className="w-4 h-4" />
            <span>Export Researched TSV</span>
          </button>
        </div>
      </div>

      {/* Excel Sheet Tabs Toolbar */}
      <div className="bg-slate-950 p-2 rounded-xl border border-slate-800 flex items-center space-x-1 overflow-x-auto">
        <button
          onClick={() => setActiveSheet('report')}
          className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
            activeSheet === 'report'
              ? 'bg-slate-800 text-white border border-slate-700 shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <span className="w-2 h-2 rounded-full bg-amber-400" />
          <span>Research Report (Tab 1)</span>
        </button>

        <button
          onClick={() => setActiveSheet('researched')}
          className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
            activeSheet === 'researched'
              ? 'bg-teal-900/60 text-teal-300 border border-teal-700 shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <span className="w-2 h-2 rounded-full bg-teal-400" />
          <span>Researched Data (Tab 2 - Staging)</span>
          <span className="px-1.5 py-0.2 rounded bg-teal-950 text-teal-300 text-[10px] font-mono">
            {researchedRows.length}
          </span>
        </button>

        <button
          onClick={() => setActiveSheet('all')}
          className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
            activeSheet === 'all'
              ? 'bg-blue-900/60 text-blue-300 border border-blue-700 shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <span className="w-2 h-2 rounded-full bg-blue-400" />
          <span>ALL_OBJECTS (Tab 3)</span>
        </button>

        <button
          onClick={() => setActiveSheet('ars')}
          className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
            activeSheet === 'ars'
              ? 'bg-purple-900/60 text-purple-300 border border-purple-700 shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <span>ARS (Schema Tab)</span>
        </button>

        <button
          onClick={() => setActiveSheet('translator')}
          className={`flex items-center space-x-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
            activeSheet === 'translator'
              ? 'bg-purple-900/60 text-purple-300 border border-purple-700 shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          <span>TRANSLATOR (Schema Tab)</span>
        </button>
      </div>

      {/* TAB CONTENT: RESEARCH REPORT */}
      {activeSheet === 'report' && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-6">
          <div>
            <h3 className="text-base font-bold text-white mb-1">Section 1: Routines Inventory & Progress</h3>
            <p className="text-xs text-slate-400">
              Calculated dynamically via Excel formulas: <code className="text-amber-300 font-mono bg-slate-950 px-1.5 py-0.5 rounded">COUNTIFS(SCHEMA_TYPE, "&lt;&gt;TABLE", SCHEMA_DESC, "?*")</code>
            </p>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-slate-950 text-slate-400 uppercase font-semibold border-b border-slate-800">
                <tr>
                  <th className="p-3">Schema Name</th>
                  <th className="p-3">Progress Ratio</th>
                  <th className="p-3">% Researched</th>
                  <th className="p-3">Visual Progress</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 font-mono">
                {schemas.map((s) => {
                  const pct = Math.round((s.researchedCount / s.totalRoutines) * 100);
                  return (
                    <tr key={s.schemaName} className="hover:bg-slate-950/60 transition-colors">
                      <td className="p-3 font-bold text-white">{s.schemaName}</td>
                      <td className="p-3 text-slate-300">{s.researchedCount} / {s.totalRoutines}</td>
                      <td className="p-3 font-bold text-emerald-400">{pct}%</td>
                      <td className="p-3 w-48">
                        <div className="w-full bg-slate-950 h-2 rounded-full overflow-hidden border border-slate-800">
                          <div className="bg-gradient-to-r from-amber-500 to-emerald-400 h-full rounded-full" style={{ width: `${pct}%` }} />
                        </div>
                      </td>
                    </tr>
                  );
                })}
                <tr className="bg-slate-950 font-bold border-t-2 border-slate-700">
                  <td className="p-3 text-amber-300">TOTAL</td>
                  <td className="p-3 text-white">{totalResearched} / {totalRoutines}</td>
                  <td className="p-3 text-emerald-400 font-extrabold">{((totalResearched / totalRoutines) * 100).toFixed(1)}%</td>
                  <td className="p-3">
                    <div className="w-full bg-slate-900 h-2.5 rounded-full overflow-hidden border border-slate-700">
                      <div className="bg-emerald-400 h-full rounded-full" style={{ width: `${(totalResearched / totalRoutines) * 100}%` }} />
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB CONTENT: RESEARCHED DATA (STAGING TAB) */}
      {activeSheet === 'researched' && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <div className="flex items-center space-x-2">
                <span className="w-3 h-3 rounded-full bg-teal-400" />
                <h3 className="text-base font-bold text-white">Researched Data (Central AI Staging Sheet)</h3>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Every line pasted here automatically populates matching rows across all schema sheets using VLOOKUP on Column A (`Lookup Key`).
              </p>
            </div>
          </div>

          {/* Quick TSV Paste Box */}
          <div className="bg-slate-950 p-4 rounded-xl border border-teal-500/30 space-y-2">
            <label className="text-xs font-bold text-teal-300 block">
              Paste New TSV Chunk Block from AI:
            </label>
            <textarea
              rows={3}
              placeholder={`Paste 7-column TSV lines here...\nExample:\nARS.-.SP_ARS_DAILY_SETTLE\tARS\t-\tSP_ARS_DAILY_SETTLE\t"Business description"\t"params"\t"deps"`}
              value={newTsvInput}
              onChange={(e) => setNewTsvInput(e.target.value)}
              className="w-full bg-slate-900 border border-slate-800 rounded-lg p-2.5 text-xs text-slate-200 font-mono placeholder-slate-600 focus:outline-none focus:border-teal-500"
            />
            <button
              onClick={handlePasteTsv}
              className="px-4 py-2 rounded-lg bg-teal-600 hover:bg-teal-500 text-white text-xs font-bold transition-all shadow cursor-pointer"
            >
              + Ingest TSV Rows into Staging Tab
            </button>
          </div>

          {/* Staging Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-teal-950/80 text-teal-200 uppercase font-semibold border-b border-teal-800">
                <tr>
                  <th className="p-3">Lookup Key (Col A)</th>
                  <th className="p-3">Schema (B)</th>
                  <th className="p-3">Package (C)</th>
                  <th className="p-3">Object Name (D)</th>
                  <th className="p-3">Description (E)</th>
                  <th className="p-3">Parameters (F)</th>
                  <th className="p-3">Dependencies (G)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 font-mono">
                {researchedRows.map((r, i) => (
                  <tr key={i} className="hover:bg-slate-950/60 transition-colors">
                    <td className="p-3 font-bold text-amber-300 whitespace-nowrap">{r.lookupKey}</td>
                    <td className="p-3 text-slate-300">{r.schema}</td>
                    <td className="p-3 text-purple-300">{r.packageName}</td>
                    <td className="p-3 text-white font-bold">{r.objectName}</td>
                    <td className="p-3 text-slate-300 font-sans min-w-[280px]">{r.description}</td>
                    <td className="p-3 text-sky-300 min-w-[220px]">{r.parameters}</td>
                    <td className="p-3 text-emerald-300 min-w-[240px]">{r.dependencies}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB CONTENT: ALL_OBJECTS & SCHEMA TABS WITH VLOOKUP SIMULATION */}
      {(activeSheet === 'all' || activeSheet === 'ars' || activeSheet === 'translator') && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-4">
          <div>
            <h3 className="text-base font-bold text-white">
              {activeSheet === 'all' ? 'Master ALL_OBJECTS Tab' : `${activeSheet.toUpperCase()} Schema Tab`}
            </h3>
            <p className="text-xs text-slate-400">
              Columns E, F, G are dynamically populated via Excel formula: <code className="text-amber-300 font-mono bg-slate-950 px-1 py-0.5 rounded">IFERROR(VLOOKUP(UPPER(B2 & "." & C2 & "." & D2), 'Researched Data'!$A$2:$G$10000, 5, FALSE), "")</code>
            </p>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead className="bg-slate-950 text-slate-400 uppercase font-semibold border-b border-slate-800">
                <tr>
                  <th className="p-3">DB</th>
                  <th className="p-3">Schema</th>
                  <th className="p-3">Package</th>
                  <th className="p-3">Object Name</th>
                  <th className="p-3">Type</th>
                  <th className="p-3 text-amber-400">Description (VLOOKUP)</th>
                  <th className="p-3 text-sky-400">Parameters (VLOOKUP)</th>
                  <th className="p-3 text-emerald-400">Dependencies (VLOOKUP)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800 font-mono">
                {SAMPLE_PROCEDURES.map((p, i) => {
                  const key = `${p.schema.toUpperCase()}.${p.packageName ? p.packageName.toUpperCase() : '-'}.${p.name.toUpperCase()}`;
                  const match = researchedRows.find(r => r.lookupKey.toUpperCase() === key);

                  return (
                    <tr key={i} className="hover:bg-slate-950/60 transition-colors">
                      <td className="p-3 text-slate-400">DEVOLTP</td>
                      <td className="p-3 font-bold text-white">{p.schema}</td>
                      <td className="p-3 text-purple-300">{p.packageName || '-'}</td>
                      <td className="p-3 text-amber-300 font-bold">{p.name}</td>
                      <td className="p-3">
                        <span className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 text-[10px]">
                          PROCEDURE
                        </span>
                      </td>
                      <td className="p-3 font-sans text-slate-300 min-w-[280px]">
                        {match ? match.description : <span className="text-slate-600 italic">[Pending AI Research]</span>}
                      </td>
                      <td className="p-3 text-sky-300 min-w-[200px]">
                        {match ? match.parameters : <span className="text-slate-600 italic">-</span>}
                      </td>
                      <td className="p-3 text-emerald-300 min-w-[220px]">
                        {match ? match.dependencies : <span className="text-slate-600 italic">-</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
