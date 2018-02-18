package spacegraph.geo;

import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import jcog.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spacegraph.SubOrtho;
import spacegraph.geo.osm.Osm;
import spacegraph.render.JoglPhysics;
import spacegraph.test.WidgetTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

/**
 * "in real life" geospatial data cache
 */
public class IRL {

    @Deprecated
    public final Osm osm = new Osm();
    private final User user;

//    final ConcurrentRTree<RectDoubleND> tree = new ConcurrentRTree(new RTree(new RectDoubleND.Builder(), 2, 3, RTree.DEFAULT_SPLIT_TYPE));

    public IRL(User u) {
        this.user = u;
    }

    static final Logger logger = LoggerFactory.getLogger(IRL.class);

    public void load(double lonMin, double latMin, double lonMax, double latMax) {
        try {
            URL u = Osm.url("http://api.openstreetmap.org", lonMin, latMin, lonMax, latMax);
            user.get(u.toString(), () -> {
                try {
                    logger.info("reading {}", u);
                    return u.openStream().readAllBytes();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }, (bb) -> {
                try {
                    osm.load(new ByteArrayInputStream(bb));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {


        //https://wiki.openstreetmap.org/wiki/API_v0.6
        //http://api.openstreetmap.org/api/0.6/changeset/#id/comment
        // /api/0.6/map?bbox=min_lon,min_lat,max_lon,max_lat (W,S,E,N)

        IRL i = new IRL(User.the());
        i.load(-80.65, 28.58, -80.60, 28.63);

        JoglPhysics sg = new JoglPhysics(new OsmSpace(i.osm));
        sg.show(800, 800);
        sg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDestroyed(WindowEvent e) {
                super.windowDestroyed(e);
                System.exit(0);
            }
        });
        sg.add(new SubOrtho(WidgetTest.widgetDemo()).posWindow(0, 0, 0.3f, 1f));

    }
}
