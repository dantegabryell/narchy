package spacegraph.space2d.widget.meta;

import com.google.common.collect.Sets;
import jcog.TODO;
import jcog.data.list.FasterList;
import jcog.event.Ons;
import jcog.math.FloatRange;
import jcog.math.IntRange;
import jcog.math.MutableEnum;
import jcog.service.Service;
import jcog.service.Services;
import jcog.util.Reflect;
import org.jetbrains.annotations.Nullable;
import spacegraph.SpaceGraph;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceBase;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.widget.button.CheckBox;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.space2d.widget.button.ToggleButton;
import spacegraph.space2d.widget.slider.FloatSlider;
import spacegraph.space2d.widget.slider.IntSlider;
import spacegraph.space2d.widget.tab.ButtonSet;
import spacegraph.space2d.widget.text.LabeledPane;
import spacegraph.space2d.widget.windo.Widget;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * constructs a representative surface for an object by reflection analysis
 */
public class ObjectSurface<X> extends Gridding {

    private final int maxDepth;
    private final Set<Object> seen = Sets.newSetFromMap(new IdentityHashMap());


    final Map<Class,Function<?,Surface>> onClass = new ConcurrentHashMap<>();
    final Map<Predicate,Function<?,Surface>> onIf = new ConcurrentHashMap<>();

    /**
     * root
     */
    private final X obj;
    private Ons ons = null;

    public ObjectSurface(X x) {
        this(x, 1);
    }

    public ObjectSurface(X x, int maxDepth) {
        super();
        this.maxDepth = maxDepth;
        this.obj = x;
    }

    @Override
    public boolean start(@Nullable SurfaceBase parent) {

        if (super.start(parent)) {
            seen.clear();
            ons = new Ons();
            List<Surface> l = new FasterList();
            collect(obj, l, 0);

            set( new ObjectMetaFrame(obj, new Gridding(l)));
            return true;
        }
        return false;
    }

    private void collect(Object y, List<Surface> l, int depth) {
        collect(y, l, depth, null);
    }

    private void collect(Object x, List<Surface> target, int depth, String yLabel /* tags*/) {

        if (!add(x))
            return;

        if (yLabel == null)
            yLabel = x.toString();

        if (x instanceof Surface) {
            Surface sx = (Surface) x;
            if (sx.parent == null) {
                target.add(new LabeledPane(yLabel, sx));
            }
            return;
        }

        {
            if (!onIf.isEmpty()) {
                onIf.forEach((Predicate test, Function builder)->{
                   if (test.test(x)) {
                       Surface s = (Surface) builder.apply(x);
                       if (s != null)
                           target.add(s);
                   }
                });
            }
        }

        {
            Function builder = onClass.get(x.getClass()); //TODO check subtypes/supertypes etc
            if (builder != null) {
                Surface s = (Surface) builder.apply(x);
                if (s != null) {
                    target.add(s);
                    return;
                }
            }
        }

        //TODO rewrite these as pluggable onClass handlers

        if (x instanceof Services) {
            target.add(new AutoServices((Services) x));
            return;
        }

        if (x instanceof Collection) {
            Surface cx = collectElements((Iterable) x, depth + 1);
            if (cx != null) {
                target.add(new LabeledPane(yLabel, cx));
            }
        }


        if (x instanceof FloatRange) {
            target.add(new MySlider((FloatRange) x, yLabel));
        } else if (x instanceof IntRange) {
            target.add(new MyIntSlider((IntRange) x, yLabel));
        } else if (x instanceof AtomicBoolean) {
            target.add(new MyAtomicBooleanCheckBox(yLabel, (AtomicBoolean) x));
        } else if (x instanceof Runnable) {
            target.add(new PushButton(yLabel, (Runnable) x));
        } else if (x instanceof MutableEnum) {
            target.add(newSwitch((MutableEnum) x));
        }

        if (depth < maxDepth) {
            collectFields(x, target, depth + 1);
        }
    }

    private <C extends Enum<C>> ButtonSet newSwitch(MutableEnum x) {
        EnumSet<C> s = EnumSet.allOf(x.klass);

        Enum initialValue = x.get();
        int initialButton = -1;

        ToggleButton[] b = new ToggleButton[s.size()];
        int i = 0;
        for (C xx : s) {
            CheckBox tb = new CheckBox(xx.name());
            tb.on((c, enabled) -> {
                if (enabled)
                    x.set(xx);
            });
            if (xx == initialValue)
                initialButton = i;
            b[i++] = tb;
        }


//JDK12 compiler error has trouble with this:
//        ToggleButton[] b = ((EnumSet) EnumSet.allOf(x.klass)).stream().map(e -> {
//            CheckBox tb = new CheckBox(e.name());
//            tb.on((c, enabled) -> {
//                if (enabled)
//                    x.set(e);
//            });
//            return tb;
//        }).toArray(ToggleButton[]::new);
//
        ButtonSet bs = new ButtonSet(ButtonSet.Mode.One, b);

        if (initialButton != -1) {
            b[initialButton].set(true);
        }

        return bs;
    }

    private Surface collectElements(Iterable<?> x, int depth) {
        FasterList<Surface> m = new FasterList();
        for (Object o : x) {
            collect(o, m, depth);
        }
        return !m.isEmpty() ? new ObjectMetaFrame(x, grid(m)) : null;
    }

    public static class ObjectMetaFrame extends MetaFrame {
        public final Object instance;
        public final Surface surface;

        public ObjectMetaFrame(Object instance, Surface surface) {
            super(surface);
            if (instance instanceof Surface)
                throw new TODO();
            this.instance = instance;
            this.surface = surface;
        }

        protected String name(Surface widget) {
            return instance!=null ?  instance.toString() : "";
        }


        //TODO other inferred features
    }

    @Override
    public boolean stop() {
        if (super.stop()) {
            if (ons != null) {
                ons.off();
                ons = null;
            }
            return true;
        }
        return false;
    }

    private void collectFields(Object x, List<Surface> target, int depth) {
        Class cc = x.getClass();
        Reflect.on(cc).fields(true, false, false).forEach((s, ff) -> {
            Field f = ff.get();
            try {
                Object y = f.get(x);
                if (y != null && y != x)
                    collect(y, target, depth, f.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
//        for (Field f : cc.getFields()) {
//
//            int mods = f.getModifiers();
//            if (Modifier.isStatic(mods))
//                continue;
//            if (!Modifier.isPublic(mods))
//                continue;
//            if (f.getType().isPrimitive())
//                continue;
//
//            try {
//
//
//                f.trySetAccessible();
//
//
//                Object y = f.get(x);
//                if (y != null && y != x)
//                    collect(y, target, depth, f.getName());
//
//            } catch (Throwable t) {
//                t.printStackTrace();
//            }
//        }


    }

    public <Y> ObjectSurface<X> on(Class<? extends Y> c, Function<? extends Y, Surface> each) {
        onClass.put(c, each);
        return this;
    }
    public <Y> ObjectSurface<X> on(Predicate test, Function<Y, Surface> each) {
        onIf.put(test, each);
        return this;
    }

    private static class MySlider extends FloatSlider {
        private final String k;

        MySlider(FloatRange p, String k) {
            super(p);
            this.k = k;
        }


        @Override
        public String text() {
            return k + '=' + super.text();
        }
    }

    private static class MyIntSlider extends IntSlider {
        private final String k;

        MyIntSlider(IntRange p, String k) {
            super(p);
            this.k = k;
        }

        @Override
        public String text() {
            return k + '=' + super.text();
        }
    }

    private static class MyAtomicBooleanCheckBox extends CheckBox {
        final AtomicBoolean a;

        public MyAtomicBooleanCheckBox(String yLabel, AtomicBoolean x) {
            super(yLabel, x);
            this.a = x;
        }

        @Override
        public boolean prePaint(SurfaceRender r) {
            set((a.getOpaque())); //load
            return super.prePaint(r);
        }
    }

    private class AutoServices extends Widget {
        AutoServices(Services<?, ?> x) {

            List<Surface> l = new FasterList(x.size());

            x.entrySet().forEach((ks) -> {
                Service<?> s = ks.getValue();

                if (addService(s)) {
                    String label = s.toString();


                    l.add(
                            new PushButton(IconBuilder.simpleBuilder.apply(s)).click(() -> SpaceGraph.window(
                                    new LabeledPane(label, new ObjectSurface(s)),
                                    500, 500))


                    );
                }


            });

            content(new ObjectMetaFrame(x, new Gridding(l)));
        }
    }

    private boolean add(Object x) {
        return seen.add(x);
    }

    private boolean addService(Service<?> x) {
        return add(x);
    }

}