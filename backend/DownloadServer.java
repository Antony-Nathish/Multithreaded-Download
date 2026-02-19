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
                String fileName = null;

                String[] pairs = query.split("&");

                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length < 2) continue;

                    String key = keyValue[0];
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");

                    if (key.equals("url")) urlString = value;
                    if (key.equals("filename")) fileName = value;
                }

                if (urlString == null) {
                    sendError(exchange, "URL parameter missing");
                    return;
                }

                if (fileName == null || fileName.isEmpty()) {
                    fileName = detectFileName(urlString);
                }

                startDownload(urlString, fileName);

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

    // ================= START DOWNLOAD =================
    private static void startDownload(String urlString, String filename) {

        try {
            workers.clear();

            URL url = new URL(urlString);

            HttpURLConnection meta = (HttpURLConnection) url.openConnection();
            meta.setRequestProperty("Range", "bytes=0-0");
            meta.setRequestProperty("User-Agent", "Mozilla/5.0");
            meta.connect();

            if (meta.getResponseCode() != 206) {
                throw new Exception("Server does not support range.");
            }

            String contentRange = meta.getHeaderField("Content-Range");

            fileSize = Long.parseLong(
                    contentRange.substring(contentRange.lastIndexOf("/") + 1)
            );

            meta.disconnect();

            // 🔥 SAVE DIRECTLY TO DOWNLOADS FOLDER
            String downloadFolder =
                    System.getProperty("user.home") + "\\Downloads\\";

            String fullPath = downloadFolder + filename;

            System.out.println("Saving to: " + fullPath);

            RandomAccessFile file =
                    new RandomAccessFile(fullPath, "rw");

            file.setLength(fileSize);

            int threadCount = 4;
            long chunkSize = fileSize / threadCount;
            long remainder = fileSize % threadCount;

            long currentStart = 0;

            for (int i = 0; i < threadCount; i++) {

                long currentChunk = chunkSize;
                if (i == threadCount - 1) {
                    currentChunk += remainder;
                }

                long start = currentStart;
                long end = start + currentChunk - 1;

                DownloadWorker worker =
                        new DownloadWorker(url, start, end, file);

                workers.add(worker);
                worker.start();

                currentStart = end + 1;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= PROGRESS JSON =================
    private static String getProgressJSON() {

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

    private static String detectFileName(String url) {
        String name = url.substring(url.lastIndexOf("/") + 1);
        if (!name.contains(".")) {
            name += ".bin";
        }
        return name;
    }
}
