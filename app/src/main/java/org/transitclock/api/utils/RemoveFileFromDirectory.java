package org.transitclock.api.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveFileFromDirectory {

    public static void removeFile(String path, String fileName) {
        var file = Path.of(path, fileName);
        try {
            Files.delete(file);
            logger.info("File {} deleted successfully" , file);
        } catch (NoSuchFileException e) {
            logger.debug("File not found: {}", e.getMessage());
        } catch (IOException e) {
            logger.warn("Error deleting file: {}", e.getMessage());
        }
    }
}
