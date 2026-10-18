package com.fllottery.dbresearch.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog entry representing an Oracle table cached in the database searcher.
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 */
public class TableCatalogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    public String schema;
    public String tableName;
    public String tableType;
    public Long numRows;
    public List<String> primaryKeys = new ArrayList<>();
    public Map<String, String> columns = new LinkedHashMap<>();
    public TableDocModel tableDocModel;

    public TableCatalogEntry() {
    }

    public String getQualifiedName() {
        String result = (this.schema != null ? this.schema : "UNKNOWN") + "." + (this.tableName != null ? this.tableName : "UNKNOWN");
        return result;
    }
}
