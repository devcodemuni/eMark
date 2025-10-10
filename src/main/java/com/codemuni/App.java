package com.codemuni;

import com.codemuni.config.ConfigManager;
import com.codemuni.gui.DialogUtils;
import com.codemuni.gui.pdfHandler.PdfViewerMain;
import com.codemuni.utils.FileUtils;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Map;

import static com.codemuni.utils.AppConstants.LOGO_PATH;

public class App {

    private static final Log log = LogFactory.getLog(App.class);

    static {
        System.setProperty("sun.security.pkcs11.disableNativeDialog", "true");
        FlatMacDarkLaf.setup();
        UIManager.put("defaultFont", new Font("SansSerif", Font.PLAIN, 13));
    }

    public static Image getAppIcon() {
        return Toolkit.getDefaultToolkit().getImage(App.class.getResource(LOGO_PATH));
    }

    public static void main(String[] args) {
        AppInitializer.initialize();      // initialize folders and config
        configureProxyFromConfig();       // read proxy from config

//        resetDialogPreferences();

        SwingUtilities.invokeLater(() -> {
            if (!isJava8()) {
                showJavaVersionErrorAndExit();
                return;
            }

            setupUiDefaults();
            launchApp(args);
        });
    }

    private static boolean isJava8() {
        String version = System.getProperty("java.version", "");
        return version.startsWith("1.8");
    }

    private static void showJavaVersionErrorAndExit() {
        String version = System.getProperty("java.version", "unknown");

        String htmlMessage = "<html><body style='"
                + "font-family:Segoe UI, sans-serif;"
                + "font-size:13px;"
                + "width:400px;"
                + "background-color:#2b2b2b;"
                + "padding:20px;"
                + "border-radius:10px;"
                + "color:#e0e0e0;"
                + "'>"
                + "<h2 style='color:#ff6b6b; margin:0 0 12px 0; text-align:center; font-size:17px;'>"
                + "Unsupported Java Version</h2>"
                + "<p style='margin-top:5px; line-height:1.9;'>"
                + "This application requires <b>Java 8</b> (version <b>1.8.x</b>) to run."
                + "<br />Detected Java version: <span style='color:#ffd54f; font-weight:bold;'>" + version + "</span>."
                + "<br />Please install <b>Java 8</b> and restart the application."
                + "</p>"
                + "</body></html>";

        DialogUtils.showError(null, "Unsupported Java Version", htmlMessage);
        System.exit(1);
    }


    private static void setupUiDefaults() {
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("ScrollBar.width", 14);
        UIManager.put("ScrollBar.thumbArc", 14);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
    }

    private static void launchApp(String[] args) {
        PdfViewerMain pdfViewerMain = new PdfViewerMain();
        pdfViewerMain.setVisible(true);

        if (args.length == 1 && FileUtils.isFileExist(args[0])) {
            pdfViewerMain.renderPdfFromPath(args[0]);
        }
    }

    /**
     * Reads proxy from config and sets system properties
     */
    private static void configureProxyFromConfig() {
        Map<String, String> proxy = ConfigManager.getProxySettings();
        String host = proxy.getOrDefault("host", "").trim();
        String port = proxy.getOrDefault("port", "").trim();
        String user = proxy.getOrDefault("username", "").trim();
        String pass = proxy.getOrDefault("password", "").trim();

        if (host.isEmpty() || port.isEmpty()) return;

        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", port);
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", port);

        log.info("Proxy configured: " + host + ":" + port);

        if (!user.isEmpty()) {
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass.toCharArray());
                }
            });
            log.info("Proxy authentication configured.");
        }
    }
}
