import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class DownloadServer {

    static List<DownloadWorker> workers = new ArrayList<>();
    static long fileSize = 0;
    static volatile long singleDownloaded = 0;
    static volatile boolean singleMode = false;

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // ================= DOWNLOAD =================
        server.createContext("/download", exchange -> {

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            try {
                String query = exchange.getRequestURI().getQuery();

                if (query == null) {
                    sendError(exchange, "Missing parameters");
                    return;
                }

                String urlString = null;
                String userFileName = null;

                String[] pairs = query.split("&");

                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length < 2) continue;

                    String key = keyValue[0];
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");

                    if (key.equals("url")) urlString = value;
                    if (key.equals("filename")) userFileName = value;
                }

                if (urlString == null || urlString.isEmpty()) {
                    sendError(exchange, "URL parameter missing");
                    return;
                }

                // 🔥 Pass raw user name — final logic happens inside startDownload
                startDownload(urlString, userFileName);

                String response = "Download Started";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());

            } catch (Exception e) {
                sendError(exchange, e.getMessage());
                e.printStackTrace();
            }

            exchange.close();
        });

        // ================= PROGRESS =================
        server.createContext("/progress", exchange -> {

            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");

            String json = getProgressJSON();
            exchange.sendResponseHeaders(200, json.length());
            exchange.getResponseBody().write(json.getBytes());
            exchange.close();
        });

        server.start();
        System.out.println("Server started on port 8000");
    }
    private static void startDownload(String urlString, String userFileName) {

        try {
            workers.clear();

            URL url = new URL(urlString);

            HttpURLConnection meta = (HttpURLConnection) url.openConnection();
            meta.setRequestProperty("Range", "bytes=0-0");
            meta.setRequestProperty("User-Agent", "Mozilla/5.0");
            meta.connect();

            int responseCode = meta.getResponseCode();
            boolean supportsRange = (responseCode == 206);

            String contentType = meta.getContentType();

            // 🔥 Build final filename correctly
            String finalName = buildFinalFileName(
                    userFileName,
                    urlString,
                    contentType
            );

            String downloadFolder =
                    System.getProperty("user.home") + "\\Downloads\\";

            String fullPath = downloadFolder + finalName;

            System.out.println("Saving as: " + fullPath);

            if (supportsRange) {

                String contentRange = meta.getHeaderField("Content-Range");

                fileSize = Long.parseLong(
                        contentRange.substring(contentRange.lastIndexOf("/") + 1)
                );

                meta.disconnect();

                singleMode = false;
                System.out.println("Multi-thread mode enabled.");

                multiThreadDownload(url, fullPath);

            } else {

                fileSize = meta.getContentLengthLong();
                if (fileSize <= 0) {
                    fileSize = 1; // prevent divide-by-zero and negative values
                }
                meta.disconnect();

                singleMode = true;
                singleDownloaded = 0;

                System.out.println("Single-thread mode enabled.");

                singleThreadDownload(url, fullPath);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= PROGRESS JSON =================
    private static String getProgressJSON() {
        if (singleMode) {

            int overall = (fileSize == 0) ? 0 :(int)((singleDownloaded * 100) / fileSize);

            return "{ \"overall\": " + overall +
                ", \"threads\": [" +
                "{ \"progress\": " + overall +
                ", \"status\": \"" +
                (overall >= 100 ? "Completed" : "Running") +
                "\" } ] }";
        }
        if (workers.isEmpty()) {
            return "{ \"overall\":0, \"threads\":[] }";
        }

        long totalDownloaded = 0;
        StringBuilder threadJson = new StringBuilder("[");

        for (int i = 0; i < workers.size(); i++) {

            DownloadWorker w = workers.get(i);
            totalDownloaded += w.downloaded;

            threadJson.append("{")
                    .append("\"progress\":")
                    .append(w.getProgress())
                    .append(",\"status\":\"")
                    .append(w.getStatus())
                    .append("\"}");

            if (i != workers.size() - 1)
                threadJson.append(",");
        }

        threadJson.append("]");

        int overall = (int)((totalDownloaded * 100) / fileSize);

        return "{ \"overall\": " + overall +
                ", \"threads\": " + threadJson + "}";
    }

    private static void sendError(HttpExchange exchange, String message)
            throws IOException {
        exchange.sendResponseHeaders(400, message.length());
        exchange.getResponseBody().write(message.getBytes());
        exchange.close();
    }

   private static String detectFileName(String urlString) {

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.connect();

            String disposition = connection.getHeaderField("Content-Disposition");
            String contentType = connection.getContentType();

            // 1️⃣ Try Content-Disposition
            if (disposition != null && disposition.contains("filename=")) {

                String name = disposition.substring(
                        disposition.indexOf("filename=") + 9
                ).replace("\"", "");

                name = sanitizeFileName(name);

                return name;
            }

            // 2️⃣ Try URL path
            String path = url.getPath();
            String name = path.substring(path.lastIndexOf("/") + 1);

            name = sanitizeFileName(name);

            // 3️⃣ If no extension → detect from Content-Type
            if (!name.contains(".")) {
                name += getExtensionFromContentType(contentType);
            }

            return name;

        } catch (Exception e) {
            return null;
        }
    }
    private static void multiThreadDownload(URL url, String fullPath) throws Exception {

        RandomAccessFile file = new RandomAccessFile(fullPath, "rw");
        file.setLength(fileSize);

        int threadCount = 4;
        long chunkSize = fileSize / threadCount;
        long remainder = fileSize % threadCount;

        long currentStart = 0;

        for (int i = 0; i < threadCount; i++) {

            long currentChunk = chunkSize;
            if (i == threadCount - 1) currentChunk += remainder;

            long start = currentStart;
            long end = start + currentChunk - 1;

            DownloadWorker worker =
                    new DownloadWorker(url, start, end, file);

            workers.add(worker);
            worker.start();

            currentStart = end + 1;
        }
    }
    private static void singleThreadDownload(URL url, String fullPath) throws Exception {

        FileOutputStream fos = new FileOutputStream(fullPath);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.connect();

        InputStream input = connection.getInputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {

            fos.write(buffer, 0, bytesRead);
            singleDownloaded += bytesRead;
        }

        fos.close();
        input.close();
    }
    private static String sanitizeFileName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        // Remove query parameters if present
        if (name.contains("?")) {
            name = name.substring(0, name.indexOf("?"));
        }

        // Remove invalid Windows characters
        name = name.replaceAll("[\\\\/:*?\"<>|]", "");

        return name.trim();
    }
    private static String getExtensionFromContentType(String type) {

        if (type == null) return ".bin";

        type = type.toLowerCase();

        if (type.contains("pdf")) return ".pdf";
        if (type.contains("zip")) return ".zip";
        if (type.contains("rar")) return ".rar";
        if (type.contains("7z")) return ".7z";
        if (type.contains("gzip") || type.contains("gz")) return ".gz";
        if (type.contains("tar")) return ".tar";

        if (type.contains("mp4")) return ".mp4";
        if (type.contains("mpeg")) return ".mp3";
        if (type.contains("mp3")) return ".mp3";
        if (type.contains("wav")) return ".wav";
        if (type.contains("aac")) return ".aac";
        if (type.contains("ogg")) return ".ogg";
        if (type.contains("flac")) return ".flac";

        if (type.contains("avi")) return ".avi";
        if (type.contains("mkv")) return ".mkv";
        if (type.contains("mov")) return ".mov";
        if (type.contains("wmv")) return ".wmv";

        if (type.contains("png")) return ".png";
        if (type.contains("jpeg") || type.contains("jpg")) return ".jpg";
        if (type.contains("gif")) return ".gif";
        if (type.contains("bmp")) return ".bmp";
        if (type.contains("webp")) return ".webp";
        if (type.contains("svg")) return ".svg";

        if (type.contains("json")) return ".json";
        if (type.contains("xml")) return ".xml";
        if (type.contains("html")) return ".html";
        if (type.contains("css")) return ".css";
        if (type.contains("javascript")) return ".js";
        if (type.contains("csv")) return ".csv";

        if (type.contains("msword")) return ".doc";
        if (type.contains("officedocument.wordprocessingml")) return ".docx";
        if (type.contains("vnd.ms-excel")) return ".xls";
        if (type.contains("officedocument.spreadsheetml")) return ".xlsx";
        if (type.contains("vnd.ms-powerpoint")) return ".ppt";
        if (type.contains("officedocument.presentationml")) return ".pptx";

        if (type.contains("apk")) return ".apk";
        if (type.contains("exe")) return ".exe";
        if (type.contains("octet-stream")) return ".bin";

    return ".bin";
    }
    private static String buildFinalFileName(String userName,String urlString,String contentType) {

        // 1️⃣ Sanitize user name
        if (userName != null) {
            userName = sanitizeFileName(userName);
        }

        // 2️⃣ If user provided name
        if (userName != null && !userName.isEmpty()) {

            // If user already included extension
            if (userName.contains(".")) {
                return userName;
            }

            // No extension → detect and append
            String ext = getExtensionFromContentType(contentType);
            return userName + ext;
        }

        // 3️⃣ No user name → detect from server
        String detected = detectFileName(urlString);

        if (detected != null && !detected.isEmpty()) {
            return detected;
        }

        // 4️⃣ Final fallback
        return "download_" + System.currentTimeMillis() + ".bin";
    }
    
}
