import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

public class FileExchangeClient extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private JTextArea outputArea;
    private JTextField inputField;
    private JFileChooser fileChooser;
    private Thread readThread;

    public FileExchangeClient() {
        setTitle("File Exchange Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.PINK);

        // Output area (screen)
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(450, 500));
        add(scrollPane, BorderLayout.NORTH);

        // Command buttons panel
        JPanel commandPanel = new JPanel();
        commandPanel.setLayout(new GridLayout(4, 3, 5, 5));
        commandPanel.setBackground(Color.PINK);

        // Buttons
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> {
            JTextField serverIPField = new JTextField("");
            JTextField portField = new JTextField("");

            JPanel panel = new JPanel(new GridLayout(2, 2));
            panel.add(new JLabel("Server IP:"));
            panel.add(serverIPField);
            panel.add(new JLabel("Port:"));
            panel.add(portField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Connect to Server", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String serverIP = serverIPField.getText();
                int port = Integer.parseInt(portField.getText());
                connect(serverIP, port);
            }
        });

        JButton disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(e -> {
            if (socket == null || socket.isClosed()) {
                outputArea.append("\nError: Disconnection failed. Please connect to the server first.\n\n");
                JOptionPane.showMessageDialog(this,
                        "Error: Disconnection failed. Please connect to the server first.\n\n", "Disconnection Error",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                sendCommand("/leave");
                closeConnection();
            }
        });

        JButton registerButton = new JButton("Register");
        registerButton.addActionListener(e -> {
            String handle = JOptionPane.showInputDialog("Enter your alias:");
            if (handle != null && !handle.trim().isEmpty()) {
                outputArea.append("\nRegistering alias: " + handle + "\n");
                sendCommand("/register " + handle);
            } else {
                JOptionPane.showMessageDialog(this, "Alias cannot be empty.", "Registration Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton storeButton = new JButton("Store File");
        storeButton.addActionListener(e -> {
            if (socket == null || socket.isClosed()) {
                outputArea.append("\nError: Storing file failed. Please connect to the server first.\n\n");
                JOptionPane.showMessageDialog(this,
                        "Error: Storing file failed. Please connect to the server first.\n\n", "Store File Error",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                int returnValue = fileChooser.showOpenDialog(this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    outputArea.append("Storing file: " + selectedFile.getName() + "\n");
                    storeFile(selectedFile);
                }
            }
        });

        JButton dirButton = new JButton("List Server Directory");
        dirButton.addActionListener(e -> sendCommand("/dir"));

        JButton getButton = new JButton("Get File");
        getButton.addActionListener(e -> {
            String fileName = JOptionPane.showInputDialog("Enter the filename to get:");
            if (fileName != null && !fileName.trim().isEmpty()) {
                outputArea.append("Requesting file: " + fileName + "\n");
                sendCommand("/get " + fileName);
                receiveFile(fileName);
            } else {
                JOptionPane.showMessageDialog(this, "Filename cannot be empty.", "Get File Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton listButton = new JButton("List Registered Users");
        listButton.addActionListener(e -> sendCommand("/list"));

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> sendCommand("/?"));

        commandPanel.add(connectButton);
        commandPanel.add(disconnectButton);
        commandPanel.add(registerButton);
        commandPanel.add(storeButton);
        commandPanel.add(dirButton);
        commandPanel.add(getButton);
        commandPanel.add(listButton);
        commandPanel.add(helpButton);
        add(commandPanel, BorderLayout.CENTER);

        // Input field (keyboard)
        inputField = new JTextField();
        inputField.setEditable(true);
        inputField.addActionListener(e -> {
            String command = inputField.getText().trim();
            inputField.setText("");
            outputArea.append("Sending command: " + command + "\n");
            sendCommand(command);
        });

        add(inputField, BorderLayout.SOUTH);

        // File chooser
        fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void connect(String serverIP, int port) {
        try {
            socket = new Socket(serverIP, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputArea.append("Connected to server: " + serverIP + " on port " + port + "\n");

            readThread = new Thread(this::readServerMessages);
            readThread.start();
        } catch (IOException e) {
            outputArea
                    .append("Error: Connection to the Serveer has failed! Please check IP Address and Port Number.\n");
            JOptionPane.showMessageDialog(this,
                    "Error: Connection to the Serveer has failed! Please check IP Address and Port Number.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendCommand(String command) {
        if (command.startsWith("/join")) {
            String[] parts = command.split(" ");
            if (parts.length == 3) {
                String serverIP = parts[1];
                int port;
                try {
                    port = Integer.parseInt(parts[2]);
                    connect(serverIP, port);
                    return;
                } catch (NumberFormatException e) {
                    outputArea.append("Error: Invalid port number.\n");
                    return;
                }
            } else {
                outputArea.append("Error: Command parameters do not match or are not allowed.\n");
                return;
            }
        }
        if (out != null) {
            out.println(command);
        } else {
            outputArea.append("Error: Not connected to server.\n");
        }
    }

    private void storeFile(File file) {
        if (!file.exists() || file.isDirectory()) {
            outputArea.append("Error: File not found.\n");
            JOptionPane.showMessageDialog(this, "Error: File not found.", "File Transfer Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        sendCommand("/store " + file.getName());
        sendFile(file);
    }

    private void sendFile(File file) {
        try (FileInputStream fis = new FileInputStream(file);
                OutputStream os = socket.getOutputStream()) {

            DataOutputStream dos = new DataOutputStream(os);
            dos.writeLong(file.length());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            outputArea.append("Error: Unable to send file.\n");
            JOptionPane.showMessageDialog(this, "Error: Unable to send file.", "File Transfer Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receiveFile(String fileName) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send the GET command to the server
            out.println("/get " + fileName);

            // Read the server's response
            String serverResponse = in.readLine();
            System.out.println("Server Response: " + serverResponse);

            if (!serverResponse.contains("Error")) {
                // Read and print file contents before receiving the binary data
                StringBuilder fileContents = new StringBuilder();
                String line;

                // Read file content until end marker is found
                while (!(line = in.readLine()).isEmpty()) {
                    fileContents.append(line).append("\n");
                }

                // Print file contents to console or output area
                outputArea.append("File contents:\n" + fileContents.toString() + "\n");

                // Receive and save the binary data
                File file = new File(fileName);
                try (FileOutputStream fos = new FileOutputStream(file);
                        InputStream is = socket.getInputStream()) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    fos.flush();
                    outputArea.append("File received: " + fileName + "\n");
                } catch (IOException e) {
                    outputArea.append("Error: Unable to save the received file.\n");
                    JOptionPane.showMessageDialog(this, "Error: Unable to save the received file.",
                            "File Transfer Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                // Handle the error response from the server
                outputArea.append("Error: " + serverResponse + "\n");
                JOptionPane.showMessageDialog(this, serverResponse, "File Transfer Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            outputArea.append("Error: Unable to receive file from server.\n");
            JOptionPane.showMessageDialog(this, "Error: Unable to receive file from server.", "File Transfer Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void readServerMessages() {
        String message;
        try {
            while ((message = in.readLine()) != null) {
                outputArea.append(message + "\n");
            }
        } catch (IOException e) {
            outputArea.append("Error: Connection with server lost.\n");
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (readThread != null) {
                readThread.interrupt();
            }
            outputArea.append("Connection closed. Thank you!.\n");
        } catch (IOException e) {
            outputArea.append("Error: Unable to close connection.\n");
            JOptionPane.showMessageDialog(this, "Error: Unable to close connection.", "Disconnection Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FileExchangeClient::new);
    }
}
