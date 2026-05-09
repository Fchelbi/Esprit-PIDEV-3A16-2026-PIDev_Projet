package entities;

import java.time.LocalDateTime;

public class Comment {

    private int id;
    private int postId;
    private String content;
    private LocalDateTime createdAt;
    private int userId;
    private Integer parentId; // null if top-level comment, set if it's a reply

    public Comment() {
    }

    // Constructor for creating a new comment (no id needed)
    public Comment(int postId, String content, LocalDateTime createdAt, int userId, Integer parentId) {
        this.postId = postId;
        this.content = content;
        this.createdAt = createdAt;
        this.userId = userId;
        this.parentId = parentId;
    }

    // Full constructor (used when reading from database)
    public Comment(int id, int postId, String content, LocalDateTime createdAt, int userId, Integer parentId) {
        this.id = id;
        this.postId = postId;
        this.content = content;
        this.createdAt = createdAt;
        this.userId = userId;
        this.parentId = parentId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
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

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", postId=" + postId +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                ", userId=" + userId +
                ", parentId=" + parentId +
                '}';
    }
}