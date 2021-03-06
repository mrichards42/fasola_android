/*
 * This file is part of FaSoLa Minutes for Android.
 * Copyright (c) 2016 Mike Richards. All rights reserved.
 */

package org.fasola.fasolaminutes;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A namespace class with SQL helpers
 */

public class SQL {
    public static final String INDEX_COLUMN = "sql_section_index";

    public static class JoinException extends RuntimeException {
        public JoinException() {
            super();
        }

        public JoinException(String msg) {
            super(msg);
        }

        public JoinException(BaseTable table1, BaseTable table2) {
            super("No join path found between " + table1 + " and " + table2);
        }
    }

    private static class JoinEntry {
        JoinEntry(String text, boolean isLeft) {
            this.text = text;
            this.isLeft = isLeft;
        }
        String text;
        boolean isLeft;
    }

    private static class JoinMap extends HashMap<String, Map<String, JoinEntry>> {
        public JoinEntry put(Object t1, Object t2, String text, boolean isLeft) {
            String key1 = t1.toString();
            String key2 = t2.toString();
            Map<String, JoinEntry> map = get(key1);
            if (map == null) {
                map = new HashMap<>();
                super.put(key1, map);
            }
            return map.put(key2, new JoinEntry(text, isLeft));
        }
    }

    /**
     * Base class for SQL table contracts
     *
     * <p>Provides automatic TABLE_NAME and id fields
     *
     * <p>Simple and complex JOINs can be made with {@link Query}
     */
    public static class BaseTable {
        public String TABLE_NAME;
        public Column id;
        protected Map<String, Column> _columns; // Make escaped column key to Column
        protected static JoinMap joinMap = new JoinMap();
        private static final int MAX_KEY_LENGTH = 20;

        protected BaseTable(String tableName) {
            TABLE_NAME = tableName;
            _columns = new HashMap<>();
            id = column("id");
        }

        // Override this to initialize calculated columns
        // Essentially a two-part constructor, this is required to reliably use columns
        // from other tables in calculations
        protected void onCreate() {
        }

        /**
         * Create a simple column
         *
         * @param name Column name
         * @return {@link Column}
         */
        public Column column(String name) {
            return column(new Column(this, name));
        }

        /**
         * Add an existing column
         *
         * @param col {@link Column}
         * @return The existing column
         */
        public Column column(Column col) {
            // Keep long column names down
            if (col.key.length() > TABLE_NAME.length() + MAX_KEY_LENGTH)
                col.key = TABLE_NAME + "_col" + _columns.values().size();
            _columns.put(col.key, col);
            return col;
        }

        /**
         * Add a subquery as a column
         * @param query {@link Query}
         * @return The query as a new column
         */
        public Column subQuery(String query) {
            return column(Column.SubQuery(this, query));
        }

        /**
         * Add a subquery as a column
         * @param col {@link Column} single column from a related table
         * @return The query as a new column
         */
        public Column subQuery(Column col) {
            // Pretend that this is a normal query that joins to this table, then pull off
            // the JOIN ON clause and turn it into a WHERE clause for the subuery
            Query query = col.getTable().select(col).as("col").join(col.getTable(), this);
            String thisJoin = query.joins.get(TABLE_NAME);
            // thisJoin should have a value, since join() will throw an exception
            // if no join is defined between the two tables
            String whereClause = thisJoin.substring(thisJoin.indexOf(" ON ") + 4);
            query.whereList.add(new Query.QueryStringBuilder().append(whereClause));
            query.joins.remove(TABLE_NAME);
            return subQuery(query.toString());
        }

        // Create a stored query as a column
        public Column queryColumn(Object... args) {
            return column(new QueryColumn(args));
        }

        public Column concat(Object... args) {
            // Insert concat operators (" || ") between each argument
            Object[] concatArgs = new Object[args.length + args.length - 1];
            for (int i = 0; i < args.length; i++) {
                concatArgs[i*2] = args[i];
                if (i != args.length-1)
                    concatArgs[i*2+1] = " || ";
            }
            return column(new QueryColumn(concatArgs));
        }

        // Add an entry to the joinMap, joining two tables by their columns
        public static void join(Column col1, Column col2) {
            join(col1.getTable(), col2.getTable(), col1 + " = " + col2, false);
        }

        public static void leftJoin(Column col1, Column col2) {
            join(col1.getTable(), col2.getTable(), col1 + " = " + col2, true);
        }

        public static void join(BaseTable t1, BaseTable t2, String text) {
            join(t1, t2, text, false);
        }

        public static void leftJoin(BaseTable t1, BaseTable t2, String text) {
            join(t1, t2, text, true);
        }

        public static void join(BaseTable t1, BaseTable t2, String text, boolean isLeft) {
            joinMap.put(t1, t2, text, isLeft);
            joinMap.put(t2, t1, text, isLeft);
        }

        // Get the JoinEntry used to join two tables
        protected static JoinEntry getJoin(Object t1, Object t2) {
            Map<String, JoinEntry> map = joinMap.get(t1.toString());
            if (map == null)
                return null;
            return map.get(t2.toString());
        }

        // Default count for the table is on _id
        public Column count() {
            return count(false);
        }

        public Column countDistinct() {
            return count(true);
        }

        public Column count(boolean distinct) {
            return id.count(distinct);
        }

        // Default string
        public String toString() {
            return TABLE_NAME;
        }

        // Query wrappers
        //---------------

        protected SQLiteDatabase getDb() {
            // Override to provide an SQLiteDatabase for queries
            return null;
        }

        // Create a new BaseTable DAO and load cursor values
        @SuppressWarnings("unchecked")
        public <T extends BaseTable> T fromCursor(Cursor cursor) {
            // Create the object
            T obj;
            try {
                obj = (T) this.getClass().newInstance();
            } catch(InstantiationException e) {
                return null;
            } catch(IllegalAccessException e) {
                return null;
            }
             // Setup secondary fields
            obj.onCreate();
            if (! obj.loadCursor(cursor))
                return null;
            return obj;
        }

        // Load values from the cursor into a BaseTable DAO
        public boolean loadCursor(Cursor cursor) {
            // Move to the first item if the cursor isn't at a position yet
            if (cursor.isBeforeFirst() && ! cursor.moveToFirst())
                return false;
            // Clear values
            for (Column col : _columns.values())
                col.setValue(null);
            // Fill values from the cursor
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                Column col = _columns.get(cursor.getColumnName(i));
                if (col != null)
                    col.setValue(cursor.getString(i));
            }
            return true;
        }

        protected <T extends BaseTable> T doQuery(Column field, Object value) {
            // Make a column list
            Column[] cols = new Column[_columns.size()];
            int idx = 0;
            for (Column col : _columns.values())
                cols[idx++] = col;
            // Make the query
            Query query = select();
            for (Column col : cols)
                query.select(col);
            query.from(this).whereEq(field).group(this.id);
            // Query
            Cursor cursor = getDb().rawQuery(query.toString(), new String[] {value.toString()});
            return fromCursor(cursor);
        }

        // Return a new BaseTable object that functions as a DAO
        protected <T extends BaseTable> T get(long id) {
            return get(this.id, id);
        }

        @SuppressWarnings("unchecked")
        protected <T extends BaseTable> T get(Column field, Object value) {
            return doQuery(field, value);
        }

        // Return a Query object for this table
        public Query select(Object... args) {
            return SQL.select(args).from(this);
        }

        // Return a Query object for a CursorAdapter (i.e. it already has the ID column)
        public Query selectList(Object... args) {
            return SQL.select(id).select(args).group(id).from(this);
        }
    }

    /**
     * Single column in a {@link BaseTable}
     *
     * @see QueryColumn
     */
    public static class Column {
        private String name;
        private String key;
        private BaseTable table;
        private boolean isSubquery = false;

        protected Column() {
            setName("");
        }

        public Column(BaseTable table, String columnName) {
            this.table = table;
            setName(table.toString() + "." + columnName);
        }

        public static Column SubQuery(BaseTable table, String subquery) {
            Column col = new Column();
            col.table = table;
            col.setName("(" + subquery + ")");
            col.isSubquery = true;
            return col;
        }

        public String toString() {
            return name;
        }

        public String getName() {
            return name;
        }

        static final Pattern keyPat = Pattern.compile("\\W+");
        public void setName(String name) {
            this.name = name;
            key = "col_" + keyPat.matcher(name).replaceAll("_");
        }

        public String getKey() {
            return key;
        }

        public BaseTable getTable() {
            return table;
        }

        // Column formatting: Use {column} for the column
        public Column format(String fmt, Object... args) {
            if (isSubquery) {
                // Extract the column part of the subquery
                Pattern pattern = Pattern.compile("^\\(SELECT\\s+(.*?)\\s+(?=AS|FROM)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(name);
                if (matcher.find()) {
                    // Format and return
                    String column = QueryColumn.formatString(fmt, matcher.group(1), args);
                    return Column.SubQuery(table, "(SELECT " + column + " " + name.substring(matcher.end()));
                }
            }
            return QueryColumn.fromFormat(fmt, this, args);
        }

        // Aggregate functions
        public Column count() {
            return count(false);
        }

        public Column countDistinct() {
            return count(true);
        }

        public Column count(boolean distinct) {
            return func("COUNT", distinct);
        }

        public Column cast(String type) {
            return format("CAST({column} AS %s)", type);
        }

        public Column sum() {
            return func("SUM");
        }

        // Generic functions
        public Column func(String funcName) {
            return func(funcName, false);
        }

        public Column func(String funcName, boolean distinct) {
            return format("%s(%s{column})", funcName, distinct ? "DISTINCT " : "");
        }

        // Extra argument aggregates, cannot use DISTINCT
        public Column func(String funcName, String... args) {
            return format("%s({column}, %s)", funcName, TextUtils.join(", ", args));
        }

        // Add the required joins to a query
        public void addJoin(Query query, BaseTable fromTable) {
            if (fromTable.toString().equals(table.toString()))
                return;
            query.join(fromTable, table);
        }

        // For use as a DAO
        private String value;
        protected void setValue(String value) {
            this.value = value;
        }

        public String getString() {
            return value;
        }

        public float getFloat() {
            return Float.parseFloat(value);
        }

        public int getInt() {
            return Integer.parseInt(value);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isNull() {
            return value == null;
        }
    }

    /**
     * A query that includes SQL operators/aggregate functions/etc
     * Can contain multiple columns
     */
    public static class QueryColumn extends Column {
        // Can have multiple columns from multiple tables
        protected List<BaseTable> tables;

        public QueryColumn(Object... args) {
            super();
            tables = new ArrayList<>();
            // Make the query
            StringBuilder q = new StringBuilder();
            for (Object arg : args)
                q.append(arg.toString());
            setName(q.toString());
            addTables(args);
        }

        protected static Column fromFormat(String fmt, Column col, Object... args) {
            QueryColumn q = new QueryColumn(col);
            q.addTables(args);
            q.setName(formatString(fmt, col, args));
            return q;
        }

        public static String formatString(String fmt, Object col, Object... args) {
            // fmt uses {} or {column} as the column placeholder
            String colName = col.toString().replace("%", "%%");
            return String.format(
                fmt.replace("{}", colName).replace("{column}", colName),
                args
            );
        }

        // Add any required tables to the table list
        protected void addTables(Object... cols) {
            for (Object arg : cols) {
                if (arg instanceof Column)
                    if (arg instanceof QueryColumn)
                        tables.addAll(((QueryColumn) arg).tables);
                    else
                        tables.add(((Column) arg).table);
            }
        }

        @Override
        public BaseTable getTable() {
            return tables.get(0);
        }

        // Override to add all joins in the tables list
        @Override
        public void addJoin(Query query, BaseTable fromTable) {
            for (BaseTable otherTable : tables)
                if (! fromTable.toString().equals(otherTable.toString()))
                    query.join(fromTable, otherTable);
        }
    }


    /**
     *  Homemade query builder
     */
    public static Query select(Object... args) {
        return new Query("SELECT", args);
    }

    // Different semantics for UNION
    public static Query union(Query q, Query... queries) {
        return q.union(queries);
    }

    @SuppressWarnings("SameParameterValue")
    public static class Query {
        // Query elements
        protected String queryType;
        protected boolean isDistinct;
        protected List<Pair<Object, String>> selectColumns = new ArrayList<>(); // Column, alias
        protected List<Column> joinColumns = new ArrayList<>();
        protected BaseTable fromTable;
        protected Map<String, String> joins = new LinkedHashMap<>(); // table name, join statement
        protected QueryStringBuilder strGroup = new QueryStringBuilder(" GROUP BY");
        protected List<QueryStringBuilder> havingList = new ArrayList<>();
        protected List<QueryStringBuilder> whereList = new ArrayList<>();
        protected List<QueryStringBuilder> lastList; // last where/having list to use for and and or
        protected QueryStringBuilder strOrder = new QueryStringBuilder(" ORDER BY");
        protected Object limit;
        protected Object offset;

        protected List<Query> union;

        protected Query(String type, Object... args) {
            queryType = type;
            select(args);
        }

        protected Query(Query other) {
            queryType = other.queryType;
            isDistinct = other.isDistinct;
            selectColumns = new ArrayList<>(other.selectColumns);
            joinColumns = new ArrayList<>(other.joinColumns);
            fromTable = other.fromTable;
            joins = new LinkedHashMap<>(other.joins);
            strGroup = new QueryStringBuilder(other.strGroup);
            whereList = new ArrayList<>();
            for (QueryStringBuilder q : other.whereList)
                whereList.add(new QueryStringBuilder(q));
            havingList = new ArrayList<>();
            for (QueryStringBuilder q : other.havingList)
                havingList.add(new QueryStringBuilder(q));
            lastList = other.lastList;
            strOrder = new QueryStringBuilder(other.strOrder);
            limit = other.limit;
            offset = other.offset;
            union = other.union; // Shouldn't need to change the other queries in the union
        }

        // SELECT
        // ------

        // Adds an automatic alias for every column (Column.key or "column[0-n]")
        // Default to FROM the table of the first select argument
        public Query select(Object... cols) {
            for (Object col : cols) {
                String alias = "column" + selectColumns.size();
                if (col instanceof Column) {
                    alias = ((Column) col).getKey();
                    if (fromTable == null)
                        fromTable = ((Column) col).getTable();
                }
                // Preserve _id column for CursorAdapters
                if (col.toString().endsWith("._id") || col.toString().endsWith(".id"))
                    alias = "_id";
                Pair<Object, String> pair = Pair.create(col, alias);
                selectColumns.add(pair);
                addJoinColumn(col);
            }
            return this;
        }

        public Query distinct(boolean distinct) {
            isDistinct = distinct;
            return this;
        }

        public Query distinct() {
            return distinct(true);
        }

        public Query as(String alias) {
            // Update the last alias
            int idx = selectColumns.size() - 1;
            Pair<Object, String> pair = selectColumns.get(idx);
            selectColumns.set(idx, Pair.create(pair.first, alias));
            return this;
        }

        // Select a column to use as a section index
        public Query sectionIndex(Object col) {
            return select(col).as(INDEX_COLUMN);
        }

        // Select a section index column and sort by this column
        public Query sectionIndex(Object col, String ascDesc) {
            return sectionIndex(col).order(col, ascDesc);
        }

        // Does this query already have a section index column?
        public boolean hasSectionIndex() {
            for (Pair<Object, String> col : selectColumns) {
                if (col.second.equals(INDEX_COLUMN))
                    return true;
            }
            return false;
        }

        // FROM
        // ----
        public Query from(BaseTable table) {
            fromTable = table;
            return this;
        }

        // JOIN
        // ----

        // Join using defined relationships
        public Query join(BaseTable t1, BaseTable t2) {
            return _join(t1, t2, false);
        }

        public Query join(BaseTable t2) {
            return join(fromTable, t2);
        }

        public Query leftJoin(BaseTable t1, BaseTable t2) {
            return _join(t1, t2, true);
        }

        public Query leftJoin(BaseTable t2) {
            return leftJoin(fromTable, t2);
        }

        // Join on specified columns
        public Query join(Object table, Column on1, Column on2) {
            return _join(table, on1 + " = " + on2, false);
        }

        public Query leftJoin(Object table, Column on1, Column on2) {
            return _join(table, on1 + " = " + on2, true);
        }

        // Search for a columns to complete this join
        protected Query _join(BaseTable t1, BaseTable t2, boolean isLeft) {
            // Table already joined
            if (joins.containsKey(t2.toString()))
                return this;
            // Simple join exists
            JoinEntry entry = BaseTable.getJoin(t1, t2);
            if (entry != null)
                return _join(t2, entry.text, entry.isLeft || isLeft);
            // Look for a simple join from already joined tables
            for (String joinTable : joins.keySet()) {
                entry = BaseTable.getJoin(joinTable, t2);
                if (entry != null)
                    return _join(t2, entry.text, entry.isLeft || isLeft);
            }
            // Look for many : many join
            String joinTable = "";
            Map<String, JoinEntry> map = BaseTable.joinMap.get(t1.TABLE_NAME);
            if (map == null)
                throw new JoinException(t1, t2);
            for (String intermediate : map.keySet()) {
                if (BaseTable.getJoin(intermediate, t2) != null) {
                    // Require ambiguous many : many joins to be resolved explicitly
                    // e.g. select(...).join()
                    if (joinTable.isEmpty())
                        joinTable = intermediate;
                    else
                        throw new JoinException(
                                "Multiple join paths exist between " + t1 + " and " + t2 + ". " +
                                "Ambiguity can be resolved by join()'ing an intermediate table");
                }
            }
            if (joinTable.isEmpty())
                throw new JoinException(t1, t2);
            // Join to the intermediate table
            JoinEntry firstJoin = BaseTable.getJoin(t1, joinTable);
            if (firstJoin == null)
                throw new JoinException(t1, t2);
            _join(joinTable, firstJoin.text, firstJoin.isLeft || isLeft);
            // Join to the second table
            JoinEntry secondJoin = BaseTable.getJoin(joinTable, t2);
            if (secondJoin == null)
                throw new JoinException(t1, t2);
            _join(t2, secondJoin.text, secondJoin.isLeft || isLeft);
            return this;
        }

        protected Query _join(Object table, String joinOn, boolean isLeft) {
            if (joins.containsKey(table.toString()))
                return this;
            joins.put(table.toString(),
                (isLeft ? " LEFT JOIN " : " JOIN ") + table + " ON " + joinOn);
            return this;
        }

        protected void addJoinColumn(Object col) {
            if (col instanceof Column)
                joinColumns.add((Column) col);
        }

        // WHERE/HAVING
        // ------------

        // Call either where() or having()
        // and(), or() operate on whichever where() or having() was used most recently

        public Query where(Object col, Object oper, Object val) {
            lastList = whereList;
            return _whereHaving(col, oper, val);
        }

        public Query having(Object col, Object oper, Object val) {
            lastList = havingList;
            return _whereHaving(col, oper, val);
        }

        public Query and(Object col, Object oper, Object val) {
            return _addWhereHaving(" AND", col, oper, val);
        }

        public Query or(Object col, Object oper, Object val) {
            return _addWhereHaving(" OR", col, oper, val);
        }

        // Better semantics for WHERE column = ? AND column = ? ...
        public Query whereEq(Object firstColumn, Object... columns) {
            where(firstColumn, "=", "?");
            for (Object columnName : columns)
                and(columnName, "=", "?");
            return this;
        }

        private String _escapeWhereHaving(Object val) {
            if (val == null) {
                return "NULL";
            } else if (val instanceof Number || val instanceof SQL.Column) {
                return val.toString();
            }
            String strVal = val.toString();
            String cmp = strVal.toUpperCase();
            if (! (strVal.equals("?") || cmp.equals("NULL")))
                return DatabaseUtils.sqlEscapeString(strVal);
            return strVal;
        }

        protected Query _whereHaving(Object col, Object oper, Object val) {
            // Don't add another QueryStringBuilder if the last one is empty
            if (lastList.isEmpty() || ! lastList.get(lastList.size() - 1).isEmpty())
                lastList.add(new QueryStringBuilder());
            return _addWhereHaving(col, oper, val);
        }

        protected Query _addWhereHaving(Object col, Object oper, Object val) {
            return _addWhereHaving("", col, oper, val);
        }

        protected Query _addWhereHaving(Object bool, Object col, Object oper, Object val) {
            QueryStringBuilder q = lastList.get(lastList.size() - 1);
            if (oper == null)
                oper = "=";
            if (val == null)
                val = "?";
            else if (oper.toString().toUpperCase().equals("IN") && val.getClass().isArray()) {
                // Treat val as an array of objects to be escaped
                Object[] valArray = (Object[]) val;
                String[] escapedVals = new String[valArray.length];
                for (int i = 0; i < valArray.length; i++)
                    escapedVals[i] = _escapeWhereHaving(valArray[i].toString());
                val = "(" + TextUtils.join(",", escapedVals) + ")";
            }
            else {
                val = _escapeWhereHaving(val);
            }
            q.append(bool, " ", col, oper, val);
            // Add join
            addJoinColumn(col);
            return this;
        }

        // GROUP BY, ORDER BY, LIMIT
        // -------------------------
        public Query group(Object... cols) {
            if (! strGroup.isEmpty())
                strGroup.append(",");
            strGroup.append(" ").appendDelim(", ", cols);
            addJoinColumn(cols[0]);
            return this;
        }

        public Query orderAsc(Object arg) {
            return order(arg, "ASC");
        }

        public Query orderDesc(Object arg) {
            return order(arg, "DESC");
        }

        public Query order(Object... args) {
            for (int i = 0; i < args.length; i+=2) {
                if (! strOrder.isEmpty())
                    strOrder.append(", ");
                else
                    strOrder.append(" ");
                // Column
                strOrder.append(args[i]).append(" ");
                addJoinColumn(args[i]);
                // ASC/DESC.  Assume ASC if there are an odd number of args
                if (args.length > i+1)
                    strOrder.append(args[i+1]);
                else
                    strOrder.append("ASC");
            }
            return this;
        }

        public Query limit(Object limit) {
            this.limit = limit;
            return this;
        }

        public Query limit(Object offset, Object limit) {
            this.offset = offset;
            this.limit = limit;
            return this;
        }

        public Query offset(Object offset) {
            this.offset = offset;
            return this;
        }

        // Union
        public Query union(Query... other) {
            if (union == null)
                union = new ArrayList<>();
            union.addAll(Arrays.asList(other));
            return this;
        }

        // Filter: create a copy of the query to use as a filter
        public Query copy() {
            return new Query(this);
        }

        // Assemble the query
        public String toString() {
            QueryStringBuilder q = new QueryStringBuilder("SELECT");
            if (isDistinct)
                q.append(" DISTINCT");
            // Columns
            {
                Set<String> aliases = new HashSet<>();
                String delim = " ";
                for (Pair<Object, String> col : selectColumns) {
                    q.append(delim).append(col.first.toString());
                    // Make sure there are no duplicate aliases by appending a sequential number if necessary
                    String alias = col.second;
                    int i = 1;
                    while (aliases.contains(alias))
                        alias = col.second + i++;
                    // Add alias
                    aliases.add(alias);
                    q.append(" AS ").append(alias);
                    delim = ", ";
                }
            }
            // From
            q.append(" FROM ").append(fromTable);
            // Check columns for joins
            for (Column col : joinColumns)
                col.addJoin(this, fromTable);
            // Add joins
            for (String str : joins.values())
                q.append(str);
            // Where/Group By/Having
            toString_whereHaving(q, whereList, "WHERE");
            q.append(strGroup);
            toString_whereHaving(q, havingList, "HAVING");
            // Union
            if (union != null)
                for (Query other : union)
                    q.append(" UNION ").append(other.toString());
            // Order By
            q.append(strOrder);
            // Limit/offset
            if (limit != null)
                q.append(" LIMIT ").append(limit);
            if (offset != null)
                q.append(" OFFSET ").append(offset);
            return q.toString();
        }

        // Process where or having list
        private void toString_whereHaving(QueryStringBuilder q, List<QueryStringBuilder> list, String type) {
            if (list.size() > 0) {
                boolean hasClause = false;
                for (QueryStringBuilder subClause : list) {
                    if (! subClause.isEmpty()) {
                        // Make sure we actually have a where clause before adding "WHERE"
                        if (! hasClause) {
                            q.append(" " + type);
                        }
                        q.append(hasClause ? " AND " : "",  "(", subClause, ")");
                        hasClause = true;
                    }
                }
            }
        }

        // Helper class that provides better append methods
        private static class QueryStringBuilder {
            public StringBuilder q;
            public boolean mHasValue;

            QueryStringBuilder() {
                q = new StringBuilder();
            }
            QueryStringBuilder(String initial) {
                q = new StringBuilder(initial);
            }

            protected QueryStringBuilder(QueryStringBuilder other) {
                q = new StringBuilder(other.q.toString());
                mHasValue = other.mHasValue;
            }

            // append overloads
            public QueryStringBuilder append(Object str) {
                mHasValue = true;
                q.append(str.toString());
                return this;
            }

            public QueryStringBuilder append(Object... args) {
                return appendDelim(" ", args);
            }

            public QueryStringBuilder appendDelim(String delim, Object... args) {
                append(args[0]);
                for (int i = 1; i < args.length; i++)
                    append(delim).append(args[i]);
                return this;
            }

            @SuppressWarnings("BooleanMethodIsAlwaysInverted")
            public boolean isEmpty() {
                return ! mHasValue;
            }

            public String toString() {
                return mHasValue ? q.toString() : "";
            }
        }
    }
}