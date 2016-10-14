package nars.util;

import com.googlecode.concurrenttrees.common.CharSequences;
import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.common.LazyIterator;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.Node;
import com.googlecode.concurrenttrees.radix.node.NodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.voidvalue.VoidValue;
import com.googlecode.concurrenttrees.radix.node.util.PrettyPrintable;
import nars.$;
import org.eclipse.collections.api.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * seh's modifications to radix tree
 * <p>
 * An implementation of {@link RadixTree} which supports lock-free concurrent reads, and allows items to be added to and
 * to be removed from the tree <i>atomically</i> by background thread(s), without blocking reads.
 * <p/>
 * Unlike reads, writes require locking of the tree (locking out other writing threads only; reading threads are never
 * blocked). Currently write locks are coarse-grained; in fact they are tree-level. In future branch-level write locks
 * might be added, but the current implementation is targeted at high concurrency read-mostly use cases.
 *
 * @author Niall Gallagher
 * @modified by seth
 */
public class MyConcurrentRadixTree<X> implements RadixTree<X>, PrettyPrintable, Serializable, Iterable<X> {

    private final NodeFactory nodeFactory;

    public volatile Node root;

    // Write operations acquire write lock.
    // Read operations are lock-free by default, but can be forced to acquire read locks via constructor flag...
    // If non-null true, force reading threads to acquire read lock (they will block on writes).
    @Nullable private final Lock readLock;
    @NotNull private final Lock writeLock;


    final AtomicInteger estSize = new AtomicInteger(0);

    /**
     * Creates a new {@link MyConcurrentRadixTree} which will use the given {@link NodeFactory} to create nodes.
     *
     * @param nodeFactory An object which creates {@link Node} objects on-demand, and which might return node
     *                    implementations optimized for storing the values supplied to it for the creation of each node
     */
    public MyConcurrentRadixTree(NodeFactory nodeFactory) {
        this(nodeFactory, false);
    }

    /**
     * Creates a new {@link MyConcurrentRadixTree} which will use the given {@link NodeFactory} to create nodes.
     *
     * @param nodeFactory         An object which creates {@link Node} objects on-demand, and which might return node
     *                            implementations optimized for storing the values supplied to it for the creation of each node
     * @param restrictConcurrency If true, configures use of a {@link ReadWriteLock} allowing
     *                            concurrent reads, except when writes are being performed by other threads, in which case writes block all reads;
     *                            if false, configures lock-free reads; allows concurrent non-blocking reads, even if writes are being performed
     *                            by other threads
     */
    public MyConcurrentRadixTree(NodeFactory nodeFactory, boolean restrictConcurrency) {
        this.nodeFactory = nodeFactory;

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.writeLock = readWriteLock.writeLock();
        if (restrictConcurrency) {
            this.readLock = readWriteLock.readLock();
        } else {
            this.readLock = null;
        }
        this.root = nodeFactory.createNode("", null, Collections.emptyList(), true);
    }

    // ------------- Helper methods for serializing writes -------------

    /** essentially a version number which increments each acquired write lock, to know if the tree has changed */
    final static AtomicInteger writes = new AtomicInteger();

    public final int acquireWriteLock() {
        writeLock.lock();
        return writes.incrementAndGet();
    }

    public final void releaseWriteLock() {
        writeLock.unlock();
    }


    public final void acquireReadLockIfNecessary() {
        if (readLock!=null)
            readLock.lock();
    }

    public final void releaseReadLockIfNecessary() {
        if (readLock!=null)
            readLock.unlock();
    }


    public final X put(Pair<CharSequence,X> value) {
        return put(value.getOne(), value.getTwo());
    }


    public X put(X value) {
        throw new UnsupportedOperationException("subclasses can implement this by creating their own key and calling put(k,v)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final X put(CharSequence key, X value) {
//        @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
//        O existingValue = (O) putInternal(key, value, true);  // putInternal acquires write lock
//        return existingValue;

        return compute(key, value, (k, r, existing, v) -> {
            return v;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final X putIfAbsent(CharSequence key, X value) {
        throw new UnsupportedOperationException();
    }

    @NotNull public final X putIfAbsent(@NotNull CharSequence key, @NotNull Supplier<X> newValue) {
        return compute(key, newValue, (k, r, existing, v) ->
            existing != null ? existing : v.get()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public X getValueForExactKey(CharSequence key) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(key);
            if (searchResult.classification.equals(SearchResult.Classification.EXACT_MATCH)) {
                @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
                X value = (X) searchResult.found.getValue();
                return value;
            }
            return null;
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<CharSequence> getKeysStartingWith(CharSequence prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            Node nodeFound = searchResult.found;
            switch (searchResult.classification) {
                case EXACT_MATCH:
                    return getDescendantKeys(prefix, nodeFound);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    CharSequence edgeSuffix = CharSequences.getSuffix(nodeFound.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    prefix = CharSequences.concatenate(prefix, edgeSuffix);
                    return getDescendantKeys(prefix, nodeFound);
                default:
                    // Incomplete match means key is not a prefix of any node...
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<X> getValuesForKeysStartingWith(CharSequence prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            Node Found = searchResult.found;
            switch (searchResult.classification) {
                case EXACT_MATCH:
                    return getDescendantValues(prefix, Found);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    return getDescendantValues(
                            CharSequences.concatenate(
                                    prefix,
                                    CharSequences.getSuffix(
                                            Found.getIncomingEdge(),
                                            searchResult.charsMatchedInNodeFound)),
                            Found);
                default:
                    // Incomplete match means key is not a prefix of any node...
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<KeyValuePair<X>> getKeyValuePairsForKeysStartingWith(CharSequence prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeyValuePairs(prefix, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    CharSequence edgeSuffix = CharSequences.getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    prefix = CharSequences.concatenate(prefix, edgeSuffix);
                    return getDescendantKeyValuePairs(prefix, searchResult.found);
                default:
                    // Incomplete match means key is not a prefix of any node...
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(@NotNull CharSequence key) {
        acquireWriteLock();
        try {
            SearchResult searchResult = searchTree(key);
            return removeHavingAcquiredWriteLock(searchResult, false);
        } finally {
            releaseWriteLock();
        }
    }

    public boolean remove(@NotNull SearchResult searchResult, boolean recurse) {
        acquireWriteLock();
        try {
            return removeHavingAcquiredWriteLock(searchResult, recurse);
        } finally {
            releaseWriteLock();
        }
    }

    /** allows subclasses to override this to handle removal events. return true if removal is accepted, false to reject the removal and reinsert */
    protected boolean onRemove(X removed) {

        return true;
    }

    public boolean removeHavingAcquiredWriteLock(SearchResult searchResult, boolean recurse) {
        SearchResult.Classification classification = searchResult.classification;
        switch (classification) {
            case EXACT_MATCH:
                Node found = searchResult.found;
                Node parent = searchResult.parentNode;

                Object v = found.getValue();
                if (!recurse && ((v == null)||(v == VoidValue.SINGLETON))) {
                    // This node was created automatically as a split between two branches (implicit node).
                    // No need to remove it...
                    return false;
                }

                List<X> reinsertions = $.newArrayList(0);

                if (v!=null && v != VoidValue.SINGLETON) {
                    X xv = (X) v;
                    boolean removed = tryRemove(xv);
                    if (!recurse) {
                        if (!removed)
                            return false; //remove was disabled for this entry
                    } else {
                        if (!removed) {
                            reinsertions.add(xv); //continue removing below then reinsert afterward
                        }
                    }
                }


                // Proceed with deleting the node...
                List<Node> childEdges = found.getOutgoingEdges();
                int numChildren = childEdges.size();
                if (numChildren > 0) {
                    if (!recurse) {
                        if (numChildren > 1) {
                            // This node has more than one child, so if we delete the value from this node, we still need
                            // to leave a similar node in place to act as the split between the child edges.
                            // Just delete the value associated with this node.
                            // -> Clone this node without its value, preserving its child nodes...
                            @SuppressWarnings({"NullableProblems"})
                            Node cloned = nodeFactory.createNode(found.getIncomingEdge(), null, found.getOutgoingEdges(), false);
                            // Re-add the replacement node to the parent...
                            parent.updateOutgoingEdge(cloned);
                        } else if (numChildren == 1) {
                            // Node has one child edge.
                            // Create a new node which is the concatenation of the edges from this node and its child,
                            // and which has the outgoing edges of the child and the value from the child.
                            Node child = childEdges.get(0);
                            CharSequence concatenatedEdges = CharSequences.concatenate(found.getIncomingEdge(), child.getIncomingEdge());
                            Node mergedNode = nodeFactory.createNode(concatenatedEdges, child.getValue(), child.getOutgoingEdges(), false);
                            // Re-add the merged node to the parent...
                            parent.updateOutgoingEdge(mergedNode);
                        }
                    } else {
                        //collect all values from the subtree, call onRemove for them. then proceed below with removal of this node and its value
                        forEach(found, (k,f) -> {
                            boolean removed = tryRemove(f);
                            if (!removed) {
                                reinsertions.add(f);
                            }
                        });
                        numChildren = 0;
                    }
                }

                if (numChildren == 0) {

                    if (reinsertions.size() == 1) {
                        //this was a leaf node that was prevented from being removed.
                        //in this case make no further changes
                        return false;
                    }

                    // Node has no children. Delete this node from its parent,
                    // which involves re-creating the parent rather than simply updating its child edge
                    // (this is why we need parentNodesParent).
                    // However if this would leave the parent with only one remaining child edge,
                    // and the parent itself has no value (is a split node), and the parent is not the root node
                    // (a special case which we never merge), then we also need to merge the parent with its
                    // remaining child.

                    List<Node> currentEdgesFromParent = parent.getOutgoingEdges();
                    // Create a list of the outgoing edges of the parent which will remain
                    // if we remove this child...
                    // Use a non-resizable list, as a sanity check to force ArrayIndexOutOfBounds...
                    List<Node> newEdgesOfParent = $.newArrayList(parent.getOutgoingEdges().size());
                    for (int i = 0, numParentEdges = currentEdgesFromParent.size(); i < numParentEdges; i++) {
                        Node node = currentEdgesFromParent.get(i);
                        if (node != found) {
                            newEdgesOfParent.add(node);
                        }
                    }

                    // Note the parent might actually be the root node (which we should never merge)...
                    boolean parentIsRoot = (parent == root);
                    Node newParent;
                    if (newEdgesOfParent.size() == 1 && parent.getValue() == null && !parentIsRoot) {
                        // Parent is a non-root split node with only one remaining child, which can now be merged.
                        Node parentsRemainingChild = newEdgesOfParent.get(0);
                        // Merge the parent with its only remaining child...
                        CharSequence concatenatedEdges = CharSequences.concatenate(parent.getIncomingEdge(), parentsRemainingChild.getIncomingEdge());
                        newParent = nodeFactory.createNode(concatenatedEdges, parentsRemainingChild.getValue(), parentsRemainingChild.getOutgoingEdges(), parentIsRoot);
                    } else {
                        // Parent is a node which either has a value of its own, has more than one remaining
                        // child, or is actually the root node (we never merge the root node).
                        // Create new parent node which is the same as is currently just without the edge to the
                        // node being deleted...
                        newParent = nodeFactory.createNode(parent.getIncomingEdge(), parent.getValue(), newEdgesOfParent, parentIsRoot);
                    }
                    // Re-add the parent node to its parent...
                    if (parentIsRoot) {
                        // Replace the root node...
                        this.root = newParent;
                    } else {
                        // Re-add the parent node to its parent...
                        searchResult.parentNodesParent.updateOutgoingEdge(newParent);
                    }
                }


                reinsertions.forEach(this::put);

                return true;
            default:
                return false;
        }
    }


    private final boolean tryRemove(X v) {
        estSize.decrementAndGet();
        return onRemove(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<CharSequence> getClosestKeys(CharSequence candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeys(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    CharSequence edgeSuffix = CharSequences.getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = CharSequences.concatenate(candidate, edgeSuffix);
                    return getDescendantKeys(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    // Example: if we searched for CX, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfParentNode = CharSequences.getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    CharSequence keyOfNodeFound = CharSequences.concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantKeys(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        // Closest match is the root node, we don't consider this a match for anything...
                        break;
                    }
                    // Example: if we searched for COFFEE, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfNodeFound = CharSequences.getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantKeys(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<X> getValuesForClosestKeys(CharSequence candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantValues(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    CharSequence edgeSuffix = CharSequences.getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = CharSequences.concatenate(candidate, edgeSuffix);
                    return getDescendantValues(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    // Example: if we searched for CX, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfParentNode = CharSequences.getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    CharSequence keyOfNodeFound = CharSequences.concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantValues(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        // Closest match is the root node, we don't consider this a match for anything...
                        break;
                    }
                    // Example: if we searched for COFFEE, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfNodeFound = CharSequences.getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantValues(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<KeyValuePair<X>> getKeyValuePairsForClosestKeys(CharSequence candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeyValuePairs(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    // Append the remaining characters of the edge to the key.
                    // For example if we searched for CO, but first matching node was COFFEE,
                    // the key associated with the first node should be COFFEE...
                    CharSequence edgeSuffix = CharSequences.getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = CharSequences.concatenate(candidate, edgeSuffix);
                    return getDescendantKeyValuePairs(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    // Example: if we searched for CX, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfParentNode = CharSequences.getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    CharSequence keyOfNodeFound = CharSequences.concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantKeyValuePairs(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        // Closest match is the root node, we don't consider this a match for anything...
                        break;
                    }
                    // Example: if we searched for COFFEE, but deepest matching node was CO,
                    // the results should include node CO and its descendants...
                    CharSequence keyOfNodeFound = CharSequences.getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantKeyValuePairs(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return size(this.root);
    }

    public int size(Node n) {
        acquireReadLockIfNecessary();
        try {
            return _size(n);
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    public int sizeIfLessThan(Node n, int limit) {
        acquireReadLockIfNecessary();
        try {
            return _sizeIfLessThan(n, limit);
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    private static int _size(Node n) {
        int sum = 0;
        Object v = n.getValue();
        if (aValue(v))
            sum++;

        List<Node> l = n.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            sum += _size(l.get(i));
        }

        return sum;
    }

    /** as soon as the limit is exceeded, it returns -1 to cancel the recursion iteration */
    private static int _sizeIfLessThan(Node n, int limit) {
        int sum = 0;
        Object v = n.getValue();
        if (aValue(v))
            sum++;

        List<Node> l = n.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            int s = _size(l.get(i));
            if (s < 0)
                return -1; //cascade
            sum += s;
            if (sum > limit)
                return -1;
        }

        return sum;
    }

    /** estimated size */
    public int sizeEst() {
        return estSize.get();
    }

    @Override
    public void forEach(Consumer<? super X> action) {
        forEach(this.root, action);
    }

    public final void forEach(Node start, Consumer<? super X> action) {
        Object v = start.getValue();
        if (aValue(v))
            action.accept((X)v);

        List<Node> l = start.getOutgoingEdges();
        for (Node child : l)
            forEach(child, action);
    }

    public final void forEach(Node start, BiConsumer<CharSequence, ? super X> action) {
        Object v = start.getValue();
        if (aValue(v))
            action.accept(start.getIncomingEdge(), (X)v);

        List<Node> l = start.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            forEach(l.get(i), action);
        }
    }

    public static boolean aValue(Object v) {
        return (v != null) && v!= VoidValue.SINGLETON;
    }

    // ------------- Helper method for put() -------------
    Object putInternal(CharSequence key, Object value, boolean overwrite) {
        throw new UnsupportedOperationException();
    }

    @NotNull public SearchResult random(float descendProb, Random rng) {
        return random(null, descendProb, rng);
    }

    @NotNull public SearchResult random(@Nullable SearchResult at, float descendProb, Random rng) {
        Node current, parent, parentParent;
        if (at!=null && at.found!=null) {
            current = at.found;
            parent = at.parentNode;
            parentParent = at.parentNodesParent;
        } else {
            current = root;
            parent = parentParent = null;
        }

        /*acquireReadLockIfNecessary();
        try {*/
            while (true) {
                List<Node> c = current.getOutgoingEdges();
                int s = c.size();
                if (s == 0) {
                    break; //select it
                } else {
                    if (rng.nextFloat() < descendProb) {
                        int which = rng.nextInt(s);
                        Node next = c.get(which);

                        parentParent = parent;
                        parent = current;
                        current = next;
                    } else {
                        break; //select it
                    }
                }
            }
        /*} finally {
            releaseReadLockIfNecessary();
        }*/

        return new SearchResult(current, parent, parentParent);
    }


    public interface QuadFunction<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    /**
     * Atomically adds the given value to the tree, creating a node for the value as necessary. If the value is already
     * stored for the same key, either overwrites the existing value, or simply returns the existing value, depending
     * on the given value of the <code>overwrite</code> flag.
     *
     * @param key       The key against which the value should be stored
     * @param newValue  The value to store against the key
     * @param overwrite If true, should replace any existing value, if false should not replace any existing value
     * @return The existing value for this key, if there was one, otherwise null
     */
    <V> X compute(@NotNull CharSequence key, V value, QuadFunction<CharSequence, SearchResult, X, V, X> computeFunc) {
//        if (key.length() == 0) {
//            throw new IllegalArgumentException("The key argument was zero-length");
//        }





        int version;
        X newValue, foundX;
        SearchResult result;
        int matched;
        Object foundValue;
        Node found;

        {

            version = writes.intValue();

            acquireReadLockIfNecessary();
            try {

                // Note we search the tree here after we have acquired the write lock...
                result = searchTree(key);
                found = result.found;
                matched = result.charsMatched;
                foundValue = found != null ? found.getValue() : null;
                foundX = ((matched == key.length()) && (foundValue != VoidValue.SINGLETON)) ? ((X) foundValue) : null;
            } finally {
                releaseReadLockIfNecessary();
            }

        }

        newValue = computeFunc.apply(key, result, foundX, value);

        if (newValue != foundX) {

            NodeFactory factory = this.nodeFactory;

            int version2 = acquireWriteLock();
            try {

                if (version+1!=version2) {
                    //search again because the tree has changed since the initial lookup
                    result = searchTree(key);
                    found = result.found;
                    matched = result.charsMatched;
                    foundValue = found != null ? found.getValue() : null;
                    foundX = ((matched == key.length()) && (foundValue != VoidValue.SINGLETON)) ? ((X) foundValue) : null;
                    if (foundX == newValue)
                        return newValue; //no change; the requested value has already been supplied since the last access
                }

                SearchResult.Classification classification = result.classification;

                if (foundX == null)
                    estSize.incrementAndGet();

                List<Node> oedges = found.getOutgoingEdges();
                switch (classification) {
                    case EXACT_MATCH:
                        // Search found an exact match for all edges leading to this node.
                        // -> Add or update the value in the node found, by replacing
                        // the existing node with a new node containing the value...

                        // First check if existing node has a value, and if we are allowed to overwrite it.
                        // Return early without overwriting if necessary...

                        if (newValue != foundValue) {
                            //clone and reattach
                            cloneAndReattach(result, factory, found, foundValue, oedges);
                        }
                        break;
                    case KEY_ENDS_MID_EDGE: {
                        // Search ran out of characters from the key while in the middle of an edge in the node.
                        // -> Split the node in two: Create a new parent node storing the new value,
                        // and a new child node holding the original value and edges from the existing node...
                        CharSequence keyCharsFromStartOfNodeFound = key.subSequence(matched - result.charsMatchedInNodeFound, key.length());
                        CharSequence commonPrefix = CharSequences.getCommonPrefix(keyCharsFromStartOfNodeFound, found.getIncomingEdge());
                        CharSequence suffixFromExistingEdge = CharSequences.subtractPrefix(found.getIncomingEdge(), commonPrefix);


                        // Create new nodes...
                        Node newChild = factory.createNode(suffixFromExistingEdge, foundValue, oedges, false);

                        Node newParent = factory.createNode(commonPrefix, newValue, Arrays.asList(newChild), false);

                        // Add the new parent to the parent of the node being replaced (replacing the existing node)...
                        result.parentNode.updateOutgoingEdge(newParent);

                        break;
                    }
                    case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                        // Search found a difference in characters between the key and the start of all child edges leaving the
                        // node, the key still has trailing unmatched characters.
                        // -> Add a new child to the node, containing the trailing characters from the key.

                        // NOTE: this is the only branch which allows an edge to be added to the root.
                        // (Root node's own edge is "" empty string, so is considered a prefixing edge of every key)

                        // Create a new child node containing the trailing characters...
                        CharSequence keySuffix = key.subSequence(matched, key.length());

                        Node newChild = factory.createNode(keySuffix, newValue, Collections.emptyList(), false);

                        // Clone the current node adding the new child...
                        List<Node> edges = $.newArrayList(oedges.size() + 1);
                        edges.addAll(oedges);
                        edges.add(newChild);
                        cloneAndReattach(result, factory, found, foundValue, edges);

                        break;

                    case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE:
                        // Search found a difference in characters between the key and the characters in the middle of the
                        // edge in the current node, and the key still has trailing unmatched characters.
                        // -> Split the node in three:
                        // Let's call node found: NF
                        // (1) Create a new node N1 containing the unmatched characters from the rest of the key, and the
                        // value supplied to this method
                        // (2) Create a new node N2 containing the unmatched characters from the rest of the edge in NF, and
                        // copy the original edges and the value from NF unmodified into N2
                        // (3) Create a new node N3, which will be the split node, containing the matched characters from
                        // the key and the edge, and add N1 and N2 as child nodes of N3
                        // (4) Re-add N3 to the parent node of NF, effectively replacing NF in the tree

                        CharSequence keyCharsFromStartOfNodeFound = key.subSequence(matched - result.charsMatchedInNodeFound, key.length());
                        CharSequence commonPrefix = CharSequences.getCommonPrefix(keyCharsFromStartOfNodeFound, found.getIncomingEdge());
                        CharSequence suffixFromExistingEdge = CharSequences.subtractPrefix(found.getIncomingEdge(), commonPrefix);
                        CharSequence suffixFromKey = key.subSequence(matched, key.length());

                        // Create new nodes...
                        Node n1 = factory.createNode(suffixFromKey, newValue, Collections.emptyList(), false);
                        Node n2 = factory.createNode(suffixFromExistingEdge, foundValue, oedges, false);
                        @SuppressWarnings({"NullableProblems"})
                        Node n3 = factory.createNode(commonPrefix, null, Arrays.asList(n1, n2), false);

                        result.parentNode.updateOutgoingEdge(n3);

                        // Return null for the existing value...
                        break;

                    default:
                        // This is a safeguard against a new enum constant being added in future.
                        throw new IllegalStateException("Unexpected classification for search result: " + result);
                }
            } finally {
                releaseWriteLock();
            }
        }

        return newValue;
    }

    private void cloneAndReattach(SearchResult searchResult, NodeFactory factory, Node found, Object foundValue, List<Node> edges) {
        CharSequence ie = found.getIncomingEdge();
        boolean root = ie.length() == 0;

        Node clonedNode = factory.createNode(ie, foundValue, edges, root);

        // Re-add the cloned node to its parent node...
        if (root) {
            this.root = clonedNode;
        } else {
            searchResult.parentNode.updateOutgoingEdge(clonedNode);
        }
    }

    // ------------- Helper method for finding descendants of a given node -------------

    /**
     * Returns a lazy iterable which will return {@link CharSequence} keys for which the given key is a prefix.
     * The results inherently will not contain duplicates (duplicate keys cannot exist in the tree).
     * <p/>
     * Note that this method internally converts {@link CharSequence}s to {@link String}s, to avoid set equality issues,
     * because equals() and hashCode() are not specified by the CharSequence API contract.
     */
    @SuppressWarnings({"JavaDoc"})
    Iterable<CharSequence> getDescendantKeys(final CharSequence startKey, final Node startNode) {
        return new DescendantKeys(startKey, startNode);
    }

    /**
     * Returns a lazy iterable which will return values which are associated with keys in the tree for which
     * the given key is a prefix.
     */
    @SuppressWarnings({"JavaDoc"})
    <O> Iterable<O> getDescendantValues(final CharSequence startKey, final Node startNode) {
        return new Iterable<O>() {
            @Override
            public Iterator<O> iterator() {
                return new LazyIterator<O>() {
                    Iterator<NodeKeyPair> descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();

                    @Override
                    protected O computeNext() {
                        // Traverse to the next matching node in the tree and return its key and value...
                        while (descendantNodes.hasNext()) {
                            NodeKeyPair nodeKeyPair = descendantNodes.next();
                            Object value = nodeKeyPair.node.getValue();
                            if (value != null) {
                                // Dealing with a node explicitly added to tree (rather than an automatically-added split node).

                                // We have to cast to generic type here, because Node objects are not generically typed.
                                // Background: Node objects are not generically typed, because arrays can't be generically typed,
                                // and we use arrays in nodes. We choose to cast here (in wrapper logic around the tree) rather than
                                // pollute the already-complex tree manipulation logic with casts.
                                @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
                                O valueTyped = (O) value;
                                return valueTyped;
                            }
                        }
                        // Finished traversing the tree, no more matching nodes to return...
                        return endOfData();
                    }
                };
            }
        };
    }

    /**
     * Returns a lazy iterable which will return {@link KeyValuePair} objects each containing a key and a value,
     * for which the given key is a prefix of the key in the {@link KeyValuePair}. These results inherently will not
     * contain duplicates (duplicate keys cannot exist in the tree).
     * <p/>
     * Note that this method internally converts {@link CharSequence}s to {@link String}s, to avoid set equality issues,
     * because equals() and hashCode() are not specified by the CharSequence API contract.
     */
    @SuppressWarnings({"JavaDoc"})
    <O> Iterable<KeyValuePair<O>> getDescendantKeyValuePairs(final CharSequence startKey, final Node startNode) {
        return new Iterable<KeyValuePair<O>>() {
            @Override
            public Iterator<KeyValuePair<O>> iterator() {
                return new LazyIterator<KeyValuePair<O>>() {
                    Iterator<NodeKeyPair> descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();

                    @Override
                    protected KeyValuePair<O> computeNext() {
                        // Traverse to the next matching node in the tree and return its key and value...
                        while (descendantNodes.hasNext()) {
                            NodeKeyPair nodeKeyPair = descendantNodes.next();
                            Object value = nodeKeyPair.node.getValue();
                            if (value != null) {
                                // Dealing with a node explicitly added to tree (rather than an automatically-added split node).

                                // Call the transformKeyForResult method to allow key to be transformed before returning to client.
                                // Used by subclasses such as ReversedRadixTree implementations...
                                CharSequence optionallyTransformedKey = transformKeyForResult(nodeKeyPair.key);

                                // -> Convert the CharSequence to a String before returning, to avoid set equality issues,
                                // because equals() and hashCode() is not specified by the CharSequence API contract...
                                String keyString = CharSequences.toString(optionallyTransformedKey);
                                return new KeyValuePairImpl<O>(keyString, value);
                            }
                        }
                        // Finished traversing the tree, no more matching nodes to return...
                        return endOfData();
                    }
                };
            }
        };
    }

    /**
     * Implementation of the {@link KeyValuePair} interface.
     */
    public final static class KeyValuePairImpl<O> implements KeyValuePair<O> {

        final String key;
        final O value;
        final int hash;

        /**
         * Constructor.
         * <p>
         * Implementation node: This constructor currently requires the key to be supplied as a {@link String}
         * - this is to allow reliable testing of object equality; the alternative {@link CharSequence}
         * does not specify a contract for {@link Object#equals(Object)}.
         *
         * @param key   The key as a string
         * @param value The value
         */
        public KeyValuePairImpl(String key, Object value) {
            this.key = key;
            // We have to cast to generic type here, because Node objects are not generically typed.
            // Background: Node objects are not generically typed, because arrays can't be generically typed,
            // and we use arrays in nodes. We choose to cast here (in wrapper logic around the tree) rather than
            // pollute the already-complex tree manipulation logic with casts.
            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            O valueTyped = (O) value;
            this.value = valueTyped;
            this.hash = key.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CharSequence getKey() {
            return key;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public O getValue() {
            return value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean equals(Object o) {
            //if (this == o) return true;
            return (this == o) ||
                    //(o instanceof KeyValuePairImpl) &&
                    key.equals(((KeyValuePairImpl) o).key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final int hashCode() {
            return hash;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "(" + key + ", " + value + ")";
        }
    }

    /**
     * Traverses the tree using depth-first, preordered traversal, starting at the given node, using lazy evaluation
     * such that the next node is only determined when next() is called on the iterator returned.
     * The traversal algorithm uses iteration instead of recursion to allow deep trees to be traversed without
     * requiring large JVM stack sizes.
     * <p/>
     * Each node that is encountered is returned from the iterator along with a key associated with that node,
     * in a NodeKeyPair object. The key will be prefixed by the given start key, and will be generated by appending
     * to the start key the edges traversed along the path to that node from the start node.
     *
     * @param startKey  The key which matches the given start node
     * @param startNode The start node
     * @return An iterator which when iterated traverses the tree using depth-first, preordered traversal,
     * starting at the given start node
     */
    protected Iterable<NodeKeyPair> lazyTraverseDescendants(final CharSequence startKey, final Node startNode) {
        return new Iterable<NodeKeyPair>() {
            @Override
            public Iterator<NodeKeyPair> iterator() {
                return new LazyIterator<NodeKeyPair>() {

                    final Deque<NodeKeyPair> stack =
                            //new LinkedList<NodeKeyPair>();
                            new ArrayDeque();

                    {
                        stack.push(new NodeKeyPair(startNode, startKey));
                    }

                    @Override
                    protected NodeKeyPair computeNext() {
                        Deque<NodeKeyPair> stack = this.stack;

                        if (stack.isEmpty()) {
                            return endOfData();
                        }
                        NodeKeyPair current = stack.pop();
                        List<Node> childNodes = current.node.getOutgoingEdges();

                        // -> Iterate child nodes in reverse order and so push them onto the stack in reverse order,
                        // to counteract that pushing them onto the stack alone would otherwise reverse their processing order.
                        // This ensures that we actually process nodes in ascending alphabetical order.
                        for (int i = childNodes.size()-1; i >= 0; i--) {
                            Node child = childNodes.get(i);
                            stack.push(new NodeKeyPair(child,
                                CharSequences.concatenate(current.key, child.getIncomingEdge())
                            ));
                        }
                        return current;
                    }
                };
            }
        };
    }


    /**
     * Encapsulates a node and its associated key. Used internally by {@link #lazyTraverseDescendants}.
     */
    protected static final class NodeKeyPair {
        public final Node node;
        public final CharSequence key;

        public NodeKeyPair(Node node, CharSequence key) {
            this.node = node;
            this.key = key;
        }
    }

    /**
     * A hook method which may be overridden by subclasses, to transform a key just before it is returned to
     * the application, for example by the {@link #getKeysStartingWith(CharSequence)} or the
     * {@link #getKeyValuePairsForKeysStartingWith(CharSequence)} methods.
     * <p/>
     * This hook is expected to be used by  {@link com.googlecode.concurrenttrees.radixreversed.ReversedRadixTree}
     * implementations, where keys are stored in the tree in reverse order but results should be returned in normal
     * order.
     * <p/>
     * <b>This default implementation simply returns the given key unmodified.</b>
     *
     * @param rawKey The raw key as stored in the tree
     * @return A transformed version of the key
     */
    protected CharSequence transformKeyForResult(CharSequence rawKey) {
        return rawKey;
    }


    // ------------- Helper method for searching the tree and associated SearchResult object -------------

    /**
     * Traverses the tree and finds the node which matches the longest prefix of the given key.
     * <p/>
     * The node returned might be an <u>exact match</u> for the key, in which case {@link SearchResult#charsMatched}
     * will equal the length of the key.
     * <p/>
     * The node returned might be an <u>inexact match</u> for the key, in which case {@link SearchResult#charsMatched}
     * will be less than the length of the key.
     * <p/>
     * There are two types of inexact match:
     * <ul>
     * <li>
     * An inexact match which ends evenly at the boundary between a node and its children (the rest of the key
     * not matching any children at all). In this case if we we wanted to add nodes to the tree to represent the
     * rest of the key, we could simply add child nodes to the node found.
     * </li>
     * <li>
     * An inexact match which ends in the middle of a the characters for an edge stored in a node (the key
     * matching only the first few characters of the edge). In this case if we we wanted to add nodes to the
     * tree to represent the rest of the key, we would have to split the node (let's call this node found: NF):
     * <ol>
     * <li>
     * Create a new node (N1) which will be the split node, containing the matched characters from the
     * start of the edge in NF
     * </li>
     * <li>
     * Create a new node (N2) which will contain the unmatched characters from the rest of the edge
     * in NF, and copy the original edges from NF unmodified into N2
     * </li>
     * <li>
     * Create a new node (N3) which will be the new branch, containing the unmatched characters from
     * the rest of the key
     * </li>
     * <li>
     * Add N2 as a child of N1
     * </li>
     * <li>
     * Add N3 as a child of N1
     * </li>
     * <li>
     * In the <b>parent node of NF</b>, replace the edge pointing to NF with an edge pointing instead
     * to N1. If we do this step atomically, reading threads are guaranteed to never see "invalid"
     * data, only either the old data or the new data
     * </li>
     * </ol>
     * </li>
     * </ul>
     * The {@link SearchResult#classification} is an enum value based on its classification of the
     * match according to the descriptions above.
     *
     * @param key a key for which the node matching the longest prefix of the key is required
     * @return A {@link SearchResult} object which contains the node matching the longest prefix of the key, its
     * parent node, the number of characters of the key which were matched in total and within the edge of the
     * matched node, and a {@link SearchResult#classification} of the match as described above
     */
    SearchResult searchTree(CharSequence key) {
        Node parentNodesParent = null;
        Node parentNode = null;
        Node currentNode = root;
        int charsMatched = 0, charsMatchedInNodeFound = 0;

        final int keyLength = key.length();
        outer_loop:
        while (charsMatched < keyLength) {
            Node nextNode = currentNode.getOutgoingEdge(key.charAt(charsMatched));
            if (nextNode == null) {
                // Next node is a dead end...
                //noinspection UnnecessaryLabelOnBreakStatement
                break outer_loop;
            }

            parentNodesParent = parentNode;
            parentNode = currentNode;
            currentNode = nextNode;
            charsMatchedInNodeFound = 0;
            CharSequence currentNodeEdgeCharacters = currentNode.getIncomingEdge();
            for (int i = 0, numEdgeChars = currentNodeEdgeCharacters.length(); i < numEdgeChars && charsMatched < keyLength; i++) {
                if (currentNodeEdgeCharacters.charAt(i) != key.charAt(charsMatched)) {
                    // Found a difference in chars between character in key and a character in current node.
                    // Current node is the deepest match (inexact match)....
                    break outer_loop;
                }
                charsMatched++;
                charsMatchedInNodeFound++;
            }
        }
        return new SearchResult(key, currentNode, charsMatched, charsMatchedInNodeFound, parentNode, parentNodesParent);
    }

    /**
     * Encapsulates results of searching the tree for a node for which a given key is a prefix. Encapsulates the node
     * found, its parent node, its parent's parent node, and the number of characters matched in the current node and
     * in total.
     * <p/>
     * Also classifies the search result so that algorithms in methods which use this SearchResult, when adding nodes
     * and removing nodes from the tree, can select appropriate strategies based on the classification.
     */
    public static class SearchResult {
        public final CharSequence key;
        public final Node found;
        public final int charsMatched;
        public final int charsMatchedInNodeFound;
        public final Node parentNode;
        public final Node parentNodesParent;
        public final Classification classification;

        enum Classification {
            EXACT_MATCH,
            INCOMPLETE_MATCH_TO_END_OF_EDGE,
            INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE,
            KEY_ENDS_MID_EDGE,
            INVALID // INVALID is never used, except in unit testing
        }

        SearchResult(Node found, Node parentNode, Node parentParentNode) {
            this(null, found, -1, -1, parentNode, parentParentNode, found!=null ? Classification.EXACT_MATCH : Classification.INVALID);
        }

        SearchResult(CharSequence key, Node found, int charsMatched, int charsMatchedInNodeFound, Node parentNode, Node parentNodesParent) {
            this(key, found, charsMatched, charsMatchedInNodeFound, parentNode, parentNodesParent, classify(key, found, charsMatched, charsMatchedInNodeFound));
        }

        SearchResult(CharSequence key, Node found, int charsMatched, int charsMatchedInNodeFound, Node parentNode, Node parentNodesParent, Classification c) {
            this.key = key;
            this.found = found;
            this.charsMatched = charsMatched;
            this.charsMatchedInNodeFound = charsMatchedInNodeFound;
            this.parentNode = parentNode;
            this.parentNodesParent = parentNodesParent;

            // Classify this search result...
            this.classification = c;
        }

        protected static SearchResult.Classification classify(CharSequence key, Node nodeFound, int charsMatched, int charsMatchedInNodeFound) {
            int len = nodeFound.getIncomingEdge().length();
            int keyLen = key.length();
            if (charsMatched == keyLen) {
                if (charsMatchedInNodeFound == len) {
                    return SearchResult.Classification.EXACT_MATCH;
                } else if (charsMatchedInNodeFound < len) {
                    return SearchResult.Classification.KEY_ENDS_MID_EDGE;
                }
            } else if (charsMatched < keyLen) {
                if (charsMatchedInNodeFound == len) {
                    return SearchResult.Classification.INCOMPLETE_MATCH_TO_END_OF_EDGE;
                } else if (charsMatchedInNodeFound < len) {
                    return SearchResult.Classification.INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE;
                }
            }
            throw new IllegalStateException("Unexpected failure to classify SearchResult");
        }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "key=" + key +
                    ", nodeFound=" + found +
                    ", charsMatched=" + charsMatched +
                    ", charsMatchedInNodeFound=" + charsMatchedInNodeFound +
                    ", parentNode=" + parentNode +
                    ", parentNodesParent=" + parentNodesParent +
                    ", classification=" + classification +
                    '}';
        }
    }

    // ------------- Helper method for pretty-printing tree (not public API) -------------

    @Override
    public Node getNode() {
        return root;
    }

    private class DescendantKeys extends LazyIterator<CharSequence> implements Iterable<CharSequence>, Iterator<CharSequence> {
        private final CharSequence startKey;
        private final Node startNode;
        private Iterator<NodeKeyPair> descendantNodes;

        public DescendantKeys(CharSequence startKey, Node startNode) {
            this.startKey = startKey;
            this.startNode = startNode;
        }

        @Override
        public Iterator<CharSequence> iterator() {
            descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();
            return this;
        }

        @Override
        protected CharSequence computeNext() {
            // Traverse to the next matching node in the tree and return its key and value...
            Iterator<NodeKeyPair> nodes = this.descendantNodes;
            while (nodes.hasNext()) {
                NodeKeyPair nodeKeyPair = nodes.next();
                Object value = nodeKeyPair.node.getValue();
                if (value != null) {
                    // Dealing with a node explicitly added to tree (rather than an automatically-added split node).

                    // Call the transformKeyForResult method to allow key to be transformed before returning to client.
                    // Used by subclasses such as ReversedRadixTree implementations...
                    CharSequence optionallyTransformedKey = transformKeyForResult(nodeKeyPair.key);

                    // -> Convert the CharSequence to a String before returning, to avoid set equality issues,
                    // because equals() and hashCode() is not specified by the CharSequence API contract...
                    return CharSequences.toString(optionallyTransformedKey);
                }
            }
            // Finished traversing the tree, no more matching nodes to return...
            return endOfData();
        }


    }

//    public void forEach(Consumer<? super O> c) {
//        //TODO rewrite as pure forEach visitor
//        this.forEach(c);
//    }

    public Iterator<X> iterator() {
        return getValuesForKeysStartingWith("").iterator();
    }
}
