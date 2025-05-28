package com.webex.summarizer.summarizer;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for generating summaries of WebEx conversations using AWS Bedrock LLMs.
 * Handles large conversations by splitting them into chunks and generating hierarchical summaries.
 */
public class LlmSummarizer {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmSummarizer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Constants for chunking and summarization
    private static final int MAX_MESSAGES_PER_CHUNK = 500; // Maximum number of messages in each chunk
    private static final int ESTIMATED_CHARS_PER_MESSAGE = 200; // Average estimated characters per message
    private static final int MAX_CHUNK_SIZE_CHARS = 100000; // Maximum characters for a single chunk to stay under token limits
    
    private final BedrockClient bedrockClient;
    private SummarizationProgressListener progressListener;
    
    /**
     * Interface for reporting summarization progress
     */
    public interface SummarizationProgressListener {
        void onSummarizationProgress(int currentChunk, int totalChunks, String status);
    }
    
    public LlmSummarizer(String awsProfile, String awsRegion, String modelId) {
        this.bedrockClient = new BedrockClient(awsProfile, awsRegion, modelId);
        logger.info("LLM Summarizer initialized with AWS Bedrock (profile: {}, region: {}, model: {})",
                    awsProfile, awsRegion, modelId);
    }
    
    /**
     * Set a progress listener to receive updates during summarization
     */
    public void setProgressListener(SummarizationProgressListener listener) {
        this.progressListener = listener;
    }
    
    /**
     * Generate a summary for a conversation, splitting into chunks if necessary
     */
    public String generateSummary(Conversation conversation) throws IOException {
        List<Message> messages = conversation.getMessages();
        String roomTitle = conversation.getRoom().getTitle();
        int messageCount = messages.size();
        
        logger.info("Starting summarization for conversation with {} messages", messageCount);
        
        // For small conversations, use single summarization
        if (messageCount <= MAX_MESSAGES_PER_CHUNK) {
            logger.info("Conversation is small enough for direct summarization");
            reportProgress(1, 1, "Processing full conversation");
            String conversationText = formatConversation(conversation);
            return generateSingleSummary(conversationText, roomTitle);
        }
        
        // For large conversations, use chunked summarization
        logger.info("Large conversation detected, using chunked summarization approach");
        return generateChunkedSummary(messages, roomTitle);
    }
    
    /**
     * Generate a summary by splitting the conversation into chunks, summarizing each chunk,
     * and then summarizing all the chunk summaries together
     */
    private String generateChunkedSummary(List<Message> messages, String roomTitle) throws IOException {
        int messageCount = messages.size();
        
        // Calculate number of chunks needed
        int chunkCount = calculateChunkCount(messages);
        logger.info("Splitting conversation into {} chunks", chunkCount);
        
        List<String> chunkSummaries = new ArrayList<>();
        List<List<Message>> messageChunks = splitMessagesIntoChunks(messages, chunkCount);
        
        // Process each chunk
        for (int i = 0; i < messageChunks.size(); i++) {
            List<Message> chunk = messageChunks.get(i);
            reportProgress(i + 1, messageChunks.size(), "Processing chunk " + (i + 1) + "/" + messageChunks.size());
            
            String chunkText = formatMessageChunk(chunk, roomTitle, i + 1, messageChunks.size());
            String chunkPrompt = "Please provide a detailed summary of the following part " + (i + 1) + 
                              " of " + messageChunks.size() + " from a Cisco WebEx conversation.\n\n" +
                              "Format your response with these clearly separated sections:\n" +
                              "1. A brief '**Summary**' section highlighting what this conversation part covers\n" +
                              "2. A '**Key Points**' section with numbered items for important topics\n" +
                              "3. A '**Details**' section with any significant information that needs attention\n" +
                              "4. If present, a '**Decisions**' section with numbered items\n" + 
                              "5. If present, an '**Action Items**' section with numbered tasks\n\n" +
                              "Format requirements:\n" +
                              "- Make each section header bold by surrounding it with asterisks (e.g., **Summary**)\n" +
                              "- Use clear numbering for all list items (1., 2., 3., etc.)\n" +
                              "- Add a visual separator line between sections using dashes (e.g., -----------)\n" +
                              "- For action items, indicate ownership with 'Owner: [Name]' and deadlines with 'Due: [Date]'\n\n" +
                              "Be thorough in capturing all important information as this will be combined with summaries of other parts.\n\n" + 
                              chunkText;
            
            logger.info("Generating summary for chunk {} of {} ({} messages)", 
                       i + 1, messageChunks.size(), chunk.size());
            String chunkSummary = bedrockClient.generateText(chunkPrompt);
            chunkSummaries.add(chunkSummary);
            logger.info("Chunk {} summary generated ({} characters)", i + 1, chunkSummary.length());
        }
        
        // Generate final summary from all chunk summaries
        reportProgress(chunkCount + 1, chunkCount + 1, "Creating final summary");
        return generateFinalSummary(chunkSummaries, roomTitle, messageCount);
    }
    
    /**
     * Calculate how many chunks are needed based on message count and estimated sizes
     */
    private int calculateChunkCount(List<Message> messages) {
        int messageCount = messages.size();
        
        // Estimate total character count
        long totalEstimatedChars = 0;
        for (Message message : messages) {
            String text = message.getText();
            totalEstimatedChars += (text != null) ? text.length() : 0;
        }
        
        // Calculate based on either raw message count or estimated character count
        int chunksByMessageCount = (int) Math.ceil((double) messageCount / MAX_MESSAGES_PER_CHUNK);
        int chunksByCharCount = (int) Math.ceil((double) totalEstimatedChars / MAX_CHUNK_SIZE_CHARS);
        
        // Take the larger value to ensure chunks aren't too big
        return Math.max(chunksByMessageCount, chunksByCharCount);
    }
    
    /**
     * Split the list of messages into roughly equal-sized chunks
     */
    private List<List<Message>> splitMessagesIntoChunks(List<Message> messages, int chunkCount) {
        List<List<Message>> chunks = new ArrayList<>();
        int messagesPerChunk = (int) Math.ceil((double) messages.size() / chunkCount);
        
        for (int i = 0; i < chunkCount; i++) {
            int startIndex = i * messagesPerChunk;
            int endIndex = Math.min(startIndex + messagesPerChunk, messages.size());
            chunks.add(messages.subList(startIndex, endIndex));
        }
        
        return chunks;
    }
    
    /**
     * Format a chunk of messages for input to the summarization model
     */
    private String formatMessageChunk(List<Message> messages, String roomTitle, int chunkNum, int totalChunks) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Room: ").append(roomTitle).append("\n");
        sb.append("Chunk: ").append(chunkNum).append(" of ").append(totalChunks).append("\n");
        sb.append("Messages: ").append(messages.size()).append("\n\n");
        
        for (Message message : messages) {
            sb.append("Time: ").append(DATE_FORMATTER.format(message.getCreated())).append("\n");
            sb.append("From: ").append(message.getPersonEmail()).append("\n");
            sb.append("Message: ").append(message.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate the final summary by combining all chunk summaries
     */
    private String generateFinalSummary(List<String> chunkSummaries, String roomTitle, int totalMessageCount) throws IOException {
        StringBuilder sb = new StringBuilder();
        
        sb.append("The following are summaries of different parts of a conversation from room '")
          .append(roomTitle).append("' containing ").append(totalMessageCount).append(" total messages.\n\n");
        
        for (int i = 0; i < chunkSummaries.size(); i++) {
            sb.append("=== Summary of Part ").append(i + 1).append(" ===\n");
            sb.append(chunkSummaries.get(i)).append("\n\n");
        }
        
        String finalPrompt = "Please create a comprehensive summary of this entire conversation based on " +
                           "the part summaries below. Structure your summary with these clearly formatted sections:\n\n" +
                           "1. Start with an '**Overview**' section that provides a high-level synthesis of the conversation\n" +
                           "2. Create a '**Key Topics**' section with numbered items covering the main subjects discussed\n" +
                           "3. Include a '**Decisions**' section with clearly numbered items for all decisions reached\n" +
                           "4. Add an '**Action Items**' section with numbered tasks, owners, and deadlines\n" +
                           "5. If appropriate, add a '**Timeline**' section noting important dates mentioned\n\n" +
                           "Format requirements:\n" +
                           "- Make each section header bold by surrounding it with asterisks (e.g., **Overview**)\n" +
                           "- Use clear numbering for all list items (1., 2., 3., etc.)\n" +
                           "- Add a visual separator line between sections using dashes (e.g., -----------)\n" +
                           "- For action items, clearly indicate ownership with 'Owner: [Name]' on a new line\n" + 
                           "- For action items with deadlines, include 'Due: [Date]' on a new line\n" +
                           "- Structure information in a highly scannable format\n\n" +
                           "Focus on synthesizing across all parts to create a unified, coherent summary.\n\n" + sb.toString();
        
        logger.info("Generating final summary from {} chunk summaries", chunkSummaries.size());
        return bedrockClient.generateText(finalPrompt);
    }
    
    /**
     * Generate a single summary for a conversation that fits within token limits
     */
    private String generateSingleSummary(String conversationText, String roomTitle) throws IOException {
        String prompt = "Please provide a well-structured summary of the following conversation from a Cisco WebEx room '" + 
                roomTitle + "'. Format your response with these clearly formatted sections:\n\n" +
                "1. Start with an '**Overview**' section that provides a high-level synthesis of the conversation\n" +
                "2. Create a '**Key Topics**' section with numbered items covering the main subjects discussed\n" +
                "3. Include a '**Decisions**' section with clearly numbered items for all decisions reached\n" +
                "4. Add an '**Action Items**' section with numbered tasks, owners, and deadlines\n" +
                "5. If appropriate, add a '**Timeline**' section noting important dates mentioned\n\n" +
                "Format requirements:\n" +
                "- Make each section header bold by surrounding it with asterisks (e.g., **Overview**)\n" +
                "- Use clear numbering for all list items (1., 2., 3., etc.)\n" +
                "- Add a visual separator line between sections using dashes (e.g., -----------)\n" +
                "- For action items, clearly indicate ownership with 'Owner: [Name]' on a new line\n" + 
                "- For action items with deadlines, include 'Due: [Date]' on a new line\n" +
                "- Structure information in a highly scannable format\n\n" +
                "Be concise but comprehensive, capturing all significant information shared. Focus on extracting actionable insights from the conversation.\n\n" + 
                conversationText;
        
        try {
            logger.info("Generating single summary using AWS Bedrock...");
            String summary = bedrockClient.generateText(prompt);
            logger.info("Summary generated successfully");
            return summary;
        } catch (IOException e) {
            logger.error("Failed to generate summary: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Report progress if a listener is registered
     */
    private void reportProgress(int current, int total, String status) {
        if (progressListener != null) {
            progressListener.onSummarizationProgress(current, total, status);
        }
    }
    
    /**
     * Format an entire conversation for the summarizer
     */
    private String formatConversation(Conversation conversation) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Room: ").append(conversation.getRoom().getTitle()).append("\n\n");
        
        List<Message> messages = conversation.getMessages();
        for (Message message : messages) {
            sb.append("Time: ").append(DATE_FORMATTER.format(message.getCreated())).append("\n");
            sb.append("From: ").append(message.getPersonEmail()).append("\n");
            sb.append("Message: ").append(message.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    public List<Map<String, String>> listAvailableModels() {
        return bedrockClient.listAvailableModels();
    }
    
    public String getModelDetails(String modelId) {
        return bedrockClient.getModelDetails(modelId);
    }
}