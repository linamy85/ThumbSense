import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by yulinlin on 2017/5/21.
 */
public class FileWriter {
    private File file;
    private FileOutputStream out;

    public FileWriter(String filename) {
        try {
            file = new File(filename);
            out = new FileOutputStream(file);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            System.out.println("File " + filename + " created successfully.");
        }
    }

    public synchronized void write(String str) {
        try {
           out.write(str.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        out.close();
    }
}
