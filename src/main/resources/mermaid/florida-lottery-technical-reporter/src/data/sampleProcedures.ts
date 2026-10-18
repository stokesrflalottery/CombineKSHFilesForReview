import { StoredProcedureDoc } from '../types/proc';

export const SAMPLE_PROCEDURES: StoredProcedureDoc[] = [
  {
    id: 'sp-ars-daily-settlement',
    name: 'SP_ARS_RETAILER_DAILY_SETTLEMENT',
    schema: 'FL_LOTTERY_ARS',
    packageName: 'PKG_ARS_SETTLEMENT_ENGINE',
    dialect: 'ORACLE',
    businessDomain: 'ARS_FINANCIAL',
    purpose: 'Performs nightly automated settlement calculation for all active Florida Lottery retail locations. Aggregates draw sales, instant ticket pack activations, validation redemptions, and commission adjustments to compute net EFT sweep amounts.',
    author: 'Ryan Stokes (Programmer Analyst III)',
    createdDate: '2023-04-12',
    lastModifiedDate: '2026-08-19',
    iwsJobSchedule: 'IWS_JOB_ARS_NIGHTLY_SETTLE_0100',
    authIdType: 'AUTHID DEFINER',
    parameters: [
      {
        name: 'p_accounting_date',
        mode: 'IN',
        dataType: 'DATE',
        isNullable: false,
        businessDescription: 'Target business accounting date for settlement aggregation.'
      },
      {
        name: 'p_retailer_category',
        mode: 'IN',
        dataType: 'VARCHAR2',
        length: 20,
        defaultValue: "'ALL'",
        isNullable: true,
        businessDescription: 'Filter by retailer category (CORPORATE, INDEPENDENT, VENDING, ALL).'
      },
      {
        name: 'p_force_recalculation',
        mode: 'IN',
        dataType: 'NUMBER',
        defaultValue: '0',
        isNullable: true,
        businessDescription: 'Flag (1=True, 0=False) to bypass previous settlement lock and recalculate.'
      },
      {
        name: 'p_batch_id',
        mode: 'OUT',
        dataType: 'NUMBER',
        isNullable: false,
        businessDescription: 'Generated unique settlement batch sequence identifier.'
      },
      {
        name: 'p_processed_count',
        mode: 'OUT',
        dataType: 'NUMBER',
        isNullable: false,
        businessDescription: 'Total number of retailer accounts successfully settled.'
      },
      {
        name: 'p_total_eft_amount',
        mode: 'OUT',
        dataType: 'NUMBER',
        precision: 18,
        scale: 2,
        isNullable: false,
        businessDescription: 'Net aggregated ACH/EFT sweep balance transferred to Florida State Treasury.'
      },
      {
        name: 'p_status_code',
        mode: 'OUT',
        dataType: 'VARCHAR2',
        length: 10,
        isNullable: false,
        businessDescription: 'Execution status code (SUCCESS, PARTIAL, FAILED_LOCK, INVALID_DATE).'
      }
    ],
    crudMatrix: [
      {
        tableName: 'ARS_RETAILER_LEDGER',
        schema: 'FL_LOTTERY_ARS',
        operations: ['SELECT', 'INSERT', 'UPDATE'],
        columnsAccessed: ['RETAILER_ID', 'SETTLEMENT_DATE', 'NET_SALES', 'COMMISSION_EARNED', 'EFT_BALANCE', 'STATUS', 'BATCH_ID']
      },
      {
        tableName: 'GAMING_DRAW_TRANSACTIONS',
        schema: 'FL_LOTTERY_GAMING',
        operations: ['SELECT'],
        columnsAccessed: ['RETAILER_ID', 'TRANS_DATE', 'DRAW_GAME_ID', 'SALES_AMOUNT', 'CANCEL_AMOUNT']
      },
      {
        tableName: 'INSTANT_PACK_SETTLEMENT',
        schema: 'FL_LOTTERY_INVENTORY',
        operations: ['SELECT', 'UPDATE'],
        columnsAccessed: ['PACK_NUMBER', 'GAME_ID', 'RETAILER_ID', 'STATUS_CODE', 'SETTLE_DATE']
      },
      {
        tableName: 'ARS_EFT_SWEEP_STAGING',
        schema: 'FL_LOTTERY_ARS',
        operations: ['INSERT', 'TRUNCATE'],
        columnsAccessed: ['BATCH_ID', 'ROUTING_NO', 'ACCOUNT_NO', 'SWEEP_AMOUNT', 'RETAILER_NAME']
      },
      {
        tableName: 'ARS_SETTLEMENT_AUDIT_LOG',
        schema: 'FL_LOTTERY_ARS',
        operations: ['INSERT'],
        columnsAccessed: ['AUDIT_ID', 'BATCH_ID', 'START_TIME', 'END_TIME', 'RETAILER_COUNT', 'TOTAL_AMOUNT', 'HOST_NAME', 'PID']
      }
    ],
    dependencies: [
      {
        name: 'PKG_ARS_COMMISSION_CALC.CALCULATE_BONUS',
        type: 'PROCEDURE',
        schema: 'FL_LOTTERY_ARS',
        direction: 'CALLS',
        description: 'Computes retailer incentive tier bonuses based on monthly sales quotas.'
      },
      {
        name: 'PKG_SECURITY_TOKEN.VALIDATE_SESSION',
        type: 'FUNCTION',
        schema: 'FL_LOTTERY_SEC',
        direction: 'CALLS',
        description: 'Verifies database execution privileges and audit trace authorization.'
      },
      {
        name: 'VW_ACTIVE_LICENSED_RETAILERS',
        type: 'VIEW',
        schema: 'FL_LOTTERY_ARS',
        direction: 'CALLS',
        description: 'Materialized view containing all currently licensed retailer master data.'
      },
      {
        name: 'SEQ_ARS_SETTLEMENT_BATCH',
        type: 'SEQUENCE',
        schema: 'FL_LOTTERY_ARS',
        direction: 'CALLS',
        description: 'Oracle sequence providing unique settlement run identifiers.'
      },
      {
        name: 'ARS_CORE_DBLINK_PROD',
        type: 'DBLINK',
        direction: 'CALLS',
        description: 'Secure Oracle database link connecting ARS cluster to Gaming Transaction Vault.'
      }
    ],
    riskFindings: [
      {
        id: 'RSK-001',
        severity: 'HIGH',
        category: 'TRANSACTION',
        title: 'Implicit Commit Inside Loop',
        detail: 'The procedure performs periodic intermediate commits every 500 rows. In the event of a network disruption or ORA-01555 error, previous batches will remain committed while the remaining records are left uncalculated.',
        recommendation: 'Refactor to autonomous transactions or single-batch unit of work managed by Spring Boot @Transactional boundary.',
        lineRange: [84, 92]
      },
      {
        id: 'RSK-002',
        severity: 'MEDIUM',
        category: 'PERFORMANCE',
        title: 'Cartesian Product Risk on Unindexed Date Range',
        detail: 'Query against GAMING_DRAW_TRANSACTIONS performs a full table scan when p_retailer_category is NULL, potentially scanning 45M rows during peak draw nights.',
        recommendation: 'Ensure index on (TRANS_DATE, RETAILER_ID) is hit and avoid using NVL on indexed predicates.',
        lineRange: [42, 58]
      },
      {
        id: 'RSK-003',
        severity: 'LOW',
        category: 'COMPLIANCE',
        title: 'Hardcoded Fallback Tax Rate',
        detail: 'State withholding adjustment constant (0.0000) is hardcoded rather than queried from central regulatory table.',
        recommendation: 'Extract state tax configuration to central etldb ServiceConfig tier.',
        lineRange: [112, 114]
      }
    ],
    metrics: {
      linesOfCode: 184,
      commentLines: 36,
      cyclomaticComplexity: 14,
      maintainabilityIndex: 78,
      nestingDepth: 4,
      parameterCount: 7,
      tablesTouched: 5,
      riskScore: 28
    },
    rawSql: `CREATE OR REPLACE PROCEDURE FL_LOTTERY_ARS.SP_ARS_RETAILER_DAILY_SETTLEMENT (
    p_accounting_date     IN  DATE,
    p_retailer_category   IN  VARCHAR2 DEFAULT 'ALL',
    p_force_recalculation IN  NUMBER DEFAULT 0,
    p_batch_id            OUT NUMBER,
    p_processed_count     OUT NUMBER,
    p_total_eft_amount    OUT NUMBER,
    p_status_code         OUT VARCHAR2
)
AUTHID DEFINER
AS
    -- Enterprise Stored Procedure for Florida Lottery ARS System
    v_batch_id          NUMBER;
    v_total_records     NUMBER := 0;
    v_total_amount      NUMBER := 0;
    v_start_time        TIMESTAMP := SYSTIMESTAMP;
    v_lock_count        NUMBER;
    
    CURSOR c_retailers IS
        SELECT r.retailer_id, r.retailer_name, r.routing_number, r.bank_account_num, r.commission_rate
        FROM FL_LOTTERY_ARS.VW_ACTIVE_LICENSED_RETAILERS r
        WHERE (p_retailer_category = 'ALL' OR r.category_code = p_retailer_category)
          AND r.status = 'ACTIVE';

    TYPE t_retailer_list IS TABLE OF c_retailers%ROWTYPE;
    v_retailers t_retailer_list;
BEGIN
    p_status_code := 'INITIALIZING';
    
    -- Step 1: Check existing lock for accounting date
    SELECT COUNT(1) INTO v_lock_count
    FROM FL_LOTTERY_ARS.ARS_RETAILER_LEDGER
    WHERE settlement_date = TRUNC(p_accounting_date)
      AND status = 'LOCKED';
      
    IF v_lock_count > 0 AND p_force_recalculation = 0 THEN
        p_status_code := 'FAILED_LOCK';
        p_processed_count := 0;
        p_total_eft_amount := 0;
        RETURN;
    END IF;

    -- Step 2: Allocate next batch sequence ID
    SELECT FL_LOTTERY_ARS.SEQ_ARS_SETTLEMENT_BATCH.NEXTVAL INTO v_batch_id FROM DUAL;
    p_batch_id := v_batch_id;

    -- Step 3: Clear transient EFT staging table
    EXECUTE IMMEDIATE 'TRUNCATE TABLE FL_LOTTERY_ARS.ARS_EFT_SWEEP_STAGING';

    -- Step 4: Bulk Collect Active Retailers
    OPEN c_retailers;
    LOOP
        FETCH c_retailers BULK COLLECT INTO v_retailers LIMIT 500;
        EXIT WHEN v_retailers.COUNT = 0;

        FOR i IN 1 .. v_retailers.COUNT LOOP
            DECLARE
                v_net_draw_sales   NUMBER := 0;
                v_instant_sales    NUMBER := 0;
                v_redemptions      NUMBER := 0;
                v_comm_amount      NUMBER := 0;
                v_net_settlement   NUMBER := 0;
            BEGIN
                -- Query Gaming Transactions
                SELECT NVL(SUM(sales_amount - cancel_amount), 0)
                INTO v_net_draw_sales
                FROM FL_LOTTERY_GAMING.GAMING_DRAW_TRANSACTIONS
                WHERE retailer_id = v_retailers(i).retailer_id
                  AND trans_date = TRUNC(p_accounting_date);

                -- Query Instant Scratch Ticket Settlements
                SELECT NVL(SUM(p.pack_value), 0)
                INTO v_instant_sales
                FROM FL_LOTTERY_INVENTORY.INSTANT_PACK_SETTLEMENT p
                WHERE p.retailer_id = v_retailers(i).retailer_id
                  AND p.settle_date = TRUNC(p_accounting_date);

                -- Calculate Commission via Specialist Subroutine
                v_comm_amount := (v_net_draw_sales + v_instant_sales) * NVL(v_retailers(i).commission_rate, 0.05);
                v_net_settlement := (v_net_draw_sales + v_instant_sales) - v_comm_amount;

                -- Insert into Staging Table
                INSERT INTO FL_LOTTERY_ARS.ARS_EFT_SWEEP_STAGING (
                    batch_id, routing_no, account_no, sweep_amount, retailer_name
                ) VALUES (
                    v_batch_id, v_retailers(i).routing_number, v_retailers(i).bank_account_num, v_net_settlement, v_retailers(i).retailer_name
                );

                -- Upsert Master Ledger
                MERGE INTO FL_LOTTERY_ARS.ARS_RETAILER_LEDGER target
                USING (SELECT v_retailers(i).retailer_id AS rid, TRUNC(p_accounting_date) AS sdate FROM DUAL) src
                ON (target.retailer_id = src.rid AND target.settlement_date = src.sdate)
                WHEN MATCHED THEN
                    UPDATE SET target.net_sales = v_net_draw_sales + v_instant_sales,
                               target.commission_earned = v_comm_amount,
                               target.eft_balance = v_net_settlement,
                               target.status = 'SETTLED',
                               target.batch_id = v_batch_id
                WHEN NOT MATCHED THEN
                    INSERT (retailer_id, settlement_date, net_sales, commission_earned, eft_balance, status, batch_id)
                    VALUES (v_retailers(i).retailer_id, TRUNC(p_accounting_date), v_net_draw_sales + v_instant_sales, v_comm_amount, v_net_settlement, 'SETTLED', v_batch_id);

                v_total_records := v_total_records + 1;
                v_total_amount := v_total_amount + v_net_settlement;
            END;
        END LOOP;
        
        -- High risk periodic commit
        COMMIT;
    END LOOP;
    CLOSE c_retailers;

    -- Step 5: Audit Log Flight Recorder Entry
    INSERT INTO FL_LOTTERY_ARS.ARS_SETTLEMENT_AUDIT_LOG (
        audit_id, batch_id, start_time, end_time, retailer_count, total_amount, host_name, pid
    ) VALUES (
        SYS_GUID(), v_batch_id, v_start_time, SYSTIMESTAMP, v_total_records, v_total_amount, SYS_CONTEXT('USERENV', 'HOST'), SYS_CONTEXT('USERENV', 'SESSIONID')
    );
    COMMIT;

    p_processed_count := v_total_records;
    p_total_eft_amount := v_total_amount;
    p_status_code := 'SUCCESS';

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        p_status_code := 'ERROR: ' || SQLERRM;
        p_processed_count := 0;
        p_total_eft_amount := 0;
        RAISE;
END SP_ARS_RETAILER_DAILY_SETTLEMENT;`,
    callGraph: {
      nodes: [
        { id: 'proc', label: 'SP_ARS_RETAILER_DAILY_SETTLEMENT', type: 'procedure' },
        { id: 'vw_ret', label: 'VW_ACTIVE_LICENSED_RETAILERS', type: 'view' },
        { id: 't_ledger', label: 'ARS_RETAILER_LEDGER', type: 'table' },
        { id: 't_gaming', label: 'GAMING_DRAW_TRANSACTIONS', type: 'table' },
        { id: 't_instant', label: 'INSTANT_PACK_SETTLEMENT', type: 'table' },
        { id: 't_staging', label: 'ARS_EFT_SWEEP_STAGING', type: 'table' },
        { id: 'pkg_comm', label: 'PKG_ARS_COMMISSION_CALC', type: 'package' }
      ],
      edges: [
        { from: 'proc', to: 'vw_ret', label: 'BULK COLLECT' },
        { from: 'proc', to: 't_gaming', label: 'SELECT' },
        { from: 'proc', to: 't_instant', label: 'SELECT' },
        { from: 'proc', to: 'pkg_comm', label: 'CALCULATE' },
        { from: 'proc', to: 't_staging', label: 'INSERT' },
        { from: 'proc', to: 't_ledger', label: 'MERGE' }
      ]
    },
    sampleExecutionSnippet: {
      plsql: `DECLARE
    v_batch_id       NUMBER;
    v_proc_count     NUMBER;
    v_total_eft      NUMBER;
    v_status         VARCHAR2(100);
BEGIN
    FL_LOTTERY_ARS.SP_ARS_RETAILER_DAILY_SETTLEMENT(
        p_accounting_date     => TRUNC(SYSDATE - 1),
        p_retailer_category   => 'ALL',
        p_force_recalculation => 0,
        p_batch_id            => v_batch_id,
        p_processed_count     => v_proc_count,
        p_total_eft_amount    => v_total_eft,
        p_status_code         => v_status
    );
    DBMS_OUTPUT.PUT_LINE('Batch ID: ' || v_batch_id || ' | Status: ' || v_status || ' | Total EFT: $' || v_total_eft);
END;`,
      javaFluentDsl: `// Executed via Florida Lottery Java 21 ETL Fluent Request DSL
DatabaseResult result = etl.db()
    .callProcedure("FL_LOTTERY_ARS.SP_ARS_RETAILER_DAILY_SETTLEMENT")
    .inParam("p_accounting_date", context.getBusinessDate())
    .inParam("p_retailer_category", "ALL")
    .inParam("p_force_recalculation", 0)
    .outParam("p_batch_id", Types.NUMERIC)
    .outParam("p_processed_count", Types.NUMERIC)
    .outParam("p_total_eft_amount", Types.NUMERIC)
    .outParam("p_status_code", Types.VARCHAR)
    .execute();

Long batchId = result.getScalarValue("p_batch_id", Long.class);
BigDecimal totalEft = result.getScalarValue("p_total_eft_amount", BigDecimal.class);
String statusCode = result.getScalarValue("p_status_code", String.class);`
    }
  },
  {
    id: 'sp-gaming-scratch-sync',
    name: 'SP_GAMING_SCRATCH_INVENTORY_SYNC',
    schema: 'FL_LOTTERY_INVENTORY',
    packageName: 'PKG_SCRATCH_WAREHOUSE_SYNC',
    dialect: 'ORACLE',
    businessDomain: 'INVENTORY',
    purpose: 'Synchronizes Scientific Games (SGI) warehouse consignment manifest records with Florida Lottery local central inventory balances. Updates pack status from RECEIVED to CONFIRMED, ACTIVE, or SETTLED.',
    author: 'Ryan Stokes (Programmer Analyst III)',
    createdDate: '2024-01-15',
    lastModifiedDate: '2026-06-11',
    iwsJobSchedule: 'IWS_JOB_INV_SCRATCH_SYNC_0300',
    authIdType: 'AUTHID DEFINER',
    parameters: [
      {
        name: 'p_game_number',
        mode: 'IN',
        dataType: 'NUMBER',
        isNullable: false,
        businessDescription: 'Florida Lottery Scratch-Off game catalog ID (e.g., 1542 for $20 Gold Rush).'
      },
      {
        name: 'p_manifest_date',
        mode: 'IN',
        dataType: 'DATE',
        isNullable: false,
        businessDescription: 'Shipped manifest delivery timestamp from central warehouse.'
      },
      {
        name: 'p_packs_synchronized',
        mode: 'OUT',
        dataType: 'NUMBER',
        isNullable: false,
        businessDescription: 'Total count of instant ticket books verified and transitioned to active state.'
      },
      {
        name: 'p_discrepancy_count',
        mode: 'OUT',
        dataType: 'NUMBER',
        isNullable: false,
        businessDescription: 'Number of barcode mismatches flagged for internal security review.'
      }
    ],
    crudMatrix: [
      {
        tableName: 'SCRATCH_GAME_CATALOG',
        schema: 'FL_LOTTERY_INVENTORY',
        operations: ['SELECT'],
        columnsAccessed: ['GAME_NUMBER', 'PRICE_POINT', 'PACK_SIZE', 'GAME_NAME', 'EXPIRY_DATE']
      },
      {
        tableName: 'SCRATCH_PACK_INVENTORY',
        schema: 'FL_LOTTERY_INVENTORY',
        operations: ['INSERT', 'UPDATE'],
        columnsAccessed: ['PACK_ID', 'GAME_NUMBER', 'PACK_SEQUENCE', 'STATUS_CODE', 'LOCATION_ID', 'ACTIVATED_TIMESTAMP']
      },
      {
        tableName: 'WAREHOUSE_CONSIGNMENT_LOG',
        schema: 'FL_LOTTERY_INVENTORY',
        operations: ['SELECT', 'UPDATE'],
        columnsAccessed: ['CONSIGNMENT_ID', 'GAME_NUMBER', 'MANIFEST_DATE', 'SYNC_FLAG']
      },
      {
        tableName: 'SCRATCH_SECURITY_DISCREPANCIES',
        schema: 'FL_LOTTERY_SEC',
        operations: ['INSERT'],
        columnsAccessed: ['DISCREPANCY_ID', 'PACK_ID', 'REASON', 'DETECTED_DATE', 'OPERATOR_ID']
      }
    ],
    dependencies: [
      {
        name: 'PKG_BARCODE_CIPHER.VERIFY_SECURITY_CHECKSUM',
        type: 'FUNCTION',
        schema: 'FL_LOTTERY_SEC',
        direction: 'CALLS',
        description: 'Verifies SHA-256 encrypted barcode integrity against master ticket seed.'
      },
      {
        name: 'SEQ_SCRATCH_DISCREPANCY',
        type: 'SEQUENCE',
        schema: 'FL_LOTTERY_SEC',
        direction: 'CALLS',
        description: 'Sequence generator for security incident logging.'
      }
    ],
    riskFindings: [
      {
        id: 'RSK-101',
        severity: 'MEDIUM',
        category: 'PERFORMANCE',
        title: 'Missing Index on WAREHOUSE_CONSIGNMENT_LOG',
        detail: 'Query on (GAME_NUMBER, MANIFEST_DATE, SYNC_FLAG) lacks a composite index, creating row locks during high-volume warehouse scanning.',
        recommendation: 'Create composite B-tree index IX_WAREHOUSE_SYNC (GAME_NUMBER, SYNC_FLAG, MANIFEST_DATE).',
        lineRange: [38, 44]
      }
    ],
    metrics: {
      linesOfCode: 132,
      commentLines: 24,
      cyclomaticComplexity: 9,
      maintainabilityIndex: 84,
      nestingDepth: 3,
      parameterCount: 4,
      tablesTouched: 4,
      riskScore: 16
    },
    rawSql: `CREATE OR REPLACE PROCEDURE FL_LOTTERY_INVENTORY.SP_GAMING_SCRATCH_INVENTORY_SYNC (
    p_game_number        IN  NUMBER,
    p_manifest_date      IN  DATE,
    p_packs_synchronized OUT NUMBER,
    p_discrepancy_count  OUT NUMBER
)
AUTHID DEFINER
AS
    v_sync_count NUMBER := 0;
    v_disc_count NUMBER := 0;
    v_valid_checksum BOOLEAN;
BEGIN
    FOR r IN (
        SELECT consignment_id, pack_id, pack_sequence, barcode_checksum, location_id
        FROM FL_LOTTERY_INVENTORY.WAREHOUSE_CONSIGNMENT_LOG
        WHERE game_number = p_game_number
          AND TRUNC(manifest_date) = TRUNC(p_manifest_date)
          AND sync_flag = 'N'
    ) LOOP
        -- Verify pack checksum against security vault
        IF FL_LOTTERY_SEC.PKG_BARCODE_CIPHER.VERIFY_SECURITY_CHECKSUM(r.pack_id, r.barcode_checksum) = 1 THEN
            MERGE INTO FL_LOTTERY_INVENTORY.SCRATCH_PACK_INVENTORY dest
            USING (SELECT r.pack_id AS pid FROM DUAL) s
            ON (dest.pack_id = s.pid)
            WHEN MATCHED THEN
                UPDATE SET status_code = 'CONFIRMED', location_id = r.location_id
            WHEN NOT MATCHED THEN
                INSERT (pack_id, game_number, pack_sequence, status_code, location_id, activated_timestamp)
                VALUES (r.pack_id, p_game_number, r.pack_sequence, 'CONFIRMED', r.location_id, SYSTIMESTAMP);

            UPDATE FL_LOTTERY_INVENTORY.WAREHOUSE_CONSIGNMENT_LOG
            SET sync_flag = 'Y'
            WHERE consignment_id = r.consignment_id;

            v_sync_count := v_sync_count + 1;
        ELSE
            INSERT INTO FL_LOTTERY_SEC.SCRATCH_SECURITY_DISCREPANCIES (
                discrepancy_id, pack_id, reason, detected_date, operator_id
            ) VALUES (
                FL_LOTTERY_SEC.SEQ_SCRATCH_DISCREPANCY.NEXTVAL, r.pack_id, 'CHECKSUM_FAIL', SYSDATE, USER
            );
            v_disc_count := v_disc_count + 1;
        END IF;
    END LOOP;

    COMMIT;
    p_packs_synchronized := v_sync_count;
    p_discrepancy_count := v_disc_count;
END SP_GAMING_SCRATCH_INVENTORY_SYNC;`,
    callGraph: {
      nodes: [
        { id: 'proc', label: 'SP_GAMING_SCRATCH_INVENTORY_SYNC', type: 'procedure' },
        { id: 't_cons', label: 'WAREHOUSE_CONSIGNMENT_LOG', type: 'table' },
        { id: 't_inv', label: 'SCRATCH_PACK_INVENTORY', type: 'table' },
        { id: 't_disc', label: 'SCRATCH_SECURITY_DISCREPANCIES', type: 'table' },
        { id: 'pkg_sec', label: 'PKG_BARCODE_CIPHER', type: 'package' }
      ],
      edges: [
        { from: 'proc', to: 't_cons', label: 'SELECT & UPDATE' },
        { from: 'proc', to: 'pkg_sec', label: 'CHECKSUM' },
        { from: 'proc', to: 't_inv', label: 'MERGE' },
        { from: 'proc', to: 't_disc', label: 'INSERT DISCREPANCY' }
      ]
    },
    sampleExecutionSnippet: {
      plsql: `DECLARE
    v_synced NUMBER;
    v_discs  NUMBER;
BEGIN
    FL_LOTTERY_INVENTORY.SP_GAMING_SCRATCH_INVENTORY_SYNC(1542, TRUNC(SYSDATE), v_synced, v_discs);
    DBMS_OUTPUT.PUT_LINE('Packs Synced: ' || v_synced || ' | Discrepancies: ' || v_discs);
END;`,
      javaFluentDsl: `DatabaseResult result = etl.db()
    .callProcedure("FL_LOTTERY_INVENTORY.SP_GAMING_SCRATCH_INVENTORY_SYNC")
    .inParam("p_game_number", 1542)
    .inParam("p_manifest_date", LocalDate.now())
    .outParam("p_packs_synchronized", Types.INTEGER)
    .outParam("p_discrepancy_count", Types.INTEGER)
    .execute();

int synced = result.getScalarValue("p_packs_synchronized", Integer.class);
int discrepancies = result.getScalarValue("p_discrepancy_count", Integer.class);`
    }
  },
  {
    id: 'sp-draw-winner-validation',
    name: 'SP_DRAW_WINNER_VALIDATION_MATRIX',
    schema: 'dbo',
    packageName: 'GAMING_VALIDATION_DB',
    dialect: 'SQL_SERVER',
    businessDomain: 'GAMING_OPERATIONS',
    purpose: 'Validates high-tier winning lottery tickets ($600+) for draw games (Powerball, Mega Millions, Florida Lotto, Fantasy 5). Enforces statutory state debt interceptions (child support, back taxes) prior to prize disbursement authorization.',
    author: 'Ryan Stokes (Programmer Analyst III)',
    createdDate: '2023-09-01',
    lastModifiedDate: '2026-07-22',
    iwsJobSchedule: 'IWS_JOB_WINNER_VALIDATE_REALTIME',
    authIdType: 'DEFAULT',
    parameters: [
      {
        name: '@ticket_serial_number',
        mode: 'IN',
        dataType: 'VARCHAR',
        length: 32,
        isNullable: false,
        businessDescription: 'Optical 2D barcode serial number scanned at district lottery headquarters.'
      },
      {
        name: '@claimant_ssn_hash',
        mode: 'IN',
        dataType: 'CHAR',
        length: 64,
        isNullable: false,
        businessDescription: 'SHA-256 hashed Social Security Number of the winner.'
      },
      {
        name: '@gross_prize_amount',
        mode: 'OUT',
        dataType: 'DECIMAL',
        precision: 18,
        scale: 2,
        isNullable: false,
        businessDescription: 'Gross prize money won before mandatory deductions.'
      },
      {
        name: '@state_debt_withholding',
        mode: 'OUT',
        dataType: 'DECIMAL',
        precision: 18,
        scale: 2,
        isNullable: false,
        businessDescription: 'Intercepted sum for child support (DOR) or state university debts.'
      },
      {
        name: '@net_payable_amount',
        mode: 'OUT',
        dataType: 'DECIMAL',
        precision: 18,
        scale: 2,
        isNullable: false,
        businessDescription: 'Final net prize check issued to claimant.'
      },
      {
        name: '@authorization_code',
        mode: 'OUT',
        dataType: 'VARCHAR',
        length: 36,
        isNullable: false,
        businessDescription: 'Cryptographic authorization UUID for terminal check print.'
      }
    ],
    crudMatrix: [
      {
        tableName: 'TICKET_WAGERS',
        schema: 'dbo',
        operations: ['SELECT', 'UPDATE'],
        columnsAccessed: ['TICKET_SERIAL', 'DRAW_ID', 'GAME_ID', 'STATUS_CODE', 'PRIZE_CLAIMED_TIMESTAMP']
      },
      {
        tableName: 'FL_STATE_DEBT_INTERCEPT',
        schema: 'dbo',
        operations: ['SELECT', 'INSERT'],
        columnsAccessed: ['SSN_HASH', 'DEBT_AGENCY', 'BALANCE_DUE', 'INTERCEPT_AMOUNT', 'TICKET_SERIAL']
      },
      {
        tableName: 'CLAIM_AUTHORIZATION_LEDGER',
        schema: 'dbo',
        operations: ['INSERT'],
        columnsAccessed: ['AUTH_CODE', 'TICKET_SERIAL', 'CLAIMANT_HASH', 'GROSS_AMT', 'WITHHOLDING_AMT', 'NET_AMT', 'CREATED_BY']
      }
    ],
    dependencies: [
      {
        name: 'FN_CALCULATE_FEDERAL_TAX_WITHHOLDING',
        type: 'FUNCTION',
        schema: 'dbo',
        direction: 'CALLS',
        description: 'Computes mandatory 24% IRS federal tax withholding on prizes over $5,000.'
      }
    ],
    riskFindings: [
      {
        id: 'RSK-201',
        severity: 'CRITICAL',
        category: 'SECURITY',
        title: 'Missing SSN Salt in Intercept Query',
        detail: 'Direct equality comparison on @claimant_ssn_hash against un-salted legacy records exposes rainbow table vulnerabilities.',
        recommendation: 'Use enterprise crypto specialist (crypto().decryptAes256(...)) with HMAC-SHA256.',
        lineRange: [28, 35]
      }
    ],
    metrics: {
      linesOfCode: 98,
      commentLines: 18,
      cyclomaticComplexity: 7,
      maintainabilityIndex: 88,
      nestingDepth: 2,
      parameterCount: 6,
      tablesTouched: 3,
      riskScore: 32
    },
    rawSql: `CREATE PROCEDURE dbo.SP_DRAW_WINNER_VALIDATION_MATRIX
    @ticket_serial_number    VARCHAR(32),
    @claimant_ssn_hash       CHAR(64),
    @gross_prize_amount      DECIMAL(18,2) OUTPUT,
    @state_debt_withholding  DECIMAL(18,2) OUTPUT,
    @net_payable_amount      DECIMAL(18,2) OUTPUT,
    @authorization_code      VARCHAR(36) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    
    BEGIN TRY
        BEGIN TRANSACTION;

        DECLARE @ticket_status VARCHAR(20);
        DECLARE @base_prize DECIMAL(18,2);
        DECLARE @fed_tax DECIMAL(18,2) = 0.00;
        DECLARE @state_debt DECIMAL(18,2) = 0.00;
        DECLARE @auth_guid UNIQUEIDENTIFIER = NEWID();

        -- 1. Inspect Ticket
        SELECT @ticket_status = status_code, @base_prize = prize_amount
        FROM dbo.TICKET_WAGERS WITH (UPDLOCK, HOLDLOCK)
        WHERE ticket_serial = @ticket_serial_number;

        IF @ticket_status <> 'UNCLAIMED' OR @base_prize IS NULL
        BEGIN
            THROW 50001, 'INVALID_TICKET_STATE_OR_ALREADY_CLAIMED', 1;
        END;

        -- 2. State Intercept Lookup
        SELECT @state_debt = ISNULL(SUM(balance_due), 0.00)
        FROM dbo.FL_STATE_DEBT_INTERCEPT
        WHERE ssn_hash = @claimant_ssn_hash
          AND status = 'ACTIVE';

        IF @base_prize >= 5000.00
        BEGIN
            SET @fed_tax = dbo.FN_CALCULATE_FEDERAL_TAX_WITHHOLDING(@base_prize);
        END;

        -- Limit intercept to maximum available net
        IF @state_debt > (@base_prize - @fed_tax)
        BEGIN
            SET @state_debt = (@base_prize - @fed_tax);
        END;

        -- 3. Update Ticket
        UPDATE dbo.TICKET_WAGERS
        SET status_code = 'CLAIMED',
            prize_claimed_timestamp = GETUTCDATE()
        WHERE ticket_serial = @ticket_serial_number;

        -- 4. Record Auth Ledger
        INSERT INTO dbo.CLAIM_AUTHORIZATION_LEDGER (
            auth_code, ticket_serial, claimant_hash, gross_amt, withholding_amt, net_amt, created_by
        ) VALUES (
            CONVERT(VARCHAR(36), @auth_guid), @ticket_serial_number, @claimant_ssn_hash,
            @base_prize, (@fed_tax + @state_debt), (@base_prize - @fed_tax - @state_debt), SUSER_SNAME()
        );

        COMMIT TRANSACTION;

        SET @gross_prize_amount = @base_prize;
        SET @state_debt_withholding = @state_debt;
        SET @net_payable_amount = (@base_prize - @fed_tax - @state_debt);
        SET @authorization_code = CONVERT(VARCHAR(36), @auth_guid);

    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;
            
        DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();
        RAISERROR(@ErrorMessage, 16, 1);
    END CATCH
END;`,
    callGraph: {
      nodes: [
        { id: 'proc', label: 'SP_DRAW_WINNER_VALIDATION_MATRIX', type: 'procedure' },
        { id: 't_wager', label: 'TICKET_WAGERS', type: 'table' },
        { id: 't_debt', label: 'FL_STATE_DEBT_INTERCEPT', type: 'table' },
        { id: 't_claim', label: 'CLAIM_AUTHORIZATION_LEDGER', type: 'table' },
        { id: 'fn_tax', label: 'FN_CALCULATE_FEDERAL_TAX_WITHHOLDING', type: 'function' }
      ],
      edges: [
        { from: 'proc', to: 't_wager', label: 'LOCK & UPDATE' },
        { from: 'proc', to: 't_debt', label: 'QUERY DEBT' },
        { from: 'proc', to: 'fn_tax', label: 'TAX CALC' },
        { from: 'proc', to: 't_claim', label: 'INSERT CLAIM' }
      ]
    },
    sampleExecutionSnippet: {
      plsql: `DECLARE @gross DECIMAL(18,2), @debt DECIMAL(18,2), @net DECIMAL(18,2), @auth VARCHAR(36);
EXEC dbo.SP_DRAW_WINNER_VALIDATION_MATRIX
    @ticket_serial_number = 'FL-2026-PB-998811',
    @claimant_ssn_hash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    @gross_prize_amount = @gross OUTPUT,
    @state_debt_withholding = @debt OUTPUT,
    @net_payable_amount = @net OUTPUT,
    @authorization_code = @auth OUTPUT;
SELECT @gross AS Gross, @debt AS StateDebt, @net AS NetPayable, @auth AS AuthCode;`,
      javaFluentDsl: `DatabaseResult result = etl.db()
    .callProcedure("dbo.SP_DRAW_WINNER_VALIDATION_MATRIX")
    .inParam("@ticket_serial_number", "FL-2026-PB-998811")
    .inParam("@claimant_ssn_hash", ssnHash)
    .outParam("@gross_prize_amount", Types.DECIMAL)
    .outParam("@state_debt_withholding", Types.DECIMAL)
    .outParam("@net_payable_amount", Types.DECIMAL)
    .outParam("@authorization_code", Types.VARCHAR)
    .execute();

BigDecimal grossPrize = result.getScalarValue("@gross_prize_amount", BigDecimal.class);
BigDecimal netPayable = result.getScalarValue("@net_payable_amount", BigDecimal.class);
String authCode = result.getScalarValue("@authorization_code", String.class);`
    }
  },
  {
    id: 'sp-audit-recon',
    name: 'SP_AUDIT_FINANCIAL_RECONCILIATION',
    schema: 'etldb',
    packageName: 'ETL_AUDIT_SUITE',
    dialect: 'MYSQL',
    businessDomain: 'AUDIT_COMPLIANCE',
    purpose: 'Nightly master flight-recorder reconciliation procedure within etldb. Correlates ServiceAudit table heartbeats across file transfers, database batches, and cryptographic operations to verify zero unexplained discrepancies.',
    author: 'Ryan Stokes (Programmer Analyst III)',
    createdDate: '2025-02-10',
    lastModifiedDate: '2026-09-01',
    iwsJobSchedule: 'IWS_JOB_AUDIT_RECON_0430',
    authIdType: 'DEFAULT',
    parameters: [
      {
        name: 'p_audit_date',
        mode: 'IN',
        dataType: 'DATE',
        isNullable: false,
        businessDescription: 'Calendar date for transaction flight-recorder reconciliation.'
      },
      {
        name: 'p_unresolved_count',
        mode: 'OUT',
        dataType: 'INT',
        isNullable: false,
        businessDescription: 'Count of failed or orphaned heartbeats detected.'
      },
      {
        name: 'p_health_status',
        mode: 'OUT',
        dataType: 'VARCHAR',
        length: 20,
        isNullable: false,
        businessDescription: 'Aggregated health rating (HEALTHY, DEGRADED, ANOMALOUS).'
      }
    ],
    crudMatrix: [
      {
        tableName: 'SERVICE_AUDIT',
        schema: 'etldb',
        operations: ['SELECT'],
        columnsAccessed: ['AUDIT_ID', 'TRACE_ID', 'SERVICE_NAME', 'STATUS', 'CREATED_AT']
      },
      {
        tableName: 'JOB_LOG',
        schema: 'etldb',
        operations: ['SELECT', 'UPDATE'],
        columnsAccessed: ['RUN_ID', 'JOB_NAME', 'EXECUTION_STATUS', 'BUSINESS_DATE', 'DURATION_MS']
      },
      {
        tableName: 'AUDIT_RECONCILIATION_REPORT',
        schema: 'etldb',
        operations: ['INSERT'],
        columnsAccessed: ['RECON_ID', 'AUDIT_DATE', 'UNRESOLVED_HEARTBEATS', 'HEALTH_SCORE', 'GENERATED_AT']
      }
    ],
    dependencies: [],
    riskFindings: [],
    metrics: {
      linesOfCode: 74,
      commentLines: 15,
      cyclomaticComplexity: 5,
      maintainabilityIndex: 92,
      nestingDepth: 2,
      parameterCount: 3,
      tablesTouched: 3,
      riskScore: 8
    },
    rawSql: `DELIMITER //
CREATE PROCEDURE etldb.SP_AUDIT_FINANCIAL_RECONCILIATION(
    IN p_audit_date DATE,
    OUT p_unresolved_count INT,
    OUT p_health_status VARCHAR(20)
)
BEGIN
    DECLARE v_failed_jobs INT DEFAULT 0;
    DECLARE v_orphaned_audits INT DEFAULT 0;
    DECLARE v_total_unresolved INT DEFAULT 0;

    -- 1. Check for Failed Job Runs
    SELECT COUNT(*) INTO v_failed_jobs
    FROM etldb.JOB_LOG
    WHERE DATE(BUSINESS_DATE) = p_audit_date
      AND EXECUTION_STATUS IN ('FAILED', 'CONFIG_FAILURE');

    -- 2. Check for Incomplete Service Audits without matching finish
    SELECT COUNT(*) INTO v_orphaned_audits
    FROM etldb.SERVICE_AUDIT sa
    WHERE DATE(sa.CREATED_AT) = p_audit_date
      AND sa.STATUS = 'RUNNING';

    SET v_total_unresolved = v_failed_jobs + v_orphaned_audits;
    SET p_unresolved_count = v_total_unresolved;

    IF v_total_unresolved = 0 THEN
        SET p_health_status = 'HEALTHY';
    ELSEIF v_total_unresolved <= 3 THEN
        SET p_health_status = 'DEGRADED';
    ELSE
        SET p_health_status = 'ANOMALOUS';
    END IF;

    -- 3. Log Reconciliation Summary
    INSERT INTO etldb.AUDIT_RECONCILIATION_REPORT (
        RECON_ID, AUDIT_DATE, UNRESOLVED_HEARTBEATS, HEALTH_SCORE, GENERATED_AT
    ) VALUES (
        UUID(), p_audit_date, v_total_unresolved, p_health_status, NOW()
    );
END //
DELIMITER ;`,
    callGraph: {
      nodes: [
        { id: 'proc', label: 'SP_AUDIT_FINANCIAL_RECONCILIATION', type: 'procedure' },
        { id: 't_audit', label: 'SERVICE_AUDIT', type: 'table' },
        { id: 't_job', label: 'JOB_LOG', type: 'table' },
        { id: 't_rep', label: 'AUDIT_RECONCILIATION_REPORT', type: 'table' }
      ],
      edges: [
        { from: 'proc', to: 't_audit', label: 'SELECT' },
        { from: 'proc', to: 't_job', label: 'SELECT' },
        { from: 'proc', to: 't_rep', label: 'INSERT SUMMARY' }
      ]
    },
    sampleExecutionSnippet: {
      plsql: `CALL etldb.SP_AUDIT_FINANCIAL_RECONCILIATION(CURDATE() - INTERVAL 1 DAY, @unresolved, @health);
SELECT @unresolved AS UnresolvedCount, @health AS HealthRating;`,
      javaFluentDsl: `DatabaseResult result = etl.db()
    .callProcedure("etldb.SP_AUDIT_FINANCIAL_RECONCILIATION")
    .inParam("p_audit_date", LocalDate.now().minusDays(1))
    .outParam("p_unresolved_count", Types.INTEGER)
    .outParam("p_health_status", Types.VARCHAR)
    .execute();

int unresolved = result.getScalarValue("p_unresolved_count", Integer.class);
String health = result.getScalarValue("p_health_status", String.class);`
    }
  }
];
