import { DatabaseDialect, StoredProcedureDoc } from '../types/proc';

export interface SchemaSummary {
  schemaName: string;
  databaseName: string;
  dboName: string;
  procsCount: number;
  pkgsCount: number;
  funcsCount: number;
  tablesCount: number;
  totalRoutines: number;
  researchedCount: number;
  status: 'COMPLETE' | 'IN_PROGRESS' | 'PENDING';
  procedures: StoredProcedureDoc[];
}

export const TARGET_SCHEMAS_LIST = [
  'ARS', 'COMMON', 'CSTS', 'DWDOL', 'GL',
  'INET2', 'PR', 'RETAILER', 'STAGE', 'TMS', 'TRANSLATOR', 'VP'
];

export const FLORIDA_LOTTERY_SCHEMAS: Record<string, SchemaSummary> = {
  ARS: {
    schemaName: 'ARS',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 48,
    pkgsCount: 12,
    funcsCount: 16,
    tablesCount: 64,
    totalRoutines: 76,
    researchedCount: 68,
    status: 'IN_PROGRESS',
    procedures: []
  },
  COMMON: {
    schemaName: 'COMMON',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 22,
    pkgsCount: 8,
    funcsCount: 34,
    tablesCount: 28,
    totalRoutines: 64,
    researchedCount: 64,
    status: 'COMPLETE',
    procedures: []
  },
  CSTS: {
    schemaName: 'CSTS',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 19,
    pkgsCount: 5,
    funcsCount: 7,
    tablesCount: 42,
    totalRoutines: 31,
    researchedCount: 18,
    status: 'IN_PROGRESS',
    procedures: []
  },
  DWDOL: {
    schemaName: 'DWDOL',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 35,
    pkgsCount: 9,
    funcsCount: 12,
    tablesCount: 88,
    totalRoutines: 56,
    researchedCount: 20,
    status: 'IN_PROGRESS',
    procedures: []
  },
  GL: {
    schemaName: 'GL',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 26,
    pkgsCount: 6,
    funcsCount: 9,
    tablesCount: 52,
    totalRoutines: 41,
    researchedCount: 38,
    status: 'IN_PROGRESS',
    procedures: []
  },
  INET2: {
    schemaName: 'INET2',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 14,
    pkgsCount: 4,
    funcsCount: 11,
    tablesCount: 30,
    totalRoutines: 29,
    researchedCount: 12,
    status: 'IN_PROGRESS',
    procedures: []
  },
  PR: {
    schemaName: 'PR',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 31,
    pkgsCount: 7,
    funcsCount: 15,
    tablesCount: 45,
    totalRoutines: 53,
    researchedCount: 45,
    status: 'IN_PROGRESS',
    procedures: []
  },
  RETAILER: {
    schemaName: 'RETAILER',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 52,
    pkgsCount: 14,
    funcsCount: 21,
    tablesCount: 72,
    totalRoutines: 87,
    researchedCount: 72,
    status: 'IN_PROGRESS',
    procedures: []
  },
  STAGE: {
    schemaName: 'STAGE',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 44,
    pkgsCount: 3,
    funcsCount: 5,
    tablesCount: 94,
    totalRoutines: 52,
    researchedCount: 40,
    status: 'IN_PROGRESS',
    procedures: []
  },
  TMS: {
    schemaName: 'TMS',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 38,
    pkgsCount: 11,
    funcsCount: 18,
    tablesCount: 61,
    totalRoutines: 67,
    researchedCount: 32,
    status: 'IN_PROGRESS',
    procedures: []
  },
  TRANSLATOR: {
    schemaName: 'TRANSLATOR',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 41,
    pkgsCount: 15,
    funcsCount: 24,
    tablesCount: 56,
    totalRoutines: 80,
    researchedCount: 58,
    status: 'IN_PROGRESS',
    procedures: []
  },
  VP: {
    schemaName: 'VP',
    databaseName: 'DEVOLTP',
    dboName: 'DEVOLTP',
    procsCount: 29,
    pkgsCount: 8,
    funcsCount: 14,
    tablesCount: 39,
    totalRoutines: 51,
    researchedCount: 22,
    status: 'IN_PROGRESS',
    procedures: []
  }
};
