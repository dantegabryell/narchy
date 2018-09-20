package spacegraph.space2d.widget.windo;

import jcog.data.graph.*;
import jcog.tree.rtree.rect.RectFloat2D;
import org.eclipse.collections.api.tuple.Pair;
import spacegraph.input.finger.DoubleClicking;
import spacegraph.input.finger.Finger;
import spacegraph.space2d.Surface;
import spacegraph.space2d.container.Container;
import spacegraph.space2d.container.collection.MutableListContainer;
import spacegraph.space2d.shape.VerletSurface;
import spacegraph.space2d.widget.meta.MetaFrame;
import spacegraph.space2d.widget.meta.ProtoWidget;
import spacegraph.space2d.widget.meta.WizardFrame;
import spacegraph.util.math.v2;
import toxi.physics2d.VerletParticle2D;
import toxi.physics2d.VerletSpring2D;

import java.util.List;
import java.util.function.Consumer;

/**
 * wall which organizes its sub-surfaces according to 2D phys dynamics
 */
public class GraphEdit<S extends Surface> extends Wall<S> {


    public GraphEdit() {
        super();
        doubleClicking = new DoubleClicking(0, this::doubleClick, this);
    }

    public GraphEdit(float w, float h) {

        this(RectFloat2D.X0Y0WH(0,0 , w, h));
    }
    public GraphEdit(RectFloat2D bounds) {
        this();
        pos(bounds);
    }

    /**
     * TODO use more efficient graph representation
     */
    final MapNodeGraph<Surface, Wire> links = new MapNodeGraph<>() {
        @Override
        protected void onRemoved(Node<Surface, Wire> r) {
            r.edges(true, true).forEach(e -> {
                e.id().remove();
            });
        }
    };


    /** for links and other supporting geometry that is self-managed */
    final MutableListContainer raw = new MutableListContainer(); /* {
        @Override
        protected boolean tangible() {
            return true;
        }
    };*/

    protected final VerletSurface physics = new VerletSurface();

    private final DoubleClicking doubleClicking;

    @Override
    protected final void starting() {

        physics.start(this);
        raw.start(this);

        super.starting();

    }

    public Windo add(Surface x) {
        return add(x, (xx) -> new Windo(new MetaFrame(xx)) {
            @Override
            protected void stopping() {
                //remove any associated links, recursively
                if (xx instanceof Container) {
                    ((Container)xx).forEachRecursively(GraphEdit.this::removingComponent);
                } else {
                    removingComponent(xx);
                }
                super.stopping();
            }
        });
    }

    private void removingComponent(Surface s) {
        synchronized (links) {
            links.removeNode(s);
        }
    }

    @Override
    public void doLayout(int dtMS) {
        physics.pos(bounds);
        raw.pos(bounds);
        super.doLayout(dtMS);
    }

    @Override
    protected final void stopping() {
        physics.stop();
        raw.stop();
        super.stopping();
    }

//    @Override
//    public boolean tangible() {
//        return true;
//    }

    public final void addRaw(Surface s) {
        raw.add(s);
    }


//    @Override
//    public boolean whileEach(Predicate<Surface> o) {
//        return super.whileEach(o);
//    }
//
//    @Override
//    public boolean whileEachReverse(Predicate<Surface> o) {
//        return super.whileEachReverse(o);
//    }


    @Override
    public void forEach(Consumer<Surface> each) {
        each.accept(physics);
        each.accept(raw);
        super.forEach(each);
    }


//    @Override
//    protected void paintBelow(GL2 gl, SurfaceRender r) {
//        raw.renderContents(gl, r);
//    }


    //    /**
//     * create a static box around the content, which moves along with the surface's bounds
//     */
//    public Dyn2DSurface enclose() {
//        new StaticBox(this::bounds);
//        return this;
//    }

//    private RectFloat2D bounds() {
//        return bounds;
//    }


//    public float rngPolar(float scale) {
//        return
//                (float) rng.nextGaussian() * scale;
//    }
//
//    public float rngNormal(float scale) {
//        return rng.nextFloat() * scale;
//    }

//    /**
//     * spawns in view center at the given size
//     */
//    public PhyWindow put(Surface content, float w, float h) {
//        //Ortho view = (Ortho) root();
//        return put(content, RectFloat2D.XYWH(0, 0, w, h)); //view.x(), view.y(),
//    }
//
//    public PhyWindow frame(Surface content, float w, float h) {
//        return put(new MetaFrame(content), w, h);
//    }
//
//    public PhyWindow put(Surface content, RectFloat2D initialBounds) {
//        return put(content, initialBounds, true);
//    }
//
//    private PhyWindow put(Surface content, RectFloat2D initialBounds, boolean collides) {
//        PhyWindow s = new PhyWindow(initialBounds, collides);
//
//        s.add(content);
//
//        return s;
//    }

//    private Snake snake(Wire wire, Runnable onRemove) {
//        Surface source = wire.a;
//        Surface target = wire.b;
//
//        assert (source != target);
//
//        float sa = source.bounds.area();
//        float ta = target.bounds.area();
//        float areaDiff = Math.abs(sa - ta) / (sa + ta);
//
//        int segments = Util.lerp(areaDiff, 8, 6);
//
//        float EXPAND_SCALE_FACTOR = 4;
//
//        PushButton deleteButton = new PushButton("x");
//        Surface menu = new TabPane(Map.of("o", () -> new Gridding(
//                new VectorLabel(source.toString()),
//                new VectorLabel(target.toString()),
//                deleteButton
//        )), (l) -> new CheckBox(l) {
//            @Override
//            protected String label(String text, boolean on) {
//                return text;
//            }
//
//            @Override
//            public ToggleButton set(boolean expanded) {
//
//                super.set(expanded);
//
//                synchronized (wire) {
//
//                    PhyWindow w = parent(PhyWindow.class);
//                    if (w == null)
//                        return this;
//                    float cx = w.cx();
//                    float cy = w.cy();
//                    float ww, hh;
//                    if (expanded) {
//
//                        ww = w.w() * EXPAND_SCALE_FACTOR;
//                        hh = w.h() * EXPAND_SCALE_FACTOR;
//                    } else {
//
//                        ww = w.w() / EXPAND_SCALE_FACTOR;
//                        hh = w.h() / EXPAND_SCALE_FACTOR;
//                    }
//                    w.pos(cx - ww / 2, cy - hh / 2, cx + ww / 2, cy + hh / 2);
//                }
//
//                return this;
//            }
//        });
//
//        PhyWindow menuBody = put(menu,
//                RectFloat2D.mid(source.bounds, target.bounds, 0.1f));
//
//        float mw = menuBody.radius();
//
////        Snake s = new Snake(source, target, segments, 1.618f * 2 * mw, mw) {
////
////            @Override
////            public void remove() {
////                onRemove.run();
////                super.remove();
////            }
////        };
//
//
//        //s.attach(menuBody.body, segments / 2 - 1);
//
//        deleteButton.click(s::remove);
//
//        int jj = 0;
//        for (Joint j : s.joints) {
//
//            float p = ((float) jj) / (segments - 1);
//
//
//            j.setData((ObjectLongProcedure<GL2>) (g, now) -> {
//
//                int TIME_DECAY_MS = 250;
//                boolean side = p < 0.5f;
//                float activity =
//                        wire.activity(side, now, TIME_DECAY_MS);
//
//
//                int th = wire.typeHash(side);
//                if (th == 0) {
//                    g.glColor4f(0.5f, 0.5f, 0.5f, 0.5f);
//                } else {
//                    Draw.colorHash(g, th, 0.9f, 0.5f + 0.5f * activity, 0.5f + 0.4f * activity);
//                }
//
//                g.glLineWidth(10f + activity * 10f);
//
//
//            });
//            jj++;
//        }
//
//        return s;
//    }

    public Iterable<FromTo<Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire>, Wire>> edges(Surface s) {
        Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire> n = links.node(s);
        return n != null ? n.edges(true, true) : List.of();
    }


    @Override
    public Surface finger(Finger finger) {

        Surface s = super.finger(finger);
//        if (s != null && s != this && !(s instanceof PhyWindow))
//            return s;

        if (s==null || s == raw)
            doubleClicking.update(finger);


//        if (finger.tryFingering(jointDrag))
//            return this;


        return s != null ? s : this;

    }

    protected void doubleClick(v2 pos) {
        float h = 100;
        float w = 100;
        add(
                new WizardFrame(new ProtoWidget()) {
                    @Override
                    protected void become(Surface next) {
                        super.become(next);

                        //GraphEdit pp = parent(GraphEdit.class);
//                        if (next instanceof ProtoWidget) {
//                            pp.setCollidable(false);
//                        } else {
//                            pp.setCollidable(true);
//                        }

                    }
                }).pos(RectFloat2D.XYWH(pos.x, pos.y, w, h));
    }

    public void removeRaw(Surface x) {
        raw.removeChild(x);
    }


    static class Cable extends Wire {

        final Windo grip;

        Cable(Wire wire, Windo grip) {
            super(wire);
            this.grip = grip;
        }

        public final void remove() {
            grip.remove();
        }
    }

    /** returns the grip window */
    public Cable cable(Wire w, Surface grip) {
        Surface a = w.a;
        Surface b = w.b;
        VerletParticle2D ap = physics.addParticleBind(a, VerletSurface.VerletSurfaceBinding.Center);
        VerletParticle2D bp = physics.addParticleBind(b, VerletSurface.VerletSurfaceBinding.Center);

        float m = 8; //TODO dynamic based on surface area, density
        ap.mass(m);
        bp.mass(m);

        int chainLen = 5; //should be an odd number
        Pair<List<VerletParticle2D>, List<VerletSpring2D>> chain = physics.addParticleChain(ap, bp,
                chainLen, 10f /* some minimal # */, 0.05f);

        final List<VerletParticle2D> points = chain.getOne();
        VerletParticle2D first = points.get(0);
        VerletParticle2D last = points.get(points.size()-1);

        VerletParticle2D mid = points.get(points.size()/2);

//        if (first!=mid) {
//            mid.addBehaviorGlobal(new AttractionBehavior2D<>(mid, 300, -1));
//        }

        Windo gripWindow = add(grip, (g)->{ return new Windo(new MetaFrame(g)) {
                @Override
                protected void stopping() {

                    unlink(a, b);

                    //destroy the chain
                    points.clear();
                    List<VerletSpring2D> springs = chain.getTwo();
                    springs.forEach(physics.physics::removeSpringElements);
                    springs.clear();

                    super.stopping();
                }
            };
        }).pos(RectFloat2D.XYWH(mid.x, mid.y, 20, 20));

        physics.bind(gripWindow,  mid, false, VerletSurface.VerletSurfaceBinding.Center);

        return new Cable(w, gripWindow);
    }


    /**
     * undirected link
     */
    public Wire link(Wire wire) {

        Surface aa = wire.a, bb = wire.b;

        synchronized (links) {

            NodeGraph.MutableNode<Surface, Wire> A = links.addNode(aa);

            Iterable<FromTo<Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire>, Wire>> edges = A.edges(false, true);
            if (edges != null) {

                for (FromTo<Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire>, Wire> e : edges) {
                    Wire ee = e.id();
                    if (wire.equals(ee))
                        return ee;
                }
            }

            if (!wire.connect()) {
                return null;
            }

            NodeGraph.MutableNode<Surface, Wire> B = links.addNode(bb);
            links.addEdge(A, wire, B);


//                W.invoke(() -> {
//
//
//                    {
//
//
//                        Snake s = snake(wire, () -> unlink(aa, bb));
//
//                    }
//
//
//                });
        }

        return wire;

    }

    private Wire unlink(Surface source, Surface target) {
        synchronized (links) {
            Wire wire = new Wire(source, target);
            Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire> an = links.node(wire.a);
            if (an != null) {
                Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire> bn = links.node(wire.b);
                if (bn != null) {
                    boolean removed = links.edgeRemove(new ImmutableDirectedEdge<>(
                            an, wire, bn)
                    );
                    return removed ? wire : null;
                }
            }
            return null;
        }
    }

//    @Deprecated public class PhyWindow extends Windo {
//        //public final Body2D body;
//        //private final PolygonShape shape;
//
//
//        PhyWindow(RectFloat2D initialBounds, boolean collides) {
//            super();
//            pos(initialBounds);
//
//
////            this.shape =
////
////                    PolygonShape.box(initialBounds.w / 2, initialBounds.h / 2);
////
////            FixtureDef fd = new FixtureDef(shape, 1f, 0.75f);
////            if (!collides) {
////                fd.filter.maskBits = 0;
////            }
////
////            fd.setRestitution(0.1f);
////
////
////            W.addBody(this.body = new WallBody(initialBounds.cx(), initialBounds.cy()), fd);
////            body.setLinearDamping(linearDampening);
////            if (!collides) {
////                body.setGravityScale(0f);
////            }
//        }
//
////        void setCollidable(boolean c) {
////            W.invoke(() -> {
////                body.fixtures.filter.maskBits = (c ? 0xffff : 0);
////                body.setGravityScale(c ? 1f : 0f);
////                body.setAwake(true);
////            });
////        }
//
//        @Override
//        public boolean fingerable(DragEdit d) {
//            if (d == DragEdit.MOVE)
//                return false;
//
//
//            return super.fingerable(d);
//        }
//
//        public void remove() {
//            synchronized (links) {
//                links.removeNode(this);
//            }
////            W.removeBody(this.body);
//            //Dyn2DSurface.this.remove(this);
//            throw new TODO();
//        }
//
//
//        public Pair<PhyWindow, Wire> sprout(Surface target, float scale) {
//            return sprout(target, scale, 1f);
//        }
//
//        /**
//         * convenience method for essentially growing a separate window
//         * of a proportional size with some content (ex: a port),
//         * and linking it to this window via a constraint.
//         */
//        Pair<PhyWindow, Wire> sprout(Surface target, float scale, float targetAspect) {
//            PhyWindow sprouted = spawn(target, scale, targetAspect);
//
//            return pair(sprouted, link(target));
//        }
//
////        /**
////         * spawns and attaches a new component to the boundary of this
////         */
////        public PhyWindow grow(Surface target, float scale, float targetAspect, Tuple2f normal) {
////
////            PhyWindow x = spawn(target, scale, targetAspect);
////
////            W.invoke(() -> {
////                Tuple2f myWeldLocal, theirWeldLocal;
////                RayCastInput input = new RayCastInput();
////                RayCastOutput output = new RayCastOutput();
////                {
////                    input.p2.set(0, 0);
////                    float r = radius() * 2;
////                    input.p1.set(0 + normal.x * r, 0 + normal.y * r);
////                    input.maxFraction = 1.0f;
////
////                    boolean hit = body.fixtures.raycast(output, input, 0);
////                    assert (hit);
////                    Tuple2f hitPoint = (input.p2.sub(input.p1)).scaled(output.fraction).added(input.p1);
////                    myWeldLocal = hitPoint;
////                }
////                {
////                    input.p2.set(0, 0);
////                    float r = x.radius() * 2;
////                    input.p1.set(0 - normal.x * r, 0 - normal.y * r);
////                    input.maxFraction = 1.0f;
////
////                    boolean hit = x.body.fixtures.raycast(output, input, 0);
////                    assert (hit);
////                    Tuple2f hitPoint = (input.p2.sub(input.p1)).scaled(output.fraction).added(input.p1);
////                    theirWeldLocal = hitPoint;
////                }
////
////                WeldJoint j = weld(x, myWeldLocal, theirWeldLocal);
////
////            });
////            return x;
////        }
//
////        private WeldJoint weld(PhyWindow x, Tuple2f myLocal, Tuple2f theirLocal) {
////            WeldJointDef jd = new WeldJointDef();
////            jd.bodyA = this.body;
////            jd.bodyB = x.body;
////            jd.localAnchorA.set(myLocal.scaled(0.5f));
////            jd.localAnchorB.set(theirLocal.scaled(0.5f));
////            jd.referenceAngle = ((v2) myLocal).angle(theirLocal);
////            jd.collideConnected = false;
////            jd.dampingRatio = 0.5f;
////            jd.frequencyHz = 0.25f;
////
////            WeldJoint j = new WeldJoint(W.pool, jd);
////            W.addJoint(j);
////            return j;
////        }
//
//        PhyWindow spawn(Surface target, float scale, float targetAspect) {
//            float W = w();
//            float H = h();
//            float sprouterRadius = radius();
//            float w = W * scale;
//            float h = H * scale;
//
//
//            RectFloat2D sproutSize = RectFloat2D.XYWH(0, 0, w, h);
//
//
//            float minRadius = sprouterRadius + sproutSize.radius();
//
//            float a = (float) (Math.random() * 2 * (float) Math.PI);
//            float dx = cx() + (float) (minRadius * Math.cos(a));
//            float dy = cy() + (float) (minRadius * Math.sin(a));
//
//            return put(target, sproutSize.move(dx, dy, Spatialization.EPSILONf));
//        }
//
//
//        /**
//         * assumes the PhyWindow wraps *THE* source
//         */
//        Wire link(Surface target) {
//            assert (children().length == 1);
//            return link(get(0), target);
//        }
//
//
//
//        /**
//         * convenience method for creating a basic undirected link joint.
//         * no endpoint is necessarily an owner of the other so
//         * it should not matter who is the callee.
//         * <p>
//         * duplicate links are prevented.
//         */
//        public Wire link(Surface source, Surface target) {
//            return link(new Wire(source, target));
//        }
//
//        /**
//         * undirected link
//         */
//        Wire link(Wire wire) {
//
//            Surface aa = wire.a;
//            Surface bb = wire.b;
//
//            synchronized (links) {
//
//                NodeGraph.MutableNode<Surface, Wire> A = links.addNode(aa);
//
//                Iterable<FromTo<Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire>, Wire>> edges = A.edges(false, true);
//                if (edges != null) {
//
//                    for (FromTo<Node<spacegraph.space2d.Surface, spacegraph.space2d.widget.windo.Wire>, Wire> e : edges) {
//                        Wire ee = e.id();
//                        if (wire.equals(ee))
//                            return ee;
//                    }
//                }
//
//                if (!wire.connect()) {
//                    return null;
//                }
//
//                NodeGraph.MutableNode<Surface, Wire> B = links.addNode(bb);
//                links.addEdge(A, wire, B);
//
//
////                W.invoke(() -> {
////
////
////                    {
////
////
////                        Snake s = snake(wire, () -> unlink(aa, bb));
////
////                    }
////
////
////                });
//            }
//
//            return wire;
//
//        }
//
//
//        void sproutBranch(String label, float scale, float childScale, Iterable<Surface> children) {
//            CheckBox toggle = new CheckBox(label);
//            Pair<PhyWindow, Wire> toggleWindo = sprout(toggle, scale);
////            List<PhyWindow> built = new FasterList(0);
////            toggle.on((cb, enabled) -> W.invoke(() -> {
////
////                if (enabled) {
////                    for (Surface x : children) {
////                        built.add(toggleWindo.getOne().sprout(x, childScale).getOne());
////                    }
////                } else {
////
////                    built.forEach(PhyWindow::remove);
////                    built.clear();
////                }
////
////            }));
//        }
//
//        public void sproutBranch(String label, float scale, float childScale, Supplier<Surface[]> children) {
//            sproutBranch(label, scale, childScale, ArrayIterator.iterable(children.get()));
//        }
//
//        @Override
//        public boolean tangible() {
//            return true;
//        }
//
//    }
}

//private class WallBody extends Body2D {
//
//    RectFloat2D physBounds = null;
//
//    WallBody(float cx, float cy) {
//        super(new BodyDef(BodyType.DYNAMIC, new v2(cx / scaling, cy / scaling)), Dyn2DSurface.this.W);
//
//        setData(this);
//
//        setFixedRotation(true);
//        this.physBounds = bounds;
//    }
//
//    @Override
//    protected void onRemoval() {
//
//        Dyn2DSurface.PhyWindow.this.remove();
//    }
//
//    @Override
//    public boolean preUpdate() {
//
//        RectFloat2D r = bounds;
//        if (r != physBounds) {
//
//            if (!Util.equals(r.w, physBounds.w, SHAPE_SIZE_EPSILON) ||
//                    !Util.equals(r.h, physBounds.h, SHAPE_SIZE_EPSILON)) {
//                updateFixtures((f) -> f.setShape(
//                        shape.setAsBox(r.w / 2 / scaling, r.h / 2 / scaling)
//
//                ));
//            }
//
//
//            v2 target = new v2(r.cx() / scaling, r.cy() / scaling);
//
//            if (setTransform(target, 0, Spatialization.EPSILONf))
//                setAwake(true);
//        }
//
//        return true;
//    }
//
//    @Override
//    public void postUpdate() {
//
//
//        Transform t = this;
//        Tuple2f p = t.pos;
//
//
//        float w = w(), h = h();
//
//        RectFloat2D r = RectFloat2D.XYWH(p.x * scaling, p.y * scaling, w, h);
//        if (!r.equals(physBounds, Spatialization.EPSILONf)) {
//            pos(physBounds = r);
//        }
//
//    }
//}

























































/*
     content = new Graph2D<>();
                //.render()...
        W.setParticleRadius(0.2f);
        W.setParticleDensity(1.0f);

        W.setWarmStarting(true);
        W.setAllowSleep(true);
        W.setContinuousPhysics(true);


 */

//    public final Dynamics2D W = new Dynamics2D(new v2(0, 0));
//    public final Random rng = new XoRoShiRo128PlusRandom(1);
//
//    private FingerDragging jointDrag = new FingerDragging(MOUSE_JOINT_BUTTON) {
//
//        final Body2D ground = W.addBody(new BodyDef(BodyType.STATIC),
//                new FixtureDef(PolygonShape.box(0, 0), 0, 0).noCollide());
//
//        private volatile MouseJoint mj;
//
//        @Override
//        protected boolean startDrag(Finger f) {
//            if (super.startDrag(f)) {
//                Body2D touched2D;
//                if (((touched2D = pick(f)) != null)) {
//                    MouseJointDef def = new MouseJointDef();
//
//                    def.bodyA = ground;
//                    def.bodyB = touched2D;
//                    def.collideConnected = true;
//
//
//                    def.target.set(f.pos);
//
//                    def.maxForce = 500f * touched2D.getMass();
//                    def.dampingRatio = 0;
//
//                    mj = (MouseJoint) W.addJoint(new MouseJoint(W.pool, def));
//                    return true;
//                }
//            }
//            return false;
//        }
//
//
//        Body2D pick(Finger ff) {
//            v2 p = ff.pos.scale(scaling);
//
//
//            float w = 0;
//            float h = 0;
//
//
//            final Fixture[] found = {null};
//            W.queryAABB((Fixture f) -> {
//                if (f.body.type != BodyType.STATIC &&
//                        f.filter.maskBits != 0 /* filter non-colllidables */ && f.testPoint(p)) {
//                    found[0] = f;
//                    return false;
//                }
//
//                return true;
//            }, new AABB(new v2(p.x - w, p.y - h), new v2(p.x + w, p.y + h), false));
//
//
//
//
//
//
//
//
//
//
//
//
//            return found[0] != null ? found[0].body : null;
//        }
//
//        @Override
//        public void stop(Finger finger) {
//            super.stop(finger);
//            if (mj != null) {
//                W.removeJoint(mj);
//                mj = null;
//            }
//        }
//
//        @Override
//        protected boolean drag(Finger f) {
//            if (mj != null) {
//                v2 p = f.pos.scale(scaling);
//
//                /*if (clickedPoint != null)*/
//
//
//
//
//                mj.setTarget(p);
//            }
//
//
//
//
//
//
//
//            return true;
//
//        }
//
//    };

//    protected RopeJoint rope(Surface source, Surface target) {
//
//        RopeJointDef jd = new RopeJointDef(source.parent(PhyWindow.class).body, target.parent(PhyWindow.class).body);
//
//        jd.collideConnected = true;
//        jd.maxLength = Float.NaN;
//
//        RopeJoint ropeJoint = new RopeJoint(Dyn2DSurface.this.W.pool, jd) {
//
//            float lengthScale = 2.05f;
//
//            @Override
//            public float targetLength() {
//
//
//                return ((source.radius() + target.radius()) * lengthScale)
//
//                        ;
//
//
//            }
//        };
//
//
//        W.addJoint(ropeJoint);
//        return ropeJoint;
//    }
//

//    @Override
//    protected void paintBelow(GL2 gl) {
//        super.paintBelow(gl);
//
//        Dynamics2D w = this.W;
//
//        long now = System.currentTimeMillis();
//
//
//        w.joints(j -> drawJoint(j, gl, now));
//
//        w.bodies(b -> drawBody(b, gl));
//
//        drawParticleSystem(gl, w.particles);
//
//
//    }
//
//    private void drawParticleSystem(GL2 gl, ParticleSystem system) {
//
//        int particleCount = system.getParticleCount();
//        if (particleCount != 0) {
//            float particleRadius = system.getParticleRadius();
//            Tuple2f[] positionBuffer = system.getParticlePositionBuffer();
//            ParticleColor[] colorBuffer = null;
//            if (system.m_colorBuffer.data != null) {
//                colorBuffer = system.getParticleColorBuffer();
//            }
//
//
//            Draw.particles(gl, positionBuffer, particleRadius, 6, colorBuffer, particleCount);
//
//        }
//    }
//
//    private void drawJoint(Joint joint, GL2 g, long now) {
//        Object data = joint.data();
//        if (data instanceof ObjectLongProcedure) {
//            ((ObjLongConsumer) data).accept(g, now);
//        } else {
//
//            Draw.colorHash(g, joint.getClass().hashCode(), 0.5f);
//            g.glLineWidth(10f);
//        }
//        Tuple2f v1 = new v2(), v2 = new v2();
//        switch (joint.getType()) {
//            default:
//                joint.getAnchorA(v1);
//                joint.getAnchorB(v2);
//                break;
//        }
//        Draw.line(g, v1.x * scaling, v1.y * scaling, v2.x * scaling, v2.y * scaling);
//
//    }
//
//    private void drawBody(Body2D body, GL2 gl) {
//
//
////        if (body.data() instanceof PhyWindow.WallBody) {
////            return;
////        }
//        if (body instanceof Consumer) {
//            ((Consumer) body).accept(gl);
//            return;
//        }
//
//
//        boolean awake = body.isAwake();
//        gl.glColor4f(0.5f, 0.5f, 0.5f, awake ? 0.75f : 0.65f);
//
//
//        for (Fixture f = body.fixtures; f != null; f = f.next) {
//            PolygonFixture pg = f.polygon;
//            if (pg != null) {
//
//            } else {
//                Shape shape = f.shape();
//                switch (shape.m_type) {
//                    case POLYGON:
//                        Draw.poly(body, gl, scaling, (PolygonShape) shape);
//                        break;
//                    case CIRCLE:
//
//                        CircleShape circle = (CircleShape) shape;
//                        float r = circle.radius;
//                        v2 v = new v2();
//                        body.getWorldPointToOut(circle.center, v);
//                        v.scale(scaling);
//
//
//                        Draw.circle(gl, v, true, r * scaling, 9);
//                        break;
//                    case EDGE:
//                        EdgeShape edge = (EdgeShape) shape;
//                        Tuple2f p1 = edge.m_vertex1;
//                        Tuple2f p2 = edge.m_vertex2;
//                        gl.glLineWidth(4f);
//                        Draw.line(gl, p1.x * scaling, p1.y * scaling, p2.x * scaling, p2.y * scaling);
//                        break;
//                }
//            }
//        }
//
//
//    }
//class StaticBox {
//
//    private final Body2D body;
//    private final Fixture bottom;
//    private final Fixture top;
//    private final Fixture left;
//    private final Fixture right;
//
//    StaticBox(Supplier<RectFloat2D> bounds) {
//
//        float w = 1, h = 1, thick = 0.5f;
//
//        body = W.addBody(new Body2D(new BodyDef(BodyType.STATIC), W) {
//            @Override
//            public boolean preUpdate() {
//                update(bounds.get());
//                synchronizeFixtures();
//                return true;
//            }
//        });
//        bottom = body.addFixture(
//                new FixtureDef(PolygonShape.box(w / 2 - thick / 2, thick / 2),
//                        0, 0)
//        );
//        top = body.addFixture(
//                new FixtureDef(PolygonShape.box(w / 2 - thick / 2, thick / 2),
//                        0, 0)
//        );
//        left = body.addFixture(
//                new FixtureDef(PolygonShape.box(thick / 2, h / 2 - thick / 2),
//                        1, 0)
//        );
//        right = body.addFixture(
//                new FixtureDef(PolygonShape.box(thick / 2, h / 2 - thick / 2),
//                        1, 0)
//        );
//
//
//    }
//
//    void update(RectFloat2D bounds) {
//
//        body.updateFixtures(f -> {
//
//            float cx = bounds.cx() / scaling;
//            float cy = bounds.cy() / scaling;
//            float thick = Math.min(bounds.w, bounds.h) / 16f / scaling;
//
//            float W = bounds.w / scaling;
//            float H = bounds.h / scaling;
//            ((PolygonShape) top.shape).setAsBox(W, thick, new v2(cx / 2, +H), 0);
//            ((PolygonShape) right.shape).setAsBox(thick, H, new v2(+W, cy / 2), 0);
//            ((PolygonShape) bottom.shape).setAsBox(W, thick, new v2(cx, 0), 0);
//            ((PolygonShape) left.shape).setAsBox(thick, H, new v2(0, cy), 0);
//        });
//
//    }
//}
