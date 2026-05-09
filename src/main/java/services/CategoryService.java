package services;

import entities.Category;
import utils.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryService implements CrudService<Category> {

    private final Connection connection;

    public CategoryService() {
        try {
            connection = Database.getInstance().getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    @Override
    public void add(Category category) {
        String query = "INSERT INTO category (name) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, category.getName());
            ps.executeUpdate();
            System.out.println("Category added successfully.");
        } catch (SQLException e) {
            System.out.println("Error adding category: " + e.getMessage());
        }
    }

    @Override
    public void update(Category category) {
        String query = "UPDATE category SET name = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, category.getName());
            ps.setInt(2, category.getId());
            ps.executeUpdate();
            System.out.println("Category updated successfully.");
        } catch (SQLException e) {
            System.out.println("Error updating category: " + e.getMessage());
        }
    }

    @Override
    public void delete(int id) {
        String query = "DELETE FROM category WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            System.out.println("Category deleted successfully.");
        } catch (SQLException e) {
            System.out.println("Error deleting category: " + e.getMessage());
        }
    }

    @Override
    public List<Category> getAll() {
        List<Category> categories = new ArrayList<>();
        String query = "SELECT * FROM category";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                Category category = new Category(
                        rs.getInt("id"),
                        rs.getString("name")
                );
                categories.add(category);
            }
        } catch (SQLException e) {
            System.out.println("Error fetching categories: " + e.getMessage());
        }
        return categories;
    }

    @Override
    public Category getById(int id) {
        String query = "SELECT * FROM category WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Category(
                        rs.getInt("id"),
                        rs.getString("name")
                );
            }
        } catch (SQLException e) {
            System.out.println("Error fetching category: " + e.getMessage());
        }
        return null;
    }
}