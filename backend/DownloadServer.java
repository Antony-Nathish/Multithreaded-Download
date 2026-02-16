import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;

public class DownloadServer {

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        SSLBypass.disableSSLVerification();


        server.createContext("/download", (HttpExchange ex) -> {

            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

             // ✅ HANDLE CORS PREFLIGHT
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                ex.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
                ex.sendResponseHeaders(204, -1);
                return;
            }

            try {
                // ✅ SAFE QUERY PARSING
                String query = ex.getRequestURI().getQuery();
                String url = null;

                for (String param : query.split("&")) {
                    if (param.startsWith("url=")) {
                        url = URLDecoder.decode(param.substring(4), "UTF-8");
                        break;
                    }
                }

                if (url == null || url.isBlank()) {
                    throw new Exception("Missing URL parameter");
                }

                DownloadManager manager = new DownloadManager(url, 4);
                String fileName = manager.startAndReturnFile();

                File file = new File(fileName);

                ex.getResponseHeaders().add(
                    "Content-Disposition",
                    "attachment; filename=\"" + file.getName() + "\""
                );

                String contentType = Files.probeContentType(file.toPath());
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                ex.getResponseHeaders().add("Content-Type", contentType);

                ex.sendResponseHeaders(200, file.length());

                try (OutputStream os = ex.getResponseBody();
                    FileInputStream fis = new FileInputStream(file)) {

                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }

                file.delete();

            } catch (Exception e) {
                e.printStackTrace();
                String error = "ERROR: " + e.getMessage();
                ex.sendResponseHeaders(500, error.length());
                ex.getResponseBody().write(error.getBytes());
                ex.getResponseBody().close();
            }
        });


        server.start();
        System.out.println("Server running at http://localhost:8080");
    }
}

