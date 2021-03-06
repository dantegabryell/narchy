package spacegraph.space2d.widget.menu;

import org.eclipse.collections.api.block.procedure.primitive.ObjectBooleanProcedure;
import spacegraph.space2d.Surface;
import spacegraph.space2d.container.Splitting;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.container.unit.AspectAlign;
import spacegraph.space2d.widget.button.CheckBox;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.space2d.widget.button.ToggleButton;
import spacegraph.space2d.widget.menu.view.GridMenuView;
import spacegraph.space2d.widget.meta.MetaHover;
import spacegraph.space2d.widget.text.VectorLabel;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static spacegraph.SpaceGraph.window;

public class TabMenu extends Menu {

    private final Gridding tabs = new Gridding();
    private final Splitting wrap;
    private final Function<String, ToggleButton> buttonBuilder;

    private static final float CONTENT_VISIBLE_SPLIT = 0.9f;

    public TabMenu(Map<String, Supplier<Surface>> options) {
        this(options, new GridMenuView());
    }

    public TabMenu(Map<String, Supplier<Surface>> options, MenuView view) {
        this(options, view, CheckBox::new);
    }

    public TabMenu(Map<String, Supplier<Surface>> options, MenuView view, Function<String, ToggleButton> buttonBuilder) {
        super(options, view);
        this.buttonBuilder = buttonBuilder;


        tabs.clear();
        options.entrySet().stream().map(x ->
            toggle(buttonBuilder, x.getKey(), x.getValue())
        ).forEach(tabs::add);

        wrap = new Splitting(tabs, content.view(),0);

        set(wrap);


    }


    void toggle(Supplier<Surface> creator, boolean onOrOff, Surface[] created, boolean inside) {
        Surface cx;
        if (onOrOff) {
            try {
                cx = creator.get();
            } catch (Throwable t) {
                String msg = t.getMessage();
                if (msg == null)
                    msg = t.toString();
                cx = new VectorLabel(msg);
            }
            cx = wrapper.apply(cx);
        } else {
            cx = null;
        }
        synchronized(TabMenu.this) {

            if (onOrOff) {

                if (inside) {
                    content.active(created[0] = cx);
                    split();
                } else {
                    window(created[0] = cx, 800, 800);
                }



            } else {

                if (created[0] != null) {
                    content.inactive(created[0]);
                    created[0] = null;
                }
                if (content.isEmpty()) {
                    unsplit();
                }

            }

        }
    }


    public void addToggle(String label, Supplier<Surface> creator) {
        toggle(CheckBox::new, label, creator);
    }

    public Surface toggle(Function<String, ToggleButton> buttonBuilder, String label, Supplier<Surface> creator) {
        final Surface[] created = {null};
        ObjectBooleanProcedure<ToggleButton> toggleInside = (cb, onOrOff) -> {
            toggle(creator, onOrOff, created, true);
        };

        Runnable toggleOutside = () -> {
            //Exe.invokeLater(()->{
            toggle(creator, true, created, false);
            //});
        };

        ToggleButton bb = buttonBuilder.apply(label).on(toggleInside);
        PushButton cc = PushButton.awesome("external-link").click(toggleOutside);

        //return Splitting.row(bb, 0.75f, new AspectAlign(cc, AspectAlign.Align.RightTop,1, 0.75f));

        AspectAlign ccc = new AspectAlign(cc, 1, AspectAlign.Align.TopRight, 0.15f, 0.15f);
        return new MetaHover(bb, ()->ccc);
    }

    protected void split() {
        wrap.split(CONTENT_VISIBLE_SPLIT);
    }

    protected void unsplit() {
        wrap.split(0f);
    }


}
