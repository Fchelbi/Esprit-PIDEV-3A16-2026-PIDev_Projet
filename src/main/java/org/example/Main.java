package org.example;

import entities.Category;
import entities.Comment;
import entities.Post;
import services.CategoryService;
import services.CommentService;
import services.PostService;
import utils.Database;

import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        try {
            // ── 1. Connection test ─────────────────────────────────────────
            if (Database.getInstance().getConnection() != null) {
                System.out.println("Connection OK");
            } else {
                System.out.println("Connection failed");
                return;
            }

            // ── 2. Create services ─────────────────────────────────────────
            CategoryService categoryService = new CategoryService();
            PostService     postService     = new PostService();
            CommentService  commentService  = new CommentService();

            // Unique suffix so we can find our test records safely
            long ts = System.currentTimeMillis();

            // ── 3. Add test category ───────────────────────────────────────
            String testCategoryName = "Test Category " + ts;
            System.out.println("\n[STEP 1] Adding test category: " + testCategoryName);
            categoryService.add(new Category(testCategoryName));

            // Find the added category by its unique name
            Category addedCategory = null;
            for (Category c : categoryService.getAll()) {
                if (c.getName().equals(testCategoryName)) {
                    addedCategory = c;
                }
            }

            System.out.println("\n--- All Categories ---");
            for (Category c : categoryService.getAll()) {
                System.out.println(c);
            }

            // ── 4. Add test post ───────────────────────────────────────────
            String testPostTitle = "Test Post " + ts;
            System.out.println("\n[STEP 2] Adding test post: " + testPostTitle);
            postService.add(new Post(1, testPostTitle, "Test content", LocalDateTime.now(), 2, null));

            // Find the added post by its unique title
            Post addedPost = null;
            for (Post p : postService.getAll()) {
                if (p.getTitle().equals(testPostTitle)) {
                    addedPost = p;
                }
            }

            System.out.println("\n--- All Posts ---");
            for (Post p : postService.getAll()) {
                System.out.println(p);
            }

            // ── 5. Add test comment ────────────────────────────────────────
            String testCommentContent = "Test Comment " + ts;
            System.out.println("\n[STEP 3] Adding test comment: " + testCommentContent);
            commentService.add(new Comment(5, testCommentContent, LocalDateTime.now(), 2, null));

            // Find the added comment by its unique content
            Comment addedComment = null;
            for (Comment cm : commentService.getAll()) {
                if (cm.getContent().equals(testCommentContent)) {
                    addedComment = cm;
                }
            }

            System.out.println("\n--- All Comments ---");
            for (Comment cm : commentService.getAll()) {
                System.out.println(cm);
            }

            // ── 6. Update test post ────────────────────────────────────────
            System.out.println("\n[STEP 4] Updating test post...");
            if (addedPost != null) {
                addedPost.setTitle("Updated Test Post " + ts);
                addedPost.setContent("Updated content");
                postService.update(addedPost);
                System.out.println("Post after update: " + postService.getById(addedPost.getId()));
            } else {
                System.out.println("Test post not found, skipping update.");
            }

            // ── 7. Update test comment ─────────────────────────────────────
            System.out.println("\n[STEP 5] Updating test comment...");
            if (addedComment != null) {
                addedComment.setContent("Updated Test Comment " + ts);
                commentService.update(addedComment);
                System.out.println("Comment after update: " + commentService.getById(addedComment.getId()));
            } else {
                System.out.println("Test comment not found, skipping update.");
            }

            // ── 8. Delete only test records (comment → post → category) ───
            System.out.println("\n[STEP 6] Deleting test records...");
            if (addedComment != null) {
                commentService.delete(addedComment.getId());
                System.out.println("Test comment deleted (id=" + addedComment.getId() + ")");
            }
            if (addedPost != null) {
                postService.delete(addedPost.getId());
                System.out.println("Test post deleted (id=" + addedPost.getId() + ")");
            }
            if (addedCategory != null) {
                categoryService.delete(addedCategory.getId());
                System.out.println("Test category deleted (id=" + addedCategory.getId() + ")");
            }

            System.out.println("\nAll CRUD tests completed successfully.");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}