/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cien.udptest;

import java.util.Objects;

/**
 *
 * @author Cien
 */
public class UserMessage {
    
    private final User user;
    private final String message;
    
    public UserMessage(User user, String message) {
        Objects.requireNonNull(user, "User is null.");
        Objects.requireNonNull(message, "Message is null.");
        this.user = user;
        this.message = message;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.user);
        hash = 53 * hash + Objects.hashCode(this.message);
        return hash;
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
        final UserMessage other = (UserMessage) obj;
        if (!Objects.equals(this.message, other.message)) {
            return false;
        }
        return Objects.equals(this.user, other.user);
    }

    @Override
    public String toString() {
        return this.user.toString()+" -> "+this.message;
    }
}
