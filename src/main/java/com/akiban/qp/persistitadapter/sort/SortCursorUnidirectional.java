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

package com.akiban.qp.persistitadapter.sort;

import com.akiban.ais.model.IndexColumn;
import com.akiban.qp.expression.BoundExpressions;
import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Bindings;
import com.akiban.qp.persistitadapter.PersistitAdapter;
import com.akiban.qp.persistitadapter.PersistitAdapterException;
import com.akiban.qp.row.Row;
import com.akiban.server.PersistitKeyValueTarget;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.std.Comparison;
import com.akiban.server.expression.std.Expressions;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.conversion.Converters;
import com.persistit.Key;
import com.persistit.exception.PersistitException;

import java.util.List;

class SortCursorUnidirectional extends SortCursor
{
    // Cursor interface

    @Override
    public void open(Bindings bindings)
    {
        exchange.clear();
        if (bounded) {
            evaluateBoundaries(bindings);
            if (startKey == null) {
                exchange.append(startBoundary);
            } else {
                if (direction == FORWARD && !startInclusive || direction == BACKWARD && startInclusive) {
                    // - direction == FORWARD && !startInclusive: If the search key is (10, 5) and there are
                    //   rows (10, 5, ...) then we do not want them if !startInclusive. Making the search key
                    //   (10, 5, AFTER) will cause these records to be skipped.
                    // - direction == BACKWARD && startInclusive: Similarly, going in the other direction, we do the
                    //   (10, 5, ...) records if startInclusive. But an LTEQ traversal would miss it unless we search
                    //   for (10, 5, AFTER).
                    startKey.append(Key.AFTER);
                }
                startKey.copyTo(exchange.getKey());
            }
        } else {
            exchange.append(startBoundary);
        }
    }

    @Override
    public Row next()
    {
        Row next = null;
        if (exchange != null) {
            try {
                if (exchange.traverse(keyComparison, true)) {
                    next = row();
                    if (bounded) {
                        if (pastEnd()) {
                            next = null;
                            close();
                        } else {
                            keyComparison = subsequentKeyComparison;
                        }
                    }
                } else {
                    close();
                }
            } catch (PersistitException e) {
                close();
                throw new PersistitAdapterException(e);
            }
        }
        return next;
    }

    // SortCursorUnidirectional interface

    public static SortCursorUnidirectional create(PersistitAdapter adapter,
                                                  IterationHelper iterationHelper,
                                                  IndexKeyRange keyRange,
                                                  API.Ordering ordering)
    {
        return
            keyRange == null || keyRange.unbounded()
            ? new SortCursorUnidirectional(adapter, iterationHelper, ordering)
            : new SortCursorUnidirectional(adapter, iterationHelper, keyRange, ordering);
    }

    // For use by this subclasses

    protected SortCursorUnidirectional(PersistitAdapter adapter,
                                      IterationHelper iterationHelper,
                                      IndexKeyRange keyRange,
                                      API.Ordering ordering)
    {
        super(adapter, iterationHelper);
        this.bounded = true;
        this.adapter = adapter;
        this.lo = keyRange.lo();
        this.hi = keyRange.hi();
        if (ordering.allAscending()) {
            this.direction = FORWARD;
            this.start = this.lo;
            this.startInclusive = keyRange.loInclusive();
            this.end = this.hi;
            this.endInclusive = keyRange.hiInclusive();
            this.keyComparison = startInclusive ? Key.GTEQ : Key.GT;
            this.subsequentKeyComparison = Key.GT;
            this.startBoundary = Key.BEFORE;
        } else if (ordering.allDescending()) {
            this.direction = BACKWARD;
            this.start = this.hi;
            this.startInclusive = keyRange.hiInclusive();
            this.end = this.lo;
            this.endInclusive = keyRange.loInclusive();
            this.keyComparison = startInclusive ? Key.LTEQ : Key.LT;
            this.subsequentKeyComparison = Key.LT;
            this.startBoundary = Key.AFTER;
        } else {
            assert false : ordering;
        }
        this.startKey = adapter.newKey();
        this.endKey = adapter.newKey();
        this.boundColumns = keyRange.boundColumns();
        this.types = new AkType[boundColumns];
        List<IndexColumn> indexColumns = keyRange.indexRowType().index().getColumns();
        for (int f = 0; f < boundColumns; f++) {
            this.types[f] = indexColumns.get(f).getColumn().getType().akType();
        }
    }

    protected void evaluateBoundaries(Bindings bindings)
    {
        /*
            Null bounds are slightly tricky. An index restriction is described by an IndexKeyRange which contains
            two IndexBounds. The IndexBound wraps an index row. The fields of the row that are being restricted are
            described by the IndexBound's ColumnSelector. The only index restrictions supported specify:
            a) equality for zero or more fields of the index,
            b) 0-1 inequality, and
            c) any remaining columns unbounded.

            By the time we get here, we've stopped paying attention to part c. Parts a and b occupy the first
            orderingColumns columns of the index. Now about the nulls: For each field of parts a and b, we have a
            lo value and a hi value. There are four cases:

            - both lo and hi are non-null: Just write the field values into startKey and endKey.

            - lo is null: Write null into the startKey.

            - hi is null, lo is not null: This restriction says that we want everything to the right of
              the lo value. Persistit ranks null lower than anything, so instead of writing null to endKey,
              we write Key.AFTER.

            - lo and hi are both null: This is NOT an unbounded case. This means that we are restricting both
              lo and hi to be null, so write null, not Key.AFTER to endKey.
        */
        // Check constraints on start and end
        BoundExpressions loExpressions = lo.boundExpressions(bindings, adapter);
        BoundExpressions hiExpressions = hi.boundExpressions(bindings, adapter);
        for (int f = 0; f < boundColumns - 1; f++) {
            ValueSource loValueSource = loExpressions.eval(f);
            ValueSource hiValueSource = hiExpressions.eval(f);
            if (loValueSource.isNull() && hiValueSource.isNull()) {
                // OK, they're equal
            } else if (loValueSource.isNull() || hiValueSource.isNull()) {
                throw new IllegalArgumentException(String.format("lo: %s, hi: %s", loValueSource, hiValueSource));
            } else {
                Expression loEQHi =
                    Expressions.compare(Expressions.valueSource(loValueSource),
                                        Comparison.EQ,
                                        Expressions.valueSource(hiValueSource));
                if (!loEQHi.evaluation().eval().getBool()) {
                    throw new IllegalArgumentException();
                }
            }
        }
        ValueSource loBound = loExpressions.eval(boundColumns - 1);
        ValueSource hiBound = hiExpressions.eval(boundColumns - 1);
        if (!loBound.isNull() && !hiBound.isNull()) {
            Expression loLEHi =
                Expressions.compare(Expressions.valueSource(loBound),
                                    Comparison.LE,
                                    Expressions.valueSource(hiBound));
            if (!loLEHi.evaluation().eval().getBool()) {
                throw new IllegalArgumentException();
            }
        }
        // Construct start and end keys
        BoundExpressions startExpressions = start.boundExpressions(bindings, adapter);
        BoundExpressions endExpressions = end.boundExpressions(bindings, adapter);
        ValueSource[] startValues = new ValueSource[boundColumns];
        ValueSource[] endValues = new ValueSource[boundColumns];
        for (int f = 0; f < boundColumns; f++) {
            startValues[f] = startExpressions.eval(f);
            endValues[f] = endExpressions.eval(f);
        }
        startKey.clear();
        startKeyTarget.attach(startKey);
        endKey.clear();
        endKeyTarget.attach(endKey);
        // Construct bounds of search. For first boundColumns - 1 columns, if start and end are both null,
        // interpret the nulls literally.
        int f = 0;
        while (f < boundColumns - 1) {
            startKeyTarget.expectingType(types[f]);
            Converters.convert(startValues[f], startKeyTarget);
            endKeyTarget.expectingType(types[f]);
            Converters.convert(endValues[f], endKeyTarget);
            f++;
        }
        // For the last column:
        //  0   >   null      <   null:      (null, AFTER)
        //  1   >   null      <   non-null:  (null, end)
        //  2   >   null      <=  null:      Shouldn't happen
        //  3   >   null      <=  non-null:  (null, end]
        //  4   >   non-null  <   null:      (start, AFTER)
        //  5   >   non-null  <   non-null:  (start, end)
        //  6   >   non-null  <=  null:      Shouldn't happen
        //  7   >   non-null  <=  non-null:  (start, end]
        //  8   >=  null      <   null:      [null, AFTER)
        //  9   >=  null      <   non-null:  [null, end)
        // 10   >=  null      <=  null:      [null, null]
        // 11   >=  null      <=  non-null:  [null, end]
        // 12   >=  non-null  <   null:      [start, AFTER)
        // 13   >=  non-null  <   non-null:  [start, end)
        // 14   >=  non-null  <=  null:      Shouldn't happen
        // 15   >=  non-null  <=  non-null:  [start, end]
        //
        if (direction == FORWARD) {
            // Start values
            startKeyTarget.expectingType(types[f]);
            Converters.convert(startValues[f], startKeyTarget);
            // End values
            if (endValues[f].isNull()) {
                if (endInclusive) {
                    if (startInclusive) {
                        // Case 10:
                        endKeyTarget.expectingType(types[f]);
                        Converters.convert(endValues[f], endKeyTarget);
                    } else {
                        // Cases 2, 6, 14:
                        assert false;
                    }
                } else {
                    // Cases 0, 4, 8, 12
                    endKey.append(Key.AFTER);
                }
            } else {
                // Cases 1, 3, 5, 7, 9, 11, 13, 15
                endKeyTarget.expectingType(types[f]);
                Converters.convert(endValues[f], endKeyTarget);
            }
        } else {
            // Same as above, swapping start and end
            // End values
            endKeyTarget.expectingType(types[f]);
            Converters.convert(endValues[f], endKeyTarget);
            // Start values
            if (startValues[f].isNull()) {
                if (startInclusive) {
                    if (endInclusive) {
                        // Case 10:
                        startKeyTarget.expectingType(types[f]);
                        Converters.convert(startValues[f], startKeyTarget);
                    } else {
                        // Cases 2, 6, 14:
                        assert false;
                    }
                } else {
                    // Cases 0, 4, 8, 12
                    startKey.append(Key.AFTER);
                }
            } else {
                // Cases 1, 3, 5, 7, 9, 11, 13, 15
                startKeyTarget.expectingType(types[f]);
                Converters.convert(startValues[f], startKeyTarget);
            }
        }
    }

    protected boolean pastEnd()
    {
        boolean pastEnd;
        if (endKey == null) {
            pastEnd = false;
        } else {
            Key key = exchange.getKey();
            assert key.getDepth() >= endKey.getDepth();
            int c = key.compareKeyFragment(endKey, 0, endKey.getEncodedSize()) * direction;
            pastEnd = c > 0 || c == 0 && !endInclusive;
        }
        return pastEnd;
    }

    // For use by this class

    private SortCursorUnidirectional(PersistitAdapter adapter,
                                     IterationHelper iterationHelper,
                                     API.Ordering ordering)
    {
        super(adapter, iterationHelper);
        this.bounded = false;
        this.adapter = adapter;
        if (ordering.allAscending()) {
            this.startBoundary = Key.BEFORE;
            this.keyComparison = Key.GT;
            this.subsequentKeyComparison = Key.GT;
        } else if (ordering.allDescending()) {
            this.startBoundary = Key.AFTER;
            this.keyComparison = Key.LT;
            this.subsequentKeyComparison = Key.LT;
        } else {
            assert false : ordering;
        }
    }

    // Class state

    private static final int FORWARD = 1;
    private static final int BACKWARD = -1;

    // Object state

    protected final PersistitAdapter adapter;
    protected final boolean bounded; // true for a scan with restrictions, false for a full scan
    protected int direction; // +1 = ascending, -1 = descending
    protected Key.Direction keyComparison;
    protected Key.Direction subsequentKeyComparison;
    protected Key.EdgeValue startBoundary; // Start of a scan that is unbounded at the start
    // For bounded scans
    protected int boundColumns; // Number of index fields with restrictions
    protected AkType[] types;
    protected IndexBound lo;
    protected IndexBound hi;
    protected IndexBound start;
    protected IndexBound end;
    protected boolean startInclusive;
    protected boolean endInclusive;
    protected Key startKey;
    protected Key endKey;
    protected final PersistitKeyValueTarget startKeyTarget = new PersistitKeyValueTarget();
    protected final PersistitKeyValueTarget endKeyTarget = new PersistitKeyValueTarget();
}