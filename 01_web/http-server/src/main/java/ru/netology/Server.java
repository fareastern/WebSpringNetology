package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final List<String> VALID_PATHS = List.of(
            "/index.html",
            "/spring.svg",
            "/spring.png",
            "/resources.html",
            "/styles.css",
            "/app.js",
            "/links.html",
            "/forms.html",
            "/classic.html",
            "/events.html",
            "/events.js"
    );
    private static final Path PUBLIC_DIR = Path.of(".", "public");
    private final int port;
    /*
        Пул потоков:
        1. Оставляю только заранее разрешенные маршруты (как было на уроке)
        2. Работа через ExecutorService для удобного управления потоками
     */
    private final ExecutorService threadPool;

    public Server(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(64);
    }

    /*
        1. Создаем сокет
        2. В цикле принимаем подключения
        3. Подключения передаем в пул
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(clientSocket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
        Обрабатываем 1 подключение:
        1. Читаем HTTP запрос клиента
        2. Валидируем запрос
        3. Отправляем Responce
     */
    private void handleConnection(Socket socket) {
        try (
                socket;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );
                BufferedOutputStream out = new BufferedOutputStream(
                        socket.getOutputStream()
                )
        ) {

            String requestLine = in.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                errorResponse(out, 400, "Bad Request");
                return;
            }

            // Достаем метод, с которым пришел клиент, и путь, что хочет получить
            String method = parts[0];
            String path = parts[1];

            // Поддерживаем только GET как в оригинале
            if (!"GET".equals(method)) {
                errorResponse(out, 405, "Method Not Allowed");
                return;
            }

            if (!VALID_PATHS.contains(path)) {
                errorResponse(out, 404, "Not Found");
                return;
            }

            Path filePath = PUBLIC_DIR.resolve(path.substring(1));

            String mimeType = Files.probeContentType(filePath);

            // Отдельная обработка classic.html, так как там динамическое поле
            if ("/classic.html".equals(path)) {
                handleClassicHtml(filePath, mimeType, out);
                return;
            }

            handleStaticFile(filePath, mimeType, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Устанавливаем текущее время как в оригинале
    private void handleClassicHtml(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {

        String template = Files.readString(filePath);

        byte[] content = template
                .replace("{time}", LocalDateTime.now().toString())
                .getBytes();

        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());

        out.write(content);
        out.flush();
    }

    // Отправка статического файла
    private void handleStaticFile(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {

        long length = Files.size(filePath);

        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());

        Files.copy(filePath, out);
        out.flush();
    }

    // Отправка простого Response для ошибок
    private void errorResponse(BufferedOutputStream out, int statusCode, String statusText) throws IOException {

        out.write((
                "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());

        out.flush();
    }
}
