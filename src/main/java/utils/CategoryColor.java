package utils;

public class CategoryColor {

    private static final String[] PALETTE = {
        "#E8956D", // orange
        "#4A6FA5", // blue
        "#10B981", // green
        "#8B5CF6", // purple
        "#EC4899", // pink
        "#06B6D4", // teal
        "#F59E0B", // amber
        "#EF4444"  // red
    };

    public static String forId(int categoryId) {
        if (categoryId <= 0) return PALETTE[0];
        return PALETTE[(categoryId - 1) % PALETTE.length];
    }
}