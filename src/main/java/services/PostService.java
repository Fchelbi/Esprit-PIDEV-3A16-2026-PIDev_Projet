package services;

import entities.Post;
import utils.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostService implements CrudService<Post> {

    private final Connection connection;

    public PostService() {
        try {
            connection = Database.getInstance().getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    @Override
    public void add(Post post) {
        String query = "INSERT INTO post (category_id, title, content, created_at, likes, dislikes, user_id, photo, is_flagged, flag_reason, moderation_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, post.getCategoryId());
            ps.setString(2, post.getTitle());
            ps.setString(3, post.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(post.getCreatedAt()));
            ps.setInt(5, post.getLikes());
            ps.setInt(6, post.getDislikes());
            ps.setInt(7, post.getUserId());
            ps.setString(8, post.getPhoto());
            ps.setBoolean(9, post.isFlagged());
            ps.setString(10, post.getFlagReason());
            ps.setString(11, post.getModerationStatus());
            ps.executeUpdate();
            System.out.println("Post added successfully.");
        } catch (SQLException e) {
            System.out.println("Error adding post: " + e.getMessage());
        }
    }

    @Override
    public void update(Post post) {
        String query = "UPDATE post SET category_id = ?, title = ?, content = ?, created_at = ?, likes = ?, dislikes = ?, user_id = ?, photo = ?, is_flagged = ?, flag_reason = ?, moderation_status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, post.getCategoryId());
            ps.setString(2, post.getTitle());
            ps.setString(3, post.getContent());
            ps.setTimestamp(4, Timestamp.valueOf(post.getCreatedAt()));
            ps.setInt(5, post.getLikes());
            ps.setInt(6, post.getDislikes());
            ps.setInt(7, post.getUserId());
            ps.setString(8, post.getPhoto());
            ps.setBoolean(9, post.isFlagged());
            ps.setString(10, post.getFlagReason());
            ps.setString(11, post.getModerationStatus());
            ps.setInt(12, post.getId());
            ps.executeUpdate();
            System.out.println("Post updated successfully.");
        } catch (SQLException e) {
            System.out.println("Error updating post: " + e.getMessage());
        }
    }

    @Override
    public void delete(int id) {
        String query = "DELETE FROM post WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            System.out.println("Post deleted successfully.");
        } catch (SQLException e) {
            System.out.println("Error deleting post: " + e.getMessage());
        }
    }

    @Override
    public List<Post> getAll() {
        List<Post> posts = new ArrayList<>();
        String query = "SELECT * FROM post";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                Post post = new Post(
                        rs.getInt("id"),
                        rs.getInt("category_id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("likes"),
                        rs.getInt("dislikes"),
                        rs.getInt("user_id"),
                        rs.getString("photo"),
                        rs.getBoolean("is_flagged"),
                        rs.getString("flag_reason"),
                        rs.getString("moderation_status")
                );
                posts.add(post);
            }
        } catch (SQLException e) {
            System.out.println("Error fetching posts: " + e.getMessage());
        }
        return posts;
    }

    @Override
    public Post getById(int id) {
        String query = "SELECT * FROM post WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Post(
                        rs.getInt("id"),
                        rs.getInt("category_id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("likes"),
                        rs.getInt("dislikes"),
                        rs.getInt("user_id"),
                        rs.getString("photo"),
                        rs.getBoolean("is_flagged"),
                        rs.getString("flag_reason"),
                        rs.getString("moderation_status")
                );
            }
        } catch (SQLException e) {
            System.out.println("Error fetching post: " + e.getMessage());
        }
        return null;
    }
}