package com.tencent.qgame.playerproj.animtool.ui;

import com.tencent.qgame.playerproj.animtool.AnimTool;
import com.tencent.qgame.playerproj.animtool.CommonArg;
import com.tencent.qgame.playerproj.animtool.TLog;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

public class ToolUI {

    private static final String TAG = "ToolUI";
    private static final String TOOL_VERSION = "VAP 工具 2.0.6";
    private static final String PROPERTIES_FILE = "setting.properties";
    private static final String APP_SUPPORT_DIR = "VapToolMac";
    private static final String DEFAULTS_VERSION = "20260618-defaults-v3";
    private static final String CODEC_H264 = "h264";
    private static final String CODEC_H265 = "h265";
    private static final String CODEC_BOTH = "both";
    private static final float H265_BOTH_MODE_BITRATE_RATIO = 0.6f;
    public static final int WIDTH = 900;
    public static final int HEIGHT = 750;

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private final JFrame frame = new JFrame(TOOL_VERSION);
    private final ButtonGroup group = new ButtonGroup();
    private final JRadioButton btnH264 = new JRadioButton("H.264");
    private final JRadioButton btnH265 = new JRadioButton("H.265");
    private final JRadioButton btnBoth = new JRadioButton("H.264 + H.265");
    private final SpinnerModel modelFps = new SpinnerNumberModel(25, 1, 60, 1);
    private final Float[] scaleArray = new Float[]{0.5f, 1f};
    private final JComboBox<Float> boxScale = new JComboBox<>(scaleArray);
    private final JTextField textInputPath = new JTextField();
    private final JButton btnCreate = new JButton("生成 VAP");
    private final JTextArea txtAreaLog = new JTextArea();
    private final JTextField textAudioPath = new JTextField();
    private final JPanel panelAudioPath = new JPanel();

    private final JPanel panelBitrate = new JPanel();
    private final JTextField textBitrate = new JTextField();
    private final JPanel panelCrf = new JPanel();
    private final JTextField textCrf = new JTextField();

    private final ButtonGroup groupQuality = new ButtonGroup();
    private final JRadioButton btnBitrate = new JRadioButton("固定码率");
    private final JRadioButton btnCrf = new JRadioButton("CRF 质量");

    private final JLabel labelOutInfo = new JLabel();
    private final Dimension labelSize = new Dimension(120, 20);
    private final Properties props = new Properties();
    private final VapxUI vapxUI = new VapxUI(this);

    private boolean needAudio = false;

    private final ItemListener qualityGroupListener = new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent itemEvent) {
            if (itemEvent.getSource() == btnBitrate) {
                panelBitrate.setVisible(true);
                panelCrf.setVisible(false);
            } else if (itemEvent.getSource() == btnCrf) {
                panelBitrate.setVisible(false);
                panelCrf.setVisible(true);
            }
        }
    };

    public ToolUI() {
        TLog.logger = new TLog.ITLog() {
            @Override
            public void i(String tag, String msg) {
                log(tag, msg);
            }

            @Override
            public void e(String tag, String msg) {
                log(tag, "错误：" + msg);
            }

            @Override
            public void w(String tag, String msg) {
                log(tag, "警告：" + msg);
            }
        };
    }


    public void run() {
        createUI();
        loadProperties();
    }

    private void loadProperties() {
        try {
            File file = getReadablePropertiesFile();
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            }
            props.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            migrateDefaultProperties();
            CommonArg commonArg = getProperties();
            String codecMode = props.getProperty("codecMode", CODEC_BOTH);
            if (CODEC_BOTH.equals(codecMode)) {
                group.setSelected(btnBoth.getModel(), true);
            } else {
                group.setSelected(commonArg.enableH265 ? btnH265.getModel() : btnH264.getModel(), true);
            }
            modelFps.setValue(commonArg.fps);
            textInputPath.setText(commonArg.inputPath);
            textAudioPath.setText(commonArg.audioPath);
            textBitrate.setText(String.valueOf(commonArg.bitrate));
            textCrf.setText(String.valueOf(commonArg.crf));
            groupQuality.setSelected(commonArg.enableCrf ? btnCrf.getModel() : btnBitrate.getModel(), true);
            if (commonArg.enableCrf) {
                panelBitrate.setVisible(false);
                panelCrf.setVisible(true);
            } else {
                panelBitrate.setVisible(true);
                panelCrf.setVisible(false);
            }

            float scale = commonArg.scale;
            for (int i = 0; i < scaleArray.length ; i++) {
                if (scaleArray[i] == scale) {
                    boxScale.setSelectedIndex(i);
                    break;
                }
            }
        } catch (Exception e) {
            TLog.e(TAG, e.getMessage());
        }
    }

    private void migrateDefaultProperties() {
        if (DEFAULTS_VERSION.equals(props.getProperty("defaultsVersion"))) {
            return;
        }
        CommonArg commonArg = new CommonArg();
        props.setProperty("defaultsVersion", DEFAULTS_VERSION);
        props.setProperty("codecMode", CODEC_BOTH);
        props.setProperty("enableH265", Boolean.FALSE.toString());
        props.setProperty("fps", String.valueOf(commonArg.fps));
        props.setProperty("scale", String.valueOf(commonArg.scale));
        props.setProperty("bitrate", String.valueOf(commonArg.bitrate));
        props.setProperty("enableCrf", Boolean.TRUE.toString());
        props.setProperty("crf", String.valueOf(commonArg.crf));
    }

    private File getReadablePropertiesFile() {
        File appSupportFile = getWritablePropertiesFile();
        if (appSupportFile.exists()) {
            return appSupportFile;
        }

        File legacyFile = new File(PROPERTIES_FILE);
        if (legacyFile.exists() && legacyFile.canRead()) {
            return legacyFile;
        }
        return appSupportFile;
    }

    private File getWritablePropertiesFile() {
        File appSupportDir = new File(System.getProperty("user.home"),
                "Library" + File.separator + "Application Support" + File.separator + APP_SUPPORT_DIR);
        return new File(appSupportDir, PROPERTIES_FILE);
    }

    private void runTool() {
        txtAreaLog.setText("");
        TLog.i(TAG, TOOL_VERSION);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runAnimTool();
                } catch (Exception e) {
                    TLog.e(TAG, e.getMessage());
                    setOutput(false, "");
                }
            }
        }).start();
    }

    private void runAnimTool() throws Exception {
        String codecMode = getSelectedCodecMode();
        btnCreate.setEnabled(false);

        if (CODEC_BOTH.equals(codecMode)) {
            CommonArg h264Arg = buildCommonArg(false, CODEC_H264);
            if (h264Arg == null) {
                setOutput(false, "");
                return;
            }
            TLog.i(TAG, "开始生成 H.264");
            boolean h264Result = runAnimToolAndWait(h264Arg, "H.264");
            if (!h264Result) {
                setOutput(false, "");
                return;
            }

            CommonArg h265Arg = buildCommonArg(true, CODEC_H265);
            if (h265Arg == null) {
                setOutput(false, "");
                return;
            }
            if (!h265Arg.enableCrf) {
                h265Arg.bitrate = Math.max(1, Math.round(h265Arg.bitrate * H265_BOTH_MODE_BITRATE_RATIO));
                TLog.i(TAG, "同时输出模式下，H.265 使用 H.264 码率的 60%，当前码率：" + h265Arg.bitrate + "k");
            }
            TLog.i(TAG, "开始生成 H.265");
            boolean h265Result = runAnimToolAndWait(h265Arg, "H.265");
            if (!h265Result) {
                setOutput(false, "");
                return;
            }

            setProperties(h265Arg, codecMode);
            String outputRoot = getOutputRootPath(h265Arg.inputPath);
            setOutput(true, outputRoot);
            Desktop.getDesktop().open(new File(outputRoot));
            return;
        }

        boolean enableH265 = CODEC_H265.equals(codecMode);
        CommonArg commonArg = buildCommonArg(enableH265, null);
        if (commonArg == null) {
            setOutput(false, "");
            return;
        }
        boolean result = runAnimToolAndWait(commonArg, enableH265 ? "H.265" : "H.264");
        if (result) {
            setProperties(commonArg, codecMode);
            setOutput(true, commonArg.outputPath);
            Desktop.getDesktop().open(new File(commonArg.outputPath));
        } else {
            setOutput(false, "");
        }
    }

    private CommonArg buildCommonArg(boolean enableH265, String outputDirName) {
        final CommonArg commonArg = new CommonArg();
        String os = System.getProperty("os.name").toLowerCase();

        commonArg.ffmpegCmd = "ffmpeg";
        commonArg.mp4editCmd = "mp4edit";

        if (os != null && !"".equals(os)) {
            File macToolDir = findBundledToolDir("mac");
            if (os.contains("mac") && macToolDir != null) {
                commonArg.ffmpegCmd = new File(macToolDir, "ffmpeg").getPath();
                commonArg.mp4editCmd = new File(macToolDir, "mp4edit").getPath();
            } else if (os.contains("windows") && new File("win").exists()) {
                commonArg.ffmpegCmd = "win/ffmpeg";
                commonArg.mp4editCmd = "win/mp4edit";
            }
        }
        commonArg.enableH265 = enableH265;
        commonArg.outputDirName = outputDirName;
        commonArg.fps = (Integer)modelFps.getValue();
        commonArg.inputPath = textInputPath.getText();
        commonArg.scale = scaleArray[boxScale.getSelectedIndex()];
        if (needAudio) {
            commonArg.needAudio = true;
            commonArg.audioPath = textAudioPath.getText();
        }

        if (vapxUI.isVapxEnable()) {
            commonArg.isVapx = true;
            commonArg.srcSet = vapxUI.getSrcSet();
            if (commonArg.srcSet == null) {
                return null;
            }
        }
        try {
            commonArg.enableCrf = groupQuality.isSelected(btnCrf.getModel());
            commonArg.bitrate = Integer.parseInt(textBitrate.getText());
            commonArg.crf = Integer.parseInt(textCrf.getText());
        } catch (NumberFormatException e) {
            TLog.e(TAG, "码率或 CRF 格式错误：" + textBitrate.getText() + " " + e.getMessage());
        }

        TLog.i(TAG, commonArg.toString());
        return commonArg;
    }

    private boolean runAnimToolAndWait(final CommonArg commonArg, final String progressLabel) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        AnimTool animTool = new AnimTool();
        animTool.setToolListener(new AnimTool.IToolListener() {
            @Override
            public void onProgress(float progress) {
                int p = (int)(progress * 100f);
                labelOutInfo.setText(progressLabel + " " + (Math.min(p, 99)) + "%");
            }

            @Override
            public void onWarning(String msg) {
                JOptionPane.showMessageDialog(frame, msg, "警告", JOptionPane.WARNING_MESSAGE);
            }

            @Override
            public void onError() {
                success.set(false);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                success.set(true);
                latch.countDown();
            }
        });
        animTool.create(commonArg, true);
        latch.await();
        return success.get();
    }

    private String getSelectedCodecMode() {
        if (group.isSelected(btnBoth.getModel())) {
            return CODEC_BOTH;
        }
        if (group.isSelected(btnH265.getModel())) {
            return CODEC_H265;
        }
        return CODEC_H264;
    }

    private String getOutputRootPath(String inputPath) {
        if (inputPath == null || inputPath.length() == 0) {
            return AnimTool.OUTPUT_DIR;
        }
        String path = inputPath;
        if (!File.separator.equals(path.substring(path.length() - 1))) {
            path = path + File.separator;
        }
        return path + AnimTool.OUTPUT_DIR;
    }

    private void createUI() {
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        int w = (Toolkit.getDefaultToolkit().getScreenSize().width - WIDTH) / 2;
        int h = (Toolkit.getDefaultToolkit().getScreenSize().height - HEIGHT) / 2;
        frame.setLocation(w, h);

        JPanel panel = new JPanel();
        frame.add(panel);
        layout(panel);
        frame.setVisible(true);
    }

    public String getInputPath() {
        return textInputPath.getText();
    }

    private void layout(JPanel panel) {
        BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
        panel.setLayout(layout);
        // 编码格式
        panel.add(getCodecLayout());
        // 帧率
        panel.add(getFpsLayout());
        // 质量模式
        panel.add(getQualityLayout());
        // 码率
        panel.add(getBitrateLayout());
        // CRF
        panel.add(getCrfLayout());
        // alpha 缩放
        panel.add(getScaleLayout());
        // 帧目录
        panel.add(getPathLayout());
        // 音频文件
        panel.add(getAudioPathLayout());
        // 融合动画
        panel.add(vapxUI.createUI());
        // 生成
        panel.add(getCreateLayout());
        // 日志
        panel.add(getLogLayout());
        // 开源许可
        panel.add(getOpenSourceLayout());

    }

    private JPanel getCodecLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));

        JLabel label = new JLabel("编码格式");
        label.setPreferredSize(labelSize);
        panel.add(label);

        JPanel panelRadio = new JPanel();
        panelRadio.setLayout(new GridLayout(1, 3));
        panelRadio.add(btnH264);
        panelRadio.add(btnH265);
        panelRadio.add(btnBoth);
        group.add(btnH264);
        group.add(btnH265);
        group.add(btnBoth);
        group.setSelected(btnBoth.getModel(), true);
        panel.add(panelRadio);

        return panel;
    }

    private JPanel getFpsLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("帧率");
        label.setPreferredSize(labelSize);
        panel.add(label);
        JSpinner spinner = new JSpinner(modelFps);
        spinner.setPreferredSize(new Dimension(60, 20));
        panel.add(spinner);
        return panel;
    }

    private JPanel getQualityLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));

        JLabel label = new JLabel("质量模式");
        label.setPreferredSize(labelSize);
        panel.add(label);

        JPanel panelRadio = new JPanel();
        panelRadio.setLayout(new GridLayout(1, 2));
        panelRadio.add(btnBitrate);
        panelRadio.add(btnCrf);
        groupQuality.add(btnBitrate);
        groupQuality.add(btnCrf);
        groupQuality.setSelected(btnCrf.getModel(), true);
        panelBitrate.setVisible(false);
        panelCrf.setVisible(true);
        btnBitrate.addItemListener(qualityGroupListener);
        btnCrf.addItemListener(qualityGroupListener);
        panel.add(panelRadio);

        return panel;
    }

    private JPanel getBitrateLayout() {
        panelBitrate.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("码率");
        label.setPreferredSize(labelSize);
        panelBitrate.add(label);
        textBitrate.setPreferredSize(new Dimension(60, 20));
        panelBitrate.add(textBitrate);
        panelBitrate.add(new JLabel("k（默认 2000k）"));
        return panelBitrate;
    }

    private JPanel getCrfLayout() {
        panelCrf.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("CRF");
        label.setPreferredSize(labelSize);
        panelCrf.add(label);
        textCrf.setPreferredSize(new Dimension(60, 20));
        panelCrf.add(textCrf);
        panelCrf.add(new JLabel("[0, 51]（默认 29，数值越小质量越高）"));
        return panelCrf;
    }

    private JPanel getScaleLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("Alpha 缩放");
        label.setPreferredSize(labelSize);
        panel.add(label);
        panel.add(boxScale);
        panel.add(new JLabel("（默认 1.0）"));
        return panel;
    }

    private JPanel getPathLayout() {
        JPanel panel = new JPanel();

        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("帧目录");
        label.setPreferredSize(labelSize);
        panel.add(label);
        JPanel gPanel = new JPanel();
        panel.add(gPanel);

        BoxLayout layout = new BoxLayout(gPanel, BoxLayout.LINE_AXIS);
        gPanel.setLayout(layout);

        textInputPath.setPreferredSize(new Dimension(400,20));
        gPanel.add(textInputPath);

        JButton btnInputPath = new JButton("选择");
        gPanel.add(btnInputPath);
        btnInputPath.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                File file = chooseDirectory("选择帧目录", getInputPath());
                if(file != null) {
                    String filePath = file.getAbsolutePath();
                    textInputPath.setText(filePath);
                }
            }
        });

        return panel;
    }


    private JPanel getAudioPathLayout() {
        JPanel panel = new JPanel();

        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("音频（mp3）");
        label.setPreferredSize(labelSize);
        panel.add(label);
        panel.add(panelAudioPath);
        final JLabel labelAudioAction = new JLabel("+");
        panel.add(labelAudioAction);
        labelAudioAction.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                needAudio = !needAudio;
                panelAudioPath.setVisible(needAudio);
                labelAudioAction.setText(needAudio ? "移除" : "+");
            }
        });

        BoxLayout layout = new BoxLayout(panelAudioPath, BoxLayout.LINE_AXIS);
        panelAudioPath.setLayout(layout);

        textAudioPath.setPreferredSize(new Dimension(400,20));
        panelAudioPath.add(textAudioPath);

        JButton btnInputPath = new JButton("选择");
        panelAudioPath.add(btnInputPath);
        btnInputPath.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                File file = chooseFile("选择音频文件", getInputPath());
                if(file != null) {
                    String filePath = file.getAbsolutePath();
                    textAudioPath.setText(filePath);
                }
            }
        });

        if (!needAudio) {
            panelAudioPath.setVisible(false);
        }

        return panel;
    }

    private void setOutput(boolean success, final String path) {
        btnCreate.setEnabled(true);
        if (success) {
            labelOutInfo.setText("<html><font color='blue'>打开输出目录</font></html>");
            labelOutInfo.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent mouseEvent) {
                    try {
                        Desktop.getDesktop().open(new File(path));
                    } catch (IOException e) {
                        TLog.e(TAG, e.getMessage());
                    }
                }
            });
        } else {
            labelOutInfo.setText("<html><font color='red'>生成失败</font></html>");
        }
    }

    private File findBundledToolDir(String dirName) {
        File workingDirTool = new File(dirName);
        if (workingDirTool.exists()) {
            return workingDirTool;
        }

        try {
            File codeSource = new File(ToolUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File appDir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            File bundledTool = new File(appDir, dirName);
            if (bundledTool.exists()) {
                return bundledTool;
            }
            File parentDir = appDir.getParentFile();
            if (parentDir != null) {
                bundledTool = new File(parentDir, dirName);
                if (bundledTool.exists()) {
                    return bundledTool;
                }
            }
        } catch (Exception e) {
            TLog.e(TAG, "查找内置工具目录失败：" + e.getMessage());
        }
        return null;
    }

    public File chooseDirectory(String title, String currentPath) {
        if (isMac()) {
            String oldValue = System.getProperty("apple.awt.fileDialogForDirectories");
            try {
                System.setProperty("apple.awt.fileDialogForDirectories", "true");
                FileDialog dialog = new FileDialog(frame, title, FileDialog.LOAD);
                setDialogDirectory(dialog, currentPath);
                dialog.setVisible(true);
                if (dialog.getFile() != null) {
                    return new File(dialog.getDirectory(), dialog.getFile());
                }
            } finally {
                if (oldValue == null) {
                    System.clearProperty("apple.awt.fileDialogForDirectories");
                } else {
                    System.setProperty("apple.awt.fileDialogForDirectories", oldValue);
                }
            }
        }
        return chooseWithSwing(title, currentPath, JFileChooser.DIRECTORIES_ONLY);
    }

    public File chooseFile(String title, String currentPath) {
        if (isMac()) {
            FileDialog dialog = new FileDialog(frame, title, FileDialog.LOAD);
            setDialogDirectory(dialog, currentPath);
            dialog.setVisible(true);
            if (dialog.getFile() != null) {
                return new File(dialog.getDirectory(), dialog.getFile());
            }
        }
        return chooseWithSwing(title, currentPath, JFileChooser.FILES_ONLY);
    }

    private File chooseWithSwing(String title, String currentPath, int mode) {
        JFileChooser fileChooser = new JFileChooser(getInitialChooserDir(currentPath));
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(mode);
        int returnVal = fileChooser.showOpenDialog(frame);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }

    private void setDialogDirectory(FileDialog dialog, String currentPath) {
        File initialDir = getInitialChooserDir(currentPath);
        if (initialDir != null) {
            dialog.setDirectory(initialDir.getAbsolutePath());
        }
    }

    private File getInitialChooserDir(String currentPath) {
        if (currentPath != null && currentPath.length() > 0) {
            File file = new File(currentPath);
            if (file.isFile()) {
                return file.getParentFile();
            }
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private boolean isMac() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("mac");
    }


    private JPanel getCreateLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        panel.add(btnCreate);
        btnCreate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                runTool();
            }
        });

        panel.add(labelOutInfo);

        return panel;
    }


    private JPanel getLogLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        txtAreaLog.setEditable(false);
        txtAreaLog.setLineWrap(true);
        txtAreaLog.setWrapStyleWord(true);
        JScrollPane areaScrollPane = new JScrollPane(txtAreaLog);
        areaScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(WIDTH, 100));
        areaScrollPane.setMinimumSize(new Dimension(WIDTH, 100));

        panel.add(areaScrollPane);
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));

        return panel;
    }

    private JPanel getOpenSourceLayout() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JLabel label = new JLabel("开源软件许可");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                new OpenSourceUI().createUI();
            }
        });
        panel.add(label);
        return panel;
    }

    private void log(String tag, String msg) {
        txtAreaLog.append("[" + tag + "]:" + msg + "\n");
        txtAreaLog.setCaretPosition(txtAreaLog.getText().length());
    }


    private CommonArg getProperties() {
        CommonArg commonArg = new CommonArg();
        try {
            String version = props.getProperty("version", "0");
            String enableH265 = props.getProperty("enableH265", Boolean.toString(commonArg.enableH265));
            String fps = props.getProperty("fps", String.valueOf(commonArg.fps));
            String inputPath = props.getProperty("inputPath", "");
            String scale = props.getProperty("scale", String.valueOf(commonArg.scale));
            String audioPath = props.getProperty("audioPath", "");
            String bitrate = props.getProperty("bitrate", String.valueOf(commonArg.bitrate));
            String enableCrf = props.getProperty("enableCrf", String.valueOf(commonArg.enableCrf));
            String crf = props.getProperty("crf", String.valueOf(commonArg.crf));

            int v = Integer.parseInt(version);
            // 版本不符直接返回默认值
            if (v != commonArg.version) return commonArg;
            commonArg.fps = Integer.parseInt(fps);
            commonArg.scale = Float.parseFloat(scale);
            commonArg.enableH265 = Boolean.TRUE.toString().equals(enableH265);
            commonArg.inputPath = inputPath;
            commonArg.audioPath = audioPath;
            commonArg.bitrate = Integer.parseInt(bitrate);
            commonArg.enableCrf = Boolean.TRUE.toString().equals(enableCrf);
            commonArg.crf = Integer.parseInt(crf);
        } catch (Exception e) {
            TLog.e(TAG, "读取配置失败：" + e.getMessage());
        }
        return commonArg;
    }


    private void setProperties(CommonArg commonArg, String codecMode) throws IOException {
        props.setProperty("version", String.valueOf(commonArg.version));
        props.setProperty("defaultsVersion", DEFAULTS_VERSION);
        props.setProperty("enableH265", commonArg.enableH265? Boolean.TRUE.toString() : Boolean.FALSE.toString());
        props.setProperty("codecMode", codecMode == null ? CODEC_H264 : codecMode);
        props.setProperty("fps", String.valueOf(commonArg.fps));
        props.setProperty("inputPath", commonArg.inputPath == null ? "" : commonArg.inputPath);
        props.setProperty("audioPath", commonArg.audioPath == null ? "" : commonArg.audioPath);
        props.setProperty("scale", String.valueOf(commonArg.scale));
        props.setProperty("bitrate", String.valueOf(commonArg.bitrate));
        props.setProperty("crf", String.valueOf(commonArg.crf));
        props.setProperty("enableCrf", String.valueOf(commonArg.enableCrf));
        File file = getWritablePropertiesFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        props.store(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), "");
    }


}
