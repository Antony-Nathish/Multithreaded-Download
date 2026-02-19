import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadWorker extends Thread {

    private final URL url;
    private final long start;
    private final long end;
    private final RandomAccessFile file;

    public volatile long downloaded = 0;
    private final long totalBytes;
    private volatile boolean completed = false;

    public DownloadWorker(URL url, long start, long end, RandomAccessFile file) {
        this.url = url;
        this.start = start;
        this.end = end;
        this.file = file;
        this.totalBytes = end - start + 1;
    }

    @Override
    public void run() {

        try {
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestProperty("Range",
                    "bytes=" + start + "-" + end);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.connect();

            if (connection.getResponseCode() != 206) {
                throw new RuntimeException("Range not supported.");
            }

            InputStream input = connection.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {

                synchronized (file) {
                    file.seek(start + downloaded);
                    file.write(buffer, 0, bytesRead);
                }

                downloaded += bytesRead;
            }

            completed = true;

            input.close();
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getProgress() {
        return (int)((downloaded * 100) / totalBytes);
    }

    public String getStatus() {
        return completed ? "Completed" : "Running";
    }
}
