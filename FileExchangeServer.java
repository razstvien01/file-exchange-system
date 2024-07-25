import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class FileExchangeServer {
    private static final String STORAGE_DIR = "server_files";
    private static Set<String> registeredAliases = new HashSet<>();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java FileExchangeServer <IP address> <port>");
            return;
        }

        String ipAddress = args[0];
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Error: Invalid port number.");
            return;
        }

        System.out.println("File Exchange Server started.");
        System.out.println("Listening on IP address " + ipAddress + " and port " + port);

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(ipAddress))) {
            Files.createDirectories(Paths.get(STORAGE_DIR));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.out.println("Error: Could not start server.");
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String handle;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println("Connection to the File Exchange Server is successful!");
                String message;

                while ((message = in.readLine()) != null) {
                    processCommand(message, out, in);
                }
            } catch (IOException e) {
                System.out.println("Error: Connection with client lost.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Error: Failed to close client socket.");
                }
            }
        }

        private void processCommand(String message, PrintWriter out, BufferedReader in) throws IOException {
            if (message.startsWith("/register")) {
                handleRegister(message, out);
            } else if (message.startsWith("/store")) {
                handleStore(message, out, socket);
            } else if (message.equals("/dir")) {
                handleDir(out);
            } else if (message.startsWith("/get")) {
                handleGet(message, out, socket);
            } else if (message.equals("/leave")) {
                handleLeave(out);
            } else if (message.equals("/list")) {
                handleList(out);
            } else if (message.equals("/?")) {
                handleHelp(out);
            } else {
                out.println("Error: Command not found.");
            }
        }

        private void handleRegister(String message, PrintWriter out) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String alias = parts[1];
                if (registeredAliases.contains(alias)) {
                    out.println("Error: Handle or alias already exists.");
                } else {
                    registeredAliases.add(alias);
                    handle = alias;
                    out.println("Welcome " + alias + "!");
                }
            } else {
                out.println("Error: Command parameters do not match or are not allowed.");
            }
        }

        private void handleStore(String message, PrintWriter out, Socket socket) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String fileName = parts[1];
                out.println("Ready to receive file: " + fileName);
                File file = new File(STORAGE_DIR, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    int totalBytesRead = 0;
                    InputStream is = socket.getInputStream();

                    // Read the file size first
                    DataInputStream dis = new DataInputStream(is);
                    long fileSize = dis.readLong();

                    // Read the exact number of bytes expected for the file transfer
                    while (totalBytesRead < fileSize && (bytesRead = is.read(buffer, 0,
                            (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    fos.flush();

                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    out.println("File uploaded successfully: " + fileName + " " + timestamp);
                } catch (IOException e) {
                    out.println("Error: Failed to store file " + fileName);
                    e.printStackTrace();
                }
            } else {
                out.println("Error: Command parameters do not match or are not allowed.");
            }
        }

        private void handleDir(PrintWriter out) {
            File dir = new File(STORAGE_DIR);
            String[] files = dir.list();
            if (files != null) {
                out.println("Server Directory:");
                for (String file : files) {
                    out.println(file);
                }
            } else {
                out.println("Error: Failed to list files.");
            }
        }

        private void handleGet(String message, PrintWriter out, Socket socket) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String fileName = parts[1];
                File file = new File(STORAGE_DIR, fileName);
                if (file.exists()) {
                    out.println("Ready to send file: " + fileName);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            socket.getOutputStream().write(buffer, 0, bytesRead);
                        }
                        out.println("File sent successfully: " + fileName);
                    } catch (IOException e) {
                        out.println("Error: Failed to send file " + fileName);
                        e.printStackTrace();
                    }
                } else {
                    out.println("Error: File not found in the server.");
                }
            } else {
                out.println("Error: Command parameters do not match or are not allowed.");
            }
        }

        private void handleLeave(PrintWriter out) {
            out.println("Disconnecting...");
        }

        private void handleList(PrintWriter out) {
            out.println("Registered Handles:");
            for (String alias : registeredAliases) {
                out.println(alias);
            }
        }

        private void handleHelp(PrintWriter out) {
            out.println("/join <server_ip_add> <port>");
            out.println("/leave");
            out.println("/register <handle>");
            out.println("/store <filename>");
            out.println("/dir");
            out.println("/get <filename>");
            out.println("/list");
            out.println("/?");
        }
    }
}
