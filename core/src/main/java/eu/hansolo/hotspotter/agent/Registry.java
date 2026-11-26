package eu.hansolo.hotspotter.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Assigns a stable counter id to every instrumented method. The same method always gets the same id, so re-transforming a class (e.g. during attach) never creates duplicates.
 */
public final class Registry {
    private final Config               config;
    private final int                  capacity;
    private final Map<String, Integer> ids       = new HashMap<>();
    private final List<String[]>       entries   = new ArrayList<>();
    private       boolean              fullWarned;


    Registry(final Config config, final int capacity) {
        this.config   = config;
        this.capacity = capacity;
    }


    /**
     * @param internalClassName class name with slashes, e.g. {@code com/acme/Foo}
     * @return the counter id, or -1 if the method must not be instrumented
     */
    public synchronized int register(final String internalClassName, final String method, final String descriptor) {
        if (!config.acceptMethod(method)) { return -1; }
        String  cls      = internalClassName.replace('/', '.');
        String  key      = cls + "," + method + "," + descriptor;
        Integer existing = ids.get(key);
        if (existing != null) { return existing; }
        if (entries.size() >= capacity) {
            if (!fullWarned) {
                fullWarned = true;
                Agent.log("counter table full (" + capacity + " methods); further methods are not instrumented. Raise it with max=<n>.");
            }
            return -1;
        }
        int id = entries.size();
        entries.add(new String[] { cls, method, descriptor });
        ids.put(key, id);
        return id;
    }

    synchronized String[][] entries() { return entries.toArray(new String[0][]); }
}
