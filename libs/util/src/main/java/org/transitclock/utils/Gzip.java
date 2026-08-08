/* (C)2023 */
package org.transitclock.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * For compressing and uncompressing gzip (.gz) files.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class Gzip {
    /**
     * Uncompresses .gz file. Deletes the original .gz file.
     *
     * @param gzipFileName Name of the compressed file. Should end with .gz
     */
    public static void uncompress(String gzipFileName) {
        try {
            InputStream fis = Files.newInputStream(Path.of(gzipFileName));
            GZIPInputStream gis = new GZIPInputStream(fis);
            String outputFileName = gzipFileName.substring(0, gzipFileName.lastIndexOf(".gz"));
            OutputStream fos = Files.newOutputStream(Path.of(outputFileName));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            // close resources
            fos.close();
            gis.close();

            // Delete the old .gz file
            File compressedFile = Path.of(gzipFileName).toFile();
            compressedFile.delete();
        } catch (IOException e) {
            logger.error("Exception when decompressing gzip file \"{}\"", gzipFileName, e);
        }
    }

    /**
     * Compresses specified file using gzip. Deletes the original file. Resulting file name will be
     * fileName + ".gz".
     *
     * @param fileName
     * @return name of compressed file
     */
    public static String compress(String fileName) {
        try {
            InputStream fis = Files.newInputStream(Path.of(fileName));
            String outputFileName = fileName + ".gz";
            OutputStream fos = Files.newOutputStream(Path.of(outputFileName));
            GZIPOutputStream gzipOS = new GZIPOutputStream(fos);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzipOS.write(buffer, 0, len);
            }
            // close resources
            gzipOS.close();
            fos.close();
            fis.close();

            // Delete the old file
            File uncompressedFile = Path.of(fileName).toFile();
            uncompressedFile.delete();

            return outputFileName;
        } catch (IOException e) {
            logger.error("Exception when compressing file \"{}\"", fileName, e);
            return null;
        }
    }

    public static boolean isGzipFile(File file) {
        String fileName = file.getName();
        return fileName.endsWith(".gz");
    }
}
