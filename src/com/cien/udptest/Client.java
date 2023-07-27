/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cien.udptest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.SwingUtilities;

/**
 *
 * @author Cien
 */
public class Client extends Thread {

    private static interface IORunnable {

        public void run() throws IOException;
    }

    public static class UsernameConflictException extends Exception {

        private static final long serialVersionUID = 1L;

        public UsernameConflictException() {
            super();
        }
    }

    private final String username;
    private final DatagramSocket socket;
    private final Users users;
    private final User clientUser;

    private volatile RuntimeException receiverThreadException;
    private final Thread receiverThread;

    private final Queue<DatagramPacket> received = new ConcurrentLinkedQueue<>();

    private final Queue<IORunnable> tasks = new ConcurrentLinkedQueue<>();
    private final ClientGUI clientGUI;

    private String[] messages = new String[32];
    private int currentMessagesIndex = 0;

    private int currentServerMessageIndex = 0;

    private long lastPacketFromServerTime;
    private int lastTimeoutWarning = 0;

    private boolean firstPing = false;
    private boolean exit = false;

    public Client(String username, SocketAddress server) throws SocketException, IOException, InterruptedException, UsernameConflictException {
        Objects.requireNonNull(username, "Username is null.");
        Objects.requireNonNull(server, "Server Address is null.");
        this.username = username;
        this.socket = new DatagramSocket();
        this.socket.connect(server);
        this.users = new Users();

        byte[] usernameData = this.username.getBytes(StandardCharsets.UTF_8);
        if (usernameData.length > Main.PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Username is too large! Max is " + usernameData.length + " bytes.");
        }

        byte[] loginPacketData = new byte[Main.PACKET_SIZE];
        ByteBuffer loginPacketBuffer = ByteBuffer.wrap(loginPacketData);

        loginPacketBuffer
                .putLong(Main.MAGIC_NUMBER)
                .putInt(Main.LOGIN_PACKET)
                .putInt(usernameData.length)
                .put(usernameData);

        DatagramPacket loginPacket = new DatagramPacket(loginPacketData, loginPacketData.length);

        boolean timeout = true;
        for (int i = 0; i < 8; i++) {
            this.socket.send(loginPacket);

            try {
                this.socket.setSoTimeout(1000);
                this.socket.receive(loginPacket);
                this.socket.setSoTimeout(0);
                timeout = false;
                break;
            } catch (SocketTimeoutException ex) {
            }
        }
        if (timeout) {
            this.socket.close();
            throw new SocketTimeoutException("Login timed out after 8 tries.");
        }

        loginPacketBuffer.rewind();

        if (loginPacketBuffer.getLong() != Main.MAGIC_NUMBER) {
            this.socket.close();
            throw new IOException("Invalid magic number! Is this the correct server?");
        }

        if (loginPacketBuffer.getInt() != Main.LOGIN_PACKET) {
            this.socket.close();
            throw new IOException("Invalid packet id! Is the server corrupted?");
        }

        long userId = loginPacketBuffer.getLong();

        if (userId < 0) {
            this.socket.close();
            throw new UsernameConflictException();
        }

        this.clientUser = new User(this.username, userId);
        this.clientUser.updateLastPacketReceivedTime();
        this.users.add(this.clientUser);

        this.receiverThread = new Thread(() -> {
            try {
                while (true) {
                    DatagramPacket packet = new DatagramPacket(new byte[Main.PACKET_SIZE], Main.PACKET_SIZE);
                    this.socket.receive(packet);
                    this.received.add(packet);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }, "Receiver-Thread-" + server.toString());
        this.receiverThread.setDaemon(true);
        this.receiverThread.setUncaughtExceptionHandler((t, e) -> {
            this.receiverThreadException = new RuntimeException("Exception in " + t.getName(), e);
        });

        this.clientGUI = new ClientGUI(this);

        this.setUncaughtExceptionHandler((t, e) -> {
            SwingUtilities.invokeLater(() -> {
                this.clientGUI.onError(e);
            });
        });

        SwingUtilities.invokeLater(() -> {
            this.clientGUI.onUserConnected(clientUser);
        });

        this.lastPacketFromServerTime = System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void typing() {
        this.tasks.add(() -> {
            byte[] typingPacketData = new byte[Main.PACKET_SIZE];
            ByteBuffer.wrap(typingPacketData)
                    .putLong(Main.MAGIC_NUMBER)
                    .putInt(Main.TYPING_PACKET);

            this.socket.send(new DatagramPacket(typingPacketData, typingPacketData.length));
        });
    }

    public boolean sendMessage(String message) {
        byte[] messageData = message.getBytes(StandardCharsets.UTF_8);
        if (messageData.length > Main.PAYLOAD_SIZE) {
            return false;
        }

        this.tasks.add(() -> {
            byte[] messagePacketData = new byte[Main.PACKET_SIZE];
            ByteBuffer.wrap(messagePacketData)
                    .putLong(Main.MAGIC_NUMBER)
                    .putInt(Main.MESSAGE_PACKET)
                    .putInt(this.currentMessagesIndex)
                    .putInt(messageData.length)
                    .put(messageData);

            if (this.currentMessagesIndex >= this.messages.length) {
                this.messages = Arrays.copyOf(this.messages, this.messages.length * 2);
            }
            this.messages[this.currentMessagesIndex] = message;
            this.currentMessagesIndex++;

            this.socket.send(new DatagramPacket(messagePacketData, messagePacketData.length));
        });
        return true;
    }

    public void exit() {
        this.tasks.add(() -> {
            try (this.socket) {
                byte[] messagePacketData = new byte[Main.PACKET_SIZE];
                ByteBuffer.wrap(messagePacketData)
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.DISCONNECTED_PACKET);

                this.socket.send(new DatagramPacket(messagePacketData, messagePacketData.length));
            }

            this.exit = true;
        });
    }

    @Override
    public void run() {
        this.receiverThread.start();
        SwingUtilities.invokeLater(() -> {
            this.clientGUI.setLocationRelativeTo(null);
            this.clientGUI.setTitle(this.username);
            this.clientGUI.setVisible(true);
        });

        System.out.println("Current client identified as " + this.clientUser.toDetailedString());

        try {
            while (true) {
                loop();
                if (this.exit) {
                    break;
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void loop() throws InterruptedException, IOException {
        if (this.receiverThreadException != null) {
            throw this.receiverThreadException;
        }

        IORunnable t;
        while ((t = this.tasks.poll()) != null) {
            t.run();
        }

        if (this.exit) {
            return;
        }

        DatagramPacket r;
        while ((r = this.received.poll()) != null) {
            processPacket(r);
        }

        int timeoutWarning = (int) ((System.currentTimeMillis() - this.lastPacketFromServerTime) / 1000);
        if (timeoutWarning > Main.MAX_TIMEOUT_WARNINGS) {
            throw new SocketTimeoutException("Timed out");
        }
        if (timeoutWarning > this.lastTimeoutWarning) {
            SwingUtilities.invokeLater(() -> {
                this.clientGUI.onTimeout(timeoutWarning);
            });
            this.lastTimeoutWarning = timeoutWarning;
            System.out.println("Warning: Lost connection to the server, retrying... (" + timeoutWarning + "/" + Main.MAX_TIMEOUT_WARNINGS + ")");

            for (User s : this.users.getUsers()) {
                if (s.isDisconnected()) {
                    continue;
                }
                s.updateLastPacketReceivedTime();
            }
        } else if (timeoutWarning == 0 && this.lastTimeoutWarning != 0) {
            SwingUtilities.invokeLater(() -> {
                this.clientGUI.onConnectionRestored();
            });
            this.lastTimeoutWarning = 0;
            System.out.println("Info: Connection restored!");
        }

        for (User s : this.users.getUsers()) {
            if (s.isTyping() && s.hasTypingTimedOut()) {
                s.stopTyping();
                SwingUtilities.invokeLater(() -> {
                    this.clientGUI.onUserStopTyping(s);
                });
            }
            if (s.hasTimedOut() && !s.isDisconnected()) {
                s.disconnect();
                if (s.isTyping()) {
                    s.stopTyping();
                    SwingUtilities.invokeLater(() -> {
                        this.clientGUI.onUserStopTyping(s);
                    });
                }
                System.out.println("Info: " + s.toDetailedString() + " Timed out!");
                SwingUtilities.invokeLater(() -> {
                    this.clientGUI.onUserDisconnected(s);
                });
            }
        }

        Thread.sleep(Main.TPS);
    }

    private void processPacket(DatagramPacket packet) throws IOException {
        ByteBuffer packetBuffer = ByteBuffer.wrap(packet.getData());

        this.lastPacketFromServerTime = System.currentTimeMillis();

        if (packetBuffer.getLong() != Main.MAGIC_NUMBER) {
            System.out.println("Warning: Received packet with unknown magic number from server!");
            return;
        }

        int packetId = packetBuffer.getInt();

        switch (packetId) {
            case Main.RESEND_MESSAGE_PACKET -> {
                int from = packetBuffer.getInt();
                int to = packetBuffer.getInt();

                if (from < 0 || to > this.currentMessagesIndex) {
                    System.out.println("Warning: Server requested a invalid range of messages, from " + from + ", to " + to);
                    return;
                }

                for (int i = from; i < to; i++) {
                    String message = this.messages[i];
                    byte[] messageData = message.getBytes(StandardCharsets.UTF_8);

                    packetBuffer.rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.MESSAGE_PACKET)
                            .putInt(i)
                            .putInt(messageData.length)
                            .put(messageData);

                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                }
            }
            case Main.MESSAGE_PACKET -> {
                int currentMessageId = packetBuffer.getInt();
                long userId = packetBuffer.getLong();
                User user = this.users.getByUserId(userId);
                if (currentMessageId < this.currentServerMessageIndex) {
                    System.out.println("Warning: Received old message packet from server, discarded!");
                    return;
                }
                if (currentMessageId > this.currentServerMessageIndex) {
                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.RESEND_MESSAGE_PACKET)
                            .putInt(this.currentServerMessageIndex)
                            .putInt(currentMessageId + 1);
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                    System.out.println("Warning: Message packet drop detected from server! (" + ((currentMessageId + 1) - this.currentServerMessageIndex) + " packets!)");
                    return;
                }
                if (user == null) {
                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.IDENTIFY_PACKET)
                            .putLong(userId);
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                    System.out.println("Info: Received message from unknown user id " + userId + ", info requested.");
                    return;
                }
                this.currentServerMessageIndex++;

                if (user.isTyping()) {
                    user.stopTyping();
                    SwingUtilities.invokeLater(() -> {
                        this.clientGUI.onUserStopTyping(user);
                    });
                }

                int messageSize = packetBuffer.getInt();
                if (messageSize < 0 || messageSize > Main.PAYLOAD_SIZE) {
                    System.out.println("Warning: Received message packet with message size too large (or too small!) from server, " + messageSize + " bytes");
                    return;
                }

                if (!user.isDisconnected()) {
                    user.updateLastPacketReceivedTime();
                }

                byte[] messageData = new byte[messageSize];
                packetBuffer.get(messageData);
                String message = new String(messageData, StandardCharsets.UTF_8);

                SwingUtilities.invokeLater(() -> {
                    this.clientGUI.onMessageReceived(new UserMessage(user, message));
                });

                System.out.println("Info: " + user.toDetailedString() + " -> " + message);
            }
            case Main.IDENTIFY_PACKET -> {
                long userId = packetBuffer.getLong();
                boolean disconnected = packetBuffer.getInt() != 0;
                if (this.users.getByUserId(userId) != null) {
                    return;
                }

                int nameSize = packetBuffer.getInt();
                if (nameSize < 0 || nameSize > Main.PAYLOAD_SIZE) {
                    System.out.println("Warning: Received identify packet with name size too large (or too small!) from server, " + nameSize + " bytes");
                    return;
                }
                byte[] nameData = new byte[nameSize];
                packetBuffer.get(nameData);
                String name = new String(nameData, StandardCharsets.UTF_8);

                User u = new User(name, userId);
                if (disconnected) {
                    u.disconnect();
                } else {
                    u.updateLastPacketReceivedTime();

                    SwingUtilities.invokeLater(() -> {
                        this.clientGUI.onUserConnected(u);
                    });
                }
                this.users.add(u);

                System.out.println("Info: User ID " + userId + " identified as " + u.toDetailedString());
            }
            case Main.PING_PACKET -> {
                this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));

                packetBuffer.getLong();
                int serverMessagesLength = packetBuffer.getInt();
                if (serverMessagesLength < 0) {
                    System.out.println("Warning: Received ping packet from server with negative messages length.");
                }
                if (serverMessagesLength > this.currentServerMessageIndex) {
                    if (!this.firstPing) {
                        this.firstPing = true;
                        packetBuffer
                                .rewind()
                                .putLong(Main.MAGIC_NUMBER)
                                .putInt(Main.IDENTIFY_PACKET)
                                .putLong(-1);
                        this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                        System.out.println("Info: Requesting users from server.");
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        System.out.println("Warning: Client is running " + (serverMessagesLength - this.currentMessagesIndex) + " messages behind, requesting...");
                        packetBuffer
                                .rewind()
                                .putLong(Main.MAGIC_NUMBER)
                                .putInt(Main.RESEND_MESSAGE_PACKET)
                                .putInt(this.currentMessagesIndex)
                                .putInt(serverMessagesLength);
                        this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                    }
                }
            }
            case Main.USER_PING_VALUE_PACKET -> {
                long userId = packetBuffer.getLong();
                User user = this.users.getByUserId(userId);
                if (user == null) {
                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.IDENTIFY_PACKET)
                            .putLong(userId);
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength()));
                    System.out.println("Info: Received ping value from unknown user id " + userId + ", info requested.");
                    return;
                }
                if (user.isDisconnected()) {
                    System.out.println("Warning: Received ping value from server of a disconnected user! " + user.toDetailedString());
                    return;
                }

                int ping = packetBuffer.getInt();

                if (ping < 0) {
                    System.out.println("Warning: Received negative ping of user " + user.toDetailedString() + " from the server.");
                    return;
                }

                user.updateLastPacketReceivedTime();
                user.setPing(ping);

                SwingUtilities.invokeLater(() -> {
                    this.clientGUI.onUserPingUpdate(user);
                });
            }
            case Main.DISCONNECTED_PACKET -> {
                long userId = packetBuffer.getLong();
                User user = this.users.getByUserId(userId);
                if (user == null || user.isDisconnected()) {
                    return;
                }
                user.disconnect();
                if (user.isTyping()) {
                    user.stopTyping();
                    SwingUtilities.invokeLater(() -> {
                        this.clientGUI.onUserStopTyping(user);
                    });
                }
                System.out.println("Info: " + user.toDetailedString() + " Disconnected.");
                SwingUtilities.invokeLater(() -> {
                    this.clientGUI.onUserDisconnected(user);
                });
            }
            case Main.TYPING_PACKET -> {
                long userId = packetBuffer.getLong();
                User user = this.users.getByUserId(userId);
                if (user == null || user.isDisconnected()) {
                    return;
                }
                if (!user.isTyping()) {
                    SwingUtilities.invokeLater(() -> {
                        this.clientGUI.onUserStartTyping(user);
                    });
                }
                user.typing();
            }
        }
    }
}
