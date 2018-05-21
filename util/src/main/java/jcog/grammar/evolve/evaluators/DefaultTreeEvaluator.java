/*
 * Copyright (C) 2015 Machine Learning Lab - University of Trieste, 
 * Italy (http://machinelearning.inginf.units.it/)  
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jcog.grammar.evolve.evaluators;

import org.eclipse.collections.impl.list.mutable.FastList;
import jcog.grammar.evolve.inputs.Context;
import jcog.grammar.evolve.inputs.DataSet.Bounds;
import jcog.grammar.evolve.inputs.DataSet.Example;
import jcog.grammar.evolve.tree.Node;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 *
 * @author MaleLabTs
 */
public class DefaultTreeEvaluator implements TreeEvaluator {

    private final static Bounds[] empty = new Bounds[0];

    @Override
    public List<Bounds[]> evaluate(Node root, Context context) throws TreeEvaluationException {

        List<Bounds[]> results = new FastList(context.getCurrentDataSetLength());

        StringBuilder sb = new StringBuilder();
        root.describe(sb);

        try {
             
            Pattern regex = Pattern.compile(sb.toString());
            Matcher matcher = regex.matcher("");

            context.getCurrentDataSet().
                getExamples().
                    forEach(e ->
                        eval(results, matcher, e));

        } catch (PatternSyntaxException ex) {
            throw new TreeEvaluationException(ex);
        }
        return results;
    }

    private static void eval(List<Bounds[]> results, Matcher matcher, Example example) {
        try {
            Matcher m = matcher.reset(example.getString());

            List<Bounds> b = new FastList<>(0);

            while (m.find()) {
                b.add(new Bounds(matcher.start(0), matcher.end(0)));
            }

            if (!b.isEmpty())
                results.add(b.toArray(new Bounds[b.size()]));
            else
                results.add(empty);

        } catch (StringIndexOutOfBoundsException ex) {
            /**
             * Workaround: ref BUG: 6984178
             * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6984178
             * with greedy quantifiers returns exception
             * instead than "false".
             */
            results.add(empty);
        }
    }

    @Override
    public void setup(Map<String, String> parameters) {
    }
}
