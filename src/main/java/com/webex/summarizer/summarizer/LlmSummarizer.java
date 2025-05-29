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
    private static final int MAX_TOKENS_PER_CHUNK = 150000; // Maximum tokens in each chunk (to stay within context limits for most models)
    private static final int TOKENS_PER_CHARACTER = 4; // Approximate ratio of characters to tokens (1 token ~= 4 chars in English)
    private static final int CHUNK_BUFFER_TOKENS = 1000; // Buffer tokens for formatting, system messages, etc.
    
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
        
        // Calculate total token count for the conversation
        int estimatedTokens = calculateTotalTokens(messages);
        int maxTokensPerChunkWithBuffer = MAX_TOKENS_PER_CHUNK - CHUNK_BUFFER_TOKENS;
        
        logger.info("Estimated token count for conversation: {}", estimatedTokens);
        
        // For small conversations (token-wise), use single summarization
        if (estimatedTokens <= maxTokensPerChunkWithBuffer) {
            logger.info("Conversation is small enough for direct summarization ({} tokens)", estimatedTokens);
            reportProgress(1, 1, "Processing full conversation");
            String conversationText = formatConversation(conversation);
            return generateSingleSummary(conversationText, roomTitle);
        }
        
        // For large conversations, use chunked summarization
        logger.info("Large conversation detected ({} tokens), using chunked summarization approach", estimatedTokens);
        return generateChunkedSummary(messages, roomTitle);
    }
    
    /**
     * Calculates the estimated token count for a list of messages
     */
    private int calculateTotalTokens(List<Message> messages) {
        int totalTokens = 0;
        
        for (Message message : messages) {
            String text = message.getText();
            if (text != null) {
                // Count tokens based on characters (approximate)
                totalTokens += text.length() / TOKENS_PER_CHARACTER;
                
                // Add tokens for message metadata (timestamp, sender)
                totalTokens += 20; // Approximate tokens for metadata
            }
        }
        
        return totalTokens;
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
            
            int chunkTokens = calculateTotalTokens(chunk);
            logger.info("Generating summary for chunk {} of {} ({} messages, ~{} tokens)", 
                       i + 1, messageChunks.size(), chunk.size(), chunkTokens);
            String chunkSummary = bedrockClient.generateText(chunkPrompt);
            chunkSummaries.add(chunkSummary);
            int summaryTokens = chunkSummary.length() / TOKENS_PER_CHARACTER;
            logger.info("Chunk {} summary generated ({} characters, ~{} tokens)", 
                       i + 1, chunkSummary.length(), summaryTokens);
        }
        
        // Generate final summary from all chunk summaries
        reportProgress(chunkCount + 1, chunkCount + 1, "Creating final summary");
        return generateFinalSummary(chunkSummaries, roomTitle, messageCount);
    }
    
    /**
     * Calculate how many chunks are needed based on token counts
     */
    protected int calculateChunkCount(List<Message> messages) {
        int totalTokens = calculateTotalTokens(messages);
        int maxTokensPerChunkWithBuffer = MAX_TOKENS_PER_CHUNK - CHUNK_BUFFER_TOKENS;
        
        // Calculate chunks needed based on total tokens
        return (int) Math.ceil((double) totalTokens / maxTokensPerChunkWithBuffer);
    }
    
    /**
     * Split the list of messages into chunks based on token counts
     */
    private List<List<Message>> splitMessagesIntoChunks(List<Message> messages, int chunkCount) {
        List<List<Message>> chunks = new ArrayList<>();
        int maxTokensPerChunk = MAX_TOKENS_PER_CHUNK - CHUNK_BUFFER_TOKENS;
        
        // If we only need one chunk but method was called, return all messages as one chunk
        if (chunkCount <= 1) {
            chunks.add(new ArrayList<>(messages));
            return chunks;
        }
        
        List<Message> currentChunk = new ArrayList<>();
        int currentChunkTokens = 0;
        
        for (Message message : messages) {
            // Calculate tokens for this message
            int messageTokens = 0;
            if (message.getText() != null) {
                messageTokens = message.getText().length() / TOKENS_PER_CHARACTER + 20; // text + metadata
            }
            
            // If adding this message would exceed the chunk token limit and current chunk is not empty
            // then finalize the current chunk and start a new one
            if (currentChunkTokens + messageTokens > maxTokensPerChunk && !currentChunk.isEmpty()) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
                currentChunkTokens = 0;
            }
            
            // Add the message to the current chunk
            currentChunk.add(message);
            currentChunkTokens += messageTokens;
            
            // Special case: if a single message exceeds the chunk token limit
            // it will be the only message in its chunk
        }
        
        // Add the final chunk if it's not empty
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }
        
        logger.info("Split conversation into {} token-based chunks", chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            int chunkTokens = calculateTotalTokens(chunks.get(i));
            logger.debug("Chunk {} contains {} messages with ~{} tokens", 
                      i + 1, chunks.get(i).size(), chunkTokens);
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