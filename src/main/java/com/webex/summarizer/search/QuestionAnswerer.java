package com.webex.summarizer.search;

import com.webex.summarizer.model.Message;
import com.webex.summarizer.summarizer.BedrockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service that uses AWS Bedrock to generate answers to questions based on message content
 */
public class QuestionAnswerer {

    private static final Logger logger = LoggerFactory.getLogger(QuestionAnswerer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final BedrockClient bedrockClient;
    
    public QuestionAnswerer(String awsProfile, String awsRegion, String modelId) {
        this.bedrockClient = new BedrockClient(awsProfile, awsRegion, modelId);
        logger.info("Question Answerer initialized with AWS Bedrock (profile: {}, region: {}, model: {})",
                    awsProfile, awsRegion, modelId);
    }
    
    /**
     * Generate an answer to a question based on the content of a list of messages
     * 
     * @param question The question to answer
     * @param messages The list of messages to derive the answer from
     * @return The generated answer
     * @throws IOException If an error occurs while calling the Bedrock API
     */
    public String generateAnswer(String question, List<Message> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            logger.warn("No messages provided for generating answer");
            return formatPrettyAnswer(question, "Based on the conversation excerpt, I cannot find information to answer this question.");
        }
        
        // Format messages into a text representation
        String messagesContent = formatMessagesForLlm(messages);
        
        // Create the prompt for the LLM
        String prompt = createPrompt(question, messagesContent);
        
        // Generate the answer using Bedrock
        logger.info("Generating answer to question: '{}'", question);
        String answer = bedrockClient.generateText(prompt);
        
        return formatPrettyAnswer(question, answer);
    }
    
    /**
     * Format the answer with nice formatting for display
     * 
     * @param question The question that was asked
     * @param answer The raw answer from the LLM
     * @return A formatted answer with decorative elements
     */
    private String formatPrettyAnswer(String question, String answer) {
        StringBuilder formatted = new StringBuilder();
        
        // Add underlined question section
        formatted.append("\n");
        formatted.append("❓ Question: ").append(question).append("\n");
        formatted.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        formatted.append("\n");
        
        // Add answer with nice formatting
        formatted.append("💬 Answer:\n\n");
        formatted.append(answer).append("\n");
        formatted.append("\n");
        formatted.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        return formatted.toString();
    }
    
    /**
     * Format messages into a text representation suitable for the LLM
     * 
     * @param messages The list of messages to format
     * @return A formatted string representation of the messages
     */
    private String formatMessagesForLlm(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("CONVERSATION EXCERPT:\n\n");
        
        for (Message message : messages) {
            sb.append("Time: ").append(DATE_FORMATTER.format(message.getCreated())).append("\n");
            sb.append("From: ").append(formatSender(message.getPersonEmail())).append("\n");
            sb.append("Message: ").append(message.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Create a prompt for the LLM based on the question and message content
     * 
     * @param question The question to answer
     * @param messagesContent The formatted message content
     * @return A prompt for the LLM
     */
    private String createPrompt(String question, String messagesContent) {
        return "You are an AI assistant that helps users find information in their WebEx conversations. " +
               "I will provide you with an excerpt from a WebEx conversation and a question. " +
               "Please answer the question based ONLY on the information in the conversation excerpt. " +
               
               // Special handling for date-based questions
               "If the question is asking about what was discussed on a specific date, provide a comprehensive " +
               "summary of the main topics and key points discussed in the messages from that date. " +
               "Focus on extracting the main subjects of conversation, decisions made, or tasks assigned.\n\n" +
               
               "If the answer is not in the provided conversation excerpt, respond with EXACTLY this phrase: " +
               "'Based on the conversation excerpt, I cannot find information to answer this question.' " +
               "Do not make up information or apologize. Be direct and concise.\n\n" +
               "Conversation excerpt:\n\n" + messagesContent + "\n\n" +
               "Question: " + question + "\n\n" +
               "Answer based ONLY on the above conversation excerpt:";
    }
    
    /**
     * Format email addresses for display
     * 
     * @param email The email address to format
     * @return A user-friendly display name
     */
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
}