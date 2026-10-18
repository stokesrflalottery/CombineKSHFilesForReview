export type DatabaseDialect = 'ORACLE' | 'SQL_SERVER' | 'MYSQL';

export type ParameterMode = 'IN' | 'OUT' | 'INOUT' | 'RETURN';

export interface ProcParameter {
  name: string;
  mode: ParameterMode;
  dataType: string;
  length?: number;
  precision?: number;
  scale?: number;
  defaultValue?: string;
  isNullable: boolean;
  businessDescription: string;
}

export type CrudAction = 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'TRUNCATE' | 'MERGE' | 'EXECUTE';

export interface CrudTableOperation {
  tableName: string;
  schema?: string;
  operations: CrudAction[];
  columnsAccessed: string[];
  isTemporaryTable?: boolean;
  alias?: string;
}

export interface DependencyItem {
  name: string;
  type: 'PROCEDURE' | 'FUNCTION' | 'PACKAGE' | 'VIEW' | 'TABLE' | 'SEQUENCE' | 'DBLINK' | 'SYNONYM';
  schema?: string;
  direction: 'CALLS' | 'REFERENCED_BY';
  description?: string;
}

export interface RiskFinding {
  id: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  category: 'SECURITY' | 'TRANSACTION' | 'PERFORMANCE' | 'MAINTAINABILITY' | 'COMPLIANCE';
  title: string;
  detail: string;
  recommendation: string;
  lineRange?: [number, number];
}

export interface ComplexityMetrics {
  linesOfCode: number;
  commentLines: number;
  cyclomaticComplexity: number;
  maintainabilityIndex: number; // 0 - 100
  nestingDepth: number;
  parameterCount: number;
  tablesTouched: number;
  riskScore: number; // 0 - 100 (higher = riskier)
}

export interface StoredProcedureDoc {
  id: string;
  name: string;
  schema: string;
  packageName?: string;
  dialect: DatabaseDialect;
  businessDomain: 'ARS_FINANCIAL' | 'GAMING_OPERATIONS' | 'AUDIT_COMPLIANCE' | 'RETAILER_PORTAL' | 'INVENTORY';
  purpose: string;
  author: string;
  createdDate: string;
  lastModifiedDate: string;
  iwsJobSchedule?: string;
  authIdType: 'AUTHID DEFINER' | 'AUTHID CURRENT_USER' | 'DEFAULT';
  parameters: ProcParameter[];
  crudMatrix: CrudTableOperation[];
  dependencies: DependencyItem[];
  riskFindings: RiskFinding[];
  metrics: ComplexityMetrics;
  rawSql: string;
  callGraph: {
    nodes: Array<{ id: string; label: string; type: string }>;
    edges: Array<{ from: string; to: string; label: string }>;
  };
  sampleExecutionSnippet: {
    plsql: string;
    javaFluentDsl: string;
  };
}
