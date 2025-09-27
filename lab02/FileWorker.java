package tcpc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class FileWorker {
    private static final long TERABYTE_SIZE = 1099511627776L;
    private static final short MAX_FILENAME_SIZE = 4096;
    // Методы для получения имени файла без расширения и получения расширения файла
    public static String removeFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    public static String getFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        return "";
    }

    public static void checkArgs(File file) throws IOException{
            byte[] fileNameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
            long fileSize = file.length();
            int fileNameSize = fileNameBytes.length;
            if (fileSize > TERABYTE_SIZE){
                throw new IOException("file size too large! ");
            }
            if (fileNameSize > MAX_FILENAME_SIZE){
                throw new IOException("file name size too large! ");
            }
    }
}