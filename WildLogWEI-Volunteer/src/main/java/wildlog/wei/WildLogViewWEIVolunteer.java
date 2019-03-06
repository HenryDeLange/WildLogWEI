package wildlog.wei;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.logging.log4j.Level;
import org.jdesktop.application.TaskMonitor;
import wildlog.WildLogApp;
import wildlog.data.dataobjects.Element;
import wildlog.data.dataobjects.Location;
import wildlog.data.dataobjects.Sighting;
import wildlog.data.dataobjects.Visit;
import wildlog.movies.gifmovie.AnimatedGIFWriter;
import wildlog.movies.utils.UtilsMovies;
import wildlog.ui.dialogs.GPSGridConversionDialog;
import wildlog.ui.dialogs.SunMoonDialog;
import wildlog.ui.dialogs.SystemMonitorDialog;
import wildlog.ui.dialogs.WildLogAboutBox;
import wildlog.ui.dialogs.utils.UtilsDialog;
import wildlog.ui.helpers.ProgressbarTask;
import wildlog.ui.helpers.WLFileChooser;
import wildlog.ui.helpers.WLOptionPane;
import wildlog.ui.panels.PanelTabBrowse;
import wildlog.ui.panels.PanelTabElements;
import wildlog.ui.panels.PanelTabLocations;
import wildlog.ui.panels.PanelTabSightings;
import wildlog.ui.panels.interfaces.PanelCanSetupHeader;
import wildlog.ui.utils.UtilsUI;
import wildlog.ui.utils.WildLogMainView;
import wildlog.utils.NamedThreadFactory;
import wildlog.utils.UtilsConcurency;
import wildlog.utils.UtilsFileProcessing;
import wildlog.utils.UtilsImageProcessing;
import wildlog.utils.WildLogFileExtentions;
import wildlog.utils.WildLogPaths;
import wildlog.utils.WildLogSystemImages;

/**
 * The application's main frame.
 */
public final class WildLogViewWEIVolunteer extends WildLogMainView {
    private final int STATIC_TAB_COUNT = 5;
    private final WildLogApp app = WildLogApp.getApplication();
    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;
    private PanelTabBrowse panelTabBrowse;

    public WildLogViewWEIVolunteer() {
        // Call the generated code to build the GUI
        initComponents();
        // status bar initialization - message timeout, idle icon and busy animation, etc
        int messageTimeout = 10000;
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = 30;
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = new ImageIcon(getClass().getResource("/wildlog/resources/busyicons/busy-icon" + i + ".png"));
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = new ImageIcon(getClass().getResource("/wildlog/resources/busyicons/idle-icon.png"));
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);
        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(app.getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                switch (propertyName) {
                    case "started":
                        if (!busyIconTimer.isRunning()) {
                            statusAnimationLabel.setIcon(busyIcons[0]);
                            busyIconIndex = 0;
                            busyIconTimer.start();
                        }
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(true);
                        messageTimer.stop();
                        break;
                    case "done":
                        busyIconTimer.stop();
                        statusAnimationLabel.setIcon(idleIcon);
                        progressBar.setVisible(false);
                        progressBar.setValue(0);
                        messageTimer.restart();
                        break;
                    case "message":
                        String text = (String)(evt.getNewValue());
                        statusMessageLabel.setText((text == null) ? "" : text);
                        messageTimer.stop();
                        break;
                    case "progress":
                        int value = (Integer)(evt.getNewValue());
                        progressBar.setVisible(true);
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(value);
                        break;
                }
            }
        });
        // Setup the tab headers
        setupTabHeaderHome();
        setupTabHeaderLocation(1);
        setupTabHeaderElement(2);
        setupTabHeaderSightings(3);
        setupTabHeaderBrowse(4);
        // Set the minimum size of the frame
        setMinimumSize(new Dimension(1024, 705));
    }

    private void setupTabHeaderHome() {
        JPanel tabHeader = new JPanel();
        ImageIcon icon = new ImageIcon(app.getClass().getResource("resources/icons/WildLog Icon.gif"));
        tabHeader.add(new JLabel(icon));
        tabHeader.add(new JLabel(""));
        tabHeader.setBackground(new Color(0, 0, 0, 0));
        tabbedPanel.setTitleAt(0, "Home");
        tabbedPanel.setIconAt(0, icon);
        tabbedPanel.setTabComponentAt(0, tabHeader);
        UtilsUI.attachMouseScrollToTabs(tabbedPanel, tabHeader, 0);
    }

    private void setupTabHeaderBrowse(int inIndex) {
        JPanel tabHeader = new JPanel();
        ImageIcon icon = new ImageIcon(app.getClass().getResource("resources/icons/Browse.png"));
        tabHeader.add(new JLabel(icon));
        tabHeader.add(new JLabel("Browse"));
        tabHeader.setBackground(new Color(0, 0, 0, 0));
        tabbedPanel.setTitleAt(inIndex, "Browse");
        tabbedPanel.setIconAt(inIndex, icon);
        tabbedPanel.setTabComponentAt(inIndex, tabHeader);
        UtilsUI.attachMouseScrollToTabs(tabbedPanel, tabHeader, inIndex);
        // Setup content
        panelTabBrowse = new PanelTabBrowse(app, tabbedPanel);
        tabbedPanel.setComponentAt(inIndex, panelTabBrowse);
    }

    private void setupTabHeaderLocation(int inIndex) {
        JPanel tabHeader = new JPanel();
        ImageIcon icon = new ImageIcon(app.getClass().getResource("resources/icons/LocationList.gif"));
        tabHeader.add(new JLabel(icon));
        tabHeader.add(new JLabel("Places"));
        tabHeader.setBackground(new Color(0, 0, 0, 0));
        tabbedPanel.setTitleAt(inIndex, "Places");
        tabbedPanel.setIconAt(inIndex, icon);
        tabbedPanel.setTabComponentAt(inIndex, tabHeader);
        UtilsUI.attachMouseScrollToTabs(tabbedPanel, tabHeader, inIndex);
        // Setup content
        tabbedPanel.setComponentAt(inIndex, new PanelTabLocations(app, tabbedPanel));
    }

    private void setupTabHeaderElement(int inIndex) {
        JPanel tabHeader = new JPanel();
        ImageIcon icon = new ImageIcon(app.getClass().getResource("resources/icons/ElementList.png"));
        tabHeader.add(new JLabel(icon));
        tabHeader.add(new JLabel("Creatures"));
        tabHeader.setBackground(new Color(0, 0, 0, 0));
        tabbedPanel.setTitleAt(inIndex, "Creatures");
        tabbedPanel.setIconAt(inIndex, icon);
        tabbedPanel.setTabComponentAt(inIndex, tabHeader);
        UtilsUI.attachMouseScrollToTabs(tabbedPanel, tabHeader, inIndex);
        // Setup content
        tabbedPanel.setComponentAt(inIndex, new PanelTabElements(app, tabbedPanel));
    }
    
    private void setupTabHeaderSightings(int inIndex) {
        JPanel tabHeader = new JPanel();
        ImageIcon icon = new ImageIcon(app.getClass().getResource("resources/icons/SightingList.png"));
        tabHeader.add(new JLabel(icon));
        tabHeader.add(new JLabel("Observations"));
        tabHeader.setBackground(new Color(0, 0, 0, 0));
        tabbedPanel.setTitleAt(inIndex, "Observations");
        tabbedPanel.setIconAt(inIndex, icon);
        tabbedPanel.setTabComponentAt(inIndex, tabHeader);
        UtilsUI.attachMouseScrollToTabs(tabbedPanel, tabHeader, inIndex);
        // Setup content
        tabbedPanel.setComponentAt(inIndex, new PanelTabSightings(app, tabbedPanel));
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        tabbedPanel = new javax.swing.JTabbedPane();
        tabHome = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        lblBlog = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        lblLocations = new javax.swing.JLabel();
        lblVisits = new javax.swing.JLabel();
        lblSightings = new javax.swing.JLabel();
        lblCreatures = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        lblMyWild = new javax.swing.JLabel();
        lblWorkspaceName = new javax.swing.JLabel();
        lblWorkspacePath = new javax.swing.JLabel();
        jSeparator6 = new javax.swing.JSeparator();
        lblEmail = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jLabel23 = new javax.swing.JLabel();
        lblSettingsPath = new javax.swing.JLabel();
        lblEdition = new javax.swing.JLabel();
        lblWorkspaceUser = new javax.swing.JLabel();
        jSeparator26 = new javax.swing.JSeparator();
        tabLocation = new javax.swing.JPanel();
        tabElement = new javax.swing.JPanel();
        tabSightings = new javax.swing.JPanel();
        tabBrowse = new javax.swing.JPanel();
        statusPanel = new javax.swing.JPanel();
        statusMessageLabel = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        progressBar = new javax.swing.JProgressBar();
        statusAnimationLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        workspaceMenu = new javax.swing.JMenu();
        mnuChangeWorkspaceMenuItem = new javax.swing.JMenuItem();
        mnuCreateWorkspaceMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        javax.swing.JMenuItem mnuExitApp = new javax.swing.JMenuItem();
        extraMenu = new javax.swing.JMenu();
        mnuExifMenuItem = new javax.swing.JMenuItem();
        mnuConvertCoordinates = new javax.swing.JMenuItem();
        mnuCreateSlideshow = new javax.swing.JMenuItem();
        mnuCreateGIF = new javax.swing.JMenuItem();
        mnuSunAndMoon = new javax.swing.JMenuItem();
        mnuSystemMonitor = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        mnuUserGuide = new javax.swing.JMenuItem();
        jSeparator17 = new javax.swing.JPopupMenu.Separator();
        mnuCheckUpdates = new javax.swing.JMenuItem();
        javax.swing.JMenuItem mnuAboutWildLog = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle(app.getWildLogOptions().getWorkspaceName() + " -- WildLog v" + WildLogApp.WILDLOG_VERSION);
        setIconImage(new ImageIcon(app.getClass().getResource("resources/icons/WildLog Icon.gif")).getImage());

        mainPanel.setMaximumSize(new java.awt.Dimension(2500, 1300));
        mainPanel.setMinimumSize(new java.awt.Dimension(1000, 630));
        mainPanel.setName("mainPanel"); // NOI18N
        mainPanel.setPreferredSize(new java.awt.Dimension(2500, 1300));
        mainPanel.setLayout(new javax.swing.BoxLayout(mainPanel, javax.swing.BoxLayout.LINE_AXIS));

        tabbedPanel.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPanel.setToolTipText("");
        tabbedPanel.setFocusable(false);
        tabbedPanel.setMaximumSize(new java.awt.Dimension(3500, 1800));
        tabbedPanel.setMinimumSize(new java.awt.Dimension(1000, 630));
        tabbedPanel.setName("tabbedPanel"); // NOI18N
        tabbedPanel.setPreferredSize(new java.awt.Dimension(1000, 630));

        tabHome.setBackground(new java.awt.Color(5, 26, 5));
        tabHome.setMinimumSize(new java.awt.Dimension(1000, 630));
        tabHome.setName("tabHome"); // NOI18N
        tabHome.setPreferredSize(new java.awt.Dimension(1000, 630));
        tabHome.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                tabHomeComponentShown(evt);
            }
        });

        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(249, 250, 241));
        jLabel10.setText("Welcome to");
        jLabel10.setName("jLabel10"); // NOI18N

        jLabel11.setFont(new java.awt.Font("Tahoma", 1, 48)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(249, 245, 239));
        jLabel11.setText("WildLog");
        jLabel11.setName("jLabel11"); // NOI18N

        jLabel12.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(237, 230, 221));
        jLabel12.setText("version " + WildLogApp.WILDLOG_VERSION);
        jLabel12.setName("jLabel12"); // NOI18N

        lblBlog.setFont(new java.awt.Font("Tahoma", 2, 12)); // NOI18N
        lblBlog.setForeground(new java.awt.Color(186, 210, 159));
        lblBlog.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblBlog.setText("http://cameratrap.mywild.co.za ");
        lblBlog.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblBlog.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        lblBlog.setName("lblBlog"); // NOI18N
        lblBlog.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                lblBlogMousePressed(evt);
            }
        });

        jLabel3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Feature 1.png"))); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        lblLocations.setForeground(new java.awt.Color(183, 195, 166));
        lblLocations.setText("Places:");
        lblLocations.setName("lblLocations"); // NOI18N

        lblVisits.setForeground(new java.awt.Color(183, 195, 166));
        lblVisits.setText("Periods:");
        lblVisits.setName("lblVisits"); // NOI18N

        lblSightings.setForeground(new java.awt.Color(183, 195, 166));
        lblSightings.setText("Observations:");
        lblSightings.setName("lblSightings"); // NOI18N

        lblCreatures.setForeground(new java.awt.Color(183, 195, 166));
        lblCreatures.setText("Creatures:");
        lblCreatures.setName("lblCreatures"); // NOI18N

        jSeparator5.setBackground(new java.awt.Color(57, 68, 43));
        jSeparator5.setForeground(new java.awt.Color(105, 123, 79));
        jSeparator5.setName("jSeparator5"); // NOI18N

        lblMyWild.setFont(new java.awt.Font("Tahoma", 2, 12)); // NOI18N
        lblMyWild.setForeground(new java.awt.Color(107, 124, 89));
        lblMyWild.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblMyWild.setText("http://www.mywild.co.za ");
        lblMyWild.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblMyWild.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        lblMyWild.setName("lblMyWild"); // NOI18N
        lblMyWild.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                lblMyWildMousePressed(evt);
            }
        });

        lblWorkspaceName.setFont(new java.awt.Font("Arial", 1, 14)); // NOI18N
        lblWorkspaceName.setForeground(new java.awt.Color(147, 169, 121));
        lblWorkspaceName.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblWorkspaceName.setText("...workspace...");
        lblWorkspaceName.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(50, 63, 32)), "Active Workspace", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 10), new java.awt.Color(107, 133, 90))); // NOI18N
        lblWorkspaceName.setName("lblWorkspaceName"); // NOI18N

        lblWorkspacePath.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        lblWorkspacePath.setForeground(new java.awt.Color(66, 78, 52));
        lblWorkspacePath.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        lblWorkspacePath.setText(WildLogPaths.getFullWorkspacePrefix().toString());
        lblWorkspacePath.setName("lblWorkspacePath"); // NOI18N

        jSeparator6.setBackground(new java.awt.Color(163, 175, 148));
        jSeparator6.setForeground(new java.awt.Color(216, 227, 201));
        jSeparator6.setName("jSeparator6"); // NOI18N

        lblEmail.setFont(new java.awt.Font("Tahoma", 2, 11)); // NOI18N
        lblEmail.setForeground(new java.awt.Color(115, 122, 107));
        lblEmail.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblEmail.setText("support@mywild.co.za ");
        lblEmail.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblEmail.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        lblEmail.setName("lblEmail"); // NOI18N
        lblEmail.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                lblEmailMousePressed(evt);
            }
        });

        jLabel21.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(66, 78, 52));
        jLabel21.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel21.setText("<html><u>Active Workspace Folder:</u></html>");
        jLabel21.setName("jLabel21"); // NOI18N

        jLabel23.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        jLabel23.setForeground(new java.awt.Color(66, 78, 52));
        jLabel23.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel23.setText("<html><u>Active Settings Folder:</u></html>");
        jLabel23.setName("jLabel23"); // NOI18N

        lblSettingsPath.setFont(new java.awt.Font("Arial", 0, 11)); // NOI18N
        lblSettingsPath.setForeground(new java.awt.Color(66, 78, 52));
        lblSettingsPath.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        lblSettingsPath.setText(WildLogApp.getACTIVE_WILDLOG_SETTINGS_FOLDER().normalize().toAbsolutePath().toString());
        lblSettingsPath.setName("lblSettingsPath"); // NOI18N

        lblEdition.setFont(new java.awt.Font("Tahoma", 0, 30)); // NOI18N
        lblEdition.setForeground(new java.awt.Color(185, 230, 161));
        lblEdition.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblEdition.setText(WildLogApp.WILDLOG_APPLICATION_TYPE.getEdition());
        lblEdition.setName("lblEdition"); // NOI18N

        lblWorkspaceUser.setFont(new java.awt.Font("Arial", 1, 14)); // NOI18N
        lblWorkspaceUser.setForeground(new java.awt.Color(147, 169, 121));
        lblWorkspaceUser.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblWorkspaceUser.setText(WildLogApp.WILDLOG_USER_NAME);
        lblWorkspaceUser.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(50, 63, 32)), "Active User", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 10), new java.awt.Color(107, 133, 90))); // NOI18N
        lblWorkspaceUser.setName("lblWorkspaceUser"); // NOI18N

        jSeparator26.setBackground(new java.awt.Color(57, 68, 43));
        jSeparator26.setForeground(new java.awt.Color(105, 123, 79));
        jSeparator26.setName("jSeparator26"); // NOI18N

        javax.swing.GroupLayout tabHomeLayout = new javax.swing.GroupLayout(tabHome);
        tabHome.setLayout(tabHomeLayout);
        tabHomeLayout.setHorizontalGroup(
            tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabHomeLayout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblSettingsPath, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblWorkspacePath, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel21)
                            .addComponent(jLabel23))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(50, 50, 50)
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 149, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(tabHomeLayout.createSequentialGroup()
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGap(110, 110, 110)
                        .addComponent(jLabel11)
                        .addGap(16, 16, 16)
                        .addComponent(jLabel12))
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGap(50, 50, 50)
                        .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel10)
                            .addComponent(jSeparator6, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(160, 495, Short.MAX_VALUE))
            .addGroup(tabHomeLayout.createSequentialGroup()
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tabHomeLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lblBlog)
                            .addComponent(lblMyWild, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lblEmail, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(tabHomeLayout.createSequentialGroup()
                                    .addGap(134, 134, 134)
                                    .addComponent(lblLocations)
                                    .addGap(18, 18, 18)
                                    .addComponent(lblVisits)
                                    .addGap(18, 18, 18)
                                    .addComponent(lblCreatures)
                                    .addGap(18, 18, 18)
                                    .addComponent(lblSightings))
                                .addGroup(tabHomeLayout.createSequentialGroup()
                                    .addGap(58, 58, 58)
                                    .addComponent(jSeparator5, javax.swing.GroupLayout.PREFERRED_SIZE, 437, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jSeparator26, javax.swing.GroupLayout.PREFERRED_SIZE, 437, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(lblWorkspaceUser, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lblWorkspaceName, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 437, Short.MAX_VALUE))
                                .addComponent(lblEdition, javax.swing.GroupLayout.PREFERRED_SIZE, 437, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        tabHomeLayout.setVerticalGroup(
            tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tabHomeLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblBlog)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblMyWild)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblEmail)
                .addGap(0, 0, 0)
                .addComponent(jLabel10)
                .addGap(0, 0, 0)
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel11)
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGap(30, 30, 30)
                        .addComponent(jLabel12)))
                .addGap(10, 10, 10)
                .addComponent(jSeparator6, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblLocations)
                    .addComponent(lblVisits)
                    .addComponent(lblSightings)
                    .addComponent(lblCreatures))
                .addGap(10, 10, 10)
                .addComponent(jSeparator5, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(tabHomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 162, Short.MAX_VALUE)
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(tabHomeLayout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addComponent(lblWorkspaceUser, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(5, 5, 5)
                        .addComponent(lblWorkspaceName, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(15, 15, 15)
                        .addComponent(jSeparator26, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(lblEdition)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel21)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblWorkspacePath)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel23)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblSettingsPath)))
                .addContainerGap())
        );

        tabbedPanel.addTab("Home", tabHome);

        tabLocation.setBackground(new java.awt.Color(194, 207, 214));
        tabLocation.setMinimumSize(new java.awt.Dimension(1000, 600));
        tabLocation.setName("tabLocation"); // NOI18N
        tabLocation.setPreferredSize(new java.awt.Dimension(1000, 600));
        tabbedPanel.addTab("All Locations", tabLocation);

        tabElement.setBackground(new java.awt.Color(201, 218, 199));
        tabElement.setMinimumSize(new java.awt.Dimension(1000, 600));
        tabElement.setName("tabElement"); // NOI18N
        tabElement.setPreferredSize(new java.awt.Dimension(1000, 600));
        tabbedPanel.addTab("All Creatures", tabElement);

        tabSightings.setBackground(new java.awt.Color(235, 233, 221));
        tabSightings.setMinimumSize(new java.awt.Dimension(1000, 600));
        tabSightings.setName("tabSightings"); // NOI18N
        tabSightings.setPreferredSize(new java.awt.Dimension(1000, 600));
        tabbedPanel.addTab("All Sightings", tabSightings);

        tabBrowse.setBackground(new java.awt.Color(204, 213, 186));
        tabBrowse.setMinimumSize(new java.awt.Dimension(1000, 630));
        tabBrowse.setName("tabBrowse"); // NOI18N
        tabBrowse.setPreferredSize(new java.awt.Dimension(1000, 630));
        tabbedPanel.addTab("Browse All", tabBrowse);

        mainPanel.add(tabbedPanel);

        getContentPane().add(mainPanel, java.awt.BorderLayout.CENTER);

        statusPanel.setBackground(new java.awt.Color(212, 217, 201));
        statusPanel.setName("statusPanel"); // NOI18N
        statusPanel.setLayout(new java.awt.BorderLayout(10, 0));

        statusMessageLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        statusMessageLabel.setAlignmentY(0.0F);
        statusMessageLabel.setMaximumSize(new java.awt.Dimension(2147483647, 20));
        statusMessageLabel.setMinimumSize(new java.awt.Dimension(100, 20));
        statusMessageLabel.setName("statusMessageLabel"); // NOI18N
        statusMessageLabel.setPreferredSize(new java.awt.Dimension(500, 20));
        statusPanel.add(statusMessageLabel, java.awt.BorderLayout.CENTER);

        jPanel1.setBackground(new java.awt.Color(212, 217, 201));
        jPanel1.setMaximumSize(new java.awt.Dimension(400, 20));
        jPanel1.setMinimumSize(new java.awt.Dimension(50, 16));
        jPanel1.setName("jPanel1"); // NOI18N
        jPanel1.setLayout(new java.awt.BorderLayout(5, 0));

        progressBar.setBackground(new java.awt.Color(204, 213, 186));
        progressBar.setMaximumSize(new java.awt.Dimension(400, 20));
        progressBar.setMinimumSize(new java.awt.Dimension(50, 14));
        progressBar.setName("progressBar"); // NOI18N
        progressBar.setPreferredSize(new java.awt.Dimension(320, 14));
        jPanel1.add(progressBar, java.awt.BorderLayout.CENTER);

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setMaximumSize(new java.awt.Dimension(20, 20));
        statusAnimationLabel.setMinimumSize(new java.awt.Dimension(20, 20));
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N
        statusAnimationLabel.setPreferredSize(new java.awt.Dimension(20, 20));
        jPanel1.add(statusAnimationLabel, java.awt.BorderLayout.EAST);

        statusPanel.add(jPanel1, java.awt.BorderLayout.EAST);

        getContentPane().add(statusPanel, java.awt.BorderLayout.PAGE_END);

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText("Application");
        fileMenu.setName("fileMenu"); // NOI18N
        fileMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                fileMenuMenuSelected(evt);
            }
        });

        workspaceMenu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon.gif"))); // NOI18N
        workspaceMenu.setText("Workspace");
        workspaceMenu.setName("workspaceMenu"); // NOI18N

        mnuChangeWorkspaceMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon.gif"))); // NOI18N
        mnuChangeWorkspaceMenuItem.setText("Switch Active Workspace");
        mnuChangeWorkspaceMenuItem.setToolTipText("Select another Workspace to use.");
        mnuChangeWorkspaceMenuItem.setName("mnuChangeWorkspaceMenuItem"); // NOI18N
        mnuChangeWorkspaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuChangeWorkspaceMenuItemActionPerformed(evt);
            }
        });
        workspaceMenu.add(mnuChangeWorkspaceMenuItem);

        mnuCreateWorkspaceMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon.gif"))); // NOI18N
        mnuCreateWorkspaceMenuItem.setText("Create New Workspace");
        mnuCreateWorkspaceMenuItem.setToolTipText("Select a folder where a new Workspace will be created.");
        mnuCreateWorkspaceMenuItem.setName("mnuCreateWorkspaceMenuItem"); // NOI18N
        mnuCreateWorkspaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuCreateWorkspaceMenuItemActionPerformed(evt);
            }
        });
        workspaceMenu.add(mnuCreateWorkspaceMenuItem);

        fileMenu.add(workspaceMenu);

        jSeparator2.setName("jSeparator2"); // NOI18N
        fileMenu.add(jSeparator2);

        mnuExitApp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon Selected.gif"))); // NOI18N
        mnuExitApp.setText("Exit WildLog");
        mnuExitApp.setToolTipText("Close the application.");
        mnuExitApp.setName("mnuExitApp"); // NOI18N
        mnuExitApp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuExitAppActionPerformed(evt);
            }
        });
        fileMenu.add(mnuExitApp);

        menuBar.add(fileMenu);

        extraMenu.setText("Extra");
        extraMenu.setName("extraMenu"); // NOI18N
        extraMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                extraMenuMenuSelected(evt);
            }
        });

        mnuExifMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/EXIF.png"))); // NOI18N
        mnuExifMenuItem.setText("Image EXIF Data Reader");
        mnuExifMenuItem.setToolTipText("Browse to any image on your computer and view the EXIF data.");
        mnuExifMenuItem.setName("mnuExifMenuItem"); // NOI18N
        mnuExifMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuExifMenuItemActionPerformed(evt);
            }
        });
        extraMenu.add(mnuExifMenuItem);

        mnuConvertCoordinates.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/GPS.png"))); // NOI18N
        mnuConvertCoordinates.setText("Convert GPS / Pentad / QDGC");
        mnuConvertCoordinates.setToolTipText("Convert between GPS, Pentad and QDS coordinates.");
        mnuConvertCoordinates.setName("mnuConvertCoordinates"); // NOI18N
        mnuConvertCoordinates.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuConvertCoordinatesActionPerformed(evt);
            }
        });
        extraMenu.add(mnuConvertCoordinates);

        mnuCreateSlideshow.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/Slideshow_Small.gif"))); // NOI18N
        mnuCreateSlideshow.setText("Create a JPEG Movie");
        mnuCreateSlideshow.setToolTipText("Create a JPEG Movie slideshow using a folder of images anywhere on your computer.");
        mnuCreateSlideshow.setName("mnuCreateSlideshow"); // NOI18N
        mnuCreateSlideshow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuCreateSlideshowActionPerformed(evt);
            }
        });
        extraMenu.add(mnuCreateSlideshow);

        mnuCreateGIF.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/GIF_Small.png"))); // NOI18N
        mnuCreateGIF.setText("Create an Animated GIF");
        mnuCreateGIF.setToolTipText("Create an animated GIF slideshow using a folder of images anywhere on your computer.");
        mnuCreateGIF.setName("mnuCreateGIF"); // NOI18N
        mnuCreateGIF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuCreateGIFActionPerformed(evt);
            }
        });
        extraMenu.add(mnuCreateGIF);

        mnuSunAndMoon.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/SunAndMoon_big.png"))); // NOI18N
        mnuSunAndMoon.setText("View Sun And Moon Phase");
        mnuSunAndMoon.setToolTipText("Opens up a Sun and Moon Phase dialog that can be used to determine the phases at any time and location.");
        mnuSunAndMoon.setName("mnuSunAndMoon"); // NOI18N
        mnuSunAndMoon.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuSunAndMoonActionPerformed(evt);
            }
        });
        extraMenu.add(mnuSunAndMoon);

        mnuSystemMonitor.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon Selected.gif"))); // NOI18N
        mnuSystemMonitor.setText("System Monitor");
        mnuSystemMonitor.setToolTipText("Opens up a Sun and Moon Phase dialog that can be used to determine the phases at any time and location.");
        mnuSystemMonitor.setName("mnuSystemMonitor"); // NOI18N
        mnuSystemMonitor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuSystemMonitorActionPerformed(evt);
            }
        });
        extraMenu.add(mnuSystemMonitor);

        menuBar.add(extraMenu);

        helpMenu.setText("Help");
        helpMenu.setName("helpMenu"); // NOI18N
        helpMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                helpMenuMenuSelected(evt);
            }
        });

        mnuUserGuide.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon Selected.gif"))); // NOI18N
        mnuUserGuide.setText("User Guide (PDF)");
        mnuUserGuide.setToolTipText("Opens the WildLog User Guide, or a link to a website where it can be downloaded.");
        mnuUserGuide.setName("mnuUserGuide"); // NOI18N
        mnuUserGuide.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuUserGuideActionPerformed(evt);
            }
        });
        helpMenu.add(mnuUserGuide);

        jSeparator17.setName("jSeparator17"); // NOI18N
        helpMenu.add(jSeparator17);

        mnuCheckUpdates.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon.gif"))); // NOI18N
        mnuCheckUpdates.setText("Check for Updates");
        mnuCheckUpdates.setToolTipText("Check online whether there is a newer version of WildLog available.");
        mnuCheckUpdates.setName("mnuCheckUpdates"); // NOI18N
        mnuCheckUpdates.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuCheckUpdatesActionPerformed(evt);
            }
        });
        helpMenu.add(mnuCheckUpdates);

        mnuAboutWildLog.setIcon(new javax.swing.ImageIcon(getClass().getResource("/wildlog/resources/icons/WildLog Icon.gif"))); // NOI18N
        mnuAboutWildLog.setText("About WildLog");
        mnuAboutWildLog.setToolTipText("Display information about this version of WildLog.");
        mnuAboutWildLog.setName("mnuAboutWildLog"); // NOI18N
        mnuAboutWildLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mnuAboutWildLogActionPerformed(evt);
            }
        });
        helpMenu.add(mnuAboutWildLog);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }// </editor-fold>//GEN-END:initComponents

    private void tabHomeComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_tabHomeComponentShown
        lblLocations.setText("Places: " + app.getDBI().countLocations(null));
        lblVisits.setText("Periods: " + app.getDBI().countVisits(null, null));
        lblSightings.setText("Observations: " + app.getDBI().countSightings(0, null, null, null));
        lblCreatures.setText("Creatures: " + app.getDBI().countElements(null, null));
        lblWorkspaceName.setText(app.getWildLogOptions().getWorkspaceName());
    }//GEN-LAST:event_tabHomeComponentShown

    private void mnuExifMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuExifMenuItemActionPerformed
        WLFileChooser fileChooser = new WLFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File inFile) {
                if (inFile.isDirectory()) {
                    return true;
                }
                if (WildLogFileExtentions.Images.isJPG(inFile.toPath())) {
                        return true;
                }
                return false;
            }
            @Override
            public String getDescription() {
                return "JPG Images";
            }
        });
        int result = fileChooser.showOpenDialog(app.getMainFrame());
        if ((result != JFileChooser.ERROR_OPTION) && (result == JFileChooser.APPROVE_OPTION)) {
            UtilsDialog.showExifPopup(app, fileChooser.getSelectedFile().toPath());
        }
    }//GEN-LAST:event_mnuExifMenuItemActionPerformed

    private void mnuCreateSlideshowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuCreateSlideshowActionPerformed
        WLFileChooser fileChooser = new WLFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("JPG Images",
                WildLogFileExtentions.Images.JPG.getExtention().toLowerCase(), WildLogFileExtentions.Images.JPEG.getExtention().toLowerCase(),
                WildLogFileExtentions.Images.JPG.getExtention().toUpperCase(), WildLogFileExtentions.Images.JPG.getExtention().toUpperCase()));
        fileChooser.setDialogTitle("Select the JPG images to use for the Custom Slideshow...");
        int result = fileChooser.showOpenDialog(app.getMainFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            UtilsConcurency.kickoffProgressbarTask(app, new ProgressbarTask(app) {
                @Override
                protected Object doInBackground() throws Exception {
                    setMessage("Starting the Custom Slideshow");
                    List<File> files = Arrays.asList(fileChooser.getSelectedFiles());
                    List<String> fileNames = new ArrayList<String>(files.size());
                    for (File tempFile : files) {
                        fileNames.add(tempFile.getAbsolutePath());
                    }
                    fileChooser.setDialogTitle("Please select where to save the Custom Slideshow...");
                    fileChooser.setMultiSelectionEnabled(false);
                    fileChooser.setSelectedFile(new File("slideshow.mov"));
                    fileChooser.setFileFilter(new FileNameExtensionFilter("Slideshow movie", "mov"));
                    int result = fileChooser.showSaveDialog(app.getMainFrame());
                    if (result == JFileChooser.APPROVE_OPTION) {
                        // Now create the slideshow
                        setMessage("Busy with the Custom Slideshow (this may take a while)");
                        UtilsMovies.generateSlideshow(fileNames, app, fileChooser.getSelectedFile().toPath());
                        setMessage("Done with the Custom Slideshow");
                    }
                    return null;
                }
            });
        }
    }//GEN-LAST:event_mnuCreateSlideshowActionPerformed

    private void mnuSunAndMoonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuSunAndMoonActionPerformed
        SunMoonDialog dialog = new SunMoonDialog(app, null);
        dialog.setVisible(true);
    }//GEN-LAST:event_mnuSunAndMoonActionPerformed

    private void mnuExitAppActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuExitAppActionPerformed
//        // Making the frame not visible (or calling dispose on it) hopefully prevents this error: java.lang.InterruptedException at java.lang.Object.wait(Native Method)
//        this.setVisible(false);
        app.quit(evt);
    }//GEN-LAST:event_mnuExitAppActionPerformed

    private void mnuAboutWildLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuAboutWildLogActionPerformed
        JDialog aboutBox = new WildLogAboutBox(app);
        aboutBox.setVisible(true);
    }//GEN-LAST:event_mnuAboutWildLogActionPerformed

    private void mnuChangeWorkspaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuChangeWorkspaceMenuItemActionPerformed
        if (WildLogApp.configureWildLogHomeBasedOnFileBrowser(app.getMainFrame(), false)) {
            // Write first
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(WildLogApp.getACTIVE_WILDLOG_SETTINGS_FOLDER().resolve("wildloghome").toFile()));
                writer.write(WildLogPaths.getFullWorkspacePrefix().toString());
                writer.flush();
            }
            catch (IOException ex) {
                WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
            }
            finally {
                if (writer != null) {
                    try {
                        writer.close();
                    }
                    catch (IOException ex) {
                        WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                    }
                }
            }
            // Shutdown
            WLOptionPane.showMessageDialog(app.getMainFrame(),
                    "The WildLog Workspace has been changed to: " + System.lineSeparator() 
                    + WildLogPaths.getFullWorkspacePrefix().toString() + System.lineSeparator()
                    + "Please restart the application.",
                    "Workspace Changed!", JOptionPane.INFORMATION_MESSAGE);
//            // Making the frame not visible (or calling dispose on it) hopefully prevents this error: java.lang.InterruptedException at java.lang.Object.wait(Native Method)
//            this.setVisible(false);
            app.quit(evt);
        }
    }//GEN-LAST:event_mnuChangeWorkspaceMenuItemActionPerformed

    private void mnuCreateWorkspaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuCreateWorkspaceMenuItemActionPerformed
        if (WildLogApp.configureWildLogHomeBasedOnFileBrowser(app.getMainFrame(), false)) {
            // Write first
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(WildLogApp.getACTIVE_WILDLOG_SETTINGS_FOLDER().resolve("wildloghome").toFile()));
                writer.write(WildLogPaths.getFullWorkspacePrefix().toString());
                writer.flush();
            }
            catch (IOException ex) {
                WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
            }
            finally {
                if (writer != null) {
                    try {
                        writer.close();
                    }
                    catch (IOException ex) {
                        WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                    }
                }
            }
            // Shutdown
            WLOptionPane.showMessageDialog(app.getMainFrame(),
                    "The WildLog Workspace has been created. Please restart the application.",
                    "Workspace Created!", JOptionPane.INFORMATION_MESSAGE);
//            // Making the frame not visible (or calling dispose on it) hopefully prevents this error: java.lang.InterruptedException at java.lang.Object.wait(Native Method)
//            this.setVisible(false);
            app.quit(evt);
        }
    }//GEN-LAST:event_mnuCreateWorkspaceMenuItemActionPerformed

    private void mnuCreateGIFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuCreateGIFActionPerformed
        WLFileChooser fileChooser = new WLFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("JPG Images",
                WildLogFileExtentions.Images.JPG.getExtention().toLowerCase(), WildLogFileExtentions.Images.JPEG.getExtention().toLowerCase(),
                WildLogFileExtentions.Images.JPG.getExtention().toUpperCase(), WildLogFileExtentions.Images.JPG.getExtention().toUpperCase()));
        fileChooser.setDialogTitle("Select the JPG images to use for the Custom Animated GIF...");
        int result = fileChooser.showOpenDialog(app.getMainFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            UtilsConcurency.kickoffProgressbarTask(app, new ProgressbarTask(app) {
                @Override
                protected Object doInBackground() throws Exception {
                    setMessage("Creating the Custom Animated GIF");
                    List<File> files = Arrays.asList(fileChooser.getSelectedFiles());
                    List<String> fileNames = new ArrayList<String>(files.size());
                    for (File tempFile : files) {
                        fileNames.add(tempFile.getAbsolutePath());
                    }
                    fileChooser.setDialogTitle("Please select where to save the Custom Animated GIF...");
                    fileChooser.setMultiSelectionEnabled(false);
                    fileChooser.setSelectedFile(new File("animated_gif.gif"));
                    fileChooser.setFileFilter(new FileNameExtensionFilter("Animated GIF", "gif"));
                    int result = fileChooser.showSaveDialog(app.getMainFrame());
                    if (result == JFileChooser.APPROVE_OPTION) {
                        setProgress(1);
                        setMessage("Creating the Custom Animated GIF " + getProgress() + "%");
                        // Now create the GIF
                        if (!fileNames.isEmpty()) {
                            Path outputPath = fileChooser.getSelectedFile().toPath();
                            Files.createDirectories(outputPath.getParent());
                            ImageOutputStream output = null;
                            try {
                                output = new FileImageOutputStream(outputPath.toFile());
                                int thumbnailSize = app.getWildLogOptions().getDefaultSlideshowSize();
                                ImageIcon image = UtilsImageProcessing.getScaledIcon(WildLogSystemImages.MOVIES.getWildLogFile().getAbsolutePath(), thumbnailSize, false);
                                BufferedImage bufferedImage = new BufferedImage(image.getIconWidth(), image.getIconHeight(), BufferedImage.TYPE_INT_RGB);
                                Graphics2D graphics2D = bufferedImage.createGraphics();
                                graphics2D.drawImage(image.getImage(), 
                                            (thumbnailSize - image.getIconWidth())/2, 
                                            (thumbnailSize - image.getIconHeight())/2, 
                                            image.getIconWidth(), 
                                            image.getIconHeight(), 
                                            Color.BLACK, null);
                                int timeBetweenFrames = (int) (1000.0 / ((double) app.getWildLogOptions().getDefaultSlideshowSpeed()));
                                AnimatedGIFWriter gifWriter = new AnimatedGIFWriter(output, bufferedImage.getType(), timeBetweenFrames, true);
                                gifWriter.writeToGIF(bufferedImage);
                                setProgress(2);
                                setMessage("Creating the Custom Animated GIF " + getProgress() + "%");
                                for (int t = 0; t < fileNames.size(); t++) {
                                    image = UtilsImageProcessing.getScaledIcon(Paths.get(fileNames.get(t)), thumbnailSize, true);
                                    bufferedImage = new BufferedImage(thumbnailSize, thumbnailSize, BufferedImage.TYPE_INT_RGB);
                                    graphics2D = bufferedImage.createGraphics();
                                    graphics2D.drawImage(image.getImage(), 
                                            (thumbnailSize - image.getIconWidth())/2, 
                                            (thumbnailSize - image.getIconHeight())/2, 
                                            image.getIconWidth(), 
                                            image.getIconHeight(), 
                                            Color.BLACK, null);
                                    gifWriter.writeToGIF(bufferedImage);
                                    setProgress(2 + (int)((((double)t)/((double)fileNames.size()))*97));
                                    setMessage("Creating the Custom Animated GIF " + getProgress() + "%");
                                }
                                gifWriter.finishGIF();
                            }
                            catch (IOException ex) {
                                WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                            }
                            finally {
                                if (output != null) {
                                    try {
                                        output.flush();
                                    }
                                    catch (IOException ex) {
                                        WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                                    }
                                    try {
                                        output.close();
                                    }
                                    catch (IOException ex) {
                                        WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                                    }
                                }
                            }
                            UtilsFileProcessing.openFile(outputPath);
                        }
                    }
                    setProgress(100);
                    setMessage("Done with the Custom Animated GIF");
                    return null;
                }
            });
        }
    }//GEN-LAST:event_mnuCreateGIFActionPerformed

    private void fileMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_fileMenuMenuSelected
        WildLogApp.LOGGER.log(Level.INFO, "[ApplicationMenu]");
    }//GEN-LAST:event_fileMenuMenuSelected

    private void extraMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_extraMenuMenuSelected
        WildLogApp.LOGGER.log(Level.INFO, "[ExtraMenu]");
    }//GEN-LAST:event_extraMenuMenuSelected

    private void helpMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_helpMenuMenuSelected
        WildLogApp.LOGGER.log(Level.INFO, "[HelpMenu]");
    }//GEN-LAST:event_helpMenuMenuSelected

    private void mnuUserGuideActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuUserGuideActionPerformed
        Path userGuide = WildLogApp.getACTIVEWILDLOG_CODE_FOLDER().getParent().resolve("documentation").resolve("WildLog - User Guide.pdf");
        if (Files.exists(userGuide)) {
            UtilsFileProcessing.openFile(userGuide);
        }
        else {
            // Show message with download link
            JLabel label = new JLabel();
            Font font = label.getFont();
            String style = "font-family:" + font.getFamily() + ";font-weight:normal" + ";font-size:" + font.getSize() + "pt;";
            JEditorPane editorPane = new JEditorPane("text/html", "<html><body style=\"" + style + "\">"
                    + "To download the WildLog User Guide go to <a href=\"http://software.mywild.co.za/p/download-wildlog.html\">http://software.mywild.co.za/p/download-wildlog.html</a>"
                    + " or visit <a href=\"http://www.mywild.co.za\">http://www.mywild.co.za</a> for more information."
                    + "</body></html>");
            editorPane.addHyperlinkListener(new HyperlinkListener() {
                    @Override
                    public void hyperlinkUpdate(HyperlinkEvent inHyperlinkEvent) {
                        if (inHyperlinkEvent.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                            try {
                                Desktop.getDesktop().browse(inHyperlinkEvent.getURL().toURI());
                            }
                            catch (IOException | URISyntaxException ex) {
                                WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
                            }
                        }
                    }
                });
            editorPane.setEditable(false);
            editorPane.setBackground(label.getBackground());
            WLOptionPane.showMessageDialog(WildLogApp.getApplication().getMainFrame(), 
                    editorPane, 
                    "WildLog User Guide", JOptionPane.INFORMATION_MESSAGE);
        }
    }//GEN-LAST:event_mnuUserGuideActionPerformed

    private void mnuCheckUpdatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuCheckUpdatesActionPerformed
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("WL_CheckForUpdates"));
        // Try to check the latest version
        executor.submit(new Runnable() {
            @Override
            public void run() {
                String latestVersion = app.checkForUpdates();
                // The checkForUpdates() call will show a popup if the versions are out of sync
                if (WildLogApp.WILDLOG_VERSION.equalsIgnoreCase(latestVersion)) {
                    WLOptionPane.showMessageDialog(WildLogApp.getApplication().getMainFrame(), 
                        "You are using the latest official release of WildLog (v" + latestVersion + ").", 
                        "WildLog is up to date", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        executor.shutdown();
    }//GEN-LAST:event_mnuCheckUpdatesActionPerformed

    private void lblBlogMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblBlogMousePressed
        try {
            Desktop.getDesktop().browse(URI.create(lblBlog.getText().trim()));
        }
        catch (IOException ex) {
            WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
        }
    }//GEN-LAST:event_lblBlogMousePressed

    private void lblMyWildMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblMyWildMousePressed
        try {
            Desktop.getDesktop().browse(URI.create(lblMyWild.getText().trim()));
        }
        catch (IOException ex) {
            WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
        }
    }//GEN-LAST:event_lblMyWildMousePressed

    private void lblEmailMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblEmailMousePressed
        try {
            Desktop.getDesktop().mail(URI.create("mailto:" + lblEmail.getText().trim()));
        }
        catch (IOException ex) {
            WildLogApp.LOGGER.log(Level.ERROR, ex.toString(), ex);
        }
    }//GEN-LAST:event_lblEmailMousePressed

    private void mnuConvertCoordinatesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuConvertCoordinatesActionPerformed
        GPSGridConversionDialog dialog = new GPSGridConversionDialog();
        dialog.setVisible(true);
    }//GEN-LAST:event_mnuConvertCoordinatesActionPerformed

    private void mnuSystemMonitorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mnuSystemMonitorActionPerformed
        SystemMonitorDialog dialog = new SystemMonitorDialog();
        dialog.setVisible(true);
    }//GEN-LAST:event_mnuSystemMonitorActionPerformed

    public void browseSelectedElement(Element inElement) {
        panelTabBrowse.browseSelectedElement(inElement);
    }

    public void browseSelectedLocation(Location inLocation) {
        panelTabBrowse.browseSelectedLocation(inLocation);
    }

    public void browseSelectedVisit(Visit inVisit) {
        panelTabBrowse.browseSelectedVisit(inVisit);
    }
    
    public void browseSelectedSighting(Sighting inSighting) {
        panelTabBrowse.browseSelectedSighting(inSighting);
    }

    public boolean closeAllTabs() {
        boolean closeStatus = true;
        while ((tabbedPanel.getTabCount() > STATIC_TAB_COUNT) && (closeStatus)) {
            tabbedPanel.setSelectedIndex(STATIC_TAB_COUNT);
            PanelCanSetupHeader tab = (PanelCanSetupHeader) tabbedPanel.getComponentAt(STATIC_TAB_COUNT);
            closeStatus = tab.closeTab();
        }
        return closeStatus;
    }

    public void refreshHomeTab() {
        tabHomeComponentShown(null);
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPanel;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu extraMenu;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPopupMenu.Separator jSeparator17;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JSeparator jSeparator26;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JLabel lblBlog;
    private javax.swing.JLabel lblCreatures;
    private javax.swing.JLabel lblEdition;
    private javax.swing.JLabel lblEmail;
    private javax.swing.JLabel lblLocations;
    private javax.swing.JLabel lblMyWild;
    private javax.swing.JLabel lblSettingsPath;
    private javax.swing.JLabel lblSightings;
    private javax.swing.JLabel lblVisits;
    private javax.swing.JLabel lblWorkspaceName;
    private javax.swing.JLabel lblWorkspacePath;
    private javax.swing.JLabel lblWorkspaceUser;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem mnuChangeWorkspaceMenuItem;
    private javax.swing.JMenuItem mnuCheckUpdates;
    private javax.swing.JMenuItem mnuConvertCoordinates;
    private javax.swing.JMenuItem mnuCreateGIF;
    private javax.swing.JMenuItem mnuCreateSlideshow;
    private javax.swing.JMenuItem mnuCreateWorkspaceMenuItem;
    private javax.swing.JMenuItem mnuExifMenuItem;
    private javax.swing.JMenuItem mnuSunAndMoon;
    private javax.swing.JMenuItem mnuSystemMonitor;
    private javax.swing.JMenuItem mnuUserGuide;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JPanel tabBrowse;
    private javax.swing.JPanel tabElement;
    private javax.swing.JPanel tabHome;
    private javax.swing.JPanel tabLocation;
    private javax.swing.JPanel tabSightings;
    private javax.swing.JTabbedPane tabbedPanel;
    private javax.swing.JMenu workspaceMenu;
    // End of variables declaration//GEN-END:variables
}
