package jcog.tree.rtree;

import jcog.Util;
import jcog.tree.rtree.point.Double2D;
import jcog.tree.rtree.point.FloatND;
import jcog.tree.rtree.rect.HyperRectFloat;
import jcog.tree.rtree.rect.RectDouble;
import jcog.tree.rtree.util.CounterNode;
import jcog.tree.rtree.util.Stats;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by me on 12/21/16.
 */
class RTreeNDTest {

    @Test
    void pointSearchTest() {

        final RTree<Double2D> pTree = new RTree<>(new Double2D.Builder(), 2, 8, Spatialization.DefaultSplits.AXIAL.get());

        for (int i = 0; i < 10; i++) {
            pTree.add(new Double2D(i, i));
        }

        final RectDouble rect = new RectDouble(new Double2D(2, 2), new Double2D(8, 8));
        final Double2D[] result = new Double2D[10];

        final int n = pTree.containedToArray(rect, result);
        assertEquals(7, n);

        for (int i = 0; i < n; i++) {
            assertTrue(result[i].coord(0) >= 2);
            assertTrue(result[i].coord(0) <= 8);
            assertTrue(result[i].coord(1) >= 2);
            assertTrue(result[i].coord(1) <= 8);
        }
    }

    /**
     * Use an small bounding box to ensure that only expected rectangles are returned.
     * Verifies the count returned from search AND the number of rectangles results.
     * 2D but using N-d impl
     */
    @Test
    void rectNDSearchTest2() {

        final int entryCount = 20;

        System.out.println("rectNDSearchTest2");

        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<HyperRectFloat> rTree = RTree2DTest.createRectNDTree(2, 8, type);
            for (int i = 0; i < entryCount; i++) {
                rTree.add(new HyperRectFloat(new FloatND(i, i), new FloatND(i + 3, i + 3)));
            }

            final HyperRectFloat searchRect = new HyperRectFloat(new FloatND(5, 5), new FloatND(10, 10));
            List<HyperRectFloat> results = new ArrayList();

            rTree.whileEachIntersecting(searchRect, results::add);
            int resultCount = 0;
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i) != null) {
                    resultCount++;
                }
            }

            final int expectedCount = 9;
            

            assertEquals(expectedCount, resultCount, "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount);

            
            Collections.sort(results);
            for (int i = 0; i < resultCount; i++) {
                assertTrue(results.get(i).min.coord(0) == i + 2 && results.get(i).min.coord(1) == i + 2 && results.get(i).max.coord(0) == i + 5 && results.get(i).max.coord(1) == i + 5, "Unexpected result found");
            }

            System.out.println("\t" + rTree.stats());
        }
    }

    @Test
    void testSearchAllWithOneDimensionRandomlyInfinite() {
        System.out.println("\n\nINfinites");
        final int entryCount = 400;
        searchAll(2, 4,
                (dim) -> RTree2DTest.generateRandomRectsWithOneDimensionRandomlyInfinite(dim, entryCount));
    }

    /**
     * Use an enormous bounding box to ensure that every rectangle is returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @Test
    void RectNDSearchAllTest() {
        System.out.println("\n\nfinites");
        final int entryCount = 400;
        searchAll(1, 6, (dim) -> RTree2DTest.generateRandomRects(dim, entryCount));
    }

    private static void searchAll(int minDim, int maxDim, IntFunction<HyperRectFloat[]> generator) {
        for (int dim = minDim; dim <= maxDim; dim++) {

            final HyperRectFloat[] rects = generator.apply(dim);
            Set<HyperRectFloat> input = new HashSet();
            Collections.addAll(input, rects);

            System.out.println("\tRectNDSearchAllTest[dim=" + dim + ']');

            for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
                RTree<HyperRectFloat> rTree = RTree2DTest.createRectNDTree(2, 8, type);
                for (int i = 0; i < rects.length; i++) {
                    rTree.add(rects[i]);
                }

                final HyperRectFloat searchRect = new HyperRectFloat(
                        FloatND.fill(dim, Float.NEGATIVE_INFINITY),
                        FloatND.fill(dim, Float.POSITIVE_INFINITY)
                );

                HyperRectFloat[] results = new HyperRectFloat[rects.length];

                final int foundCount = rTree.containedToArray(searchRect, results);
                int resultCount = 0;
                for (int i = 0; i < results.length; i++) {
                    if (results[i] != null) {
                        resultCount++;
                    }
                }

                final int expectedCount = rects.length;
                
                assertTrue(Math.abs(expectedCount - foundCount) < 10,
                        "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + foundCount /* in case of duplicates */);

                assertTrue(Math.abs(expectedCount - resultCount) < 10,
                        "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount /* in case of duplicates */);

                Set<HyperRectFloat> output = new HashSet();
                Collections.addAll(output, results);


                


                Stats s = rTree.stats();
                s.print(System.out);
                
                assertTrue(s.getMaxDepth() <= 8 /* reasonable */);
            }
        }
    }


    /**
     * Use an small bounding box to ensure that only expected rectangles are returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @Test
    void RectDouble2DSearchTest() {

        final int entryCount = 20;

        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRectDouble2DTree(2, 8, type);
            for (int i = 0; i < entryCount; i++) {
                rTree.add(new RectDouble(i, i, i + 3, i + 3));
            }

            final RectDouble searchRect = new RectDouble(5, 5, 10, 10);
            RectDouble[] results = new RectDouble[3];

            final int foundCount = rTree.containedToArray(searchRect, results);
            int resultCount = 0;
            for (int i = 0; i < results.length; i++) {
                if (results[i] != null) {
                    resultCount++;
                }
            }

            final int expectedCount = 3;
            assertEquals(expectedCount, foundCount, "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + foundCount);
            assertEquals(expectedCount, resultCount, "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount);

            Arrays.sort(results);
            
            for (int i = 0; i < resultCount; i++) {
                assertTrue(Util.equals(results[i].min.x, (double) (i + 5), Spatialization.EPSILON) &&
                                Util.equals(results[i].min.y, (double) (i + 5), Spatialization.EPSILON) &&
                                Util.equals(results[i].max.x, (double) (i + 8), Spatialization.EPSILON) &&
                                Util.equals(results[i].max.y, (double) (i + 8), Spatialization.EPSILON),
                        "Unexpected result found:" + results[i]);
            }
        }
    }

    /**
     * Use an small bounding box to ensure that only expected rectangles are returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @Test
    void RectDouble2DIntersectTest() {

        final int entryCount = 20;

        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRectDouble2DTree(2, 8, type);
            for (int i = 0; i < entryCount; i++) {
                rTree.add(new RectDouble(i, i, i + 3, i + 3));
            }

            final RectDouble searchRect = new RectDouble(5, 5, 10, 10);

            final int expectedCount = 9;
            List<RectDouble> results = new ArrayList(expectedCount);

            rTree.whileEachIntersecting(searchRect, results::add);
            final int resultCount = results.size();


            assertEquals(expectedCount, resultCount, "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + resultCount);
            assertEquals(
                    expectedCount, resultCount, "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount);

            Collections.sort(results);

            
            for (int i = 0; i < resultCount; i++) {
                assertTrue(Util.equals(results.get(i).min.x, (double) (i + 2), Spatialization.EPSILON) &&
                                Util.equals(results.get(i).min.y, (double) (i + 2), Spatialization.EPSILON) &&
                                Util.equals(results.get(i).max.x, (double) (i + 5), Spatialization.EPSILON) &&
                                Util.equals(results.get(i).max.y, (double) (i + 5), Spatialization.EPSILON),
                        "Unexpected result found");
            }
        }
    }


    private static RectDouble[] generateRandomRects(int count) {
        final Random rand = new Random(13);

        
        final int minX = 500;
        final int minY = 500;
        final int maxXRange = 25;
        final int maxYRange = 25;

        final double hitProb = 1.0 * count * maxXRange * maxYRange / (minX * minY);

        final RectDouble[] rects = new RectDouble[count];
        for (int i = 0; i < count; i++) {
            final int x1 = rand.nextInt(minX);
            final int y1 = rand.nextInt(minY);
            final int x2 = x1 + rand.nextInt(maxXRange);
            final int y2 = y1 + rand.nextInt(maxYRange);
            rects[i] = new RectDouble(x1, y1, x2, y2);
        }

        return rects;
    }

    /**
     * Use an enormous bounding box to ensure that every rectangle is returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @Test
    void RectDouble2DSearchAllTest() {

        final int entryCount = 1000;
        final RectDouble[] rects = generateRandomRects(entryCount);

        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRectDouble2DTree(2, 8, type);
            for (int i = 0; i < rects.length; i++) {
                rTree.add(rects[i]);
            }

            final RectDouble searchRect = new RectDouble(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
            RectDouble[] results = new RectDouble[entryCount];

            final int foundCount = rTree.containedToArray(searchRect, results);
            int resultCount = 0;
            for (int i = 0; i < results.length; i++) {
                if (results[i] != null) {
                    resultCount++;
                }
            }

            final AtomicInteger visitCount = new AtomicInteger();
            rTree.whileEachContaining(searchRect, (n) -> {
                visitCount.incrementAndGet();
                return true;
            });
            assertEquals(entryCount, visitCount.get());

            final int expectedCount = entryCount;
            assertEquals(expectedCount, foundCount, "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + foundCount);
            assertEquals(expectedCount, resultCount, "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount);
        }
    }

    /**
     * Collect stats making the structure of trees of each split type
     * more visible.
     */
    @Disabled
    void treeStructureStatsTest() {

        final int entryCount = 50_000;

        final RectDouble[] rects = generateRandomRects(entryCount);
        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRectDouble2DTree(2, 8, type);
            for (int i = 0; i < rects.length; i++) {
                rTree.add(rects[i]);
            }

            Stats stats = rTree.stats();
            stats.print(System.out);
        }
    }

    /**
     * Do a search and collect stats on how many nodes we hit and how many
     * bounding boxes we had to evaluate to get all the results.
     * <p>
     * Preliminary findings:
     * - Evals for QUADRATIC tree increases with size of the search bounding box.
     * - QUADRATIC seems to be ideal for small search bounding boxes.
     */
    @Disabled
    void treeSearchStatsTest() {

        final int entryCount = 5000;

        final RectDouble[] rects = generateRandomRects(entryCount);

        for (int j = 0; j < 6; j++) {
            for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
                RTree<RectDouble> rTree = createRectDouble2DTree(2, 12, type);
                for (int i = 0; i < rects.length; i++) {
                    rTree.add(rects[i]);
                }

                rTree.instrumentTree();

                final RectDouble searchRect = new RectDouble(100, 100, 120, 120);
                RectDouble[] results = new RectDouble[entryCount];
                final long start = System.nanoTime();
                int foundCount = rTree.containedToArray(searchRect, results);
                final long end = System.nanoTime() - start;
                CounterNode<RectDouble> root = (CounterNode<RectDouble>) rTree.root();

                
                System.out.println("[" + type + "] evaluated " + CounterNode.bboxEvalCount + " b-boxes, returning " + foundCount + " entries");

                System.out.println("Run was " + end / 1000 + " us");
            }
        }
    }

    @Test
    void treeContainsTest() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        final RectDouble[] rects = new RectDouble[5];
        for (int i = 0; i < rects.length; i++) {
            rects[i] = new RectDouble(i, i, i + 1, i + 1);
            rTree.add(rects[i]);
        }

        assertEquals(rTree.size(), rects.length);

        for (int i = 0; i < rects.length; i++) {
            assertFalse(rTree.containedToSet(rects[i]).isEmpty());
        }
    }


    @Test
    void treeRemovalTest5Entries() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        final RectDouble[] rects = new RectDouble[5];
        for (int i = 0; i < rects.length; i++) {
            rects[i] = new RectDouble(i, i, i + 1, i + 1);
            rTree.add(rects[i]);
        }

        for (int i = 1; i < rects.length; i++) {
            assertTrue(rTree.remove(rects[i]));
            assertEquals(rects.length - i, rTree.size());
        }

        assertEquals(1, rTree.size());

        assertFalse(rTree.containedToSet(rects[0]).isEmpty(), "Missing hyperRect that should  be found " + rects[0]);

        for (int i = 1; i < rects.length; i++) {
            assertTrue(rTree.containedToSet(rects[i]).isEmpty(), "Found hyperRect that should have been removed on search " + rects[i]);
        }

        final RectDouble hr = new RectDouble(0, 0, 5, 5);
        rTree.add(hr);
        assertFalse(rTree.containedToSet(hr).isEmpty());
        assertTrue(rTree.size() != 0, "Found hyperRect that should have been removed on search");
    }

    @Test
    void treesize() {

        final int NENTRY = 500;

        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        for (int i = 0; i < NENTRY; i++) {
            final RectDouble rect = new RectDouble(i, i, i + 1, i + 1);
            rTree.add(rect);
        }

        assertEquals(NENTRY, rTree.size());
    }


    @Test
    void treeRemovalTestDuplicates() {

        final int NENTRY = 50;

        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        final RectDouble[] rect = new RectDouble[2];
        for (int i = 0; i < rect.length; i++) {
            rect[i] = new RectDouble(i, i, i + 1, i + 1);
            rTree.add(rect[i]);
        }
        assertEquals(2, rTree.size());

        for (int i = 0; i < NENTRY; i++) {
            rTree.add(rect[1]);
        }

        assertEquals(2, rTree.size());

        for (int i = 0; i < rect.length; i++) {
            rTree.remove(rect[i]);
        }
        assertEquals(0, rTree.size());

        for (int i = 0; i < rect.length; i++) {
            assertTrue(rTree.containedToSet(rect[i]).isEmpty(), "Found hyperRect that should have been removed " + rect[i]);
        }
    }

    @Test
    void treeRemovalTest1000Entries() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        int N = 1000;
        final RectDouble[] rect = new RectDouble[N];
        for (int i = 0; i < rect.length; i++) {
            rect[i] = new RectDouble(i, i, i + 1, i + 1);
            rTree.add(rect[i]);
        }

        assertEquals(N, rTree.size());

        for (int i = 0; i < N; i++) {
            boolean removed = rTree.remove(rect[i]);
            assertTrue(removed);
        }

        assertEquals(0, rTree.size());

        for (int i = 0; i < N; i++) {
            assertTrue(rTree.containedToSet(rect[i]).isEmpty(), "#" + i + " of " + rect.length + ": Found hyperRect that should have been removed" + rect[i]);
        }

        assertFalse(rTree.size() > 0, "Found hyperRect that should have been removed on search ");
    }

    @Test
    void treeSingleRemovalTest() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble rect = new RectDouble(0, 0, 2, 2);
        rTree.add(rect);
        assertTrue(rTree.size() > 0, "Did not add HyperRect to Tree");
        rTree.remove(rect);
        assertTrue(rTree.size() == 0, "Did not remove HyperRect from Tree");
        rTree.add(rect);
        assertTrue(rTree.size() > 0, "Tree nulled out and could not add HyperRect back in");
    }

    @Disabled
    void treeRemoveAndRebalanceTest() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble[] rect = new RectDouble[65];
        for (int i = 0; i < rect.length; i++) {
            if (i < 4) {
                rect[i] = new RectDouble(0, 0, 1, 1);
            } else if (i < 8) {
                rect[i] = new RectDouble(2, 2, 4, 4);
            } else if (i < 12) {
                rect[i] = new RectDouble(4, 4, 5, 5);
            } else if (i < 16) {
                rect[i] = new RectDouble(5, 5, 6, 6);
            } else if (i < 20) {
                rect[i] = new RectDouble(6, 6, 7, 7);
            } else if (i < 24) {
                rect[i] = new RectDouble(7, 7, 8, 8);
            } else if (i < 28) {
                rect[i] = new RectDouble(8, 8, 9, 9);
            } else if (i < 32) {
                rect[i] = new RectDouble(9, 9, 10, 10);
            } else if (i < 36) {
                rect[i] = new RectDouble(2, 2, 4, 4);
            } else if (i < 40) {
                rect[i] = new RectDouble(4, 4, 5, 5);
            } else if (i < 44) {
                rect[i] = new RectDouble(5, 5, 6, 6);
            } else if (i < 48) {
                rect[i] = new RectDouble(6, 6, 7, 7);
            } else if (i < 52) {
                rect[i] = new RectDouble(7, 7, 8, 8);
            } else if (i < 56) {
                rect[i] = new RectDouble(8, 8, 9, 9);
            } else if (i < 60) {
                rect[i] = new RectDouble(9, 9, 10, 10);
            } else if (i < 65) {
                rect[i] = new RectDouble(1, 1, 2, 2);
            }
        }
        for (int i = 0; i < rect.length; i++) {
            rTree.add(rect[i]);
        }
        Stats stat = rTree.stats();
        stat.print(System.out);
        for (int i = 0; i < 5; i++) {
            rTree.remove(rect[64]);
        }
        Stats stat2 = rTree.stats();
        stat2.print(System.out);
    }

    @Test
    void treeUpdateTest() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble rect = new RectDouble(0, 1, 2, 3);
        rTree.add(rect);
        RectDouble oldRect = new RectDouble(0, 1, 2, 3);
        RectDouble newRect = new RectDouble(1, 2, 3, 4);
        rTree.replace(oldRect, newRect);
        RectDouble[] results = new RectDouble[2];
        final int num = rTree.containedToArray(newRect, results);
        assertTrue(num == 1, "Did not find the updated HyperRect");
        System.out.print(results[0]);
    }

    private static RTree<RectDouble> createRectDouble2DTree(Spatialization.DefaultSplits splitType) {
        return createRectDouble2DTree(2, 8, splitType);
    }

    private static RTree<RectDouble> createRectDouble2DTree(int minM, int maxM, Spatialization.DefaultSplits splitType) {
        return new RTree<>((x -> x), minM, maxM, splitType.get());
    }

    @Test
    void testAddsubtreeWithSideTree() {
        final RTree<RectDouble> rTree = createRectDouble2DTree(3, 6, Spatialization.DefaultSplits.QUADRATIC);

        final RectDouble search;

        rTree.add(new RectDouble(2, 2, 4, 4));
        rTree.add(search = new RectDouble(5, 2, 6, 3));

        
        for (int i = 0; i < 5; i++) {
            rTree.add(new RectDouble(3.0 - 1.0 / (10.0 + i), 3.0 - 1.0 / (10.0 + i), 3.0 + 1.0 / (10.0 + i), 3.0 + 1.0 / (10.0 + i)));
        }

        
        rTree.add(new RectDouble(2.5, 2.5, 3.5, 3.5));

        assertEquals(8, rTree.size());

        final AtomicInteger hitCount = new AtomicInteger();
        
        rTree.whileEachContaining(search, (closure) -> {
            hitCount.incrementAndGet();
            return true;
        });

        assertEquals(1, hitCount.get());

    }

}
