package com.codemuni.config;

import com.codemuni.utils.AppConstants;
import com.codemuni.utils.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.codemuni.utils.AppConstants.CONFIG_FILE;


public final class ConfigManager {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final Log log = LogFactory.getLog(ConfigManager.class);

    // Prevent instantiation
    private ConfigManager() {
    }

    /**
     * Load config from file or return new empty config
     */
    public static AppConfig readConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return new AppConfig();
        }
        try {
            return mapper.readValue(file, AppConfig.class);
        } catch (IOException e) {
            log.error("Failed to read config file", e);
            return new AppConfig();
        }
    }

    /**
     * Save given config to disk
     */
    public static boolean writeConfig(AppConfig config) {
        try {
            FileUtils.ensureDirectory(new File(CONFIG_FILE).getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE), config);
            return true;
        } catch (IOException e) {
            log.error("Failed to write config file", e);
            return false;
        }
    }

    // ──────────────────────────────
    // PKCS#11 PATHS
    // ──────────────────────────────

    public static List<String> getPKCS11Paths() {
        return new ArrayList<>(readConfig().pkcs11);
    }

    public static boolean addPKCS11Path(String newPath) {
        AppConfig config = readConfig();
        if (!config.pkcs11.contains(newPath)) {
            config.pkcs11.add(newPath);
            return writeConfig(config);
        }
        return true;
    }

    public static void removePKCS11Path(String path) {
        AppConfig config = readConfig();
        if (config.pkcs11.remove(path)) {
            writeConfig(config);
        }
    }

    public static boolean setPKCS11Paths(List<String> paths) {
        AppConfig config = readConfig();
        config.pkcs11 = new ArrayList<>(paths);
        return writeConfig(config);
    }

    public static boolean clearPKCS11Paths() {
        AppConfig config = readConfig();
        config.pkcs11.clear();
        return writeConfig(config);
    }

    // ──────────────────────────────
    // PFX_STORE FILE
    // ──────────────────────────────

    public static String getPFXPath() {
        return readConfig().softHSM;
    }

    public static boolean setPFXPath(String path) {
        AppConfig config = readConfig();
        config.softHSM = path;
        return writeConfig(config);
    }

    public static boolean clearPFXPath() {
        AppConfig config = readConfig();
        config.softHSM = "";
        return writeConfig(config);
    }

    // ──────────────────────────────
    // ACTIVE STORE
    // ──────────────────────────────

    public static Map<String, Boolean> getActiveStore() {
        return new HashMap<>(readConfig().activeStore);
    }

    public static boolean isWindowStoreActive() {
        return getActiveStore().get(AppConstants.WIN_KEY_STORE);
    }

    public static boolean isPKCS11StoreActive() {
        return getActiveStore().get(AppConstants.PKCS11_KEY_STORE);
    }

    public static boolean isPFXStoreActive() {
        return getActiveStore().get(AppConstants.SOFTHSM);
    }

    public static void setActiveStore(String key, Boolean value) {
        AppConfig config = readConfig();
        config.activeStore.put(key, value);
        writeConfig(config);
    }

    public static boolean removeActiveStoreKey(String key) {
        AppConfig config = readConfig();
        if (config.activeStore.remove(key) != null) {
            return writeConfig(config);
        }
        return false;
    }

    public static boolean clearActiveStore() {
        AppConfig config = readConfig();
        config.activeStore.clear();
        return writeConfig(config);
    }


    // ──────────────────────────────
// Timestamp Server
// ──────────────────────────────
    public static Map<String, String> getTimestampServer() {
        return new HashMap<>(readConfig().timestampServer);
    }

    public static boolean setTimestampServer(String url, String username, String password) {
        AppConfig config = readConfig();
        Map<String, String> ts = new HashMap<>();
        ts.put("url", url);
        ts.put("username", username);
        ts.put("password", password);
        config.setTimestampServer(ts);
        return writeConfig(config);
    }

    // ──────────────────────────────
// Proxy Settings
// ──────────────────────────────
    public static Map<String, String> getProxySettings() {
        return new HashMap<>(readConfig().proxy);
    }

    public static boolean setProxySettings(String host, String port, String username, String password) {
        AppConfig config = readConfig();
        Map<String, String> proxy = new HashMap<>();
        proxy.put("host", host);
        proxy.put("port", port);
        proxy.put("username", username);
        proxy.put("password", password);
        config.setProxy(proxy);
        return writeConfig(config);
    }

}
