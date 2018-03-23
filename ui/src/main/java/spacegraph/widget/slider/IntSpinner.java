package spacegraph.widget.slider;

import jcog.Util;
import jcog.math.MutableInteger;
import spacegraph.container.Gridding;
import spacegraph.container.Splitting;
import spacegraph.widget.button.PushButton;
import spacegraph.widget.text.Label;
import spacegraph.widget.windo.Widget;

import java.util.function.IntFunction;

public class IntSpinner extends Widget {

    private final int min;
    private final int max;
    private final Label label;
    private final MutableInteger i;
    private final IntFunction<String> labeller;

    public IntSpinner(MutableInteger i, IntFunction<String> labeller, int min, int max) {
        this.min = min;
        this.max = max;
        this.i = i;
        this.labeller = labeller;
        content(
            new Splitting(
                label = new Label(),
                new Gridding(Gridding.HORIZONTAL,
                    new PushButton("+", ()->{
                        update(+1);
                    }),
                    new PushButton("-", ()->{
                        update(-1);
                    })
            ), 0.2f)
        );
        update(0);
    }

    public synchronized void update(int delta) {
        int nextValue = Util.clamp(i.intValue() + delta, min, max);
        label.text(labeller.apply(nextValue));
        i.set(nextValue);
    }

}
