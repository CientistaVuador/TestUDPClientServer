/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cien.udptest;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Cien
 */
public class User {

    private static final AtomicLong userIdCounter = new AtomicLong(0);
    
    private final String name; //server, client
    private final long userId; //server, client
    
    private volatile boolean disconnected = false; //server, client
    private volatile int ping = 0; //server, client
    private volatile long lastPacketReceivedTime = 0; //server, client (from the server)
    
    private SocketAddress socketAddress; //server
    private volatile int nextMessageId = 0; //server
    private volatile long nextPingTime = 0; //server
    
    private volatile long typingTime = 0; //client
    
    public User(String name, SocketAddress socketAddress) {
        Objects.requireNonNull(name, "Name is null.");
        Objects.requireNonNull(socketAddress, "Socket Address is null.");
        
        this.name = name;
        this.socketAddress = socketAddress;
        this.userId = User.userIdCounter.incrementAndGet();
    }
    
    public User(String name, long id) {
        Objects.requireNonNull(name, "Name is null.");
        
        this.name = name;
        this.userId = id; 
   }

    public String getName() {
        return name;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public long getUserId() {
        return userId;
    }

    public long getLastPacketReceivedTime() {
        return lastPacketReceivedTime;
    }
    
     public boolean hasTimedOut() {
        return (System.currentTimeMillis() - this.lastPacketReceivedTime) >= Main.MAX_TIMEOUT_WARNINGS * 1000;
    }
    
    public boolean isDisconnected() {
        return this.disconnected;
    }
    
    public void disconnect() {
        this.disconnected = true;
    }
    
    public void updateLastPacketReceivedTime() {
        this.lastPacketReceivedTime = System.currentTimeMillis();
    }

    public int getNextMessageId() {
        return this.nextMessageId;
    }

    public void incrementMessageId() {
        this.nextMessageId++;
    }

    public long getNextPingTime() {
        return nextPingTime;
    }

    public void setNextPingTime(long nextPingTime) {
        this.nextPingTime = nextPingTime;
    }

    public int getPing() {
        return ping;
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    public boolean isTyping() {
        return this.typingTime != 0;
    }
    
    public void typing() {
        this.typingTime = System.currentTimeMillis() + Main.TYPING_DELAY;
    }
    
    public void stopTyping() {
        this.typingTime = 0;
    }
    
    public boolean hasTypingTimedOut() {
        return this.typingTime != 0 && System.currentTimeMillis() > this.typingTime;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final User other = (User) obj;
        return this.userId == other.userId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + (int) (this.userId ^ (this.userId >>> 32));
        return hash;
    }
    
    public String toDetailedString() {
        return "[user:"+this.name+",id:"+this.userId+",ip:"+this.socketAddress+",ping:"+this.ping+",disconnected:"+isDisconnected()+",timedOut:"+hasTimedOut()+"]";
    }
    
    @Override
    public String toString() {
        return this.name+", "+this.ping+" ms";
    }
}
