package me.drton.flightplot;

import me.drton.flightplot.export.GPXTrackExporter;
import me.drton.flightplot.export.KMLTrackExporter;
import me.drton.flightplot.export.TrackExportDialog;
import me.drton.flightplot.export.TrackExporter;
import me.drton.flightplot.processors.PlotProcessor;
import me.drton.flightplot.processors.ProcessorsList;
import me.drton.flightplot.processors.Simple;
import me.drton.jmavlib.log.FormatErrorException;
import me.drton.jmavlib.log.LogReader;
import me.drton.jmavlib.log.px4.PX4LogReader;
import me.drton.jmavlib.log.ulog.MessageLog;
import me.drton.jmavlib.log.ulog.ULogReader;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.ChartChangeEvent;
import org.jfree.chart.event.ChartChangeEventType;
import org.jfree.chart.event.ChartChangeListener;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * User: ton Date: 03.06.13 Time: 23:24
 */
public class FlightPlot {
    private static final int TIME_MODE_LOG_START = 0;
    private static final int TIME_MODE_BOOT = 1;
    private static final int TIME_MODE_GPS = 2;
    private static final NumberFormat doubleNumberFormat = NumberFormat.getInstance(Locale.ROOT);

    static {
        doubleNumberFormat.setGroupingUsed(false);
        doubleNumberFormat.setMinimumFractionDigits(1);
        doubleNumberFormat.setMaximumFractionDigits(10);
    }

    private static String appName = "FlightPlot";
    private static String version = "0.3.2";
    private static String appNameAndVersion = appName + " v." + version;
    private static String colorParamPrefix = "Color ";
    private final Preferences preferences;
    private JFrame mainFrame;
    private JLabel statusLabel;
    private JPanel mainPanel;
    private JTable parametersTable;
    private JTable logTable;
    private DefaultTableModel parametersTableModel;
    private DefaultTableModel logsTableModel;
    private ChartPanel chartPanel;
    private JTable processorsList;
    private DefaultTableModel processorsListModel;
    private TableModelListener parameterChangedListener;
    private JButton addProcessorButton;
    private JButton removeProcessorButton;
    private JButton openLogButton;
    private JButton fieldsListButton;
    private JComboBox presetComboBox;
    private List<Preset> presetsList = new ArrayList<Preset>();
    private JButton deletePresetButton;
    private JButton logInfoButton;
    private JCheckBox markerCheckBox;
    private JButton savePresetButton;
    private JCheckBoxMenuItem autosavePresets;
    private JRadioButtonMenuItem[] timeModeItems;
    private LogReader logReader = null;
    private XYSeriesCollection dataset;
    private JFreeChart chart;
    private ColorSupplier colorSupplier;
    private ProcessorsList processorsTypesList;
    private File lastPresetDirectory = null;
    private AddProcessorDialog addProcessorDialog;
    private FieldsListDialog fieldsListDialog;
    private LogInfo logInfo;
    private JFileChooser openLogFileChooser;
    private FileNameExtensionFilter presetExtensionFilter = new FileNameExtensionFilter("FlightPlot Presets (*.fplot)",
            "fplot");
    private FileNameExtensionFilter parametersExtensionFilter = new FileNameExtensionFilter("Parameters (*.txt)", "txt");
    private AtomicBoolean invokeProcessFile = new AtomicBoolean(false);
    private TrackExportDialog trackExportDialog;
    private PlotExportDialog plotExportDialog;
    private NumberAxis domainAxisSeconds;
    private DateAxis domainAxisDate;
    private int timeMode = 0;
    private boolean autosave = false;
    private List<Map<String, Integer>> seriesIndex = new ArrayList<Map<String, Integer>>();
    private ProcessorPreset editingProcessor = null;
    private List<ProcessorPreset> activeProcessors = new ArrayList<ProcessorPreset>();
    private Range lastTimeRange = null;
    private String currentPreset = null;

    public FlightPlot() {
        Map<String, TrackExporter> exporters = new LinkedHashMap<String, TrackExporter>();
        for (TrackExporter exporter : new TrackExporter[]{
                new KMLTrackExporter(),
                new GPXTrackExporter()
        }) {
            exporters.put(exporter.getName(), exporter);
        }
        trackExportDialog = new TrackExportDialog(exporters);
        plotExportDialog = new PlotExportDialog(this);

        preferences = Preferences.userRoot().node(appName);
        mainFrame = new JFrame(appNameAndVersion);
        mainFrame.setContentPane(mainPanel);
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onQuit();
            }
        });
        mainFrame.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles.size() == 1) {
                        File file = droppedFiles.get(0);
                        openLog(file.getAbsolutePath());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        createMenuBar();
        java.util.List<String> processors = new ArrayList<String>(processorsTypesList.getProcessorsList());
        Collections.sort(processors);
        addProcessorDialog = new AddProcessorDialog(processors.toArray(new String[processors.size()]));
        addProcessorDialog.pack();
        fieldsListDialog = new FieldsListDialog(new Runnable() {
            @Override
            public void run() {
                StringBuilder fieldsValue = new StringBuilder();
                String processorTitle = "New";
                if (fieldsListDialog.getSelectedFields().size() == 1)
                    processorTitle = fieldsListDialog.getSelectedFields().get(0);
                for (String field : fieldsListDialog.getSelectedFields()) {
                    if (fieldsValue.length() > 0) {
                        fieldsValue.append(" ");
                    }
                    fieldsValue.append(field);
                }
                PlotProcessor processor = new Simple();
                processor.setParameters(Collections.<String, Object>singletonMap("Fields", fieldsValue.toString()));
                ProcessorPreset pp = new ProcessorPreset(processorTitle, processor.getProcessorType(),
                        processor.getParameters(), Collections.<String, Color>emptyMap(), true);
                updatePresetParameters(pp, null);
                int i = processorsListModel.getRowCount();
                processorsListModel.addRow(new Object[]{pp.isVisible(), pp});
                processorsList.getSelectionModel().setSelectionInterval(i, i);
                processorsList.repaint();
                updateUsedColors();
                showAddProcessorDialog(true);
                processFile();
            }
        });
        logInfo = new LogInfo();
        addProcessorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAddProcessorDialog(false);
            }
        });
        removeProcessorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedProcessor();
            }
        });
        openLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOpenLogDialog();
            }
        });
        fieldsListButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fieldsListDialog.setVisible(true);
                fieldsListDialog.repaint();
            }
        });
        logInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logInfo.setVisible(true);
            }
        });
        processorsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processorsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                // If processor changed during editing skip this event to avoid inconsistent editor state
                if (editingProcessor == null) {
                    showProcessorParameters();
                }
            }
        });
        processorsList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        processorsList.getActionMap().put("Enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                showAddProcessorDialog(true);
            }
        });
        processorsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable) e.getSource();
                if (e.getClickCount() > 1 && target.getSelectedColumn() == 1) {
                    showAddProcessorDialog(true);
                }
            }
        });
        processorsListModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    if (e.getColumn() == 0) {
                        // Update processor preset field here to remember visibility state
                        ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(e.getFirstRow(), 1);
                        if ((Boolean) processorsListModel.getValueAt(e.getFirstRow(), 0)) {
                            pp.setVisible(true);
                        } else {
                            pp.setVisible(false);
                        }

                        updatePresetEdited(true);
                        processFile();
                    }
                }
            }
        });
        parameterChangedListener = new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    int row = e.getFirstRow();
                    onParameterChanged(row);
                    editingProcessor = null;
                }
            }
        };
        parametersTableModel.addTableModelListener(parameterChangedListener);

        // Open Log Dialog
        FileNameExtensionFilter[] logExtensionfilters = new FileNameExtensionFilter[]{
                new FileNameExtensionFilter("All known Log files (*.px4log, *.bin, *.ulg)", "px4log", "bin", "ulg"),
                new FileNameExtensionFilter("PX4/APM Log (*.px4log, *.bin)", "px4log", "bin"),
                new FileNameExtensionFilter("ULog (*.ulg)", "ulg")
        };

        openLogFileChooser = new JFileChooser();
        for (FileNameExtensionFilter filter : logExtensionfilters) {
            openLogFileChooser.addChoosableFileFilter(filter);
        }
        openLogFileChooser.setFileFilter(logExtensionfilters[0]);
        openLogFileChooser.setDialogTitle("Open Log");

        presetComboBox.setMaximumRowCount(30);
        presetComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onPresetAction(e);
            }
        });
        savePresetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSavePreset();
            }
        });
        deletePresetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDeletePreset();
            }
        });
        markerCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setChartMarkers();
            }
        });

        mainFrame.pack();
        mainFrame.setVisible(true);

        // Load preferences
        try {
            loadPreferences();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private void updateAndSavePresets() {
        // Update and save current preset if selected
        if (currentPreset != null) {
            Preset preset = formatPreset(currentPreset);
            updatePreset(preset);
            loadPresetsList();
            savePreferences();
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException,
            IllegalAccessException {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (OSValidator.isMac()) {
                    System.setProperty("apple.laf.useScreenMenuBar", "true");
                }
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                new FlightPlot();
            }
        });
    }

    private static Object formatParameterValue(Object value) {
        Object returnValue;
        if (value instanceof Double) {
            returnValue = doubleNumberFormat.format(value);
        } else if (value instanceof Color) {
            returnValue = value;
        } else {
            returnValue = value.toString();
        }
        return returnValue;
    }

    private void onQuit() {
        savePreferences();
        System.exit(0);
    }

    private void onPresetAction(ActionEvent e) {
        if ("comboBoxEdited".equals(e.getActionCommand())) {
            // Save preset
            onSavePreset();
        } else if ("comboBoxChanged".equals(e.getActionCommand())) {
            String oldPreset = currentPreset;
            Object selection = presetComboBox.getSelectedItem();

            // Load selected preset
            if (selection == null) {
                processorsListModel.setRowCount(0);
                updateUsedColors();
                currentPreset = null;
            } else if (selection instanceof Preset) {
                loadPreset((Preset) selection);
                currentPreset = ((Preset) selection).getTitle();
            }
            updatePresetEdited(false);
            if ((currentPreset == null && oldPreset != null) || (currentPreset != null && !currentPreset.equals(oldPreset))) {
                processFile();
            }
        }
    }

    private void onSavePreset() {
        String presetTitle = presetComboBox.getSelectedItem().toString();
        if (presetTitle.isEmpty()) {
            setStatus("Enter preset name first");
            return;
        }
        Preset preset = formatPreset(presetTitle);
        updatePreset(preset);
        loadPresetsList();
        updatePresetEdited(false);
        savePreferences();
    }

    private void updatePreset(Preset preset) {
        boolean addNew = true;
        for (int i = 0; i < presetsList.size(); i++) {
            if (preset.getTitle().equals(presetsList.get(i).getTitle())) {
                // Update existing preset
                addNew = false;
                presetsList.set(i, preset);
                setStatus("Preset \"" + preset.getTitle() + "\" updated");
                break;
            }
        }
        if (addNew) {
            // Add new preset
            presetsList.add(preset);
            currentPreset = preset.getTitle();
            setStatus("Preset \"" + preset.getTitle() + "\" added");
        }
    }

    private void onDeletePreset() {
        int i = presetComboBox.getSelectedIndex();
        Preset removedPreset = null;
        if (i > 0) {
            removedPreset = presetsList.remove(i - 1);
        }
        if (removedPreset != null) {
            loadPresetsList();
            setStatus("Preset \"" + removedPreset.getTitle() + "\" deleted");
            savePreferences();
        }
    }

    private void updatePresetEdited(boolean edited) {
        presetComboBox.getEditor().getEditorComponent().setForeground(edited ? Color.GRAY : Color.BLACK);

        if (edited && autosave) {
            updateAndSavePresets();
        }
    }

    private void loadPreferences() throws BackingStoreException {
        PreferencesUtil.loadWindowPreferences(mainFrame, preferences.node("MainWindow"), 800, 600);
        PreferencesUtil.loadWindowPreferences(fieldsListDialog, preferences.node("FieldsListDialog"), 300, 600);
        PreferencesUtil.loadWindowPreferences(addProcessorDialog, preferences.node("AddProcessorDialog"), -1, -1);
        PreferencesUtil.loadWindowPreferences(logInfo.getFrame(), preferences.node("LogInfoFrame"), 600, 600);
        String logDirectoryStr = preferences.get("LogDirectory", null);
        if (logDirectoryStr != null) {
            File dir = new File(logDirectoryStr);
            openLogFileChooser.setCurrentDirectory(dir);
        }
        String presetDirectoryStr = preferences.get("PresetDirectory", null);
        if (presetDirectoryStr != null) {
            lastPresetDirectory = new File(presetDirectoryStr);
        }
        Preferences presets = preferences.node("Presets");
        presetsList.clear();
        for (String p : presets.keys()) {
            try {
                Preset preset = Preset.unpackJSONObject(new JSONObject(presets.get(p, "{}")));
                if (preset != null) {
                    presetsList.add(preset);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        loadPresetsList();
        timeMode = Integer.parseInt(preferences.get("TimeMode", "0"));
        timeModeItems[timeMode].setSelected(true);
        autosave = preferences.getBoolean("Autosave", false);
        autosavePresets.setState(autosave);
        markerCheckBox.setSelected(preferences.getBoolean("ShowMarkers", false));
        trackExportDialog.loadPreferences(preferences);
        plotExportDialog.loadPreferences(preferences);
    }

    private void loadPresetsList() {
        Comparator<Preset> presetComparator = new Comparator<Preset>() {
            @Override
            public int compare(Preset o1, Preset o2) {
                return o1.getTitle().compareToIgnoreCase(o2.getTitle());
            }
        };
        Collections.sort(presetsList, presetComparator);

        // need to keep this because the change logic of the list will clean it
        String currentPresetCached = currentPreset;

        presetComboBox.removeAllItems();
        presetComboBox.addItem(null);
        Preset selectPreset = null;
        for (Preset preset : presetsList) {
            presetComboBox.addItem(preset);
            if (preset.getTitle().equals(currentPresetCached)) {
                currentPreset = currentPresetCached;
                selectPreset = preset;
            }
        }
        presetComboBox.setSelectedItem(selectPreset);
    }

    private void savePreferences() {
        try {
            preferences.clear();
            for (String child : preferences.childrenNames()) {
                preferences.node(child).removeNode();
            }
            PreferencesUtil.saveWindowPreferences(mainFrame, preferences.node("MainWindow"));
            PreferencesUtil.saveWindowPreferences(fieldsListDialog, preferences.node("FieldsListDialog"));
            PreferencesUtil.saveWindowPreferences(addProcessorDialog, preferences.node("AddProcessorDialog"));
            PreferencesUtil.saveWindowPreferences(logInfo.getFrame(), preferences.node("LogInfoFrame"));
            File lastLogDirectory = openLogFileChooser.getCurrentDirectory();
            if (lastLogDirectory != null) {
                preferences.put("LogDirectory", lastLogDirectory.getAbsolutePath());
            }
            if (lastPresetDirectory != null) {
                preferences.put("PresetDirectory", lastPresetDirectory.getAbsolutePath());
            }
            Preferences presetsPref = preferences.node("Presets");
            for (int i = 0; i < presetComboBox.getItemCount(); i++) {
                Object object = presetComboBox.getItemAt(i);
                if (object != null) {
                    Preset preset = (Preset) object;
                    try {
                        presetsPref.put(preset.getTitle(), preset.packJSONObject().toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            preferences.put("TimeMode", Integer.toString(timeMode));
            preferences.putBoolean("Autosave", autosave);
            preferences.putBoolean("ShowMarkers", markerCheckBox.isSelected());
            trackExportDialog.savePreferences(preferences);
            plotExportDialog.savePreferences(preferences);
            preferences.sync();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private void loadPreset(Preset preset) {
        processorsListModel.setRowCount(0);
        for (ProcessorPreset pp : preset.getProcessorPresets()) {
            updatePresetParameters(pp, null);
            processorsListModel.addRow(new Object[]{pp.isVisible(), pp.clone()});
        }
        updateUsedColors();
    }

    private Preset formatPreset(String title) {
        List<ProcessorPreset> processorPresets = new ArrayList<ProcessorPreset>();
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            processorPresets.add(((ProcessorPreset) processorsListModel.getValueAt(i, 1)).clone());
        }
        return new Preset(title, processorPresets);
    }

    private void createUIComponents() throws IllegalAccessException, InstantiationException {
        // Chart panel
        processorsTypesList = new ProcessorsList();
        dataset = new XYSeriesCollection();
        colorSupplier = new ColorSupplier();
        chart = ChartFactory.createXYLineChart("", "", "", null, PlotOrientation.VERTICAL, true, true, false);
        chart.getXYPlot().setDataset(dataset);

        // Set plot colors
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        plot.setRenderer(renderer);

        // Domain (X) axis - seconds
        domainAxisSeconds = new NumberAxis("T") {
            // Use default auto range to adjust range
            protected void autoAdjustRange() {
                setRange(getDefaultAutoRange());
            }
        };
        //domainAxisSeconds.setAutoRangeIncludesZero(false);
        domainAxisSeconds.setLowerMargin(0.0);
        domainAxisSeconds.setUpperMargin(0.0);

        // Domain (X) axis - date
        domainAxisDate = new DateAxis("T") {
            // Use default auto range to adjust range
            protected void autoAdjustRange() {
                setRange(getDefaultAutoRange());
            }
        };
        domainAxisDate.setTimeZone(TimeZone.getTimeZone("GMT"));
        domainAxisDate.setLowerMargin(0.0);
        domainAxisDate.setUpperMargin(0.0);

        // Use seconds by default
        plot.setDomainAxis(domainAxisSeconds);

        // Range (Y) axis
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);

        chartPanel = new ChartPanel(chart, false);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setMouseZoomable(true, false);
        chartPanel.setPopupMenu(null);
        chart.addChangeListener(new ChartChangeListener() {
            @Override
            public void chartChanged(ChartChangeEvent chartChangeEvent) {
                if (chartChangeEvent.getType() == ChartChangeEventType.GENERAL) {
                    Range timeRange = chart.getXYPlot().getDomainAxis().getRange();
                    if (!timeRange.equals(lastTimeRange)) {
                        lastTimeRange = timeRange;
                        processFile();
                    }
                }
            }
        });

        // Processors list
        processorsListModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }
        };
        processorsListModel.addColumn("");
        processorsListModel.addColumn("Processor");
        processorsList = new JTable(processorsListModel);
        processorsList.getColumnModel().getColumn(0).setMinWidth(20);
        processorsList.getColumnModel().getColumn(0).setMaxWidth(20);
        // Parameters table
        parametersTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 1;
            }
        };
        parametersTableModel.addColumn("Parameter");
        parametersTableModel.addColumn("Value");
        parametersTable = new JTable(parametersTableModel);
        parametersTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "startEditing");
        parametersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parametersTable.getColumnModel().getColumn(1).setCellEditor(new ParamValueTableCellEditor(this));
        parametersTable.getColumnModel().getColumn(1).setCellRenderer(new ParamValueTableCellRenderer());
        parametersTable.putClientProperty("JTable.autoStartsEdit", false);
        parametersTable.putClientProperty("terminateEditOnFocusLost", true);

        logsTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        logsTableModel.addColumn("Time");
        logsTableModel.addColumn("Level");
        logsTableModel.addColumn("Message");
        logTable = new JTable(logsTableModel);
        logTable.getColumnModel().getColumn(2).setMinWidth(350);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

    }

    private void createMenuBar() {
        // File menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem fileOpenItem = new JMenuItem("Open Log...");
        fileOpenItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOpenLogDialog();
            }
        });
        fileMenu.add(fileOpenItem);

        JMenuItem importPresetItem = new JMenuItem("Import Preset...");
        importPresetItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showImportPresetDialog();
            }
        });
        fileMenu.add(importPresetItem);

        JMenuItem exportPresetItem = new JMenuItem("Export Preset...");
        exportPresetItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportPresetDialog();
            }
        });
        fileMenu.add(exportPresetItem);

        autosavePresets = new JCheckBoxMenuItem("Autosave Presets");
        autosavePresets.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                autosave = autosavePresets.getState();
            }
        });
        fileMenu.add(autosavePresets);

        JMenuItem exportAsImageItem = new JMenuItem("Export As Image...");
        exportAsImageItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportAsImageDialog();
            }
        });
        fileMenu.add(exportAsImageItem);

        JMenuItem exportTrackItem = new JMenuItem("Export Track...");
        exportTrackItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportTrackDialog();
            }
        });
        fileMenu.add(exportTrackItem);

        JMenuItem exportParametersItem = new JMenuItem("Export Parameters...");
        exportParametersItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportParametersDialog();
            }
        });
        fileMenu.add(exportParametersItem);

        if (!OSValidator.isMac()) {
            fileMenu.add(new JPopupMenu.Separator());
            JMenuItem exitItem = new JMenuItem("Exit");
            exitItem.setAccelerator(KeyStroke.getKeyStroke('Q', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            exitItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    onQuit();
                }
            });
            fileMenu.add(exitItem);
        }

        // View menu
        JMenu viewMenu = new JMenu("View");
        timeModeItems = new JRadioButtonMenuItem[3];
        timeModeItems[TIME_MODE_LOG_START] = new JRadioButtonMenuItem("Log Start Time");
        timeModeItems[TIME_MODE_BOOT] = new JRadioButtonMenuItem("Boot Time");
        timeModeItems[TIME_MODE_GPS] = new JRadioButtonMenuItem("GPS Time");
        ButtonGroup timeModeGroup = new ButtonGroup();
        for (JRadioButtonMenuItem item : timeModeItems) {
            timeModeGroup.add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    onTimeModeChanged();
                    processFile();
                }
            });
            viewMenu.add(item);
        }

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        mainFrame.setJMenuBar(menuBar);
    }

    private void onTimeModeChanged() {
        int timeModeOld = timeMode;
        for (int i = 0; i < timeModeItems.length; i++) {
            if (timeModeItems[i].isSelected()) {
                timeMode = i;
                break;
            }
        }

        long timeOffset = 0;
        long logStart = 0;
        long logSize = 1000000;
        Range rangeOld = new Range(0.0, 1.0);

        if (logReader != null) {
            timeOffset = getTimeOffset(timeMode);
            logStart = logReader.getStartMicroseconds() + timeOffset;
            logSize = logReader.getSizeMicroseconds();
            if (logSize == 0) {
                logSize = 1000;
            }
            rangeOld = getLogRange(timeModeOld);
        }

        ValueAxis domainAxis = selectDomainAxis(timeMode);
        // Set axis type according to selected time mode
        chart.getXYPlot().setDomainAxis(0, domainAxis, false);

        if (domainAxis == domainAxisDate) {
            // DateAxis uses ms instead of seconds
            domainAxis.setRange(new Range(rangeOld.getLowerBound() * 1e3 + timeOffset * 1e-3,
                    rangeOld.getUpperBound() * 1e3 + timeOffset * 1e-3), true, false);
            domainAxis.setDefaultAutoRange(new Range(logStart * 1e-3, (logStart + logSize) * 1e-3));
        } else {
            domainAxis.setRange(new Range(rangeOld.getLowerBound() + timeOffset * 1e-6,
                    rangeOld.getUpperBound() + timeOffset * 1e-6), true, false);
            domainAxis.setDefaultAutoRange(new Range(logStart * 1e-6, (logStart + logSize) * 1e-6));
        }
    }

    /**
     * Displayed log range in seconds of native log time
     *
     * @param tm time mode
     * @return displayed log range [s]
     */
    private Range getLogRange(int tm) {
        Range range = selectDomainAxis(tm).getRange();
        if (tm == TIME_MODE_GPS) {
            long timeOffset = getTimeOffset(tm);
            return new Range((range.getLowerBound() * 1e3 - timeOffset) * 1e-6,
                    (range.getUpperBound() * 1e3 - timeOffset) * 1e-6);
        } else {
            long timeOffset = getTimeOffset(tm);
            return new Range(range.getLowerBound() - timeOffset * 1e-6, range.getUpperBound() - timeOffset * 1e-6);
        }
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void showOpenLogDialog() {
        int returnVal = openLogFileChooser.showDialog(mainFrame, "Open");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = openLogFileChooser.getSelectedFile();
            String logFileName = file.getPath();
            openLog(logFileName);
        }
    }

    private void openLog(String logFileName) {
        String logFileNameLower = logFileName.toLowerCase();
        LogReader logReaderNew;
        logsTableModel.setRowCount(0);
        try {
            if (logFileNameLower.endsWith(".bin") || logFileNameLower.endsWith(".px4log")) {
                logReaderNew = new PX4LogReader(logFileName);
            } else if (logFileNameLower.endsWith(".ulg")) {
                ULogReader ulogReader = new ULogReader(logFileName);
                logReaderNew = ulogReader;
                for (MessageLog loggedMsg : ulogReader.loggedMessages) {
                    long t = loggedMsg.timestamp / 1000;
                    String time = String.format("%2d:%02d:%03d", t / 1000 / 60, ((t / 1000) % 60), t % 1000);
                    logsTableModel.addRow(new Object[] { time, loggedMsg.getLevelStr(),
                            loggedMsg.message });
                }
            } else {
                setStatus("Log format not supported: " + logFileName);
                return;
            }
        } catch (Exception e) {
            setStatus("Error: " + e);
            e.printStackTrace();
            return;
        }

        mainFrame.setTitle(appNameAndVersion + " - " + logFileName);
        if (logReader != null) {
            try {
                logReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logReader = null;
        }
        logReader = logReaderNew;
        if (logReader.getErrors().size() > 0) {
            setStatus("Log file opened: " + logFileName + " (errors: " + logReader.getErrors().size() + ", see console output)");
            printLogErrors();
        } else {
            setStatus("Log file opened: " + logFileName);
        }
        logInfo.updateInfo(logReader);
        fieldsListDialog.setFieldsList(logReader.getFields());
        onTimeModeChanged();
        chart.getXYPlot().getDomainAxis().setAutoRange(true);
        chart.getXYPlot().getRangeAxis().setAutoRange(true);
        processFile();
    }

    public void showImportPresetDialog() {
        JFileChooser fc = new JFileChooser();
        if (lastPresetDirectory != null) {
            fc.setCurrentDirectory(lastPresetDirectory);
        }
        fc.setFileFilter(presetExtensionFilter);
        fc.setDialogTitle("Import Preset");
        int returnVal = fc.showDialog(mainFrame, "Import");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastPresetDirectory = fc.getCurrentDirectory();
            File file = fc.getSelectedFile();
            try {
                byte[] b = new byte[(int) file.length()];
                FileInputStream fileInputStream = new FileInputStream(file);
                int n = 0;
                while (n < b.length) {
                    int r = fileInputStream.read(b, n, b.length - n);
                    if (r <= 0) {
                        throw new Exception("Read error");
                    }
                    n += r;
                }
                Preset preset = Preset.unpackJSONObject(new JSONObject(new String(b, Charset.forName("utf8"))));
                loadPreset(preset);
                processFile();
            } catch (Exception e) {
                setStatus("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    public void showExportPresetDialog() {
        JFileChooser fc = new JFileChooser();
        if (lastPresetDirectory != null) {
            fc.setCurrentDirectory(lastPresetDirectory);
        }
        fc.setFileFilter(presetExtensionFilter);
        fc.setDialogTitle("Export Preset");
        int returnVal = fc.showDialog(mainFrame, "Export");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastPresetDirectory = fc.getCurrentDirectory();
            String fileName = fc.getSelectedFile().toString();
            if (presetExtensionFilter == fc.getFileFilter() && !fileName.toLowerCase().endsWith(".fplot")) {
                fileName += ".fplot";
            }
            try {
                Object item = presetComboBox.getSelectedItem();
                String presetTitle = item == null ? "" : item.toString();
                Preset preset = formatPreset(presetTitle);
                FileWriter fileWriter = new FileWriter(new File(fileName));
                fileWriter.write(preset.packJSONObject().toString(1));
                fileWriter.close();
                setStatus("Preset saved to: " + fileName);
            } catch (Exception e) {
                setStatus("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    public void showExportAsImageDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            plotExportDialog.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
            showExportTrackStatusMessage("Track could not be exported.");
        }
    }

    public void showExportTrackDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            trackExportDialog.display(logReader, getLogRange(timeMode));
        } catch (Exception e) {
            e.printStackTrace();
            showExportTrackStatusMessage("Track could not be exported.");
        }
    }

    private void showExportTrackStatusMessage(String message) {
        setStatus(String.format("Track export: %s", message));
    }

    public void showExportParametersDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(parametersExtensionFilter);
        fc.setDialogTitle("Export Parameters");
        int returnVal = fc.showDialog(mainFrame, "Export");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String fileName = fc.getSelectedFile().toString();
            if (parametersExtensionFilter == fc.getFileFilter() && !fileName.toLowerCase().endsWith(".txt")) {
                fileName += ".txt";
            }
            try {
                FileWriter fileWriter = new FileWriter(new File(fileName));
                List<Map.Entry<String, Object>> paramsList = new ArrayList<Map.Entry<String, Object>>(logReader.getParameters().entrySet());
                Collections.sort(paramsList, new Comparator<Map.Entry<String, Object>>() {
                    @Override
                    public int compare(Map.Entry<String, Object> o1, Map.Entry<String, Object> o2) {
                        return o1.getKey().compareTo(o2.getKey());
                    }
                });
                for (Map.Entry<String, Object> param : paramsList) {
                    int typeID = 0;
                    Object value = param.getValue();
                    if (value instanceof Float) {
                        typeID = 1;
                    }
                    fileWriter.write(String.format("%s\t%s\t%s\n", param.getKey(), typeID, param.getValue()));
                }
                fileWriter.close();
            } catch (Exception e) {
                setStatus("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    private void processFile() {
        if (logReader != null) {
            if (invokeProcessFile.compareAndSet(false, true)) {
                final boolean notEmptyPlot = (getActiveProcessors().size() > 0);
                if (notEmptyPlot) {
                    setStatus("Processing...");
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            generateSeries();
                            if (notEmptyPlot) {
                                if (logReader.getErrors().size() > 0) {
                                    setStatus("Log parsing errors, see console output");
                                    printLogErrors();
                                } else {
                                    setStatus(" ");
                                }
                            }
                        } catch (Exception e) {
                            setStatus("Error: " + e);
                            e.printStackTrace();
                        }
                        invokeProcessFile.lazySet(false);
                    }
                });
            }
        }
    }

    private void printLogErrors() {
        System.err.println("Log parsing errors:");
        int maxErrors = 100;
        for (Exception e : logReader.getErrors().subList(0, Math.min(logReader.getErrors().size(), maxErrors))) {
            System.err.println("\t" + e.getMessage());
        }
        if (logReader.getErrors().size() > maxErrors) {
            System.err.println("\t...");
        }
    }

    private long getTimeOffset(int tm) {
        // Set time offset according t selected time mode
        long timeOffset = 0;
        if (tm == TIME_MODE_GPS) {
            // GPS time
            timeOffset = logReader.getUTCTimeReferenceMicroseconds();
            if (timeOffset < 0) {
                timeOffset = 0;
            }
        } else if (tm == TIME_MODE_LOG_START) {
            // Log start time
            timeOffset = -logReader.getStartMicroseconds();
        }
        return timeOffset;
    }

    private ValueAxis selectDomainAxis(int tm) {
        if (tm == TIME_MODE_GPS) {
            return domainAxisDate;
        } else {
            return domainAxisSeconds;
        }
    }

    private List<ProcessorPreset> getActiveProcessors() {
        List<ProcessorPreset> processors = new ArrayList<ProcessorPreset>();
        for (int row = 0; row < processorsListModel.getRowCount(); row++) {
            ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(row, 1);
            if ((Boolean) processorsListModel.getValueAt(row, 0)) {
                processors.add(pp);
            }
        }
        return processors;
    }

    private void generateSeries() throws IOException, FormatErrorException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        activeProcessors.clear();
        activeProcessors.addAll(getActiveProcessors());

        dataset.removeAllSeries();
        seriesIndex.clear();
        PlotProcessor[] processors = new PlotProcessor[activeProcessors.size()];

        // Update time offset according to selected time mode
        long timeOffset = getTimeOffset(timeMode);

        // Displayed log range in seconds of native log time
        Range range = getLogRange(timeMode);

        // Process some extra data in hidden areas
        long timeStart = (long) ((range.getLowerBound() - range.getLength()) * 1e6);
        long timeStop = (long) ((range.getUpperBound() + range.getLength()) * 1e6);
        timeStart = Math.max(logReader.getStartMicroseconds(), timeStart);
        timeStop = Math.min(logReader.getStartMicroseconds() + logReader.getSizeMicroseconds(), timeStop);

        double timeScale = (selectDomainAxis(timeMode) == domainAxisDate) ? 1000.0 : 1.0;

        int displayPixels = 2000;
        double skip = range.getLength() / displayPixels;
        if (processors.length > 0) {
            for (int i = 0; i < activeProcessors.size(); i++) {
                ProcessorPreset pp = activeProcessors.get(i);
                PlotProcessor processor;
                processor = processorsTypesList.getProcessorInstance(pp, skip, logReader.getFields());
                processor.setFieldsList(logReader.getFields());
                processors[i] = processor;
            }
            logReader.seek(timeStart);
            logReader.clearErrors();
            Map<String, Object> data = new HashMap<String, Object>();
            while (true) {
                long t;
                data.clear();
                try {
                    t = logReader.readUpdate(data);
                } catch (EOFException e) {
                    break;
                }
                if (t > timeStop) {
                    break;
                }
                for (PlotProcessor processor : processors) {
                    processor.process((t + timeOffset) * 1e-6, data);
                }
            }
            chart.getXYPlot().clearDomainMarkers();

            if (logReader instanceof ULogReader) {
                Map<String, List<ULogReader.ParamUpdate>> updateMap = ((ULogReader) logReader).parameterUpdates;
                for (List<ULogReader.ParamUpdate> updList: updateMap.values()) {
                    for (ULogReader.ParamUpdate upd: updList) {
                        // add a labelled marker for each parameter update
                        double mTime = (upd.getTimestamp() + timeOffset) * 1e-6;
                        if (timeMode == TIME_MODE_GPS) {
                            // time axis is msec instead of sec
                            mTime *= 1e3;
                        }
                        final org.jfree.chart.plot.Marker updMarker = new ValueMarker(mTime);
                        updMarker.setPaint(Color.black);
                        updMarker.setLabel(upd.getName() + ":" + upd.getValue());
                        updMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
                        updMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
                        chart.getXYPlot().addDomainMarker(updMarker);
                    }
                }
            }

            for (int i = 0; i < activeProcessors.size(); i++) {
                PlotProcessor processor = processors[i];
                String processorTitle = activeProcessors.get(i).getTitle();
                Map<String, Integer> processorSeriesIndex = new HashMap<String, Integer>();
                seriesIndex.add(processorSeriesIndex);
                for (PlotItem item : processor.getSeriesList()) {
                    if (item instanceof Series) {
                        Series series = (Series) item;
                        processorSeriesIndex.put(series.getTitle(), dataset.getSeriesCount());
                        XYSeries jseries = new XYSeries(series.getFullTitle(processorTitle), false);
                        for (XYPoint point : series) {
                            jseries.add(point.x * timeScale, point.y, false);
                        }
                        dataset.addSeries(jseries);
                    } else if (item instanceof MarkersList) {
                        MarkersList markers = (MarkersList) item;
                        processorSeriesIndex.put(markers.getTitle(), dataset.getSeriesCount());
                        XYSeries jseries = new XYSeries(markers.getFullTitle(processorTitle), false);
                        dataset.addSeries(jseries);
                        for (Marker marker : markers) {
                            TaggedValueMarker m = new TaggedValueMarker(i, marker.x * timeScale);
                            m.setPaint(Color.black);
                            m.setLabel(marker.label);
                            m.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
                            m.setLabelTextAnchor(TextAnchor.TOP_LEFT);
                            chart.getXYPlot().addDomainMarker(0, m, Layer.BACKGROUND, false);
                        }
                    }
                }
            }
            setChartColors();
            setChartMarkers();
        }
        chartPanel.repaint();
    }

    private void setChartColors() {
        if (dataset.getSeriesCount() > 0) {
            Collection<ValueMarker> markers = chart.getXYPlot().getDomainMarkers(0, Layer.BACKGROUND);
            for (int i = 0; i < activeProcessors.size(); i++) {
                for (Map.Entry<String, Integer> entry : seriesIndex.get(i).entrySet()) {
                    ProcessorPreset processorPreset = activeProcessors.get(i);
                    AbstractRenderer renderer = (AbstractRenderer) chart.getXYPlot().getRendererForDataset(dataset);
                    Paint color = processorPreset.getColors().get(entry.getKey());
                    renderer.setSeriesPaint(entry.getValue(), color, true);
                    if (markers != null) {
                        for (ValueMarker marker : markers) {
                            if (((TaggedValueMarker) marker).tag == i) {
                                marker.setPaint(color);
                            }
                        }
                    }
                }
            }
        }
    }

    private void setChartMarkers() {
        if (dataset.getSeriesCount() > 0) {
            boolean showMarkers = markerCheckBox.isSelected();
            Shape marker = new Ellipse2D.Double(-1.5, -1.5, 3, 3);
            Object renderer = chart.getXYPlot().getRendererForDataset(dataset);
            if (renderer instanceof XYLineAndShapeRenderer) {
                for (int j = 0; j < dataset.getSeriesCount(); j++) {
                    if (showMarkers) {
                        ((XYLineAndShapeRenderer) renderer).setSeriesShape(j, marker, false);
                    }
                    ((XYLineAndShapeRenderer) renderer).setSeriesShapesVisible(j, showMarkers);
                }
            }
        }
    }

    private void showAddProcessorDialog(boolean editMode) {
        ProcessorPreset selectedProcessor = editMode ? getSelectedProcessor() : null;
        addProcessorDialog.display(new Runnable() {
            @Override
            public void run() {
                onAddProcessorDialogOK();
            }
        }, selectedProcessor);
    }

    private void onAddProcessorDialogOK() {
        ProcessorPreset processorPreset = addProcessorDialog.getOrigProcessorPreset();
        String title = addProcessorDialog.getProcessorTitle();
        String processorType = addProcessorDialog.getProcessorType();
        if (processorPreset != null) {
            // Edit processor
            ProcessorPreset processorPresetNew = processorPreset;
            if (!processorPreset.getProcessorType().equals(processorType)) {
                // Processor type changed
                Map<String, Object> parameters = processorPreset.getParameters();
                processorPresetNew = new ProcessorPreset(title, processorType, new HashMap<String, Object>(), Collections.<String, Color>emptyMap(), true);
                updatePresetParameters(processorPresetNew, parameters);
                for (int row = 0; row < processorsListModel.getRowCount(); row++) {
                    if (processorsListModel.getValueAt(row, 1) == processorPreset) {
                        processorsListModel.setValueAt(processorPresetNew, row, 1);
                        processorsList.setRowSelectionInterval(row, row);
                        break;
                    }
                }
                showProcessorParameters();
            } else {
                // Only change title
                processorPresetNew.setTitle(title);
            }
        } else {
            processorPreset = new ProcessorPreset(title, processorType, Collections.<String, Object>emptyMap(), Collections.<String, Color>emptyMap(), true);
            updatePresetParameters(processorPreset, null);
            int i = processorsListModel.getRowCount();
            processorsListModel.addRow(new Object[]{true, processorPreset});
            processorsList.setRowSelectionInterval(i, i);
        }
        updateUsedColors();
        updatePresetEdited(true);
        processFile();
    }

    private void updatePresetParameters(ProcessorPreset processorPreset, Map<String, Object> parametersUpdate) {
        if (parametersUpdate != null) {
            // Update parameters of preset
            processorPreset.getParameters().putAll(parametersUpdate);
        }
        // Construct and initialize processor to cleanup parameters list and get list of series
        PlotProcessor p;
        try {
            p = processorsTypesList.getProcessorInstance(processorPreset, 0.0, null);
        } catch (Exception e) {
            setStatus("Error in processor \"" + processorPreset + "\"");
            e.printStackTrace();
            return;
        }
        processorPreset.setParameters(p.getParameters());
        Map<String, Color> colorsNew = new HashMap<String, Color>();
        for (PlotItem series : p.getSeriesList()) {
            Color color = processorPreset.getColors().get(series.getTitle());
            if (color == null) {
                color = colorSupplier.getNextColor(series.getTitle());
            }
            colorsNew.put(series.getTitle(), color);
        }
        processorPreset.setColors(colorsNew);
    }

    private void removeSelectedProcessor() {
        ProcessorPreset selectedProcessor = getSelectedProcessor();
        if (selectedProcessor != null) {
            int row = processorsList.getSelectedRow();
            processorsListModel.removeRow(row);
            updatePresetEdited(true);
            updateUsedColors();
            processFile();
        }
    }

    private void updateUsedColors() {
        colorSupplier.resetColorsUsed();
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(i, 1);
            for (Color color : pp.getColors().values()) {
                colorSupplier.markColorUsed(color);
            }
        }
    }

    private ProcessorPreset getSelectedProcessor() {
        int row = processorsList.getSelectedRow();
        return row < 0 ? null : (ProcessorPreset) processorsListModel.getValueAt(row, 1);
    }

    private void showProcessorParameters() {
        while (parametersTableModel.getRowCount() > 0) {
            parametersTableModel.removeRow(0);
        }
        ProcessorPreset selectedProcessor = getSelectedProcessor();
        if (selectedProcessor != null) {
            // Parameters
            Map<String, Object> params = selectedProcessor.getParameters();
            List<String> param_keys = new ArrayList<String>(params.keySet());
            Collections.sort(param_keys);
            for (String key : param_keys) {
                parametersTableModel.addRow(new Object[]{key, formatParameterValue(params.get(key))});
            }
            // Colors
            Map<String, Color> colors = selectedProcessor.getColors();
            List<String> color_keys = new ArrayList<String>(colors.keySet());
            Collections.sort(color_keys);
            for (String key : color_keys) {
                parametersTableModel.addRow(new Object[]{colorParamPrefix + key, colors.get(key)});
            }
        }
    }

    private void onParameterChanged(int row) {
        boolean changed = false;
        if (editingProcessor != null && editingProcessor == getSelectedProcessor()) {
            String key = parametersTableModel.getValueAt(row, 0).toString();
            Object value = parametersTableModel.getValueAt(row, 1);
            if (value instanceof Color) {
                editingProcessor.getColors().put(key.substring(colorParamPrefix.length(), key.length()), (Color) value);
                setChartColors();
            }
            try {
                updatePresetParameters(editingProcessor, Collections.<String, Object>singletonMap(key, value.toString()));
                changed = true;
            } catch (Exception e) {
                e.printStackTrace();
                setStatus("Error: " + e);
            }
            if (!(value instanceof Color)) {
                parametersTableModel.removeTableModelListener(parameterChangedListener);
                showProcessorParameters(); // refresh all parameters because changing one param might influence others (e.g. color)
                parametersTableModel.addTableModelListener(parameterChangedListener);
                parametersTable.addRowSelectionInterval(row, row);
                processFile();
            }
        }

        if (changed) {
            updatePresetEdited(true);
        }
    }

    ColorSupplier getColorSupplier() {
        return colorSupplier;
    }

    void setEditingProcessor() {
        editingProcessor = getSelectedProcessor();
    }

    public JFreeChart getChart() {
        return chart;
    }
}
