/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.ais.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HKeyColumn
{
    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(column().getTable().getName().getTableName());
        buffer.append('.');
        buffer.append(column().getName());
        return buffer.toString();
    }

    public HKeySegment segment()
    {
        return segment;
    }

    public Column column()
    {
        return column;
    }

    public List<Column> equivalentColumns()
    {
        if (equivalentColumns == null) {
            synchronized (lazyDerivedDataLock) {
                if (equivalentColumns == null) {
                    assert column.getTable().isGroupTable() : "null equivalentColumns on non-group-table column: " + column;
                    throw new UnsupportedOperationException("group tables have no equivalent columns");
                }
            }
        }
        return equivalentColumns;
    }

    // Returns a list of tables whose hkey would be affected by a modification of this column. E.g.,
    // in a COI schema, the dependentTables of the hkey column O.CIDs are (O, I). I is included because
    // its hkey depends on O.CID, not having a CID of its own.
    //
    // The return value is null for an hkey column not belonging to this hkey column's segment's hkey's table.
    // E.g. The hkey of I contains O.CID, so dependentTables is null.
    public List<UserTable> dependentTables()
    {
        boolean mayHaveDependentTables = segment.hKey().userTable() == column.getUserTable();
        if (mayHaveDependentTables) {
            if (dependentTables == null) {
                synchronized (lazyDerivedDataLock) {
                    if (dependentTables == null) {
                        dependentTables = new ArrayList<UserTable>();
                        findDependentTables(column, column.getUserTable(), dependentTables);
                    }
                }
            }
        }
        return dependentTables;
    }
    
    public int positionInHKey()
    {
        return positionInHKey;
    }

    public HKeyColumn(HKeySegment segment, Column column)
    {
        this.segment = segment;
        this.column = column;
        this.positionInHKey = segment.positionInHKey() + segment.columns().size() + 1;
        if (column.getTable().isUserTable()) {
            UserTable userTable = (UserTable) column.getTable();
            this.equivalentColumns = Collections.unmodifiableList(userTable.matchingColumns(column));
        }
    }

    // For use by this class
    
    private void findDependentTables(Column column, UserTable table, List<UserTable> dependentTables)
    {
        boolean dependent = false;
        for (HKeySegment segment : table.hKey().segments()) {
            for (HKeyColumn hKeyColumn : segment.columns()) {
                dependent = dependent || hKeyColumn.column() == column;
            }
        }
        if (dependent) {
            dependentTables.add(table);
        }
        for (Join join : table.getChildJoins()) {
            findDependentTables(column, join.getChild(), dependentTables);
        }
    }

    // State

    private HKeySegment segment;
    private Column column;
    private int positionInHKey;
    private final Object lazyDerivedDataLock = new Object();
    // If column is a group table column, then we need to know all columns in the group table that are constrained
    // to have matching values, e.g. customer$cid and order$cid. For a user table, equivalentColumns contains just
    // column.
    private volatile List<Column> equivalentColumns;
    private volatile List<UserTable> dependentTables;
}
