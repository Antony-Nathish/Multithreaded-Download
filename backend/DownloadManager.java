import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

public class DownloadManager {

    private final URL url;
    private final int threads;

    public DownloadManager(String urlStr, int threads) throws Exception {
        this.url = new URL(urlStr);
        this.threads = threads;
    }

    public String startAndReturnFile() throws Exception {

        // ============================
        // PHASE 1: METADATA (SAFE)
        // ============================
        HttpURLConnection meta = (HttpURLConnection) url.openConnection();
        meta.setRequestMethod("GET");
        meta.setRequestProperty("Range", "bytes=0-0");
        meta.setRequestProperty("User-Agent", "Mozilla/5.0");
        meta.setInstanceFollowRedirects(true);

        int code = meta.getResponseCode();
        if (code != 200 && code != 206) {
            throw new Exception("Cannot access URL. HTTP " + code);
        }

        long size = extractFileSize(meta);
        if (size <= 0) {
            throw new Exception("Invalid file size");
        }

        String detectedName = detectFileName(meta);
        boolean supportsRange = (code == 206);
        meta.disconnect();

        // ============================
        // TEMP FILE NAME
        // ============================
        String tempName = "download_tmp_" + System.currentTimeMillis();

        // ============================
        // PHASE 2: DOWNLOAD
        // ============================
        if (!supportsRange || threads <= 1) {
            singleThreadDownload(tempName);
        } else {
            multiThreadDownload(tempName, size);
        }

        // ============================
        // PHASE 3: RENAME AFTER DOWNLOAD
        // ============================
        String finalName = buildFinalName(detectedName);
        renameFile(tempName, finalName);

        return finalName;
    }

    // ============================
    // MULTI-THREAD DOWNLOAD
    // ============================
    private void multiThreadDownload(String tempName, long size) throws Exception {

        long chunk = size / threads;
        List<DownloadWorker> workers = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            long start = i * chunk;
            long end = (i == threads - 1) ? size - 1 : start + chunk - 1;
            workers.add(new DownloadWorker(url, start, end, i));
        }

        for (DownloadWorker w : workers) w.start();
        for (DownloadWorker w : workers) w.join();

        FileMerger.merge(tempName, threads);
    }

    // ============================
    // SINGLE THREAD DOWNLOAD
    // ============================
    private void singleThreadDownload(String tempName) throws Exception {

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setInstanceFollowRedirects(true);

        int code = con.getResponseCode();
        if (code != 200 && code != 206) {
            throw new Exception("Download failed. HTTP " + code);
        }

        String type = con.getContentType();
        if (type != null && type.toLowerCase().contains("text/html")) {
            throw new Exception("Server returned HTML instead of file");
        }

        try (
            InputStream in = con.getInputStream();
            FileOutputStream out = new FileOutputStream(tempName)
        ) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // ============================
    // FINAL NAME DECISION
    // ============================
    private String buildFinalName(String detected) {

        if (detected != null && detected.contains(".")) {
            return detected;
        }

        return "download_" + System.currentTimeMillis() + ".bin";
    }

    // ============================
    // SAFE RENAME
    // ============================
    private void renameFile(String tempName, String finalName) throws Exception {

        File temp = new File(tempName);
        File target = new File(finalName);

        if (!temp.exists()) {
            throw new Exception("Temporary file missing");
        }

        if (!temp.renameTo(target)) {
            throw new Exception("Failed to rename downloaded file");
        }
    }

    // ============================
    // FILE NAME DETECTION
    // ============================
    private String detectFileName(HttpURLConnection con) throws Exception {

        String disposition = con.getHeaderField("Content-Disposition");
        if (disposition != null && disposition.contains("filename=")) {
            String name = disposition
                    .substring(disposition.indexOf("filename=") + 9)
                    .replace("\"", "");
            return URLDecoder.decode(name, "UTF-8");
        }

        String path = url.getPath();
        if (path != null && path.contains(".")) {
            return path.substring(path.lastIndexOf("/") + 1);
        }

        String type = con.getContentType();
        if (type != null) {
            type = type.toLowerCase();
            if (type.contains("pdf")) return "download.pdf";
            if (type.contains("zip")) return "download.zip";
            if (type.contains("mp4")) return "download.mp4";
            if (type.contains("mpeg")) return "download.mp3";
            if (type.contains("png")) return "download.png";
            if (type.contains("jpeg")) return "download.jpg";
        }

        return null;
    }

    // ============================
    // FILE SIZE FROM RANGE
    // ============================
    private long extractFileSize(HttpURLConnection con) {
        String range = con.getHeaderField("Content-Range");
        if (range != null && range.contains("/")) {
            return Long.parseLong(range.substring(range.lastIndexOf("/") + 1));
        }
        return con.getContentLengthLong();
    }
}
