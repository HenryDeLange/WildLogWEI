package wildlog.wei;

import org.apache.logging.log4j.Level;
import wildlog.WildLogApp;
import wildlog.data.enums.WildLogUserTypes;
import wildlog.utils.WildLogApplicationTypes;

/**
 * The main class of the application.
 */
public class WildLogAppWEIVolunteer extends WildLogApp {
    public final static String WILDLOG_WEI_VOLUNTEER_VERSION = "1.0.1";
    
    public static void main(String[] args) {
        WILDLOG_APPLICATION_TYPE = WildLogApplicationTypes.WILDLOG_WEI_VOLUNTEER;
        WILDLOG_USER_NAME = "WildLogVolunteer";
        WILDLOG_USER_TYPE = WildLogUserTypes.VOLUNTEER; // Default to the Volunteer user
        // Stick to the default WildLog application framework
        WildLogApp.main(args);
        WildLogApp.LOGGER.log(Level.INFO, "WildLogWEI-Volunteer Version: {}", WILDLOG_WEI_VOLUNTEER_VERSION);
    }
    
}
