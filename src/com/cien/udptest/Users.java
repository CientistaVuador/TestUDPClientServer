/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cien.udptest;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class Users {
    
    private final Set<User> users = new HashSet<>();
    private final Map<SocketAddress, User> usersAddresses = new HashMap<>();
    private final Map<Long, User> usersIds = new HashMap<>();
    
    public Users() {
        
    }
    
    public boolean add(User user) {
        if (this.users.contains(user)) {
            return false;
        }
        this.users.add(user);
        this.usersAddresses.put(user.getSocketAddress(), user);
        this.usersIds.put(user.getUserId(), user);
        return true;
    }
    
    public boolean remove(User user) {
        boolean removed = this.users.remove(user);
        if (removed) {
            this.usersAddresses.remove(user.getSocketAddress());
            this.usersIds.remove(user.getUserId());
        }
        return removed;
    }
    
    public User getBySocketAddress(SocketAddress address) {
        return this.usersAddresses.get(address);
    }
    
    public User getByUserId(long id) {
        return this.usersIds.get(id);
    }
    
    public User[] getUsers() {
        return this.users.toArray(User[]::new);
    }
}
