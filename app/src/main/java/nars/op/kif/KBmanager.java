package nars.op.kif;

/**
 * This code is copyright Articulate Software (c) 2003. Some portions copyright
 * Teknowledge (c) 2003 and reused under the terms of the GNU license. This
 * software is released under the GNU Public License
 * <http:
 * use of this code, to credit Articulate Software and Teknowledge in any
 * writings, briefings, publications, presentations, or other representations of
 * any software which incorporates, builds on, or uses this code. Please cite
 * the following article in any publication with references:
 *
 * Pease, A., (2003). The Sigma Ontology Development Environment, in Working
 * Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems, August
 * 9, Acapulco, Mexico.
 */

import java.io.File;
import java.io.Serializable;
import java.util.*;

/** This is a class that manages a group of knowledge bases.  It should only
 *  have one instance, contained in its own static member variable.
 */
public class KBmanager implements Serializable {

    /** A numeric (bitwise) constant used to signal whether type
     * prefixes (sortals) should be added during formula
     * preprocessing.
     */
    public static final int USE_TYPE_PREFIX  = 1;

    /** A numeric (bitwise) constant used to signal whether holds
     * prefixes should be added during formula preprocessing.
     */
    public static final int USE_HOLDS_PREFIX = 2;

    /** A numeric (bitwise) constant used to signal whether the closure
     * of instance and subclass relastions should be "cached out" for
     * use by the inference engine.
     */
    public static final int USE_CACHE        = 4;

    /*** A numeric (bitwise) constant used to signal whether formulas
     * should be translated to TPTP format during the processing of KB
     * constituent files.
     */
    public static final int USE_TPTP         = 8;


    private static KBmanager manager = new KBmanager();
    protected static final String CONFIG_FILE = "config.xml";

    private final HashMap<String,String> preferences = new HashMap<String,String>();
    public HashMap<String,KB> kbs = new HashMap<String,KB>();
    public static boolean initialized = false;
    public static boolean initializing = false;
    public static boolean debug = false;
    private final int oldInferenceBitValue = -1;
    private String error = "";

    public static final List<String> configKeys =
            Arrays.asList("sumokbname", "testOutputDir", "TPTPDisplay", "semRewrite",
                    "inferenceEngine", "inferenceTestDir", "baseDir", "hostname",
                    "logLevel", "systemsDir", "dbUser", "loadFresh", "userBrowserLimit",
                    "adminBrowserLimit", "https", "graphWidth", "overwrite", "typePrefix",
                    "graphDir", "nlpTools","TPTP","cache","editorCommand","graphVizDir",
                    "kbDir","loadCELT","celtdir","lineNumberCommand","prolog","port",
                    "tptpHomeDir","showcached","leoExecutable","holdsPrefix","logDir",
                    "englishPCFG");

    public static final List<String> fileKeys =
            Arrays.asList("testOutputDir", "inferenceEngine", "inferenceTestDir", "baseDir",
                    "systemsDir","graphVizDir", "kbDir", "celtdir", "tptpHomeDir", "logDir",
                    "englishPCFG");

    /** ***************************************************************
     */
    public KBmanager() {
    }

    /** ***************************************************************
     * Set an error string for file loading.
     */
    public void setError(String er) {
        error = er;
    }

    /** ***************************************************************
     * Get the error string for file loading.
     */
    public String getError() {
        return error;
    }

    /** ***************************************************************
     *  Check whether sources are newer than serialized version.
     */
    public static boolean serializedExists() {

        String kbDir = System.getenv("SIGMA_HOME") + File.separator + "KBs";
        File serfile = new File(kbDir + File.separator + "kbmanager.ser");
        System.out.println("KBmanager.serializedExists(): " + serfile.exists());
        return serfile.exists();
    }














































































































    /** ***************************************************************
     * Set default attribute values if not in the configuration file.
     */
    public void setDefaultAttributes() {

        try {
            String sep = File.separator;
            String base = System.getenv("SIGMA_HOME");
            String tptpHome = System.getenv("TPTP_HOME");
            String systemsHome = System.getenv("SYSTEMS_HOME");
            if (StringUtil.emptyString(base))
                base = System.getProperty("user.dir");
            if (StringUtil.emptyString(tptpHome))
                tptpHome = System.getProperty("user.dir");
            if (StringUtil.emptyString(systemsHome))
                systemsHome = System.getProperty("user.dir");
            String tomcatRoot = System.getenv("CATALINA_HOME");
            if (StringUtil.emptyString(tomcatRoot))
                tomcatRoot = System.getProperty("user.dir");
            File tomcatRootDir = new File(tomcatRoot);
            File baseDir = new File(base);
            File tptpHomeDir = new File(tptpHome);
            File systemsDir = new File(systemsHome);
            File kbDir = new File(baseDir, "KBs");
            File inferenceTestDir = new File(kbDir, "tests");
            File logDir = new File(baseDir, "logs");
            logDir.mkdirs();

            
            
            
            File testOutputDir = new File(tomcatRootDir,
                    ("webapps" + sep + "sigma" + sep + "tests"));
            preferences.put("baseDir",baseDir.getCanonicalPath());
            preferences.put("tptpHomeDir",tptpHomeDir.getCanonicalPath());
            preferences.put("systemsDir",systemsDir.getCanonicalPath());
            preferences.put("kbDir",kbDir.getCanonicalPath());
            preferences.put("inferenceTestDir",inferenceTestDir.getCanonicalPath());
            preferences.put("testOutputDir",testOutputDir.getCanonicalPath());

            File graphVizDir = new File("/usr/bin");
            preferences.put("graphVizDir", graphVizDir.getCanonicalPath());

            File graphDir = new File(tomcatRootDir, "webapps" + sep + "sigma" + sep + "graph");
            if (!graphDir.exists())
                graphDir.mkdir();
            preferences.put("graphDir", graphDir.getCanonicalPath());

            
            
            
            String _OS = System.getProperty("os.name");
            String ieExec = "e_ltb_runner";
            if (StringUtil.isNonEmptyString(_OS) && _OS.matches("(?i).*win.*"))
                ieExec = "e_ltb_runner.exe";
            File ieDirFile = new File(baseDir, "inference");
            File ieExecFile = (ieDirFile.isDirectory()
                    ? new File(ieDirFile, ieExec)
                    : new File(ieExec));
            String leoExec = "leo";
            File leoExecFile = (ieDirFile.isDirectory()
                    ? new File(ieDirFile, leoExec)
                    : new File(leoExec));
            preferences.put("inferenceEngine",ieExecFile.getCanonicalPath());
            preferences.put("leoExecutable",leoExecFile.getCanonicalPath());
            preferences.put("loadCELT","no");
            preferences.put("showcached","yes");
            preferences.put("typePrefix","no");

            
            preferences.put("holdsPrefix","no");
            preferences.put("cache","yes");
            preferences.put("TPTP","yes");
            preferences.put("TPTPDisplay","no");
            preferences.put("userBrowserLimit","25");
            preferences.put("adminBrowserLimit","200");
            preferences.put("port","8080");
            preferences.put("hostname","localhost");
            preferences.put("https","false");
            preferences.put("sumokbname","SUMO");

            
            preferences.put("logDir", logDir.getCanonicalPath());
            preferences.put("logLevel", "warning");

        }
        catch (Exception ex) {
            System.out.println("Error in KBmanager.setDefaultAttributes(): " + Arrays.toString(ex.getStackTrace()));
            ex.printStackTrace();
        }
        return;
    }


















































































































































































































































































































    /** ***************************************************************
     * Reads in the KBs and other parameters defined in the XML
     * configuration file, or uses the default parameters.
     */
    public void initializeOnce() {

        System.out.println("Info in KBmanager.initializeOnce()");
        
        String base = System.getenv("SIGMA_HOME");
        initializeOnce(base + File.separator + "KBs");
        return;
    }

    /** ***************************************************************
     * Reads in the KBs and other parameters defined in the XML
     * configuration file, or uses the default parameters.  If
     * configFileDir is not null and a configuration file can be read
     * from the directory, reinitialization is forced.
     */
    public void initializeOnce(String configFileDir) {

        boolean loaded = false;
        if (initializing || initialized) {
            System.out.println("Info in KBmanager.initializeOnce(): initialized is " + initialized);
            System.out.println("Info in KBmanager.initializeOnce(): initializing is " + initializing);
            System.out.println("Info in KBmanager.initializeOnce(): returning ");
            return;
        }
        initializing = true;
        KBmanager.getMgr().setPref("kbDir",configFileDir);
        if (debug) System.out.println("KBmanager.initializeOnce(): number of preferences: " +
                preferences.keySet().size());

            System.out.println("Info in KBmanager.initializeOnce(): initializing with " + configFileDir);
















                System.out.println("Info in KBmanager.initializeOnce(): reading from sources");
                if (debug) System.out.println("KBmanager.initializeOnce(): number of preferences: " +
                        preferences.keySet().size());
                manager = this;
                KBmanager.getMgr().setPref("kbDir",configFileDir); 





                    setDefaultAttributes();
                System.out.println("Info in KBmanager.initializeOnce(): completed initialization");

                initializing = false;
                initialized = true;







        System.out.println("Info in KBmanager.initializeOnce(): initialized is " + initialized);
        if (debug) System.out.println("KBmanager.initializeOnce(): number of preferences: " +
                preferences.keySet().size());
        return;
    }





























    /** ***************************************************************
     * Double the backslash in a filename so that it can be saved to a text
     * file and read back properly.
     */
    public static String escapeFilename(String fname) {

        StringBuilder newstring = new StringBuilder();
        for (int i = 0; i < fname.length(); i++) {
            if (fname.charAt(i) == 92 && fname.charAt(i+1) != 92)
                newstring = newstring.append("\\\\");
            if (fname.charAt(i) == 92 && fname.charAt(i+1) == 92) {
                newstring = newstring.append("\\\\");
                i++;
            }
            if (fname.charAt(i) != 92)
                newstring = newstring.append(fname.charAt(i));
        }
        return newstring.toString();
    }

    /** ***************************************************************
     * Create a new empty KB with a name.
     * @param name - the name of the KB
     */
    public KB addKB(String name) {
        return addKB(name, true);
    }

    public KB addKB(String name, boolean isVisible) {

        KB kb = new KB(name, preferences.get("kbDir"), isVisible);
        kbs.put(name.intern(),kb);
        return kb;
    }

























































































    /** ***************************************************************
     * Get the KB that has the given name.
     */
    public KB getKB(String name) {

        if (!kbs.containsKey(name))
            System.out.println("KBmanager.getKB(): KB " + name + " not found.");
        return kbs.get(name.intern());
    }

    /** ***************************************************************
     * Returns true if a KB with the given name exists.
     */
    public boolean existsKB(String name) {

        return kbs.containsKey(name);
    }

    /** ***************************************************************
     * Remove the KB that has the given name.
     */
    public void remove(String name) {

        kbs.remove(name);
    }

    /** ***************************************************************
     * Get the one instance of KBmanager from its class variable.
     */
    public static KBmanager getMgr() {

        
        
        return manager;
    }

    /** ***************************************************************
     * Get the Set of KB names in this manager.
     */
    public HashSet<String> getKBnames() {

        HashSet<String> names = new HashSet<String>();
        Iterator<String> it = kbs.keySet().iterator();
        while (it.hasNext()) {
            String kbName = it.next();
            KB kb = getKB(kbName);
            if (kb.isVisible())
                names.add(kbName);
        }
        return names;
    }

    /** ***************************************************************
     * Get the the complete list of languages available in all KBs
     */
    public ArrayList<String> allAvailableLanguages() {

        ArrayList<String> result = new ArrayList<String>();
        Iterator<String> it = kbs.keySet().iterator();
        while (it.hasNext()) {
            String kbName = it.next();
            KB kb = getKB(kbName);
            result.addAll(kb.availableLanguages());
        }
        return result;
    }

    /** ***************************************************************
     * Print all peferences to stdout
     */
    public void printPrefs() {

        System.out.println("KBmanager.printPrefs()");
        if (preferences == null || preferences.size() == 0)
            System.out.println("KBmanager.printPrefs(): preference list is empty");
        for (String key : preferences.keySet()) {
            String value = preferences.get(key);
            System.out.println(key + " : " + value);
        }
    }

    /** ***************************************************************
     * Get the preference corresponding to the given key
     */
    public String getPref(String key) {

        if (!configKeys.contains(key)) {
            System.out.println("Error in KBmanager.getPref(): bad key: " + key);
            return "";
        }
        String ans = preferences.get(key);
        if (ans == null)
            ans = "";
        return ans;
    }

    /** ***************************************************************
     * Set the preference to the given value.
     */
    public void setPref(String key, String value) {

        if (!configKeys.contains(key)) {
            System.out.println("Error in KBmanager.setPref(): bad key: " + key);
            return;
        }
        preferences.put(key,value);
    }

























    /** ***************************************************************
     * A test method.
     */
    public static void printHelp() {

        System.out.println("Sigma Knowledge Engineering Environment");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -p - demo Python interface");
        System.out.println("  with no arguments show this help screen an execute a test");
    }





























}
