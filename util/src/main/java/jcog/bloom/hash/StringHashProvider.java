package jcog.bloom.hash;

/**
 * Created by jeff on 16/05/16.
 */
public class StringHashProvider extends AbstractHashProvider<String> {

    @Override
    public byte[] asBytes(String element) {
        return element.getBytes();
    }

}
