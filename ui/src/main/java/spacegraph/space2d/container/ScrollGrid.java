package spacegraph.space2d.container;

import jcog.TODO;
import jcog.Util;
import jcog.data.map.CellMap;
import jcog.data.map.MRUMap;
import jcog.tree.rtree.Spatialization;
import jcog.tree.rtree.rect.RectFloat;
import org.jetbrains.annotations.Nullable;
import spacegraph.input.finger.Finger;
import spacegraph.input.finger.FingerMove;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceBase;
import spacegraph.space2d.container.collection.MutableMapContainer;
import spacegraph.space2d.widget.slider.FloatSlider;
import spacegraph.space2d.widget.slider.SliderModel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static jcog.Util.short2Int;
import static spacegraph.space2d.widget.slider.SliderModel.KnobVert;

/** see also:
 *      https:
 *      https:
 * */
public class ScrollGrid<X> extends Bordering {


    private static final float MIN_DISPLAYED_CELLS = 0.25f;
//    private static final int MAX_DISPLAYED_CELLS_X = 32;
    private static final int MAX_DISPLAYED_CELLS = 32;

    private final GridModel<X> model;
    private final GridRenderer<X> render;
    private final ScrollGridContainer content;




    /** proportional in scale to bounds */
    private static final float defaultScrollEdge = 0.12f;

    private final FloatSlider scrollX, scrollY, scaleW, scaleH;

    /**
     * current view, in local grid coordinate
     */
    private volatile RectFloat view = RectFloat.Zero;

    private static final boolean autoHideScrollForSingleColumnOrRow = true;

//    /** layout temporary values */
//    private transient float cw, ch, dx, dy;


    public ScrollGrid(GridModel<X> model, GridRenderer<X> render, int visX, int visY) {
        this(model, render);
        view(0, 0, visX, visY);
    }

    /**
     * by default, only the first cell will be visible
     */
    public ScrollGrid(GridModel<X> model, GridRenderer<X> render) {
        super();
        borderSize(defaultScrollEdge);
        this.model = model;
        this.render = render;

        set(C, new Clipped(content = new ScrollGridContainer<X>() {

            @Override
            protected void hide(X x, Surface s) {
                render.hide(x, s);
            }

            @Override
            protected Surface surface(short x, short y, X nextValue) {
                return render.apply(x, y, nextValue);
            }

            @Override
            protected X value(short sx, short sy) {
                return model.get(sx, sy);
            }

            @Override
            public Surface finger(Finger finger) {
                Surface inner = super.finger(finger);
                final int moveDragButton = 1;
                if ((inner == null || inner == this) && finger.pressing(moveDragButton)) {
                    if (finger.tryFingering(new FingerMove(moveDragButton,
                            0.05f, 0.05f) {

                        final float sx = view.x;
                        final float sy = view.y;

                        @Override
                        public void move(float tx, float ty) {
                            view(sx - tx, sy - ty);
                        }
                    }))
                        return this;
                }
                return inner;
            }

        }));

        set(S, this.scrollX = new FloatSlider("X",
                new FloatSlider.FloatSliderModel() {
                    @Override
                    public float min() {
                        return 0;
                    }

                    @Override
                    public float max() {
                        return model.cellsX();
                    }
                }
        ).type(SliderModel.KnobHoriz));

        set(E, this.scrollY = new FloatSlider("Y",
                new FloatSlider.FloatSliderModel() {
                    @Override
                    public float min() {
                        return 0;
                    }

                    @Override
                    public float max() {
                        return model.cellsY();
                    }
                }
        ).type(KnobVert)); 


        set(N, new Gridding(
                new EmptySurface(), 
                this.scaleW = new FloatSlider("W",
                        new FloatSlider.FloatSliderModel() {

                            {  setValue(1); }

                            @Override
                            public float min() {
                                return MIN_DISPLAYED_CELLS;
                            }

                            @Override
                            public float max() {
                                return Util.clamp(model.cellsX() * 1.25f, MIN_DISPLAYED_CELLS, MAX_DISPLAYED_CELLS);
                            }
                        }
                ),
                new EmptySurface()  
        ));
        set(W, new Gridding(
                new EmptySurface(), 
                this.scaleH = new FloatSlider("H",
                        new FloatSlider.FloatSliderModel() {

                            {  setValue(1); }

                            @Override
                            public float min() {
                                return MIN_DISPLAYED_CELLS;
                            }

                            @Override
                            public float max() {
                                return Util.clamp(model.cellsY() * 1.25f, MIN_DISPLAYED_CELLS, MAX_DISPLAYED_CELLS);
                            }
                        }
                ).type(KnobVert),
                new EmptySurface()  
        ));
        scrollX.on((sx, x) -> view(x, view.y));
        scrollY.on((sy, y) -> view(view.x, y));
        scaleW.on((sx, w) -> view(view.x, view.y, w, view.h));
        scaleH.on((sy, h) -> view(view.x, view.y, view.w, h));


        
        view(0,0, model.cellsX(), model.cellsY());
    }

    /** the current view */
    public final RectFloat view() {
        return view;
    }

    /** set the view window's center of focus, re-using the current width and height */
    private ScrollGrid<X> view(float x, float y) {
        return view(x, y, view.w, view.h);
    }

    /** set the view window's center and size of focus, in grid coordinates */
    private ScrollGrid<X> view(RectFloat v) {
        return view(v.x, v.y, v.w, v.h);
    }


    /** enables requesting entries from the -1'th row and -1'th column of
     * the model to use as 'pinned' row header cells
     */
    public ScrollGrid<X> setHeader(boolean rowOrColumn, boolean enabled) {
        throw new TODO();
    }

    /** enables or disables certain scrollbar-related features per axis */
    public ScrollGrid<X> setScrollBar(boolean xOrY, boolean scrollVisible, boolean scaleVisible) {
        if (xOrY) {
            scrollX.visible(scrollVisible);
            borderSize(S, scrollVisible ? defaultScrollEdge : 0);
            scaleW.visible(scaleVisible);
            borderSize(N, scaleVisible ? defaultScrollEdge : 0);
        } else {
            scrollY.visible(scrollVisible);
            borderSize(E, scrollVisible ? defaultScrollEdge : 0);
            scaleH.visible(scaleVisible);
            borderSize(W, scaleVisible ? defaultScrollEdge : 0);
        }
        return this;
    }

    /** limits the scaling range per axis */
    public ScrollGrid<X> setCellScale(boolean xOrY, float minScale, float maxScale) {
        throw new TODO();
    }

    /** limits the viewing range per axis */
    public ScrollGrid<X> setCellView(boolean xOrY, float minCoord, float maxCoord) {
        throw new TODO();
    }

    /**
     * sets the x, y position as a fraction of the entire model bounds.
     * if a coordinate is NaN, that coordinate is not affected,
     * allowing shift of either or both X and Y coordinates of the
     * visible cell window.
     */
    public ScrollGrid<X> view(float x, float y, float w, float h) {

        RectFloat v = view;

        float x1, x2, y1, y2;

        float maxW = model.cellsX();
        if (maxW == 1 && autoHideScrollForSingleColumnOrRow) {
            x1 = 0;
            x2 = 1;
            setScrollBar(true, false, false);
        } else {
            if (w < maxW) {
                x = ((((x / maxW) - 0.5f) * 2 /* -1..+1 */ * (1f - w / maxW)) / 2 + 0.5f) * maxW;
            } else {
                x = maxW/2; 
            }
            x1 = (x - w / 2);
            x2 = (x + w / 2);








        }

        float maxH = model.cellsY();
        if (maxH == 1 && autoHideScrollForSingleColumnOrRow) {
            y1 = 0;
            y2 = 1;
            setScrollBar(false, false, false);
        } else {

            
            if (h < maxH) {
                y = ((((y / maxH) - 0.5f) * 2 /* -1..+1 */ * (1f - h / maxH)) / 2 + 0.5f) * maxH;
            } else {
                
                y = maxH / 2;
            }
            y1 = (y - h / 2);
            y2 = (y + h / 2);








        }

        RectFloat nextView = RectFloat.XYXY(x1, y1, x2, y2);
        if (!v.equals(nextView, Spatialization.EPSILONf)) {




        }

        view = nextView;

        content.layout(view,
                (short) Math.max(0, Math.floor(x1)),
                (short) Math.max(0, Math.floor(y1)),
                (short) Math.min(maxW,Math.ceil(x2)),
                (short) Math.min(maxH,Math.ceil(y2))
        ); 

        

        return this;
    }


    @Override
    public boolean start(SurfaceBase parent) {
        if (super.start(parent)) {
            model.start(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean stop() {
        if (super.stop()) {
            model.stop(this);
            return true;
        }
        return false;
    }

    public final void refresh() {
        view(view);
    }


    @FunctionalInterface
    public interface GridRenderer<X> {

        /** adapter for value-only usage */
        static <X> GridRenderer<X> value(Function<X, Surface> builder) {
            return (x, y, v) -> builder.apply(v);
        }

        static <X> GridRenderer<X> valueCached(Function<X, Surface> builder, int capacity) {
            return new GridRenderer<X>() {

//                final HijackMemoize<X,Surface> cache = new HijackMemoize<X,Surface>((x->{
//                    throw new UnsupportedOperationException();
//                }), capacity, 3, true) {
//                    @Override
//                    protected void removed(PriProxy<X, Surface> value) {
//                        Surface s = value.get();
//                        assert(s!=null);
//                        s.stop();
//                    }
//                };

                final MRUMap<X,Surface> cache = new MRUMap<X,Surface>(capacity) {
                    @Override
                    protected void onEvict(Map.Entry<X, Surface> entry) {
                        entry.getValue().stop();
                    }
                };

                @Override
                public Surface apply(int x, int y, X value) {
                    @Nullable Surface s = cache.computeIfAbsent(value, builder);
                    //s.show();
                    return s;
                }

                @Override
                public void hide(X key, Surface s) {
                    //nothing
                }
            };
        }

        Surface apply(int x, int y, X value);


        default void hide(X key, Surface s) {
            s.stop();
        }
    }



    /** hashes 2D cell entries in 16-bit pairs of x,y coordinates */
    abstract static class ScrollGridContainer<X> extends MutableMapContainer<Integer,X> {

        volatile short x1=0, y1=0, x2=1, y2=1;
        private transient RectFloat view;
        private transient float dx, dy, cw, ch;

        ScrollGridContainer() {
            super();
        }

        /**
         * test if a cell is currently visible
         */
        boolean cellVisible(short x, short y) {
            return (x >= x1 && x < x2)
                    &&
                    (y >= y1 && y < y2);
        }

        void layout(RectFloat view, short x1, short y1, short x2, short y2) {
            this.view = view;

            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;

            layout();
        }



        @Override
        protected void doLayout(int dtMS) {

            if (parent == null)
                return;

            dx = x();
            dy = y();
            float ww = w();
            float hh = h();
            cw = ww / view.w;
            ch = hh / view.h;


            cells.map.removeIf(e -> {
                Surface s = ((SurfaceCacheCell)e).surface;

                if (s == null) {
                    //return true;
                } else {
                    int cellID = e.key;
                    short sx = (short) (cellID >> 16);
                    short sy = (short) (cellID & 0xffff);
                    if (!cellVisible(sx, sy)) {
                        return true;
                    }
                }

                return false;
            });



            short x1 = this.x1, y1 = this.y1, x2 = this.x2, y2 = this.y2;

            for (short sx = x1; sx < x2; sx++) {
                for (short sy = y1; sy < y2; sy++) {
                    SurfaceCacheCell e = (SurfaceCacheCell) set(sx, sy, value(sx, sy), true);
                    if (e!=null) {
                        Surface s = e.surface;
                        if (s == null)
                            continue;

                        doLayout(s, sx, sy);
                        if (s.parent==null)
                            s.start(this);
                    }
                }
            }

        }


        void doLayout(Surface s, short sx, short sy) {
            float cx = dx + (sx - view.x + 0.5f) * cw;
            float cy = dy + h() - ((sy - view.y + 0.5f) * ch);
            cellVisible(s, cw, ch, cx, cy);
        }

        abstract protected X value(short sx, short sy);

        void cellVisible(Surface s, float cw, float ch, float cx, float cy) {
            s.pos(RectFloat.XYWH(cx, cy, cw, ch));
        }


        public final void set(short x, short y, @Nullable X v) {
            set(x, y, v, false);
        }

        /**
         * allows a model to asynchronously report changes, which may be visible or not.
         * set 'v' to null to remove an entry (followed by a subsequent non-null 'v'
         * is a way to force rebuilding of a cell.)
         * returns if there was a change
         */
        CellMap.CacheCell set(short x, short y, @Nullable X nextValue, boolean force) {
            if (!force && !cellVisible(x, y))
                return null;

            return put(short2Int(x,y), nextValue, this::renderer);
        }

        private Surface renderer(int cellID, X value) {
            short sx = (short) (cellID >> 16);
            short sy = (short) (cellID & 0xffff);
            return surface(sx, sy, value);
        }

        abstract protected Surface surface(short x, short y, X nextValue);

    }


    public static <X> ScrollGrid<X> array(GridRenderer<X> builder, X... list) {
        return new ScrollGrid<>( ListModel.of(list), builder);
    }
    public static <X> ScrollGrid<X> list(GridRenderer<X> builder, List<X> list) {
        return new ScrollGrid<>( ListModel.of(list), builder);
    }
    public static <X> ScrollGrid<X> list(Function<X,Surface> builder, List<X> list) {
        return new ScrollGrid<>( ListModel.of(list), GridRenderer.value(builder));
    }
    public static <X> ScrollGrid<X> listCached(Function<X,Surface> builder, List<X> list, int cacheCapacity) {
        return new ScrollGrid<>( ListModel.of(list), GridRenderer.valueCached(builder, cacheCapacity));
    }

}



















































































































































































































































































































































































































































































































