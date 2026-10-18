import React from 'react';
import { Terminal, ShieldCheck } from 'lucide-react';

export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6 font-mono selection:bg-amber-500 selection:text-slate-950">
      <div className="max-w-xl w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl space-y-5">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-lg bg-amber-500 text-slate-950 font-black flex items-center justify-center text-sm shadow">
              FL
            </div>
            <div>
              <h1 className="text-sm font-bold text-white tracking-wide">Florida Lottery ETL Engine</h1>
              <p className="text-[11px] text-slate-400">Java 21 / Spring Boot 3.4.0 CLI Monolith</p>
            </div>
          </div>
          <div className="flex items-center space-x-1.5 text-xs text-amber-400 bg-amber-950/40 border border-amber-800/40 px-2.5 py-1 rounded-full">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
            <span>Headless Mode</span>
          </div>
        </div>

        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-xs text-slate-300 space-y-2">
          <div className="flex items-center space-x-2 text-slate-400">
            <Terminal className="w-4 h-4 text-sky-400" />
            <span>Unattended Batch CLI Target</span>
          </div>
          <p className="text-[11px] text-slate-400 leading-relaxed">
            Web preview UI is disabled per architect instructions. All ETL batch jobs and stored procedure inventory extractors execute via CLI switches under IBM Workload Scheduler (IWS).
          </p>
        </div>

        <div className="text-[11px] text-slate-500 flex items-center justify-between pt-2">
          <span>Target Instance: DEVOLTP</span>
          <span>Status: Standby for IWS Trigger</span>
        </div>
      </div>
    </div>
  );
}
