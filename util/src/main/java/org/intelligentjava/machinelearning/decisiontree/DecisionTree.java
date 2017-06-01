package org.intelligentjava.machinelearning.decisiontree;

import jcog.list.FasterList;
import org.eclipse.collections.api.block.function.primitive.IntToFloatFunction;
import org.intelligentjava.machinelearning.decisiontree.impurity.GiniIndexImpurityCalculation;
import org.intelligentjava.machinelearning.decisiontree.impurity.ImpurityCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.intelligentjava.machinelearning.decisiontree.DecisionTree.Node.leaf;

/**
 * Decision tree implementation.
 *
 * @author Ignas
 */
public class DecisionTree<K, V> {

    /**
     * When data is considered homogeneous and node becomes leaf and is labeled. If it is equal 1.0 then absolutely all
     * data must be of the same label that node would be considered a leaf.
     */
    public static final float DEFAULT_PRECISION = 0.90f;

    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(DecisionTree.class);
    /**
     * Impurity calculation method.
     */
    private final ImpurityCalculator impurityCalculator = new GiniIndexImpurityCalculation();
    /**
     * Max depth parameter. Growth of the tree is stopped once this depth is reached. Limiting depth of the tree can
     * help with overfitting, however if depth will be set too low tree will not be acurate.
     */
    private static final int maxDepth = 15;
    /**
     * Root node.
     */
    private Node root;

//    protected static <K,V> V label(K value, Collection<Function<K, V>> data) {
//        return DecisionTree.label(value, data, DEFAULT_PRECISION);
//    }

    /**
     * Returns Label if data is homogeneous.
     */
    protected static <K, V> V label(K value, Collection<Function<K, V>> data, float homogenityPercentage) {
        // group by to map <Label, count>
        Map<V, Long> labelCount = data.stream().collect(groupingBy((x) -> x.apply(value), counting()));
        long totalCount = data.size();
        for (Map.Entry<V, Long> e : labelCount.entrySet()) {
            long nbOfLabels = e.getValue();
            if ((nbOfLabels / (double) totalCount) >= homogenityPercentage) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Get root.
     */
    public Node root() {
        return root;
    }

    /**
     * Trains tree on training data for provided features.
     *
     * @param value        The value column being learned
     * @param trainingData List of training data samples.
     * @param features     List of possible features.
     */
    public void put(K value, Collection<Function<K, V>> trainingData, List<Predicate<Function<K, V>>> features, IntToFloatFunction precision) {
        root = put(value, trainingData, features, 1, precision);
    }

    /** constant precision */
    public void put(K value, Collection<Function<K, V>> data, List<Predicate<Function<K, V>>> features, float precision) {
        put(value, data, features, (depth) -> precision);
    }
    /** default constant precision */
    public void put(K value, Collection<Function<K, V>> data, List<Predicate<Function<K, V>>> features) {
        put(value, data, features, DEFAULT_PRECISION);
    }

    /**
     * Split data according to if it has this feature.
     *
     * @param data Data to by split by this feature.
     * @return Sublists of split data samples.
     */
    static <K, V> Stream<List<Function<K, V>>> split(Predicate<Function<K, V>> p, Collection<Function<K, V>> data) {
        // TODO:  maybe use sublist streams instead of creating new list just track indexes
        // http://stackoverflow.com/questions/22917270/how-to-get-a-range-of-items-from-stream-using-java-8-lambda
        Map<Boolean, List<Function<K, V>>> split = data.stream().collect(partitioningBy(p::test));

        return Stream.of(split.get(true), split.get(false));
    }

    /**
     * Grow tree during training by splitting data recusively on best feature.
     *
     * @param trainingData List of training data samples.
     * @param features     List of possible features.
     * @return Node after split. For a first invocation it returns tree root node.
     */
    protected Node put(K value, Collection<Function<K, V>> trainingData, List<Predicate<Function<K, V>>> features, int currentDepth, IntToFloatFunction depthToPrecision) {

        // if dataset already homogeneous enough (has label assigned) make this node a leaf
        V currentNodeLabel;
        if ((currentNodeLabel = label(value, trainingData, depthToPrecision.valueOf(currentDepth))) != null) {
            return leaf(currentNodeLabel); //log.debug("New leaf is created because data is homogeneous: {}", currentNodeLabel.name());
        }

        int fs = features.size();
        boolean stoppingCriteriaReached = (fs == 0) || currentDepth >= maxDepth;
        if (stoppingCriteriaReached) {
            return leaf(majority(value, trainingData)); //log.debug("New leaf is created because stopping criteria reached: {}", majorityLabel.name());
        }

        Predicate<Function<K, V>> split = bestSplit(value, trainingData, features); // get best set of literals
        //log.debug("Best split found: {}", bestSplit.toString());

        // add children to current node according to split
        // if subset data is empty add a leaf with label calculated from initial data
        // else grow tree further recursively

        //log.debug("Data is split into sublists of sizes: {}", splitData.stream().map(List::size).collect(Collectors.toList()));
        return split(split, trainingData).map(

                subsetTrainingData -> subsetTrainingData.isEmpty() ?

                        leaf(majority(value, trainingData))

                        :

                        put(value, subsetTrainingData,
                                new FasterList<>(() -> (
                                        features.stream().filter(p -> !p.equals(split)).iterator()), fs - 1),
                                currentDepth + 1,
                                depthToPrecision
                        ))

                .collect(Collectors.toCollection(() -> Node.feature(split)));
    }

    /**
     * Classify a sample.
     *
     * @param value Data sample
     * @return Return label of class.
     */
    public V get(Function<K, V> value) {
        Node node = root;
        while (!node.isLeaf()) { // go through tree until leaf is reached
            // only binary splits for now - has feature first child node(left branch), does not have feature second child node(right branch).
            node = node.get(node.feature.test(value) ? 0 : 1);
        }
        return (V) node.label;
    }

    /**
     * Finds best feature to split on which is the one whose split results in lowest impurity measure.
     */
    protected Predicate<Function<K, V>> bestSplit(K value, Collection<Function<K, V>> data, Iterable<Predicate<Function<K, V>>> features) {
        double currentImpurity = 1;
        Predicate<Function<K, V>> bestSplitFeature = null; // rename split to feature

        for (Predicate<Function<K, V>> feature : features) {

            // totalSplitImpurity = sum(singleLeafImpurities) / nbOfLeafs
            // in other words splitImpurity is average of leaf impurities
            double calculatedSplitImpurity =
                    split(feature, data).filter(list -> !list.isEmpty()).mapToDouble(splitData -> impurityCalculator.impurity(value, splitData)).average().orElse(Double.POSITIVE_INFINITY);
            if (calculatedSplitImpurity < currentImpurity) {
                currentImpurity = calculatedSplitImpurity;
                bestSplitFeature = feature;
            }
        }

        return bestSplitFeature;
    }

    /**
     * Differs from getLabel() that it always return some label and does not look at homogenityPercentage parameter. It
     * is used when tree growth is stopped and everything what is left must be classified so it returns majority label for the data.
     */
    static <K, V> V majority(K value, Collection<Function<K, V>> data) {
        // group by to map <Label, count> like in getLabels() but return Label with most counts
        return data.stream().collect(groupingBy((x) -> x.apply(value), counting())).entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    // -------------------------------- TREE PRINTING ------------------------------------

    public void print() {
        print(System.out);
    }

    public void print(PrintStream o) {
        printSubtree(root, o);
    }

    private void printSubtree(Node node, PrintStream o) {
        if (!node.isEmpty() && node.get(0) != null) {
            print(node.get(0), true, "", o);
        }
        print(node, o);
        if (node.size() > 1 && node.get(1) != null) {
            print(node.get(1), false, "", o);
        }
    }

    private static <V> void print(Node node, PrintStream o) {
        o.print(node);
        o.println();
    }

    private static <K, V> void print(Node node, boolean isRight, K indent, PrintStream o) {
        if (!node.isEmpty() && node.get(0) != null) {
            print(node.get(0), true, indent + (isRight ? "        " : " |      "), o);
        }
        o.print(indent);
        if (isRight) {
            o.print(" /");
        } else {
            o.print(" \\");
        }
        o.print("----- ");
        print(node, o);
        if (node.size() > 1 && node.get(1) != null) {
            print(node.get(1), false, indent + (isRight ? " |      " : "        "), o);
        }
    }

    static class Node extends FasterList<Node> {


        /**
         * Node's feature used to split it further.
         */
        public final Predicate feature;

        public final Object label;

        Node(Predicate feature) {
            super();
            this.feature = feature;
            this.label = null;
        }

        private Node(Predicate feature, Object label) {
            super();
            this.label = label;
            this.feature = feature;
        }

        public static Node feature(Predicate feature) {
            return new Node(feature);
        }

        public static Node leaf(Object label) {
            return new Node(null, label);
        }

        public boolean isLeaf() {
            return label != null;
        }

        public String toString() {
            return feature != null ? feature.toString() : label.toString();
        }

    }
}
