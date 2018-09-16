package spacegraph.space2d.widget.adapter;

import com.jcraft.jcterm.JCTermSwingFrame;
import spacegraph.SpaceGraph;
import spacegraph.space2d.container.Gridding;
import spacegraph.space2d.widget.windo.GraphEdit;

import javax.swing.*;

/**
 * Created by me on 11/13/16.
 */
public class SSHSurface extends AWTSurface {

    private SSHSurface() {
        super(new JCTermSwingFrame(), 800, 600);
    }

    public static void main(String[] args) {

        GraphEdit w = SpaceGraph.wall(800, 600);
        w.add(new Gridding(new SSHSurface()), 800, 600);
        w.add(new Gridding(new AWTSurface(new JColorChooser(), 200, 200)),
                3, 3);

    }


}










































































































































































































































































































































































































































































