package wildlog.wei;

import org.apache.logging.log4j.Level;
import wildlog.WildLogApp;
import wildlog.utils.WildLogApplicationTypes;

/**
 * The main class of the application.
 */
public class WildLogAppWEI extends WildLogApp {
    public final static String WILDLOG_WEI_VOLUNTEER_VERSION = "0.0.1";
    
    // Stick to the default WildLog application framework
    public static void main(String[] args) {
        WILDLOG_TYPE = WildLogApplicationTypes.WILDLOG_WEI_VOLUNTEER;
        WildLogApp.main(args);
        WildLogApp.LOGGER.log(Level.INFO, "WildLogWEI-Volunteer Version: {}", WILDLOG_WEI_VOLUNTEER_VERSION);
    }
    
}
