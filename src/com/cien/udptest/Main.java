/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package com.cien.udptest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author Cien
 */
public class Main {

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.WARNING, "Native Look and Feel not supported.", ex);
        }
    }

    public static final int PACKET_SIZE = 512;
    public static final int PAYLOAD_SIZE = PACKET_SIZE - 64;
    public static final long MAGIC_NUMBER = -3534974220920654048L;
    public static final int TPS = 1000 / 60; //60 ticks per second
    public static final int MAX_TIMEOUT_WARNINGS = 10;
    public static final int PING_INTERVAL = 400;
    public static final int TYPING_DELAY = 3000;
    
    public static final int LOGIN_PACKET = 0;
    public static final int MESSAGE_PACKET = 1;
    public static final int RESEND_MESSAGE_PACKET = 2;
    public static final int IDENTIFY_PACKET = 3;
    public static final int PING_PACKET = 4;
    public static final int USER_PING_VALUE_PACKET = 5;
    public static final int DISCONNECTED_PACKET = 6;
    public static final int TYPING_PACKET = 7;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("-client <name> <ip> <port>");
            System.out.println("-server <port> [ip]");
            return;
        }
        switch (args[0]) {
            case "-client" -> {
                if (args.length > 4) {
                    System.out.println("Too much arguments!");
                } else if (args.length < 4) {
                    System.out.println("Too little arguments!");
                }
                if (args.length != 4) {
                    System.out.println("Usage: -client <name> <ip> <port>");
                    return;
                }

                String name = args[1];

                String host = args[2];

                int port;
                try {
                    port = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid port number: " + ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                    return;
                }

                SocketAddress serverAddress;
                try {
                    serverAddress = new InetSocketAddress(InetAddress.getByName(host), port);
                } catch (UnknownHostException ex) {
                    System.out.println("Invalid host: " + ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                    return;
                }
                
                try {
                    Client c = new Client(name, serverAddress);
                    System.out.println("Connected with success to " + c.getSocket().getRemoteSocketAddress());
                    System.out.println("Starting...");
                    c.start();
                } catch (SocketTimeoutException ex) {
                    System.out.println("Timeout! Could not connect: " + ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                } catch (PortUnreachableException ex) {
                    System.out.println("Port unreachable!");
                    System.out.println("Usage: -client <name> <ip> <port>");
                } catch (SocketException | IllegalArgumentException ex) {
                    System.out.println("Error: " + ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                } catch (IOException ex) {
                    System.out.println("Error! Could not connect: " + ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                } catch (InterruptedException ex) {
                    System.out.println("Client thread interrupted: "+ex.getMessage());
                    System.out.println("Usage: -client <name> <ip> <port>");
                } catch (Client.UsernameConflictException ex) {
                    System.out.println("This username is already being used!");
                    System.out.println("Usage: -client <name> <ip> <port>");
                }
            }
            case "-server" -> {
                if (args.length > 3) {
                    System.out.println("Too much arguments!");
                    System.out.println("Usage: -server <port> [ip]");
                    return;
                } else if (args.length < 2) {
                    System.out.println("Too little arguments!");
                    System.out.println("Usage: -server <port> [ip]");
                    return;
                }
                
                int port;
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid port number: " + ex.getMessage());
                    System.out.println("Usage: -server <port> [ip]");
                    return;
                }
                
                InetAddress address = null;
                if (args.length == 3) {
                    try {
                        address = InetAddress.getByName(args[2]);
                    } catch (UnknownHostException ex) {
                        System.out.println("Unknown ip: "+ex.getMessage());
                        System.out.println("Usage: -server <port> [ip]");
                        return;
                    }
                }
                
                try {
                    Server s = new Server(port, address);
                    s.start();
                    System.out.println("Server started on port "+port);
                } catch (SocketException | IllegalArgumentException ex) {
                    System.out.println("Invalid port: " + ex.getMessage());
                    System.out.println("Usage: -server <port> [ip]");
                }
            }
            default -> {
                System.out.println("Unknown option '" + args[0] + "'");
                System.out.println("Usage:");
                System.out.println("-client <name> <ip> <port>");
                System.out.println("-server <port>");
            }
        }
    }

}
