/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cien.udptest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author Cien
 */
public class Server extends Thread {

    private final DatagramSocket socket;
    private final Users users;

    private volatile RuntimeException receiverThreadException;
    private final Thread receiverThread;

    private final Queue<DatagramPacket> received = new ConcurrentLinkedQueue<>();

    private UserMessage[] messages = new UserMessage[64];
    private int currentMessagesIndex = 0;

    public Server(int port, InetAddress address) throws SocketException {
        this.socket = new DatagramSocket(port, address);
        this.users = new Users();

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
        }, "Receiver-Thread-" + port);
        this.receiverThread.setDaemon(true);
        this.receiverThread.setUncaughtExceptionHandler((t, e) -> {
            this.receiverThreadException = new RuntimeException("Exception in " + t.getName(), e);
        });
    }
    
    public Server(int port) throws SocketException {
        this(port, null);
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    @Override
    public void run() {
        this.receiverThread.start();

        try {
            while (true) {
                loop();
            }
        } catch (InterruptedException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void loop() throws InterruptedException, IOException {
        if (this.receiverThreadException != null) {
            throw this.receiverThreadException;
        }

        DatagramPacket r;
        while ((r = this.received.poll()) != null) {
            processPacket(r);
        }

        byte[] packetData = new byte[Main.PACKET_SIZE];
        ByteBuffer packetBuffer = ByteBuffer.wrap(packetData);
        for (User s : this.users.getUsers()) {
            if (s.hasTimedOut() && !s.isDisconnected()) {
                s.disconnect();
                System.out.println("Info: " + s.toDetailedString() + " Timed out!");
                continue;
            }
            if (System.currentTimeMillis() >= s.getNextPingTime()) {
                s.setNextPingTime(System.currentTimeMillis() + Main.PING_INTERVAL);
                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.PING_PACKET)
                        .putLong(System.currentTimeMillis())
                        .putInt(this.currentMessagesIndex);
                this.socket.send(new DatagramPacket(packetData, packetData.length, s.getSocketAddress()));
            }
        }

        Thread.sleep(Main.TPS);
    }

    private void processPacket(DatagramPacket packet) throws IOException {
        ByteBuffer packetBuffer = ByteBuffer.wrap(packet.getData());
        SocketAddress socketAddress = packet.getSocketAddress();
        User user = this.users.getBySocketAddress(socketAddress);
        
        if (user != null) {
            if (user.isDisconnected()) {
                System.out.println("Warning: Received packet from a disconnected user, ignoring. "+user.toDetailedString());
                return;
            }
            user.updateLastPacketReceivedTime();
        }

        if (packetBuffer.getLong() != Main.MAGIC_NUMBER) {
            System.out.println("Warning: Received packet with unknown magic number from " + socketAddress);
            return;
        }

        int packetId = packetBuffer.getInt();

        switch (packetId) {
            case Main.LOGIN_PACKET -> {
                if (user != null) {
                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.LOGIN_PACKET)
                            .putLong(user.getUserId());

                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
                    return;
                }
                
                int nameSize = packetBuffer.getInt();
                if (nameSize < 0 || nameSize > Main.PAYLOAD_SIZE) {
                    System.out.println("Warning: Received login packet with name size too large (or too small!) from " + socketAddress + ", " + nameSize);
                    return;
                }
                byte[] nameData = new byte[nameSize];
                packetBuffer.get(nameData);

                String name = new String(nameData, StandardCharsets.UTF_8);

                long userId = -1;
                
                boolean nameConflict = false;
                for (User u:this.users.getUsers()) {
                    if (u.isDisconnected()) {
                        continue;
                    }
                    if (u.getName().equals(name)) {
                        nameConflict = true;
                        break;
                    }
                }
                
                if (!nameConflict) {
                    user = new User(name, socketAddress);
                    user.updateLastPacketReceivedTime();
                    user.setNextPingTime(System.currentTimeMillis() + Main.PING_INTERVAL);

                    userId = user.getUserId();

                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.IDENTIFY_PACKET)
                            .putLong(user.getUserId())
                            .putInt(0)
                            .putInt(nameData.length)
                            .put(nameData);

                    for (User otherUser : this.users.getUsers()) {
                        if (otherUser.isDisconnected()) {
                            continue;
                        }
                        this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), otherUser.getSocketAddress()));
                    }

                    System.out.println("Info: " + user.toDetailedString() + " Connected!");
                    this.users.add(user);
                }

                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.LOGIN_PACKET)
                        .putLong(userId);

                this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
            }
        }

        if (user == null) {
            return;
        }

        switch (packetId) {
            case Main.MESSAGE_PACKET -> {
                int currentMessageId = packetBuffer.getInt();
                if (currentMessageId < user.getNextMessageId()) {
                    System.out.println("Warning: Received old message packet from " + user.toDetailedString() + ", discarded!");
                    return;
                }
                if (currentMessageId > user.getNextMessageId()) {
                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.RESEND_MESSAGE_PACKET)
                            .putInt(user.getNextMessageId())
                            .putInt(currentMessageId + 1);
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
                    System.out.println("Warning: Message packet drop detected from " + user.toDetailedString() + "! (" + ((currentMessageId + 1) - user.getNextMessageId()) + " packets!)");
                    return;
                }
                user.incrementMessageId();

                int messageSize = packetBuffer.getInt();
                if (messageSize < 0 || messageSize > Main.PAYLOAD_SIZE) {
                    System.out.println("Warning: Received message packet with message size too large (or too small!) from " + socketAddress + ", " + messageSize + " bytes");
                    return;
                }
                byte[] messageData = new byte[messageSize];
                packetBuffer.get(messageData);

                String message = new String(messageData, StandardCharsets.UTF_8);

                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.MESSAGE_PACKET)
                        .putInt(this.currentMessagesIndex)
                        .putLong(user.getUserId())
                        .putInt(messageData.length)
                        .put(messageData);

                for (User u : this.users.getUsers()) {
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), u.getSocketAddress()));
                }

                if (this.currentMessagesIndex >= this.messages.length) {
                    this.messages = Arrays.copyOf(this.messages, this.messages.length * 2);
                }
                this.messages[this.currentMessagesIndex] = new UserMessage(user, message);
                this.currentMessagesIndex++;

                System.out.println("Info: " + user.toDetailedString() + " -> " + message);
            }
            case Main.IDENTIFY_PACKET -> {
                long userId = packetBuffer.getLong();
                if (userId == -1) {
                    for (User otherUser : this.users.getUsers()) {
                        if (otherUser.equals(user)) {
                            continue;
                        }
                        
                        String username = otherUser.getName();
                        byte[] usernameData = username.getBytes(StandardCharsets.UTF_8);

                        packetBuffer
                                .rewind()
                                .putLong(Main.MAGIC_NUMBER)
                                .putInt(Main.IDENTIFY_PACKET)
                                .putLong(otherUser.getUserId())
                                .putInt(otherUser.isDisconnected() ? 1 : 0)
                                .putInt(usernameData.length)
                                .put(usernameData);
                        this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
                    }
                    return;
                }

                User otherUser = this.users.getByUserId(userId);
                if (otherUser == null) {
                    System.out.println("Warning: " + user.toDetailedString() + " requested info about unknown user id " + userId + "!");
                    return;
                }

                String username = otherUser.getName();
                byte[] usernameData = username.getBytes(StandardCharsets.UTF_8);

                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.IDENTIFY_PACKET)
                        .putLong(otherUser.getUserId())
                        .putInt(otherUser.isDisconnected() ? 1 : 0)
                        .putInt(usernameData.length)
                        .put(usernameData);
                this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
            }
            case Main.RESEND_MESSAGE_PACKET -> {
                int from = packetBuffer.getInt();
                int to = packetBuffer.getInt();

                if (to == -1) {
                    to = this.currentMessagesIndex;
                }
                if (from < 0 || to > this.currentMessagesIndex) {
                    System.out.println("Warning: " + user.toDetailedString() + " requested a invalid range of messages, from " + from + ", to " + to);
                    return;
                }

                for (int i = from; i < to; i++) {
                    UserMessage message = this.messages[i];
                    byte[] messageData = message.getMessage().getBytes(StandardCharsets.UTF_8);

                    packetBuffer
                            .rewind()
                            .putLong(Main.MAGIC_NUMBER)
                            .putInt(Main.MESSAGE_PACKET)
                            .putInt(i)
                            .putLong(message.getUser().getUserId())
                            .putInt(messageData.length)
                            .put(messageData);

                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), socketAddress));
                }
            }
            case Main.PING_PACKET -> {
                long time = packetBuffer.getLong();
                int ping = (int) ((System.currentTimeMillis() - time) / 2);

                if (ping < 0) {
                    System.out.println("Warning: " + user.toDetailedString() + " sent an invalid ping time (negative ping!), " + time);
                    return;
                }

                user.setPing(ping);

                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.USER_PING_VALUE_PACKET)
                        .putLong(user.getUserId())
                        .putInt(ping);
                for (User s : this.users.getUsers()) {
                    if (s.isDisconnected()) {
                        continue;
                    }
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), s.getSocketAddress()));
                }
            }
            case Main.DISCONNECTED_PACKET -> {
                user.disconnect();
                System.out.println("Info: " + user.toDetailedString() + " Disconnected!");
                
                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.DISCONNECTED_PACKET)
                        .putLong(user.getUserId());
                for (User s : this.users.getUsers()) {
                    if (s.isDisconnected()) {
                        continue;
                    }
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), s.getSocketAddress()));
                }
            }
            case Main.TYPING_PACKET -> {
                packetBuffer
                        .rewind()
                        .putLong(Main.MAGIC_NUMBER)
                        .putInt(Main.TYPING_PACKET)
                        .putLong(user.getUserId());
                
                for (User s : this.users.getUsers()) {
                    if (s.isDisconnected()) {
                        continue;
                    }
                    if (s.equals(user)) {
                        continue;
                    }
                    this.socket.send(new DatagramPacket(packet.getData(), packet.getLength(), s.getSocketAddress()));
                }
            }
        }
    }
}
