package com.ispw2.util;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for common file operations to reduce code duplication.
 * Provides standardized file handling methods used across the application.
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a directory if it doesn't exist.
     * 
     * @param dirPath The path of the directory to create
     * @param logger The logger instance for error reporting
     * @throws IOException If directory creation fails
     */
    public static void createDirectoryIfNotExists(final String dirPath, final Logger logger) throws IOException {
        final Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created directory: {}", dirPath);
            } catch (final IOException e) {
                logger.error("Failed to create directory: {}", dirPath, e);
                throw e;
            }
        }
    }

    /**
     * Checks if a file exists and is readable.
     * 
     * @param filePath The path of the file to check
     * @param logger The logger instance for error reporting
     * @return true if file exists and is readable, false otherwise
     */
    public static boolean isFileValid(final String filePath, final Logger logger) {
        final File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("File does not exist: {}", filePath);
            return false;
        }
        if (!file.canRead()) {
            logger.warn("File is not readable: {}", filePath);
            return false;
        }
        return true;
    }

    /**
     * Gets the parent directory of the current working directory.
     * 
     * @param logger The logger instance for error reporting
     * @return The parent directory path
     * @throws IOException If parent directory cannot be determined
     */
    public static String getParentDirectory(final Logger logger) throws IOException {
        final String currentDir = System.getProperty("user.dir");
        Path probe = Paths.get(currentDir);

        // Walk upwards until we find a directory that looks like the workspace root.
        // Heuristics: contains a module folder `DefectPredictionProject`, a `datasets` folder, or a top-level pom.xml.
        while (probe != null) {
            final Path candidate1 = probe.resolve("DefectPredictionProject");
            final Path candidate2 = probe.resolve("datasets");
            final Path candidate3 = probe.resolve("pom.xml");

            if (Files.exists(candidate1) || Files.exists(candidate2) || Files.exists(candidate3)) {
                logger.debug("Determined workspace root at: {}", probe.toString());
                return probe.toString();
            }
            probe = probe.getParent();
        }

        logger.error("Cannot determine workspace root from current directory: {}", currentDir);
        throw new IOException("Cannot determine parent directory. Please run from within the project folder.");
    }

    /**
     * Builds a file path by combining base path and filename.
     * 
     * @param basePath The base directory path
     * @param filename The filename
     * @return The complete file path
     */
    public static String buildFilePath(final String basePath, final String filename) {
        return Paths.get(basePath, filename).toString();
    }

    /**
     * Checks if a file has a specific extension.
     * 
     * @param filePath The path of the file to check
     * @param extension The extension to check for (with or without dot)
     * @return true if file has the specified extension
     */
    public static boolean hasExtension(final String filePath, final String extension) {
        final String normalizedExtension = extension.startsWith(".") ? extension : "." + extension;
        return filePath.toLowerCase().endsWith(normalizedExtension.toLowerCase());
    }
}
