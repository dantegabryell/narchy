package nars.control;

import jcog.event.Off;
import jcog.event.Offs;
import jcog.service.Service;
import nars.$;
import nars.NAR;
import nars.term.Term;
import nars.term.Termed;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NARService extends Service<NAR> implements Termed {

    static final Logger logger = LoggerFactory.getLogger(NARService.class);

    public final Term id;

    /** TODO encapsulate */
    private final Offs ons = new Offs();

    protected NAR nar;

    protected NARService() {
        this((NAR)null);
    }

    protected NARService(NAR nar) {
        this(null, nar);
    }

    protected NARService(Term id, NAR nar) {
        this(id);

        if (nar != null) {
            (this.nar = nar).on(this);
        }
    }

    protected NARService(@Nullable Term id) {
        this.id = id != null ? id :
                $.identity(this);
    }

    /** attach a handler.  should only be called in starting() implementations */
    protected final void on(Off... x) {
        for (Off xx : x)
            ons.add(xx);
    }

    @Override
    protected final void start(NAR nar) {

        logger.debug("start {}", id);

        assert (this.nar == null || this.nar == nar);

        this.nar = nar;

        on(nar.eventClear.on(n -> clear()));

        starting(nar);

    }

    @Override
    protected final void stop(NAR nar) {

        this.ons.off();

        stopping(nar);

        logger.debug("stop {}", id);
    }

    /**
     * soft-reset signal to vountarily and quickly vacuum state
     */
    public void clear() {

    }


    protected void starting(NAR nar) {

    }

    protected void stopping(NAR nar) {

    }

    public final void off() {
        NAR n = nar;
        if (n != null) {
            n.services.remove(id);
        }
    }

    @Override
    public final Term term() {
        return id;
    }

    @Nullable public final NAR nar() {
        return nar;
    }
}
