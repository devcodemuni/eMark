package com.codemuni.utils;

import java.io.File;

public class FileUtils {

    /**
     * Ensures that a directory exists, creating it (and parent directories) if necessary.
     * @param path The directory path as a String
     * @return true if the directory exists or was successfully created
     */
    public static boolean ensureDirectory(String path) {
        File dir = new File(path);
        return dir.exists() || dir.mkdirs();
    }

    /**
     * Ensures that a directory exists, creating it (and parent directories) if necessary.
     * @param dir The directory as a File object
     * @return true if the directory exists or was successfully created
     */
    public static boolean ensureDirectory(File dir) {
        return dir.exists() || dir.mkdirs();
    }

    /**
     * Checks if a file exists at the specified path.
     * @param filePath The file path to check
     * @return true if the file exists and is a regular file
     */
    public static boolean isFileExist(String filePath) {
        if (filePath == null) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }
}
