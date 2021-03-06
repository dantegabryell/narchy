package spacegraph.input.key;

import spacegraph.util.math.v3;
import spacegraph.video.JoglSpace;

import static spacegraph.util.math.v3.v;

/** simple XYZ control using keys (ex: numeric keypad) */
public class KeyXYZ extends KeyXY {

    public KeyXYZ(JoglSpace g) {
        super(g);



    }

    @Override void moveX(float speed) {
        v3 x = v(space.camFwd);
        
        x.cross(x, space.camUp);
        x.normalize();
        x.scale(-speed);
        space.camPos.add(x);
    }

    @Override void moveY(float speed) {
        v3 y = v(space.camUp);
        y.normalize();
        y.scale(speed);
        
        space.camPos.add(y);
    }


    @Override void moveZ(float speed) {
        v3 z = v(space.camFwd);
        
        z.scale(speed);
        space.camPos.add(z);
    }






}
