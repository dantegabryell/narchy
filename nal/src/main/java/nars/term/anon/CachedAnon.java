package nars.term.anon;

import nars.term.Term;
import nars.term.Termed;
import nars.util.term.transform.CachedTermTransform;
import nars.util.term.transform.DirectTermTransform;
import nars.util.term.transform.TermTransform;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** TODO implement these as CachedTransform-wrapped sub-implementations of the Anon.GET and PUT transforms, each with their own cache */
public class CachedAnon extends Anon {

    private Map<Term,Term> externCache;
    private DirectTermTransform.CachedDirectTermTransform internCache;

    public CachedAnon(int capacity, int internCacheSize) {
        super(capacity);
        this.internCache.resize(internCacheSize);
    }

    @Override
    public boolean rollback(int uniques) {
        if (uniques == 0) {
            clear();
            return true;
        }
        if (super.rollback(uniques)) {
            externCache.clear(); 
            

            
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        
        externCache.clear();
        super.clear();
    }


    protected TermTransform newPut() {
        return internCache = new DirectTermTransform.CachedDirectTermTransform(0) {
            @Override
            public final @Nullable Termed transformAtomic(Term atomic) {
                return put(atomic);
            }
        };
    }

    protected TermTransform newGet() {
        
        if (externCache == null)
            
            externCache = new UnifiedMap<>();

        return new CachedTermTransform(super.newGet(), externCache);
    }

}
