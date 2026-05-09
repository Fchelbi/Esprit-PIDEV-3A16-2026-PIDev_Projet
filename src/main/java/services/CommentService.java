package services;

import entities.Comment;
import utils.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CommentService implements CrudService<Comment> {

    private final Connection connection;

    public CommentService() {
        try {
            connection = Database.getInstance().getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    @Override
    public void add(Comment comment) {
        String query = "INSERT INTO comment (post_id, content, created_at, user_id, parent_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, comment.getPostId());
            ps.setString(2, comment.getContent());
            ps.setTimestamp(3, Timestamp.valueOf(comment.getCreatedAt()));
            ps.setInt(4, comment.getUserId());
            if (comment.getParentId() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, comment.getParentId());
            }
            ps.executeUpdate();
            System.out.println("Comment added successfully.");
        } catch (SQLException e) {
            System.out.println("Error adding comment: " + e.getMessage());
        }
    }

    @Override
    public void update(Comment comment) {
        String query = "UPDATE comment SET post_id = ?, content = ?, created_at = ?, user_id = ?, parent_id = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, comment.getPostId());
            ps.setString(2, comment.getContent());
            ps.setTimestamp(3, Timestamp.valueOf(comment.getCreatedAt()));
            ps.setInt(4, comment.getUserId());
            if (comment.getParentId() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, comment.getParentId());
            }
            ps.setInt(6, comment.getId());
            ps.executeUpdate();
            System.out.println("Comment updated successfully.");
        } catch (SQLException e) {
            System.out.println("Error updating comment: " + e.getMessage());
        }
    }

    @Override
    public void delete(int id) {
        String query = "DELETE FROM comment WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            System.out.println("Comment deleted successfully.");
        } catch (SQLException e) {
            System.out.println("Error deleting comment: " + e.getMessage());
        }
    }

    @Override
    public List<Comment> getAll() {
        List<Comment> comments = new ArrayList<>();
        String query = "SELECT * FROM comment";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                // parent_id can be null, so we read it as an Integer object
                int parentIdValue = rs.getInt("parent_id");
                Integer parentId = rs.wasNull() ? null : parentIdValue;

                Comment comment = new Comment(
                        rs.getInt("id"),
                        rs.getInt("post_id"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("user_id"),
                        parentId
                );
                comments.add(comment);
            }
        } catch (SQLException e) {
            System.out.println("Error fetching comments: " + e.getMessage());
        }
        return comments;
    }

    @Override
    public Comment getById(int id) {
        String query = "SELECT * FROM comment WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int parentIdValue = rs.getInt("parent_id");
                Integer parentId = rs.wasNull() ? null : parentIdValue;

                return new Comment(
                        rs.getInt("id"),
                        rs.getInt("post_id"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("user_id"),
                        parentId
                );
            }
        } catch (SQLException e) {
            System.out.println("Error fetching comment: " + e.getMessage());
        }
        return null;
    }
}