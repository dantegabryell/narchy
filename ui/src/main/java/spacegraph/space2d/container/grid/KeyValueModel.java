package spacegraph.space2d.container.grid;



import org.eclipse.collections.api.multimap.Multimap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/** simple 2-column/2-row key->value table */
public class KeyValueModel implements GridModel {

    private final Function map;

    /** cached keys as an array for fast access */
    private final transient Object[] keys;

    public KeyValueModel(Map map) {
        this.map = map::get;
        this.keys = map.keySet().toArray();
    }
    public KeyValueModel(com.google.common.collect.Multimap map) {
        this.map = map::get;
        this.keys = map.keySet().toArray();
    }
    public KeyValueModel(Multimap map) {
        this.map = map::get;
        this.keys = map.keySet().toArray();
    }

    @Override
    public int cellsX() {
        return 2;
    }

    @Override
    public int cellsY() {
        return keys.length;
    }

    @Nullable
    @Override
    public Object get(int x, int y) {
        switch (x) {
            case 0: 
                return keys[y];
            case 1: 
                return map.apply(keys[y]);
        }
        return null;
    }
}
