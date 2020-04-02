package wildlog.wei;

import wildlog.wei.utils.UtilsWEI;
import org.apache.logging.log4j.Level;
import wildlog.WildLogApp;
import wildlog.data.enums.system.WildLogUserTypes;
import wildlog.utils.WildLogApplicationTypes;

public class WildLogAppWEIRemote extends WildLogApp {
    private final static String WILDLOG_WEI_REMOTE_VERSION = "1.1.0";
    
    public static void main(String[] args) {
        WILDLOG_APPLICATION_TYPE = WildLogApplicationTypes.WILDLOG_WEI_REMOTE;
        WILDLOG_USER_NAME = "WildLogRemote";
        WILDLOG_USER_TYPE = WildLogUserTypes.REMOTE;
        APPLICATION_CLASS = WildLogAppWEIRemote.class;
        // Stick to the default WildLog application framework
        WildLogApp.main(args);
        WildLogApp.LOGGER.log(Level.INFO, "WildLogWEI-Remote Version: {}", WILDLOG_WEI_REMOTE_VERSION);
    }

    @Override
    protected void startup() {
        if (useH2AutoServer) {
            // Check that the primary computer is the first instance to start
            UtilsWEI.checkPrimaryComputer();
            // Add a listerner to warn when the primary instance is closed while there are still active connections
            UtilsWEI.addExitListener();
        }
        // Continue to do the normal WildLog startup
        super.startup();
    }
    
}
