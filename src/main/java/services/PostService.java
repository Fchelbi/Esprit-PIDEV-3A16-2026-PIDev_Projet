package services;

import entities.Post;
import utils.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class PostService implements CrudService<Post> {

    public record DailyPostCount(String day, int count) {}
    public record CategoryPostCount(String category, int count) {}

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
    public void likePost(int postId, int userId) {
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement removeDislike = connection.prepareStatement(
                    "DELETE FROM post_dislikes WHERE post_id = ? AND user_id = ?")) {
                removeDislike.setInt(1, postId);
                removeDislike.setInt(2, userId);
                removeDislike.executeUpdate();
            }

            try (PreparedStatement addLike = connection.prepareStatement(
                    "INSERT IGNORE INTO post_likes (post_id, user_id) VALUES (?, ?)")) {
                addLike.setInt(1, postId);
                addLike.setInt(2, userId);
                addLike.executeUpdate();
            }

            updateReactionCounters(postId);

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            System.err.println("Error liking post: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public void dislikePost(int postId, int userId) {
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement removeLike = connection.prepareStatement(
                    "DELETE FROM post_likes WHERE post_id = ? AND user_id = ?")) {
                removeLike.setInt(1, postId);
                removeLike.setInt(2, userId);
                removeLike.executeUpdate();
            }

            try (PreparedStatement addDislike = connection.prepareStatement(
                    "INSERT IGNORE INTO post_dislikes (post_id, user_id) VALUES (?, ?)")) {
                addDislike.setInt(1, postId);
                addDislike.setInt(2, userId);
                addDislike.executeUpdate();
            }

            updateReactionCounters(postId);

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            System.err.println("Error disliking post: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void updateReactionCounters(int postId) throws SQLException {
        String query = """
            UPDATE post
            SET likes = (SELECT COUNT(*) FROM post_likes WHERE post_id = ?),
                dislikes = (SELECT COUNT(*) FROM post_dislikes WHERE post_id = ?)
            WHERE id = ?
            """;

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, postId);
            ps.setInt(2, postId);
            ps.setInt(3, postId);
            ps.executeUpdate();
        }
    }
    public int countPosts() {
        return countQuery("SELECT COUNT(*) FROM post");
    }

    public int countFlaggedPosts() {
        return countQuery("SELECT COUNT(*) FROM post WHERE is_flagged = 1 OR moderation_status = 'flagged'");
    }

    public int totalPostLikes() {
        return countQuery("SELECT COUNT(*) FROM post_likes");
    }

    public int totalPostDislikes() {
        return countQuery("SELECT COUNT(*) FROM post_dislikes");
    }

    public int countActiveForumUsers() {
        return countQuery("""
            SELECT COUNT(DISTINCT user_id)
            FROM (
                SELECT user_id FROM post
                UNION
                SELECT user_id FROM comment
            ) AS active_users
            """);
    }

    private int countQuery(String sql) {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.err.println("[Stats] Query error: " + e.getMessage());
        }

        return 0;
    }
    public List<DailyPostCount> getPostsActivityLast30Days() {
        List<DailyPostCount> data = new ArrayList<>();

        String query = """
            SELECT DATE(created_at) AS day, COUNT(*) AS total
            FROM post
            WHERE created_at >= CURDATE() - INTERVAL 30 DAY
            GROUP BY DATE(created_at)
            ORDER BY day
            """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            while (rs.next()) {
                data.add(new DailyPostCount(
                        rs.getString("day"),
                        rs.getInt("total")
                ));
            }

        } catch (SQLException e) {
            System.err.println("[Stats] Error loading posts activity: " + e.getMessage());
        }

        return data;
    }

    public List<CategoryPostCount> getPostsByCategory() {
        List<CategoryPostCount> data = new ArrayList<>();

        String query = """
            SELECT c.name AS category_name, COUNT(p.id) AS total
            FROM category c
            LEFT JOIN post p ON p.category_id = c.id
            GROUP BY c.id, c.name
            ORDER BY total DESC
            """;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            while (rs.next()) {
                data.add(new CategoryPostCount(
                        rs.getString("category_name"),
                        rs.getInt("total")
                ));
            }

        } catch (SQLException e) {
            System.err.println("[Stats] Error loading posts by category: " + e.getMessage());
        }

        return data;
    }
}