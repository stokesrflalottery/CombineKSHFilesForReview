import React, { useState } from 'react';
import { SAMPLE_PROCEDURES } from '../data/sampleProcedures';
import { FLORIDA_LOTTERY_SCHEMAS, TARGET_SCHEMAS_LIST } from '../data/schemasData';
import { generateTsvRowForResearchedData, parseStoredProcedureSql } from '../services/sqlParser';
import { Sparkles, Copy, Check, Terminal, FileCode, Play, Database, Layers, ArrowRight, Download } from 'lucide-react';

export const AiResearchWorkspaceView: React.FC<{ defaultSchema?: string }> = ({ defaultSchema = 'TRANSLATOR' }) => {
  const [selectedSchema, setSelectedSchema] = useState(defaultSchema);
  const [copiedPrompt, setCopiedPrompt] = useState(false);
  const [copiedTsv, setCopiedTsv] = useState(false);
  const [customSql, setCustomSql] = useState('');
  const [generatedTsvResults, setGeneratedTsvResults] = useState<string[]>([]);
  const [isProcessing, setIsProcessing] = useState(false);

  const masterPromptText = `You are an expert Oracle PL/SQL database architect and technical documenter.
We are populating an Excel inventory of legacy database routines (schema: ${selectedSchema} on DEVOLTP).

I will provide a batch of database routines in JSON format.
Your task is to analyze the PL/SQL and SQL logic for each routine and output EXACTLY SEVEN columns formatted as a clean, Tab-Separated (TSV) block that can be directly pasted into the 'Researched Data' tab starting at Column A.

### Required Columns (Tab-Separated):
\`Lookup Key\` <TAB> \`Schema\` <TAB> \`Package\` <TAB> \`Object Name\` <TAB> \`Description\` <TAB> \`Parameters\` <TAB> \`Dependencies\`

### Processing Rules:
1. **Lookup Key:** Build as \`SCHEMA.PACKAGE.OBJECT\` (uppercase). If standalone procedure/function, use \`-\` for package: \`SCHEMA.-.OBJECT\`.
2. **Schema:** The schema name in UPPERCASE (e.g., ${selectedSchema}).
3. **Package:** The package name in UPPERCASE, or \`-\` if standalone.
4. **Object Name:** The procedure/function/method name in UPPERCASE, or \`[PACKAGE SPECIFICATION/BODY]\` for package-level bodies.
5. **Description (What it does):**
   - 2 to 3 concise, professional sentences explaining the business purpose, execution/batch context, and core logic/transformations.
   - STRICT EXCEL RULE: The description MUST be on a single continuous line. Do NOT include line breaks (\\n, \\r), hard returns, bullet points, or tab characters.
6. **Parameters:**
   - List all arguments: \`param1 IN VARCHAR2, param2 OUT NUMBER\` (or \`None\` if parameterless).
7. **Dependencies:**
   - Format: Reads: [TABLE1, TABLE2] | Writes: [TABLE3] | Calls: [PKG.PROC or None]
8. **Clean Formatting Rule:** Do NOT include citation markers, footnotes, or internal bracket tags.

### Output Constraints:
- Use a single literal TAB character between columns on each line.
- Wrap fields in double quotes if they contain commas or quotes.
- Exactly ONE line per routine. No extra blank lines.
- Output ONLY the raw block inside a single \`\`\`tsv code block (do NOT include a header row).

Here is the JSON batch to analyze:`;

  const handleCopyPrompt = () => {
    navigator.clipboard.writeText(masterPromptText);
    setCopiedPrompt(true);
    setTimeout(() => setCopiedPrompt(false), 2500);
  };

  const handleRunBatchAiResearch = () => {
    setIsProcessing(true);
    setTimeout(() => {
      let results: string[] = [];
      if (customSql.trim()) {
        const parsed = parseStoredProcedureSql(customSql, 'ORACLE', selectedSchema);
        results.push(generateTsvRowForResearchedData(parsed));
      } else {
        // Run on all sample routines
        results = SAMPLE_PROCEDURES.map(p => generateTsvRowForResearchedData(p));
      }
      setGeneratedTsvResults(results);
      setIsProcessing(false);
    }, 400);
  };

  const handleCopyAllTsv = () => {
    if (generatedTsvResults.length === 0) return;
    navigator.clipboard.writeText(generatedTsvResults.join('\n'));
    setCopiedTsv(true);
    setTimeout(() => setCopiedTsv(false), 2500);
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2 text-amber-400 text-xs font-semibold uppercase tracking-wider mb-1">
            <Sparkles className="w-4 h-4" />
            <span>AI Research Assistant & Prompt Engineer</span>
          </div>
          <h2 className="text-2xl font-extrabold text-white tracking-tight">
            Stored Procedure AI Reverse-Engineering Studio
          </h2>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Generate standardized 7-column TSV data blocks compliant with Ryan Stokes's <span className="text-amber-300 font-mono">Database_Routines_Inventory.xlsx</span> specification.
          </p>
        </div>

        {/* Schema Selector */}
        <div className="flex items-center space-x-2 bg-slate-950 p-2 rounded-xl border border-slate-800">
          <span className="text-xs text-slate-400 font-medium pl-1">Target Schema:</span>
          <select
            value={selectedSchema}
            onChange={(e) => setSelectedSchema(e.target.value)}
            className="bg-slate-900 text-amber-400 font-bold font-mono text-xs rounded-lg px-2.5 py-1.5 border border-slate-700 focus:outline-none focus:border-amber-500 cursor-pointer"
          >
            {TARGET_SCHEMAS_LIST.map(s => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column: Master AI System Prompt & Rules */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center space-x-2">
                <FileCode className="w-4 h-4 text-amber-400" />
                <h3 className="text-base font-bold text-white">Master AI System Prompt</h3>
              </div>
              <button
                onClick={handleCopyPrompt}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition-all shadow cursor-pointer"
              >
                {copiedPrompt ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedPrompt ? 'Prompt Copied!' : 'Copy Master Prompt'}</span>
              </button>
            </div>
            <p className="text-xs text-slate-400 mb-3">
              Copy and paste this exact prompt into your AI model along with any chunk JSON file (<span className="text-amber-300 font-mono">&lt;450 KB</span>).
            </p>

            <pre className="bg-slate-950 p-4 rounded-xl text-slate-300 font-mono text-[11px] leading-relaxed border border-slate-800 max-h-[380px] overflow-y-auto whitespace-pre-wrap select-all">
              {masterPromptText}
            </pre>
          </div>

          <div className="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800 text-xs space-y-1">
            <span className="font-bold text-amber-300 block">Workflow Reminder:</span>
            <span className="text-slate-400">
              1. Copy Prompt • 2. Attach Chunk JSON • 3. Copy TSV Output • 4. Paste in 'Researched Data' Tab.
            </span>
          </div>
        </div>

        {/* Right Column: Live SQL/Routine TSV Generator Sandbox */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col justify-between space-y-4">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Terminal className="w-4 h-4 text-sky-400" />
                <h3 className="text-base font-bold text-white">Instant Reverse-Engineering Sandbox</h3>
              </div>
              <button
                onClick={handleRunBatchAiResearch}
                disabled={isProcessing}
                className="flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-bold transition-all shadow cursor-pointer disabled:opacity-50"
              >
                <Play className="w-3.5 h-3.5" />
                <span>{isProcessing ? 'Analyzing...' : 'Generate 7-Col TSV'}</span>
              </button>
            </div>

            <p className="text-xs text-slate-400">
              Paste custom PL/SQL procedure code below or click "Generate 7-Col TSV" to test with enterprise Florida Lottery sample routines.
            </p>

            <textarea
              rows={6}
              placeholder="Paste Oracle PL/SQL or T-SQL code here (optional)..."
              value={customSql}
              onChange={(e) => setCustomSql(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-xs text-sky-300 font-mono placeholder-slate-600 focus:outline-none focus:border-sky-500"
            />
          </div>

          {/* TSV Output Area */}
          {generatedTsvResults.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-400 flex items-center space-x-1.5">
                  <Check className="w-3.5 h-3.5" />
                  <span>Generated TSV Block ({generatedTsvResults.length} Lines)</span>
                </span>
                <button
                  onClick={handleCopyAllTsv}
                  className="flex items-center space-x-1 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 cursor-pointer"
                >
                  {copiedTsv ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                  <span>{copiedTsv ? 'Copied' : 'Copy All TSV'}</span>
                </button>
              </div>

              <pre className="bg-slate-950 p-3 rounded-xl text-emerald-300 font-mono text-xs overflow-x-auto border border-emerald-800/40 max-h-[160px] whitespace-pre">
                {generatedTsvResults.join('\n')}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
