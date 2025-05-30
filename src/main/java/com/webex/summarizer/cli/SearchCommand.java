package com.webex.summarizer.cli;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.search.ConversationSearch;
import com.webex.summarizer.search.QuestionAnswerer;
import com.webex.summarizer.storage.ConversationStorage;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "search", description = "Search WebEx conversations and get AI-generated answers to questions", mixinStandardHelpOptions = true)
public class SearchCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(SearchCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";
    
    @Option(names = {"-o", "--output-dir"}, description = "Output directory for downloaded conversations")
    private String outputDir;
    
    @Option(names = {"--file"}, description = "Path to a saved conversation file to search", required = true)
    private String filePath;
    
    @Option(names = {"--query", "-q"}, description = "Search query to find matching messages")
    private String searchQuery;
    
    @Option(names = {"--question", "-Q"}, description = "A question to answer based on the conversation")
    private String question;
    
    @Option(names = {"--context", "-n"}, description = "Number of messages to include before and after each match for context")
    private Integer contextSize = 2;
    
    @Option(names = {"--start-date", "--from"}, description = "Start date for filtering messages (format: yyyy-MM-dd)")
    private String startDateStr;
    
    @Option(names = {"--end-date", "--to"}, description = "End date for filtering messages (format: yyyy-MM-dd)")
    private String endDateStr;
    
    @Option(names = {"-p", "--aws-profile"}, description = "AWS profile to use")
    private String awsProfile;
    
    @Option(names = {"--region"}, description = "AWS region to use")
    private String awsRegion;
    
    @Option(names = {"-m", "--model"}, description = "AWS Bedrock model ID to use")
    private String modelId;
    
    @Override
    public Integer call() throws Exception {
        try {
            ConfigLoader configLoader = new ConfigLoader(configPath);
            
            // Set output directory
            if (outputDir == null) {
                outputDir = configLoader.getProperty("storage.directory", "conversations");
            }
            
            ConversationStorage storage = new ConversationStorage(outputDir);
            ConversationSearch searcher = new ConversationSearch();

            // Load conversation
            Conversation conversation = loadConversation(filePath, storage);
            if (conversation == null) {
                return 1;
            }

            // Parse dates if provided
            ZonedDateTime startDate = parseStartDate(startDateStr);
            ZonedDateTime endDate = parseEndDate(endDateStr);
            
            // Search for messages
            if (searchQuery != null && !searchQuery.isEmpty()) {
                performSearch(conversation, searcher, searchQuery, startDate, endDate);
            }
            
            // Answer a question
            if (question != null && !question.isEmpty()) {
                answerQuestion(conversation, question, searcher, configLoader, startDate, endDate);
            }
            
            // If neither search nor question was provided
            if ((searchQuery == null || searchQuery.isEmpty()) && 
                (question == null || question.isEmpty())) {
                System.err.println("Please provide either a search query (--query) or a question (--question)");
                return 1;
            }
            
            return 0;
        } catch (Exception e) {
            logger.error("Failed to execute search command: {}", e.getMessage(), e);
            System.err.println("Failed to execute search command: " + e.getMessage());
            return 1;
        }
    }
    
    private Conversation loadConversation(String filePath, ConversationStorage storage) throws IOException {
        if (!Paths.get(filePath).toFile().exists()) {
            System.err.println("File not found: " + filePath);
            return null;
        }
        
        System.out.println("Loading conversation from " + filePath);
        return storage.loadConversation(filePath);
    }
    
    private void performSearch(
            Conversation conversation, 
            ConversationSearch searcher, 
            String query, 
            ZonedDateTime startDate, 
            ZonedDateTime endDate) {
        List<Message> matchingMessages;
        
        // Search with or without date filtering
        if (startDate != null || endDate != null) {
            matchingMessages = searcher.searchMessages(conversation, query, startDate, endDate);
        } else {
            matchingMessages = searcher.searchMessages(conversation, query);
        }
        
        // Display results with enhanced formatting
        System.out.println("\n");
        System.out.println("╭───────────────────────────────────────────────────────────────────────────────╮");
        System.out.println("│                           📄 SEARCH RESULTS                                   │");
        System.out.println("╰───────────────────────────────────────────────────────────────────────────────╯");
        
        if (matchingMessages.isEmpty()) {
            System.out.println("\n❌ No messages found matching query: '" + query + "'");
            return;
        }
        
        System.out.println("\n🔍 Found " + matchingMessages.size() + " messages matching query: '" + query + "'");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Display each match with context
        for (int i = 0; i < matchingMessages.size(); i++) {
            Message match = matchingMessages.get(i);
            
            System.out.println("\n┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
            System.out.println("┃ 🔎 Match " + (i + 1) + " of " + matchingMessages.size() + " (with context)                                         ┃");
            System.out.println("┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
            
            // Get context around this match
            List<Message> contextMessages = searcher.getMessageContext(conversation, match, contextSize);
            
            // Display each message in the context
            for (int j = 0; j < contextMessages.size(); j++) {
                Message msg = contextMessages.get(j);
                boolean isMatch = msg.getId().equals(match.getId());
                
                // Format time and sender
                String time = msg.getCreated().format(TIME_FORMATTER);
                String date = msg.getCreated().format(DATE_FORMATTER);
                String sender = formatSender(msg.getPersonEmail());
                
                // Display with special formatting for the actual match
                if (isMatch) {
                    System.out.println("\n>> " + date + " " + time + " | " + sender);
                    
                    // Split message by lines and highlight query terms
                    String text = msg.getText();
                    if (text != null) {
                        String[] lines = text.split("\n");
                        for (String line : lines) {
                            String highlightedLine = highlightQueryTerms(line, query);
                            System.out.println(">> " + highlightedLine);
                        }
                    }
                } else {
                    System.out.println("\n" + date + " " + time + " | " + sender);
                    
                    // Display context message content
                    String text = msg.getText();
                    if (text != null) {
                        String[] lines = text.split("\n");
                        for (String line : lines) {
                            System.out.println("   " + line);
                        }
                    }
                }
            }
            
            System.out.println("\n╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌");
        }
    }
    
    private void answerQuestion(
            Conversation conversation, 
            String question,
            ConversationSearch searcher, 
            ConfigLoader configLoader,
            ZonedDateTime startDate,
            ZonedDateTime endDate) throws IOException {
        
        // Initialize the question answerer
        String profile = awsProfile != null ? awsProfile : configLoader.getProperty("aws.profile", "default");
        String region = awsRegion != null ? awsRegion : configLoader.getProperty("aws.region", "us-east-1");
        String model = modelId != null ? modelId : configLoader.getProperty("aws.bedrock.model", "anthropic.claude-v2");
        
        QuestionAnswerer answerer = new QuestionAnswerer(profile, region, model);
        
        // First, we need to search for relevant messages
        // We use the question itself as the search query for finding relevant content
        List<Message> relevantMessages;
        
        if (searchQuery != null && !searchQuery.isEmpty()) {
            // If a search query was provided, use those results
            if (startDate != null || endDate != null) {
                relevantMessages = searcher.searchMessages(conversation, searchQuery, startDate, endDate);
            } else {
                relevantMessages = searcher.searchMessages(conversation, searchQuery);
            }
        } else {
            // Check if the question is asking about a specific date
            ZonedDateTime specificDate = extractDateFromQuestion(question);
            
            if (specificDate != null) {
                // If a date is found in the question, retrieve messages from that date
                ZonedDateTime startOfDay = specificDate.toLocalDate().atStartOfDay(ZoneId.systemDefault());
                ZonedDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
                
                System.out.println("\nDetected date query for: " + startOfDay.format(DATE_FORMATTER));
                
                // Get all messages from that date
                relevantMessages = conversation.getMessages().stream()
                    .filter(m -> !m.getCreated().isBefore(startOfDay) && !m.getCreated().isAfter(endOfDay))
                    .collect(java.util.stream.Collectors.toList());
                
                System.out.println("Found " + relevantMessages.size() + " messages from this date");
            } else if (startDate != null || endDate != null) {
                relevantMessages = searcher.searchMessages(conversation, question, startDate, endDate);
            } else {
                relevantMessages = searcher.searchMessages(conversation, question);
            }
        }
        
        if (relevantMessages.isEmpty()) {
            // Even when no messages are found, generate a formatted answer
            String noMessagesAnswer = answerer.generateAnswer(question, new ArrayList<>());
            System.out.println(noMessagesAnswer);
            return;
        }
        
        // Add context to the relevant messages
        List<Message> messagesWithContext = new ArrayList<>();
        for (Message message : relevantMessages) {
            List<Message> contextMessages = searcher.getMessageContext(conversation, message, contextSize);
            for (Message contextMsg : contextMessages) {
                // Add message if it's not already in the list
                if (!containsMessage(messagesWithContext, contextMsg)) {
                    messagesWithContext.add(contextMsg);
                }
            }
        }
        
        System.out.println("\n");
        System.out.println("╭───────────────────────────────────────────────────────────────────────────────╮");
        System.out.println("│                       🔄 GENERATING ANSWER                                    │");
        System.out.println("╰───────────────────────────────────────────────────────────────────────────────╯");
        System.out.println("\n🔍 Analyzing " + messagesWithContext.size() + " messages to generate an answer...");
        System.out.println("\n⏳ Please wait while I process your question...");
        
        // Generate and display the answer - already formatted in the QuestionAnswerer class
        String answer = answerer.generateAnswer(question, messagesWithContext);
        
        // The answer is already formatted with the question in it
        System.out.println(answer);
    }
    
    private boolean containsMessage(List<Message> messages, Message message) {
        for (Message msg : messages) {
            if (msg.getId().equals(message.getId())) {
                return true;
            }
        }
        return false;
    }
    
    private ZonedDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // Try to parse the date in yyyy-MM-dd format
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            return date.atStartOfDay(ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            System.err.println("Invalid start date format. Please use yyyy-MM-dd (example: 2023-01-31)");
            return null;
        }
    }
    
    private ZonedDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // Try to parse the date in yyyy-MM-dd format
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            // Set the end date to the end of the day (23:59:59)
            return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid end date format. Please use yyyy-MM-dd (example: 2023-01-31)");
            return null;
        }
    }
    
    /**
     * Extract a date from a question text
     * Handles patterns like:
     * - "on May 26th"
     * - "on 26th May"
     * - "on 26 May"
     * - "on May 26"
     * - "on 26/05"
     * - "on 05/26"
     * 
     * @param question The question text
     * @return The extracted date or null if no date is found
     */
    private ZonedDateTime extractDateFromQuestion(String question) {
        if (question == null || question.isEmpty()) {
            return null;
        }
        
        int currentYear = ZonedDateTime.now().getYear();
        Map<String, Integer> monthMap = new HashMap<>();
        monthMap.put("january", 1);
        monthMap.put("jan", 1);
        monthMap.put("february", 2);
        monthMap.put("feb", 2);
        monthMap.put("march", 3);
        monthMap.put("mar", 3);
        monthMap.put("april", 4);
        monthMap.put("apr", 4);
        monthMap.put("may", 5);
        monthMap.put("june", 6);
        monthMap.put("jun", 6);
        monthMap.put("july", 7);
        monthMap.put("jul", 7);
        monthMap.put("august", 8);
        monthMap.put("aug", 8);
        monthMap.put("september", 9);
        monthMap.put("sept", 9);
        monthMap.put("sep", 9);
        monthMap.put("october", 10);
        monthMap.put("oct", 10);
        monthMap.put("november", 11);
        monthMap.put("nov", 11);
        monthMap.put("december", 12);
        monthMap.put("dec", 12);
        
        // Convert to lowercase for case-insensitive matching
        String questionLower = question.toLowerCase();
        
        // Pattern: "on May 26th" or "on May 26" 
        for (String month : monthMap.keySet()) {
            if (questionLower.contains(month)) {
                // Find the position of the month name
                int monthPos = questionLower.indexOf(month);
                
                // Look for numbers after the month name
                String afterMonth = questionLower.substring(monthPos + month.length()).trim();
                
                // Use regex to extract the day number
                java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})(st|nd|rd|th)?\\b");
                java.util.regex.Matcher dayMatcher = dayPattern.matcher(afterMonth);
                
                if (dayMatcher.find()) {
                    int day = Integer.parseInt(dayMatcher.group(1));
                    int monthValue = monthMap.get(month);
                    
                    // Create date
                    try {
                        return LocalDate.of(currentYear, monthValue, day)
                                .atStartOfDay(ZoneId.systemDefault());
                    } catch (Exception e) {
                        // Invalid date, like February 31st
                        return null;
                    }
                }
                
                // Look for numbers before the month name
                String beforeMonth = questionLower.substring(0, monthPos).trim();
                dayMatcher = dayPattern.matcher(beforeMonth);
                
                // Find the last number before the month (closest to month name)
                int day = -1;
                while (dayMatcher.find()) {
                    day = Integer.parseInt(dayMatcher.group(1));
                }
                
                if (day > 0) {
                    int monthValue = monthMap.get(month);
                    
                    // Create date
                    try {
                        return LocalDate.of(currentYear, monthValue, day)
                                .atStartOfDay(ZoneId.systemDefault());
                    } catch (Exception e) {
                        // Invalid date
                        return null;
                    }
                }
            }
        }
        
        // Pattern: "on 26/05" or "on 05/26" (check both formats)
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
        java.util.regex.Matcher dateMatcher = datePattern.matcher(questionLower);
        
        if (dateMatcher.find()) {
            int first = Integer.parseInt(dateMatcher.group(1));
            int second = Integer.parseInt(dateMatcher.group(2));
            
            // Try to determine if it's MM/DD or DD/MM
            // If first number is > 12, it must be a day
            if (first > 12) {
                try {
                    return LocalDate.of(currentYear, second, first)
                            .atStartOfDay(ZoneId.systemDefault());
                } catch (Exception e) {
                    // Invalid date
                    return null;
                }
            } 
            // If second number is > 12, it must be a day
            else if (second > 12) {
                try {
                    return LocalDate.of(currentYear, first, second)
                            .atStartOfDay(ZoneId.systemDefault());
                } catch (Exception e) {
                    // Invalid date
                    return null;
                }
            }
            // Otherwise, assume MM/DD format (could be customized by locale)
            else {
                try {
                    return LocalDate.of(currentYear, first, second)
                            .atStartOfDay(ZoneId.systemDefault());
                } catch (Exception e) {
                    // Try the other way around
                    try {
                        return LocalDate.of(currentYear, second, first)
                                .atStartOfDay(ZoneId.systemDefault());
                    } catch (Exception e2) {
                        // Invalid date
                        return null;
                    }
                }
            }
        }
        
        // Pattern for "26th"
        java.util.regex.Pattern ordinalPattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})(st|nd|rd|th)\\b");
        java.util.regex.Matcher ordinalMatcher = ordinalPattern.matcher(questionLower);
        
        // Get the current month
        int currentMonth = ZonedDateTime.now().getMonthValue();
        
        if (ordinalMatcher.find()) {
            int day = Integer.parseInt(ordinalMatcher.group(1));
            
            // Check for nearby month words
            for (String month : monthMap.keySet()) {
                if (questionLower.contains(month)) {
                    // If a month is specified, use that
                    int monthValue = monthMap.get(month);
                    try {
                        return LocalDate.of(currentYear, monthValue, day)
                                .atStartOfDay(ZoneId.systemDefault());
                    } catch (Exception e) {
                        // Invalid date
                        return null;
                    }
                }
            }
            
            // If no month is specified, use current month
            try {
                return LocalDate.of(currentYear, currentMonth, day)
                        .atStartOfDay(ZoneId.systemDefault());
            } catch (Exception e) {
                // Invalid date
                return null;
            }
        }
        
        return null;
    }
    
    private String formatSender(String email) {
        if (email == null) {
            return "Unknown sender";
        }
        
        // Extract the name part of the email if possible
        if (email.contains("@")) {
            String name = email.substring(0, email.indexOf('@'));
            name = name.replace(".", " ");
            
            // Capitalize words
            StringBuilder formattedName = new StringBuilder();
            String[] words = name.split(" ");
            for (String word : words) {
                if (!word.isEmpty()) {
                    formattedName.append(Character.toUpperCase(word.charAt(0)))
                               .append(word.substring(1))
                               .append(" ");
                }
            }
            
            return formattedName.toString().trim();
        }
        
        return email;
    }
    
    private String highlightQueryTerms(String text, String query) {
        if (text == null || query == null) {
            return text;
        }
        
        // Simple implementation - just add ** around matched terms
        // In a real CLI app, you might use ANSI escape codes for color highlighting
        String textLower = text.toLowerCase();
        String queryLower = query.toLowerCase();
        
        StringBuilder result = new StringBuilder(text);
        
        // Find all occurrences of the query term
        int index = textLower.indexOf(queryLower);
        int offset = 0;
        
        while (index >= 0) {
            // Calculate the positions with the offset from previous insertions
            int startPos = index + offset;
            int endPos = startPos + queryLower.length();
            
            // Insert highlighting markers
            result.insert(endPos, "**");
            result.insert(startPos, "**");
            
            // Update offset and find next occurrence
            offset += 4; // 4 = length of "**" + "**"
            index = textLower.indexOf(queryLower, index + queryLower.length());
        }
        
        return result.toString();
    }
    
    static class ArrayList<T> extends java.util.ArrayList<T> {
        // Just for aliasing java.util.ArrayList with a simpler import
    }
}