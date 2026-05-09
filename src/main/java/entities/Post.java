package entities;

import java.time.LocalDateTime;

public class Post {

    private int id;
    private int categoryId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private int likes;
    private int dislikes;
    private int userId;
    private String photo;
    private boolean isFlagged;
    private String flagReason;
    private String moderationStatus;

    public Post() {
    }

    // Constructor for creating a new post (no id, no likes/dislikes, no flags)
    public Post(int categoryId, String title, String content, LocalDateTime createdAt, int userId, String photo) {
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.userId = userId;
        this.photo = photo;
        this.likes = 0;
        this.dislikes = 0;
        this.isFlagged = false;
        this.moderationStatus = "pending";
    }

    // Full constructor (used when reading from database)
    public Post(int id, int categoryId, String title, String content, LocalDateTime createdAt,
                int likes, int dislikes, int userId, String photo,
                boolean isFlagged, String flagReason, String moderationStatus) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.likes = likes;
        this.dislikes = dislikes;
        this.userId = userId;
        this.photo = photo;
        this.isFlagged = isFlagged;
        this.flagReason = flagReason;
        this.moderationStatus = moderationStatus;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public int getDislikes() {
        return dislikes;
    }

    public void setDislikes(int dislikes) {
        this.dislikes = dislikes;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public boolean isFlagged() {
        return isFlagged;
    }

    public void setFlagged(boolean flagged) {
        isFlagged = flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public void setFlagReason(String flagReason) {
        this.flagReason = flagReason;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", categoryId=" + categoryId +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                ", likes=" + likes +
                ", dislikes=" + dislikes +
                ", userId=" + userId +
                ", photo='" + photo + '\'' +
                ", isFlagged=" + isFlagged +
                ", flagReason='" + flagReason + '\'' +
                ", moderationStatus='" + moderationStatus + '\'' +
                '}';
    }
}