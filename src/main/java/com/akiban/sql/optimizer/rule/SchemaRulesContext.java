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

package com.akiban.sql.optimizer.rule;

import com.akiban.server.expression.ExpressionFactory;
import com.akiban.sql.optimizer.plan.PhysicalSelect.PhysicalResultColumn;
import com.akiban.sql.optimizer.plan.ResultSet.ResultField;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;

import com.akiban.sql.types.DataTypeDescriptor;

import com.akiban.qp.rowtype.Schema;

import java.util.List;

/** The context associated with an AIS schema. */
public class SchemaRulesContext extends RulesContext
{
    private Schema schema;
    private ExpressionFactory expressionFactory;

    public SchemaRulesContext(AkibanInformationSchema ais, ExpressionFactory expressionFactory, List<BaseRule> rules) {
        super(rules);
        schema = new Schema(ais);
        this.expressionFactory = expressionFactory;
    }

    public Schema getSchema() {
        return schema;
    }

    public PhysicalResultColumn getResultColumn(ResultField field) {
        return new PhysicalResultColumn(field.getName());
    }

    public ExpressionFactory getExpressionFactory() {
        return expressionFactory;
    }

    // TODO: Something like this.
    /*
    public AggregatorFactory getAggregatorFactory() {...}
    */
      
}