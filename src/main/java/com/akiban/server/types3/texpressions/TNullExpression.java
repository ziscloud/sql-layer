/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.types3.texpressions;

import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.row.Row;
import com.akiban.server.explain.*;
import com.akiban.server.explain.std.TExpressionExplainer;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueSources;

public final class TNullExpression implements TPreparedExpression {

    public TNullExpression (TInstance tInstance) {
        this.tInstance = tInstance.withNullable(true);
    }

    @Override
    public TPreptimeValue evaluateConstant(QueryContext queryContext) {
        TEvaluatableExpression eval = build();
        return new TPreptimeValue(tInstance, eval.resultValue());
    }

    @Override
    public TInstance resultType() {
        return tInstance;
    }

    @Override
    public TEvaluatableExpression build() {
        return new InnerEvaluation(tInstance);
    }

    @Override
    public CompoundExplainer getExplainer(ExplainContext context)
    {
        CompoundExplainer ex = new TExpressionExplainer(Type.LITERAL, "Literal", context);
        ex.addAttribute(Label.OPERAND, PrimitiveExplainer.getInstance("NULL"));
        return ex;
    }

    @Override
    public String toString() {
        return "Literal(NULL)";
    }

    private final TInstance tInstance;

    private static class InnerEvaluation implements TEvaluatableExpression {
        @Override
        public PValueSource resultValue() {
            return valueSource;
        }

        @Override
        public void evaluate() {
        }

        @Override
        public void with(Row row) {
        }

        @Override
        public void with(QueryContext context) {
        }

        private InnerEvaluation(TInstance underlying) {
            this.valueSource = PValueSources.getNullSource(underlying);
        }

        private final PValueSource valueSource;
    }
}
