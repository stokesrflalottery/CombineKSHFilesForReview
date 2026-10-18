import React, { useState } from 'react';
import { ENTERPRISE_JAVA_FILES, JavaFileItem } from '../data/javaSourceFiles';
import { Code2, Copy, Check, Download, FileCode, CheckCircle2, ShieldCheck, FolderTree, Cpu } from 'lucide-react';

export const JavaArchitectureHubView: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [selectedFile, setSelectedFile] = useState<JavaFileItem>(ENTERPRISE_JAVA_FILES[0]);
  const [copied, setCopied] = useState(false);

  const handleCopyCode = () => {
    navigator.clipboard.writeText(selectedFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadFile = () => {
    const blob = new Blob([selectedFile.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = selectedFile.name;
    a.click();
    URL.revokeObjectURL(url);
  };

  const filteredFiles = ENTERPRISE_JAVA_FILES.filter((file: JavaFileItem) => {
    const matchesSearch = file.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          file.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          file.description.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCat = selectedCategory === 'ALL' || file.category === selectedCategory;
    return matchesSearch && matchesCat;
  });

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2 text-emerald-400 text-xs font-semibold uppercase tracking-wider mb-1">
            <Cpu className="w-4 h-4" />
            <span>Java 21 • Spring Boot 3.4.0 • Enterprise Architecture Repository</span>
          </div>
          <h2 className="text-2xl font-extrabold text-white tracking-tight">
            Production Java ETL Code Repository
          </h2>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Pristine, 100% compliant Java source classes matching Florida Lottery architecture guidelines: Template Method Pattern (<span className="text-amber-300 font-mono">AbstractJob</span>), Fluent DSL, Zero-YAML config (<span className="text-amber-300 font-mono">ServiceConfigDispatcher</span>), and Explicit Return Variables.
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={handleCopyCode}
            className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition-all shadow cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied to Clipboard!' : 'Copy Active Java Class'}</span>
          </button>

          <button
            onClick={handleDownloadFile}
            className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-all border border-slate-700 cursor-pointer"
          >
            <Download className="w-4 h-4 text-sky-400" />
            <span>Download .java</span>
          </button>
        </div>
      </div>

      {/* Main Grid: File Tree + Code Editor Viewer */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Sidebar: Package Explorer */}
        <div className="lg:col-span-4 bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-slate-800">
            <div className="flex items-center space-x-2 text-xs font-bold text-slate-300">
              <FolderTree className="w-4 h-4 text-amber-400" />
              <span>Package Explorer (`com.fllottery`)</span>
            </div>
            <span className="text-[11px] font-mono text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800/60 font-semibold">
              {filteredFiles.length} files
            </span>
          </div>

          {/* Search & Filter */}
          <div className="space-y-2">
            <input
              type="text"
              placeholder="Search classes or packages..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50"
            />
            <div className="flex gap-1 overflow-x-auto pb-1">
              {['ALL', 'UTIL', 'SERVICE', 'JOB'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`text-[10px] font-bold px-2 py-1 rounded-lg border transition-all ${
                    selectedCategory === cat
                      ? 'bg-amber-500/20 border-amber-500/50 text-amber-300'
                      : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-1.5 max-h-[480px] overflow-y-auto pr-1">
            {filteredFiles.map((file: JavaFileItem) => (
              <button
                key={file.name}
                onClick={() => setSelectedFile(file)}
                className={`w-full text-left p-2.5 rounded-xl transition-all cursor-pointer border ${
                  selectedFile.name === file.name
                    ? 'bg-amber-500/10 border-amber-500/40 text-amber-300 shadow'
                    : 'bg-slate-950/60 border-slate-800 text-slate-300 hover:bg-slate-800/60 hover:text-white'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono font-bold text-xs truncate max-w-[200px]">{file.name}</span>
                  <span className="text-[9px] px-1.5 py-0.2 rounded font-bold uppercase bg-slate-900 text-slate-400 border border-slate-800">
                    {file.category}
                  </span>
                </div>
                <span className="text-[10px] text-slate-500 font-mono block truncate">{file.path}</span>
              </button>
            ))}
            {filteredFiles.length === 0 && (
              <div className="text-center py-6 text-xs text-slate-500">
                No matching Java files found.
              </div>
            )}
          </div>

          {/* Architectural Compliance Checklist */}
          <div className="mt-4 pt-4 border-t border-slate-800 space-y-2 text-xs">
            <span className="font-bold text-slate-200 block">Governance Checks:</span>
            <div className="flex items-center space-x-2 text-emerald-400">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>Extends AbstractJob (Template Method)</span>
            </div>
            <div className="flex items-center space-x-2 text-emerald-400">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>Zero-YAML Config (doc-report_config)</span>
            </div>
            <div className="flex items-center space-x-2 text-emerald-400">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>Explicit Return Variables Enforced</span>
            </div>
            <div className="flex items-center space-x-2 text-emerald-400">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>Failsafe Logging (BlackboxUtil)</span>
            </div>
          </div>
        </div>

        {/* Right Code Display */}
        <div className="lg:col-span-8 bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800">
            <div>
              <div className="flex items-center space-x-2">
                <FileCode className="w-4 h-4 text-emerald-400" />
                <h3 className="font-mono font-bold text-white text-sm">{selectedFile.path}</h3>
              </div>
              <p className="text-xs text-slate-400 mt-1">{selectedFile.description}</p>
            </div>
          </div>

          <pre className="bg-slate-950 p-4 rounded-xl text-slate-200 font-mono text-xs leading-relaxed overflow-x-auto border border-slate-800 max-h-[620px] select-all">
            {selectedFile.code}
          </pre>
        </div>
      </div>
    </div>
  );
};
