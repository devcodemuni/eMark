package com.codemuni.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConstants {

    public static final String APP_NAME = "eMark";
    public static final String APP_VERSION = "V1.1.2";
    public static final String APP_DESCRIPTION = "Cross-platform PDF signer for Windows Certificate Store, PKCS#11/HSM, and PFX files.";

    public static final String APP_AUTHOR = "CodeMuni";
    public static final String APP_WEBSITE = "https://devcodemuni.github.io/eMark/";
    public static final String APP_GITHUB = "https://github.com/devcodemuni/eMark";
    public static final String APP_LICENSE_URL = "https://github.com/devcodemuni/eMark/blob/main/LICENSE";
    public static final String LOGO_PATH = "/icons/logo.png";


    public static final String TIMESTAMP_SERVER = "http://timestamp.comodoca.com";

    // Config directory: ~/.eMark/
    public static final Path CONFIG_DIR_PATH = Paths.get(System.getProperty("user.home"), "." + APP_NAME);
    public static final Path CONFIG_FILE_PATH = CONFIG_DIR_PATH.resolve("config.yml");
    public static final String CONFIG_FILE = CONFIG_FILE_PATH.toString();
    public static final String CONFIG_DIR = CONFIG_DIR_PATH.toString();


    // Store names
    public static final String WIN_KEY_STORE = "WINDOWS";
    public static final String PKCS11_KEY_STORE = "PKCS11";
    public static final String SOFTHSM = "SOFTHSM";

    public static boolean isWindow = getOs().toLowerCase().contains("win");
    public static boolean isLinux = getOs().toLowerCase().contains("nix") || getOs().contains("nux") || getOs().contains("aix");
    public static boolean isMac = getOs().contains("mac");

    public static String getOs() {
        return System.getProperty("os.name");
    }


}
