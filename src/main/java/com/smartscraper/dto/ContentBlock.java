package com.smartscraper.dto;

/**
 * Represents a block of content extracted from a webpage.
 * Contains a paragraph with its associated heading context.
 */
public class ContentBlock {

    private String heading;
    private String paragraph;
    private int position;

    public ContentBlock() {
    }

    public ContentBlock(String heading, String paragraph, int position) {
        this.heading = heading;
        this.paragraph = paragraph;
        this.position = position;
    }

    // Getters and Setters
    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getParagraph() {
        return paragraph;
    }

    public void setParagraph(String paragraph) {
        this.paragraph = paragraph;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * Get combined text (heading + paragraph).
     * 
     * @return Combined text
     */
    public String getCombinedText() {
        if (heading != null && !heading.isEmpty()) {
            return heading + "\n\n" + paragraph;
        }
        return paragraph;
    }

    @Override
    public String toString() {
        return "ContentBlock{" +
                "heading='" + heading + '\'' +
                ", paragraph='" + (paragraph != null ? paragraph.substring(0, Math.min(50, paragraph.length())) + "..." : "null") + '\'' +
                ", position=" + position +
                '}';
    }
}
