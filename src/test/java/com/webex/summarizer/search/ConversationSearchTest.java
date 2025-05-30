package com.webex.summarizer.search;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConversationSearchTest {

    private ConversationSearch conversationSearch;
    private Conversation testConversation;

    @BeforeEach
    public void setup() {
        conversationSearch = new ConversationSearch();
        
        // Set up test conversation with sample messages
        testConversation = new Conversation();
        
        Room room = new Room();
        room.setId("testRoomId");
        room.setTitle("Test Room");
        room.setType("group");
        
        testConversation.setRoom(room);
        
        List<Message> messages = new ArrayList<>();
        
        // Add sample messages
        messages.add(createMessage("1", "john.doe@example.com", 
                "Hello everyone, we need to discuss the project timeline.", 
                ZonedDateTime.of(2025, 5, 1, 9, 0, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("2", "jane.smith@example.com", 
                "I agree, the deadline is approaching fast.", 
                ZonedDateTime.of(2025, 5, 1, 9, 5, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("3", "peter.jones@example.com", 
                "Can we schedule a meeting for tomorrow to review progress?", 
                ZonedDateTime.of(2025, 5, 1, 9, 10, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("4", "jane.smith@example.com", 
                "Yes, I'm available tomorrow afternoon.", 
                ZonedDateTime.of(2025, 5, 1, 9, 15, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("5", "john.doe@example.com", 
                "Great, let's schedule it for 2 PM tomorrow then.", 
                ZonedDateTime.of(2025, 5, 1, 9, 20, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("6", "alice.wang@example.com", 
                "I won't be able to make it at 2 PM. Can we do it earlier?", 
                ZonedDateTime.of(2025, 5, 1, 9, 25, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("7", "john.doe@example.com", 
                "How about 11 AM instead?", 
                ZonedDateTime.of(2025, 5, 1, 9, 30, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("8", "peter.jones@example.com", 
                "11 AM works for me.", 
                ZonedDateTime.of(2025, 5, 1, 9, 35, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("9", "alice.wang@example.com", 
                "11 AM is perfect. I'll prepare the project report by then.", 
                ZonedDateTime.of(2025, 5, 2, 9, 0, 0, 0, ZoneId.systemDefault())));
        
        messages.add(createMessage("10", "jane.smith@example.com", 
                "I'll update the presentation with the latest numbers.", 
                ZonedDateTime.of(2025, 5, 2, 9, 5, 0, 0, ZoneId.systemDefault())));
        
        testConversation.setMessages(messages);
    }

    @Test
    public void testSearchMessages_BasicQuery() {
        // Test simple search
        List<Message> results = conversationSearch.searchMessages(testConversation, "meeting");
        
        assertEquals(1, results.size());
        assertEquals("3", results.get(0).getId());
        assertTrue(results.get(0).getText().contains("meeting"));
    }

    @Test
    public void testSearchMessages_MultipleMatches() {
        // Test search with multiple matches
        List<Message> results = conversationSearch.searchMessages(testConversation, "11 AM");
        
        assertEquals(3, results.size());
        assertEquals("7", results.get(0).getId());
        assertEquals("8", results.get(1).getId());
        assertEquals("9", results.get(2).getId());
    }

    @Test
    public void testSearchMessages_CaseInsensitive() {
        // Test case-insensitive search
        List<Message> resultsLower = conversationSearch.searchMessages(testConversation, "project");
        List<Message> resultsUpper = conversationSearch.searchMessages(testConversation, "PROJECT");
        
        assertEquals(resultsLower.size(), resultsUpper.size());
        assertEquals(2, resultsLower.size());
    }

    @Test
    public void testSearchMessages_WithDateRange() {
        // Test search with date range
        ZonedDateTime startDate = ZonedDateTime.of(2025, 5, 2, 0, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime endDate = ZonedDateTime.of(2025, 5, 2, 23, 59, 59, 0, ZoneId.systemDefault());
        
        List<Message> results = conversationSearch.searchMessages(testConversation, "update", startDate, endDate);
        
        assertEquals(1, results.size());
        assertEquals("10", results.get(0).getId());
    }

    @Test
    public void testSearchMessages_NoMatches() {
        // Test search with no matches
        List<Message> results = conversationSearch.searchMessages(testConversation, "nonexistentterm");
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearchMessages_NullOrEmptyConversation() {
        // Test with null conversation
        List<Message> results1 = conversationSearch.searchMessages(null, "test");
        assertTrue(results1.isEmpty());
        
        // Test with empty messages
        Conversation emptyConversation = new Conversation();
        emptyConversation.setMessages(new ArrayList<>());
        List<Message> results2 = conversationSearch.searchMessages(emptyConversation, "test");
        assertTrue(results2.isEmpty());
    }

    @Test
    public void testGetMessageContext() {
        // Find a message
        Message targetMessage = findMessageById(testConversation.getMessages(), "5");
        assertNotNull(targetMessage);
        
        // Get context with 1 message before and after
        List<Message> context = conversationSearch.getMessageContext(testConversation, targetMessage, 1);
        
        assertEquals(3, context.size());
        assertEquals("4", context.get(0).getId());
        assertEquals("5", context.get(1).getId());
        assertEquals("6", context.get(2).getId());
    }

    @Test
    public void testGetMessageContext_AtStart() {
        // Test context at the start of conversation
        Message targetMessage = findMessageById(testConversation.getMessages(), "1");
        assertNotNull(targetMessage);
        
        List<Message> context = conversationSearch.getMessageContext(testConversation, targetMessage, 2);
        
        assertEquals(3, context.size());
        assertEquals("1", context.get(0).getId());
        assertEquals("2", context.get(1).getId());
        assertEquals("3", context.get(2).getId());
    }

    @Test
    public void testGetMessageContext_AtEnd() {
        // Test context at the end of conversation
        Message targetMessage = findMessageById(testConversation.getMessages(), "10");
        assertNotNull(targetMessage);
        
        List<Message> context = conversationSearch.getMessageContext(testConversation, targetMessage, 2);
        
        assertEquals(3, context.size());
        assertEquals("8", context.get(0).getId());
        assertEquals("9", context.get(1).getId());
        assertEquals("10", context.get(2).getId());
    }

    @Test
    public void testFormatSearchResults() {
        // Test formatting of search results
        List<Message> messages = new ArrayList<>();
        messages.add(createMessage("1", "john.doe@example.com", "Test message", 
                ZonedDateTime.of(2025, 5, 1, 9, 0, 0, 0, ZoneId.systemDefault())));
        
        String formatted = conversationSearch.formatSearchResults(messages);
        
        assertTrue(formatted.contains("Message 1 of 1"));
        assertTrue(formatted.contains("From: john.doe@example.com"));
        assertTrue(formatted.contains("Content: Test message"));
    }

    // Helper method to create a test Message
    private Message createMessage(String id, String personEmail, String text, ZonedDateTime created) {
        Message message = new Message();
        message.setId(id);
        message.setPersonEmail(personEmail);
        message.setText(text);
        message.setCreated(created);
        return message;
    }
    
    // Helper method to find a message by ID
    private Message findMessageById(List<Message> messages, String id) {
        for (Message message : messages) {
            if (message.getId().equals(id)) {
                return message;
            }
        }
        return null;
    }
}