package com.webex.summarizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for formatting and displaying summaries with enhanced visual presentation.
 */
public class SummaryFormatter {

    private static final Logger logger = LoggerFactory.getLogger(SummaryFormatter.class);

    /**
     * Format and display a summary with enhanced visual presentation
     * 
     * @param summary The text summary to format and display
     * @return A formatted string representation of the summary
     */
    public static String formatSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return "";
        }

        StringBuilder formattedSummary = new StringBuilder();
        
        // Add current timestamp to the header
        String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        // Display the summary with improved formatting and timestamp
        formattedSummary.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        formattedSummary.append("║                       CONVERSATION SUMMARY                                 ║\n");
        formattedSummary.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        formattedSummary.append("║  Generated: ").append(currentTime).append("                                     ║\n");
        formattedSummary.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        
        // Process and print each line of the summary with enhanced formatting
        String[] summaryLines = summary.split("\n");
        boolean inActionItems = false;
        boolean inDecisions = false;
        
        for (String line : summaryLines) {
            if (line.trim().isEmpty()) {
                formattedSummary.append("\n"); // preserve empty lines
            } else if (line.trim().startsWith("**") && line.trim().endsWith("**")) {
                // Track which section we're in for special formatting
                String headerContent = line.trim().replaceAll("\\*\\*", "");
                inActionItems = headerContent.toLowerCase().contains("action") || 
                               headerContent.toLowerCase().contains("task");
                inDecisions = headerContent.toLowerCase().contains("decision") ||
                             headerContent.toLowerCase().contains("conclusion");
                
                // Create a more visually distinctive section header
                String decoration = getHeaderDecoration(headerContent);
                formattedSummary.append("\n");
                formattedSummary.append(decoration).append("\n");
                formattedSummary.append("┏━━━━━━").append("━".repeat(headerContent.length())).append("━━━━━┓\n");
                formattedSummary.append("┃  ").append(headerContent.toUpperCase()).append("  ┃\n");
                formattedSummary.append("┗━━━━━━").append("━".repeat(headerContent.length())).append("━━━━━┛\n");
                formattedSummary.append(decoration).append("\n");
            } else if (line.trim().startsWith("---")) {
                // Separator lines - convert to a nicer format with different styles
                formattedSummary.append("  ∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙\n");
            } else if (line.trim().matches("^\\d+\\..*")) {
                // Numbered items with enhanced formatting based on section
                String number = line.trim().split("\\.", 2)[0];
                String content = line.trim().substring(number.length() + 1).trim();
                
                if (inActionItems) {
                    // Action items get a distinctive marker and formatting
                    formattedSummary.append("  ➤ ").append(number).append(". ").append(content).append("\n");
                } else if (inDecisions) {
                    // Decisions get a different marker
                    formattedSummary.append("  ✓ ").append(number).append(". ").append(content).append("\n");
                } else {
                    // Regular numbered items
                    formattedSummary.append("  • ").append(number).append(". ").append(content).append("\n");
                }
            } else {
                // Regular text with context-aware indentation
                if (inActionItems && (line.trim().toLowerCase().contains("by:") || 
                    line.trim().toLowerCase().contains("owner:") ||
                    line.trim().toLowerCase().contains("due:"))) {
                    // Highlight ownership and deadlines in action items
                    formattedSummary.append("      ↳ ").append(line.trim()).append("\n");
                } else {
                    // Standard indentation for regular text
                    formattedSummary.append("    ").append(line).append("\n");
                }
            }
        }
        
        // Add footer with legend for markers
        formattedSummary.append("\n").append("═".repeat(76)).append("\n");
        formattedSummary.append(" Legend:  • Regular Point   ➤ Action Item   ✓ Decision\n");
        formattedSummary.append("═".repeat(76));
        
        return formattedSummary.toString();
    }
    
    /**
     * Get decorative line based on section header content
     */
    private static String getHeaderDecoration(String headerContent) {
        if (headerContent.toLowerCase().contains("overview")) {
            return "┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅";
        } else if (headerContent.toLowerCase().contains("action") || 
                  headerContent.toLowerCase().contains("task")) {
            return "⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥";
        } else if (headerContent.toLowerCase().contains("decision") ||
                  headerContent.toLowerCase().contains("conclusion")) {
            return "◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈";
        } else if (headerContent.toLowerCase().contains("key")) {
            return "▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪";
        } else {
            return "────────────────────────────────────────────────────────────────────────────";
        }
    }
    
    /**
     * Print a formatted summary to the console
     * 
     * @param summary The text summary to format and display
     */
    public static void printFormattedSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            logger.warn("Cannot display empty summary");
            return;
        }
        
        String formatted = formatSummary(summary);
        System.out.println(formatted);
    }
}