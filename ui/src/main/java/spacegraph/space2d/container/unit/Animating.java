package spacegraph.space2d.container.unit;

import jcog.event.Off;
import jcog.exe.Exe;
import jcog.math.FloatSupplier;
import spacegraph.space2d.Surface;
import spacegraph.util.animate.Animated;

import java.util.function.Consumer;

public class Animating<X extends Surface> extends UnitContainer<X> implements Animated {

    private final FloatSupplier minUpdatePeriod;
    private final Consumer<X> each;
    private float waiting = 0;
    private Off animate;

    /** in seconds.  TODO adjustable */
    static final float maxLag = 0.01f;

    public Animating(X the, Runnable each, float minUpdatePeriod) {
        this(the, (z)->each.run(), () -> minUpdatePeriod);
    }

    public Animating(X the, Runnable each, FloatSupplier minUpdatePeriod) {
        this(the, (z)->each.run(), minUpdatePeriod);
    }

    public Animating(X the, Consumer<X> each, float minUpdatePeriod) {
        this(the, each, ()->minUpdatePeriod);
    }

    public Animating(X the, Consumer<X> each, FloatSupplier minUpdatePeriod) {
        super(the);
        this.minUpdatePeriod = minUpdatePeriod;
        this.each = each;
    }

    @Override
    protected void starting() {
        super.starting();
        animate = root().animate(this);
        waiting = minUpdatePeriod.asFloat();
    }

    @Override
    protected void stopping() {
        animate.off();
        animate = null;
        waiting = 0;
    }

    @Override
    public boolean animate(float dt) {
        waiting -= dt;
        if (waiting < 0) {
            Exe.invoke(this::update);
        }
        return true;
    }

    private void update() {
        each.accept(the);
        waiting = Math.max(waiting, -maxLag) + minUpdatePeriod.asFloat();
    }

}
