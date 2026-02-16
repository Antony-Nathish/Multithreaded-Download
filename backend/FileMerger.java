import java.io.*;

public class FileMerger {

    public static void merge(String fileName, int parts)
            throws Exception {

        FileOutputStream out = new FileOutputStream(fileName);

        for (int i = 0; i < parts; i++) {
            File part = new File("part-" + i + ".tmp");
            FileInputStream in = new FileInputStream(part);

            byte[] buffer = new byte[8192];
            int bytes;
            while ((bytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytes);
            }

            in.close();
            part.delete();
        }
        out.close();
    }
}
