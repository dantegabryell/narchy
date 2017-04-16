package spacegraph.audio;


import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;


public class ListenerMixer extends CopyOnWriteArrayList<Sound> implements StereoSoundProducer {

    private float[] buf = new float[0];

    private final int maxChannels;

    private SoundListener soundListener;

    public ListenerMixer(int maxChannels) {
        this.maxChannels = maxChannels;
    }

    public void setSoundListener(SoundListener soundListener) {
        this.soundListener = soundListener;
    }

    public <S extends SoundProducer> Sound<S> addSoundProducer(S producer, SoundSource soundSource, float volume, float priority) {
        Sound s = new Sound(producer, soundSource, volume, priority);
        add(s);
        return s;
    }

    public void update(float alpha) {
        boolean updating = (soundListener != null);

        this.removeIf(sound -> {
            if (updating)
                sound.update(soundListener, alpha);

            return !sound.isLive();
        });

//        for (Iterator it = sounds.iterator(); it.hasNext();)         {
//
//            Sound sound = (Sound) it.next();
//
//            if (updating)
//                sound.update(soundListener, alpha);
//
//            if (!sound.isLive()) {
//                it.remove();
//            }
//        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void read(float[] leftBuf, float[] rightBuf, int readRate) {

        int s = size();

        if (s == 0)
            return;

        if (buf.length != leftBuf.length)
            buf = new float[leftBuf.length];

        if (s > maxChannels) {
            Collections.sort(this);
        }

        Arrays.fill(leftBuf, 0);
        Arrays.fill(rightBuf, 0);

        for (int i = 0; i < s && i >= 0; ) {
            Sound sound = get(i);

            if (i < maxChannels) {
                float[] buf = this.buf;

                sound.read(buf, readRate);

                float pan = sound.pan;

                float rp = (pan < 0 ? 1 : 1 - pan) * sound.amplitude;
                float lp = (pan > 0 ? 1 : 1 + pan) * sound.amplitude;

                int l = leftBuf.length;

                for (int j = 0; j < l; j++) {
                    float bj = buf[j];

                    float lb = leftBuf[j];
                    lb += bj * lp;
                    leftBuf[j] = lb;

                    float rb = rightBuf[j];
                    rb += bj * rp;
                    rightBuf[j] = rb;
                }
            } else {
                sound.skip(leftBuf.length, readRate);
            }

            i++;
        }

    }

    @Override
    public void skip(int samplesToSkip, int readRate) {
        for (Sound sound : this) {
            sound.skip(samplesToSkip, readRate);
        }
    }
}