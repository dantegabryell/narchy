package spacegraph.space2d.widget.meter;

import com.jogamp.opengl.GL2;
import jcog.signal.buffer.CircularFloatBuffer;
import spacegraph.audio.WaveCapture;
import spacegraph.input.finger.Finger;
import spacegraph.input.finger.FingerDragging;
import spacegraph.input.finger.FingerMove;
import spacegraph.input.finger.Fingering;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.space2d.widget.meta.MetaFrame;
import spacegraph.space2d.widget.windo.Widget;
import spacegraph.video.Draw;

import static java.lang.Float.NaN;

/** waveform viewing and editing */
public class WaveView extends Widget implements MetaFrame.Menu, Finger.WheelAbsorb {

    final static int SELECT_BUTTON = 0;
    final static int PAN_BUTTON = 2;

    private final long startMS;
    private final float[] wave;
    protected final BitmapWave vis;


    public WaveView(CircularFloatBuffer wave) {
        this.startMS = System.currentTimeMillis();
        //int totalSamples = wave.capacity(); //int) Math.ceil(seconds * capture.source.samplesPerSecond());

        this.wave = null; ///capture.buffer.peekLast(new float[totalSamples]);


        vis = new BitmapWave(1024, 128, wave);
        set(vis);
    }
    @Deprecated public WaveView(WaveCapture capture, float seconds) {
        super();
        this.startMS = System.currentTimeMillis();
        int totalSamples = (int) Math.ceil(seconds * capture.source.samplesPerSecond());

        this.wave = capture.buffer.peekLast(new float[totalSamples]);


        vis = new BitmapWave(1024, 128, new CircularFloatBuffer(wave));
        set(vis);
    }

    final Fingering pan = new FingerMove(PAN_BUTTON) {
        @Override
        protected void move(float tx, float ty) {
            vis.pan(tx/100.0f);
        }
    };

    volatile private float selectStart = NaN, selectEnd = NaN;


    final Fingering select = new FingerDragging(SELECT_BUTTON) {

        float sample(float x) {
            return vis.first + (vis.last - vis.first) * (x / w());
        }

        @Override
        protected boolean startDrag(Finger f) {
            selectStart = sample(f.pos.x);
            return true;
        }

        @Override
        protected boolean drag(Finger f) {
            selectEnd = sample(f.pos.x);
            return true;
        }
    };

    @Override
    public Surface tryTouch(Finger finger) {

//        if (selectStart!=null && !finger.pressing(SELECT_BUTTON)) {
//            selectStart
//        }

        float wheel;

        if ((wheel = finger.rotationY())!=0) {
//            scale = Util.clamp(scale * ( (1f - wheel*0.1f) ), 0.1f, 10f);


            vis.scale(( (1f + wheel*0.1f)));
            //vis.pan(+1);
            return this;
        }

        if (finger.tryFingering(pan)) {
            return this;
        }
        if (finger.tryFingering(select)) {
            return this;
        }

        return this;
    }

    @Override
    protected void paintAbove(GL2 gl, SurfaceRender r) {
        float sStart = selectStart;
        if (sStart==sStart) {
            float sEnd = selectEnd;
            if (sEnd==sEnd) {
                float ss = x(selectStart);
                gl.glColor4f(1f, 0.8f, 0, 0.5f);
                Draw.rect(gl, x() + ss, y(), x(selectEnd)- ss, h());
                //System.out.println("select: " + sStart + ".." + sEnd);
            }
        }

        super.paintAbove(gl, r);
    }

    float x(float sample) {
        long f = vis.first;
        return (sample - f)/(vis.last - f) * w();
    }

    @Override
    public Surface menu() {
        return new Gridding(
            PushButton.awesome("play"),
            PushButton.awesome("microphone"),
            PushButton.awesome("save"), //remember
            PushButton.awesome("question-circle") //recognize

                //TODO trim, etc
        );
    }

    public void update() {
        vis.update();
    }
}