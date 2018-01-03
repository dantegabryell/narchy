package spacegraph.render;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import jcog.tree.rtree.rect.RectFloat2D;
import spacegraph.Surface;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jogamp.opengl.GL.GL_BGRA;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL2GL3.GL_UNSIGNED_INT_8_8_8_8_REV;

public class Tex {

    public com.jogamp.opengl.util.texture.Texture texture;

    public boolean mipmap;

    //TODO use a PPM uncompressed format for transferring from CPU to GPU


    final AtomicBoolean textureUpdated = new AtomicBoolean(false);
    public GLProfile profile;
    private TextureData nextData;


    private Object src;

    public final void paint(GL2 gl, RectFloat2D bounds) {
        paint(gl, bounds, -1);
    }

    public void paint(GL2 gl, RectFloat2D bounds, float repeatScale) {


        if (profile == null) {
            profile = gl.getGLProfile();
        }

        if (nextData != null && textureUpdated.compareAndSet(true, false)) {

            if (texture == null) {
                texture = TextureIO.newTexture(gl, nextData);
            } else {
                //TODO compute 'd' outside of rendering paint in the update method
                texture.updateImage(gl, nextData);
            }


        }

        if (texture != null) {
            Draw.rectTex(gl, texture, bounds.min.x, bounds.min.y, bounds.w(), bounds.h(), 0, repeatScale);
        }

    }

    public void update(BufferedImage iimage) {
        if (profile == null)
            return;

        if (nextData == null || this.src != iimage) {
            DataBuffer b = iimage.getRaster().getDataBuffer();
            int W = iimage.getWidth();
            int H = iimage.getHeight();
            if (b instanceof DataBufferInt)
                update(((DataBufferInt) b).getData(), W, H);
            else if (b instanceof DataBufferByte) {
                update(((DataBufferByte) b).getData(), W, H);
            }
        }

        textureUpdated.set(true);
    }

    protected void update(byte[] iimage, int width, int height) {

        this.src = iimage;

        ByteBuffer buffer = ByteBuffer.wrap(iimage);
        nextData = new TextureData(profile, GL_RGB,
                width, height,
                0 /* border */,
                GL_RGB,
                GL_UNSIGNED_BYTE,
                mipmap,
                false,
                false,
                buffer, null
        );
    }
    protected void update(int[] iimage, int width, int height) {

        this.src = iimage;

        IntBuffer buffer = IntBuffer.wrap(iimage);
        nextData = new TextureData(profile, GL_RGB,
                width, height,
                0 /* border */,
                GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV,
                mipmap,
                false,
                false,
                buffer, null
        );
    }

    public Surface view() {
        return new TexSurface();
    }

    private class TexSurface extends Surface {

        @Override
        protected void paint(GL2 gl, int dtMS) {
            Tex.this.paint(gl, bounds);
        }
    }
}
