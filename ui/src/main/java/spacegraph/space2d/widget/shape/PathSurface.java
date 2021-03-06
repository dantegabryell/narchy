package spacegraph.space2d.widget.shape;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import jcog.math.v2;
import jcog.tree.rtree.rect.RectFloat;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.util.Path2D;

/**
 * rendered using GL LINE_STRIP / lineWidth
 */
public class PathSurface extends Surface {

    private final Path2D path;

    float lineWidth = 8;
    float r = 0.5f, g = 0.5f, b = 0.5f, a = 1f;
    public boolean invalid = true;

    public PathSurface(Path2D path) {
        this.path = path;
    }

    public PathSurface(int preAllocPoints) {
        this(new Path2D(preAllocPoints, preAllocPoints));
    }

    public void set(int point, float x, float y) {
        int n = point * 2;
        float[] p = path.array();
        //TODO test if the value actually changed
        p[n++] = x;
        p[n] = y;
        invalid = true;
    }

    @Override
    public boolean visible() {
        if (invalid) {
            //update bounds
            RectFloat newBounds = path.bounds();
            //System.out.println(path + " " + newBounds);
            pos(newBounds);
            invalid = false;
        }
        return super.visible();
    }

    @Override
    protected void paint(GL2 gl, SurfaceRender surfaceRender) {
        //System.out.println(path);
        if (path.points() > 1) {
            gl.glLineWidth(lineWidth);
            gl.glColor4f(r, g, b, a);
            gl.glBegin(GL.GL_LINE_STRIP);
            path.vertex2f(gl);
            gl.glEnd();
        }
    }

    public boolean add(v2 pos, int pointLimit) {
        if (path.add(pos, pointLimit)) {
            invalid = true;
            return true;
        }
        return false;
    }
}
