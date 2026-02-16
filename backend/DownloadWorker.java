import java.io.*;
import java.net.*;

public class DownloadWorker extends Thread {

    private final URL url;
    private final long start;
    private final long end;
    private final int id;

    public DownloadWorker(URL url, long start, long end, int id) {
        this.url = url;
        this.start = start;
        this.end = end;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection con =
                (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty(
                "Range", "bytes=" + start + "-" + end
            );
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.connect();

            if (con.getResponseCode() != 206) {
                throw new RuntimeException("Range not supported");
            }

            InputStream in = con.getInputStream();
            FileOutputStream out =
                new FileOutputStream("part-" + id + ".tmp");

            byte[] buffer = new byte[8192];
            int bytes;
            while ((bytes = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytes);
            }

            out.close();
            in.close();

            System.out.println("Thread " + id + " finished");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
