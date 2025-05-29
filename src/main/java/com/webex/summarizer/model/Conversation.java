package com.webex.summarizer.model;

import java.time.ZonedDateTime;
import java.util.List;

public class Conversation {
    private Room room;
    private List<Message> messages;
    private ZonedDateTime downloadDate;
    private String summary;
    private ZonedDateTime dateFrom;
    private ZonedDateTime dateTo;

    public Conversation() {
        this.downloadDate = ZonedDateTime.now();
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public ZonedDateTime getDownloadDate() {
        return downloadDate;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public ZonedDateTime getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(ZonedDateTime dateFrom) {
        this.dateFrom = dateFrom;
    }

    public ZonedDateTime getDateTo() {
        return dateTo;
    }

    public void setDateTo(ZonedDateTime dateTo) {
        this.dateTo = dateTo;
    }
}