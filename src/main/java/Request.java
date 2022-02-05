import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Request implements Runnable {
    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css",
            "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final Socket socket;

    public Request(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                socket;
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                return;
            }

            final var pathAndQuery = parts[1];
            System.out.println("Параметры");
            var parsResultParams = getQueryParams(pathAndQuery);
            var path = getQueryParamsPath(pathAndQuery);
            System.out.println(parsResultParams);
            System.out.println(path);
            if (!validPaths.contains(path)) {
                out.write((
                        errorMessage()).getBytes());
                out.flush();
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath); //"application/octet-stream" "text/plain"

            // special case for classic
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                return;
            }

            final var length = Files.size(filePath);
            out.write((
                    okMassage(mimeType, length)).getBytes());
            Files.copy(filePath, out);
            out.flush();
        } catch (
                IOException e) {
            e.printStackTrace();
        }
    }

    private String errorMessage() {
        return "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    private String okMassage(String mimeType, long length) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    private MultiMap getQueryParams(String url) {
        MultiMap parameter = new MultiValueMap();
        List<NameValuePair> params;
        try {
            params = URLEncodedUtils.parse(new URI(url), StandardCharsets.UTF_8);
            for (NameValuePair param : params) {
                if (param.getName() != null && param.getValue() != null)
                    parameter.put(param.getName(), param.getValue());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return parameter;
    }

    private String getQueryParamsPath(String url) {
        String result;
        int i = url.indexOf("?");
        if (i == -1) {
            return url;
        }
        result = url.substring(0, i);
        return result;
    }
}