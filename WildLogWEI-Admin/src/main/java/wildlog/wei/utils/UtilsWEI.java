package wildlog.wei.utils;

import java.net.InetAddress;
import java.util.EventObject;
import javax.swing.JOptionPane;
import org.apache.logging.log4j.Level;
import org.jdesktop.application.Application;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;
import wildlog.WildLogApp;
import wildlog.data.dataobjects.AdhocData;
import wildlog.ui.helpers.WLOptionPane;


public class UtilsWEI {
    private static final String IDENTIFIER = "IDENTIFIER";
    private static final String NOTES = "<hr/>"
            + "<u>NOTE:</u>"
            + "<br/>The primary (first) WildLog instance should be opened on the same computer as where the Workspace is stored."
            + "<br/>Secondary (subsequent) WildLog instances can be opened on different computers "
            + "<br/>and will then connect to the primary WildLog instance on the computer where the Workspace is stored."
            + "<br/>The primary WildLog instance should also be the last instance to be closed.";
    
    public static void checkPrimaryComputer() {
        if (WildLogApp.getApplication().getDBI().activeSessionsCount() == 1) {
            String identifier = getComputerIdentifier();
            WildLogApp.LOGGER.log(Level.INFO, "WildLogWEI Starting Primary Instance: {}", identifier);
            AdhocData expectedMaster = WildLogApp.getApplication().getDBI().findAdhocData(
                    AdhocData.ADHOC_FIELD_IDS.WORKSPACE_PRIMARY_COMPUTER.toString(), IDENTIFIER, AdhocData.class);
            if (expectedMaster != null) {
                if (!identifier.equals(expectedMaster.getDataValue())) {
                    WildLogApp.LOGGER.log(Level.INFO, "New primary computer detected for this workspace");
                    int choice = WLOptionPane.showOptionDialog(WildLogApp.getApplication().getMainFrame(),
                            "<html><b>The current computer appears to be different to the one that was previously the first to open this Workspace.</b>"
                                    + "<hr/>"
                                    + "It is strongly recommended to always open a Workspace first from the computer where the Workspace is located. "
                                    + "<br/>Subsequent WildLog clients on other computers can then be opened afterwards. "
                                    + NOTES
                                    + "</html>",
                            "Different Primary Computer!", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, 
                            new String[] { "Make this the primary computer", "Don't make this the primary computer", "Exit" }, null);
                    if (choice == 0) {
                        WildLogApp.LOGGER.log(Level.WARN, "Changing the primary computer for this workspace");
                        expectedMaster.setDataValue(identifier);
                        WildLogApp.getApplication().getDBI().updateAdhocData(expectedMaster);
                    }
                    else
                    if (choice == 1) {
                        WildLogApp.LOGGER.log(Level.WARN, "Not changing the primary computer for this workspace");
                    }
                    else {
                        WildLogApp.getApplication().exit();
                    }
                }
            }
            else {
                WildLogApp.getApplication().getDBI().createAdhocData(new AdhocData(AdhocData.ADHOC_FIELD_IDS.WORKSPACE_PRIMARY_COMPUTER.toString(), IDENTIFIER, identifier));
            }
        }
    }
    
    public static void addExitListener() {
        WildLogApp.getApplication().addExitListener(new Application.ExitListener() {
            
            @Override
            public boolean canExit(EventObject inEvent) {
                try {
                    if (WildLogApp.getApplication().getDBI().activeSessionsCount() > 1) {
                        String identifier = getComputerIdentifier();
                        AdhocData expectedMaster = WildLogApp.getApplication().getDBI().findAdhocData(
                                AdhocData.ADHOC_FIELD_IDS.WORKSPACE_PRIMARY_COMPUTER.toString(), IDENTIFIER, AdhocData.class);
                        if (expectedMaster != null && identifier.equals(expectedMaster.getDataValue())) {
                            int choice = WLOptionPane.showOptionDialog(WildLogApp.getApplication().getMainFrame(),
                                    "<html><b>The current computer appears to be the primary computer for this Workspace.</b>"
                                            + "<hr/>"
                                            + "It is strongly recommended to always close the primary WildLog instance last, "
                                            + "<br/>only after all other WildLog instances have been closed."
                                            + NOTES
                                            + "</html>",
                                    "Early Primary Computer Shutdown!", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, 
                                    new String[] { "Don't Exit", "Continue to Exit" }, null);
                            if (choice == 1) {
                                WildLogApp.LOGGER.log(Level.WARN, "Closing the primary instance with {} connections", 
                                        WildLogApp.getApplication().getDBI().activeSessionsCount());
                            }
                            else {
                                WildLogApp.LOGGER.log(Level.INFO, "Keeping primary instance open ({} connections)", 
                                        WildLogApp.getApplication().getDBI().activeSessionsCount());
                                return false;
                            }
                        }
                    }
                }
                catch (Exception ex) {
                    WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                }
                return true;
            }

            @Override
            public void willExit(EventObject inEvent) {
                // Do nothing
            }
            
        });
    }
    
    private static String getComputerIdentifier() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
            HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
            CentralProcessor centralProcessor = hardwareAbstractionLayer.getProcessor();
            ComputerSystem computerSystem = hardwareAbstractionLayer.getComputerSystem();
            String vendor = operatingSystem.getManufacturer();
            String processorSerialNumber = computerSystem.getSerialNumber();
            String processorIdentifier = centralProcessor.getIdentifier();
            int processors = centralProcessor.getLogicalProcessorCount();
            String delimiter = " | ";
            InetAddress inetAddress = InetAddress.getLocalHost();
            String hostname = "unknown_name";
            if (inetAddress != null) {
                hostname = inetAddress.getHostName();
            }
            return hostname + delimiter + vendor + delimiter + processorSerialNumber + delimiter + processorIdentifier + delimiter + processors;
        }
        catch (Exception ex) {
            WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
        }
        return "unknown";
    }
    
}
